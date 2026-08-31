package com.example.engine

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * Installs the prebuilt distro (Alpine + Node.js + Go + the pi coding agent +
 * opencode) produced by CI and published as GitHub release assets.
 *
 * The image is downloaded as a gzip stream, decompressed on the fly and
 * SHA-256-verified, so the device only pays once for the transfer and never
 * stores the compressed copy.
 */
class DistroManager(private val context: Context) {

    sealed class DistroState {
        data object NotInstalled : DistroState()
        data class Downloading(val percent: Int, val downloadedMb: Long, val totalMb: Long) : DistroState()
        data class Verifying(val downloadedMb: Long) : DistroState()
        data class Installed(val sizeGb: Float, val manifest: String?) : DistroState()
        data class Failed(val error: String) : DistroState()
        data object Uninstalling : DistroState()
    }

    /** Where the distro files live (shared with [QemuEngine]). */
    private val distroDir: File
        get() = File(context.filesDir, "qemu/distro").apply { mkdirs() }

    val rootfsFile: File get() = File(distroDir, "rootfs.img")
    val kernelFile: File get() = File(distroDir, "vmlinuz-lts")
    val initrdFile: File get() = File(distroDir, "initramfs-lts")
    private val manifestFile: File get() = File(distroDir, "manifest.txt")

    fun isInstalled(): Boolean =
        rootfsFile.length() > 0L && kernelFile.length() > 0L && initrdFile.length() > 0L

    fun installedSizeBytes(): Long =
        listOf(rootfsFile, kernelFile, initrdFile, manifestFile)
            .filter { it.exists() }
            .sumOf { it.length() }

    fun manifestText(): String? = manifestFile.takeIf { it.exists() }?.readText()

    fun currentInstanceState(): DistroState =
        if (isInstalled()) {
            DistroState.Installed(
                sizeGb = installedSizeBytes() / (1024f * 1024f * 1024f),
                manifest = manifestText()
            )
        } else {
            DistroState.NotInstalled
        }

    fun uninstall() {
        distroDir.deleteRecursively()
    }

    /**
     * Download + verify + install the distro. Reports progress through [onState].
     */
    suspend fun install(
        assets: DistroAssets?,
        onState: (DistroState) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
        val resolved = assets ?: try {
            resolveDefaultAssets()
        } catch (e: Exception) {
            onState(DistroState.Failed("Could not resolve distro download: ${e.message}"))
            return@withContext
        }

        try {
            distroDir.mkdirs()

            // Small files first: kernel + initramfs (must match the rootfs).
            downloadToFile(resolved.kernelUrl, kernelFile)
            downloadToFile(resolved.initrdUrl, initrdFile)

            // Optional but expected: the manifest shown after install.
            if (resolved.manifestUrl != null) {
                runCatching { downloadToFile(resolved.manifestUrl, manifestFile) }
            }

            // The rootfs image: streamed gzip -> raw ext4 with progress + digest.
            val partFile = File(distroDir, "rootfs.img.part")
            partFile.delete()

            val expectedSha = resolved.sha256Url?.let { url ->
                runCatching { fetchText(url).trim().lowercase().substringBefore(' ') }.getOrNull()
            }

            val digest = MessageDigest.getInstance("SHA-256")
            val connection = openConnection(resolved.imageUrl)
            val totalBytes = connection.contentLengthLong
            var compressedRead = 0L

            try {
                CountingInputStream(connection.inputStream).use { counting ->
                    counting.onRead = { n -> compressedRead += n }

                    GZIPInputStream(counting, 1 shl 16).use { gzip ->
                        FileOutputStream(partFile).use { out ->
                            val buffer = ByteArray(1 shl 16)
                            while (true) {
                                val read = gzip.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                digest.update(buffer, 0, read)

                                if (totalBytes > 0) {
                                    val percent = ((compressedRead * 100) / totalBytes).toInt().coerceIn(0, 100)
                                    onState(
                                        DistroState.Downloading(
                                            percent = percent,
                                            downloadedMb = compressedRead / (1024 * 1024),
                                            totalMb = totalBytes / (1024 * 1024)
                                        )
                                    )
                                } else {
                                    onState(
                                        DistroState.Downloading(
                                            percent = -1,
                                            downloadedMb = compressedRead / (1024 * 1024),
                                            totalMb = -1
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }

            onState(DistroState.Verifying(partFile.length() / (1024 * 1024)))

            val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
            if (expectedSha != null && actualSha != expectedSha) {
                partFile.delete()
                onState(DistroState.Failed("Checksum mismatch (expected $expectedSha, got $actualSha)"))
                return@withContext
            }
            if (partFile.length() < 64L * 1024L * 1024L) {
                partFile.delete()
                onState(DistroState.Failed("Downloaded image looks truncated (${partFile.length()} bytes)"))
                return@withContext
            }

            if (!partFile.renameTo(rootfsFile)) {
                rootfsFile.delete()
                if (!partFile.renameTo(rootfsFile)) {
                    partFile.delete()
                    onState(DistroState.Failed("Could not finalize the distro image"))
                    return@withContext
                }
            }

            onState(currentInstanceState())
        } catch (e: Exception) {
            runCatching { File(distroDir, "rootfs.img.part").delete() }
            onState(DistroState.Failed("Distro install failed: ${e.message ?: e.javaClass.simpleName}"))
        }
    }

    // ── GitHub release asset resolution ─────────────────────────────────────

    /** Asset names produced by .github/workflows/android-build.yml (build-distro job). */
    data class DistroAssets(
        val imageUrl: String,
        val sha256Url: String?,
        val kernelUrl: String,
        val initrdUrl: String,
        val manifestUrl: String?
    )

    fun resolveDefaultAssets(): DistroAssets {
        val json = fetchText("$GITHUB_API/releases/latest")
        return parseReleaseJson(json)
    }

    internal fun parseReleaseJson(json: String): DistroAssets {
        val release = JSONObject(json)
        val byName = HashMap<String, String>()
        val assets = release.optJSONArray("assets") ?: JSONArray()
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (name.isNotEmpty() && url.isNotEmpty()) byName[name] = url
        }

        val image = byName[ASSET_IMAGE]
            ?: throw IllegalStateException("release has no '$ASSET_IMAGE' asset")
        val kernel = byName[ASSET_KERNEL]
            ?: throw IllegalStateException("release has no '$ASSET_KERNEL' asset")
        val initrd = byName[ASSET_INITRD]
            ?: throw IllegalStateException("release has no '$ASSET_INITRD' asset")

        return DistroAssets(
            imageUrl = image,
            sha256Url = byName[ASSET_IMAGE_SHA256],
            kernelUrl = kernel,
            initrdUrl = initrd,
            manifestUrl = byName[ASSET_MANIFEST]
        )
    }

    // ── Minimal HTTP helpers (zero extra dependencies) ───────────────────────

    private fun openConnection(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("User-Agent", "linux-vm-android")
        return connection
    }

    private fun fetchText(url: String): String {
        val connection = openConnection(url)
        try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadToFile(url: String, dest: File) {
        val connection = openConnection(url)
        try {
            val tmp = File(dest.parentFile, dest.name + ".part")
            connection.inputStream.use { input ->
                FileOutputStream(tmp).use { output -> input.copyTo(output) }
            }
            if (!tmp.renameTo(dest)) {
                dest.delete()
                check(tmp.renameTo(dest)) { "could not move ${dest.name} into place" }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** InputStream wrapper that reports how many (compressed) bytes were read. */
    private class CountingInputStream(input: InputStream) : InputStream() {
        var onRead: (Int) -> Unit = {}
        private val delegate = input

        override fun read(): Int {
            val b = delegate.read()
            if (b >= 0) onRead(1)
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = delegate.read(b, off, len)
            if (n > 0) onRead(n)
            return n
        }

        override fun close() = delegate.close()
    }

    companion object {
        private const val GITHUB_API = "https://api.github.com/repos/Chaim12345/qemaa"

        const val ASSET_IMAGE = "linux-vm-rootfs.img.gz"
        const val ASSET_IMAGE_SHA256 = "linux-vm-rootfs.img.gz.sha256"
        const val ASSET_KERNEL = "linux-vm-vmlinuz-lts"
        const val ASSET_INITRD = "linux-vm-initramfs-lts"
        const val ASSET_MANIFEST = "linux-vm-distro-manifest.txt"

        /** Base64 of UTF-8 text — convenience for callers feeding the terminal. */
        fun encodeForTerminal(text: String): String =
            Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }
}
