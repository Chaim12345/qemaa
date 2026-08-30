package com.example.engine

import android.content.Context
import kotlinx.coroutines.*
import java.io.*

/**
 * Pure QEMU VM engine.
 * Manages QEMU process lifecycle with full system emulation.
 */
class QemuEngine(
    private val context: Context
) {
    private var process: Process? = null
    private var outputThread: Thread? = null
    private var errorThread: Thread? = null
    private var scope: CoroutineScope? = null

    private val workDir: File by lazy {
        File(context.filesDir, "qemu").apply { mkdirs() }
    }

    private val qemuBinary: File by lazy {
        resolveQemuBinary()
    }

    private val kernel: File by lazy {
        File(workDir, "vmlinuz-lts")
    }

    private val initrd: File by lazy {
        File(workDir, "initramfs-lts")
    }

    private val pcbiosDir: File by lazy {
        File(workDir, "pc-bios").apply { mkdirs() }
    }

    /**
     * Resolve the QEMU executable location.
     *
     * Preferred source: the binary is packaged as a native library (jniLibs), so the
     * package installer extracts it into nativeLibraryDir. This is the only location
     * from which an app targeting API 29+ may exec() a file, because SELinux (W^X)
     * denies executing anything inside the app's writable data directory.
     *
     * Legacy fallback: builds that bundle the binary as an APK asset instead extract
     * it to filesDir. That only works when exec from app data is permitted (old
     * targetSdk levels); on modern targets it fails with "Permission denied".
     */
    private fun resolveQemuBinary(): File {
        val nativeBinary = File(
            context.applicationInfo.nativeLibraryDir,
            "libqemu-system-x86_64.so"
        )
        if (nativeBinary.exists() && nativeBinary.length() > 0L) {
            return nativeBinary
        }

        val assetBinary = File(workDir, "qemu-system-x86_64")
        if (!assetBinary.exists() || assetBinary.length() == 0L) {
            try {
                extractAsset(context.assets, "qemu/qemu-system-x86_64", assetBinary)
                assetBinary.setExecutable(true)
            } catch (_: Exception) {
                // validateAssets() reports the missing binary with a clear message.
            }
        }
        return assetBinary
    }

    /**
     * Start QEMU with full system emulation.
     */
    fun start(
        onOutput: (String) -> Unit,
        onError: (String) -> Unit,
        onComplete: () -> Unit
    ) {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        scope?.launch {
            try {
                // Extract assets if not present
                extractAssetsIfNeeded()

                // Validate required files
                validateAssets()

                // Build QEMU command
                val command = buildQemuCommand()

                // Start process
                val processBuilder = ProcessBuilder(command)
                processBuilder.directory(workDir)
                processBuilder.redirectErrorStream(false)

                process = processBuilder.start()

                // Start output reader
                outputThread = Thread {
                    try {
                        BufferedReader(InputStreamReader(process!!.inputStream)).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                line?.let { onOutput(it) }
                            }
                        }
                    } catch (e: Exception) {
                        if (process?.isAlive == true) {
                            onError("Output stream error: ${e.message}")
                        }
                    }
                }.apply { start() }

                // Start error reader
                errorThread = Thread {
                    try {
                        BufferedReader(InputStreamReader(process!!.errorStream)).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                line?.let { onError("QEMU: $it") }
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }.apply { start() }

                // Wait for process completion
                val exitCode = process?.waitFor() ?: -1
                onComplete()

            } catch (e: Exception) {
                onError("Failed to start QEMU: ${e.message}")
                onComplete()
            }
        }
    }

    /**
     * Send input to QEMU's stdin.
     */
    fun sendInput(input: String) {
        try {
            process?.outputStream?.let { stream ->
                stream.write("$input\n".toByteArray())
                stream.flush()
            }
        } catch (e: Exception) {
            // Ignore write errors
        }
    }

    /**
     * Stop QEMU process.
     */
    fun stop() {
        try {
            // Send quit command to QEMU monitor
            sendInput("quit")

            // Wait a bit for graceful shutdown
            Thread.sleep(500)

            // Force kill if still running
            process?.destroy()
            process?.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)

            // Cleanup threads
            outputThread?.interrupt()
            errorThread?.interrupt()

            // Cancel coroutine scope
            scope?.cancel()

        } catch (e: Exception) {
            // Ignore cleanup errors
        } finally {
            process = null
            outputThread = null
            errorThread = null
            scope = null
        }
    }

    /**
     * Check if QEMU is currently running.
     */
    fun isRunning(): Boolean {
        return process?.isAlive == true
    }

    /**
     * Extract QEMU and Linux assets from APK if not already present.
     */
    private suspend fun extractAssetsIfNeeded() = withContext(Dispatchers.IO) {
        val assets = context.assets

        // The QEMU binary itself is resolved from nativeLibraryDir (see
        // resolveQemuBinary); only the kernel and initramfs are extracted here.

        // Extract kernel
        if (!kernel.exists() || kernel.length() == 0L) {
            extractAsset(assets, "alpine/vmlinuz-lts", kernel)
        }

        // Extract initrd
        if (!initrd.exists() || initrd.length() == 0L) {
            extractAsset(assets, "alpine/initramfs-lts", initrd)
        }

        // Extract the QEMU firmware directory (flat set of BIOS/ROM files).
        // Firmware is plain data — reading it from app storage is fine; only
        // executing files there is restricted.
        val firmwareFiles = assets.list("qemu/pc-bios") ?: emptyArray()
        if (firmwareFiles.isEmpty()) {
            throw IllegalStateException("Missing required assets: QEMU firmware (qemu/pc-bios)")
        }
        for (name in firmwareFiles) {
            val dest = File(pcbiosDir, name)
            if (!dest.exists() || dest.length() == 0L) {
                extractAsset(assets, "qemu/pc-bios/$name", dest)
            }
        }

    }

    /**
     * Validate that all required assets are present.
     */
    private fun validateAssets() {
        val missing = mutableListOf<String>()

        if (!qemuBinary.exists()) missing.add("QEMU binary")
        if (!kernel.exists()) missing.add("Linux kernel")
        if (!initrd.exists()) missing.add("initramfs")

        if (missing.isNotEmpty()) {
            throw IllegalStateException("Missing required assets: ${missing.joinToString(", ")}")
        }
    }

    /**
     * Build QEMU command line with full system emulation.
     */
    private fun buildQemuCommand(): List<String> {
        return listOf(
            qemuBinary.absolutePath,
            // Point QEMU at the extracted firmware (SeaBIOS, linuxboot ROMs,
            // virtio option ROM) — without -L it cannot find bios-256k.bin.
            "-L", pcbiosDir.absolutePath,
            "-kernel", kernel.absolutePath,
            "-initrd", initrd.absolutePath,
            "-append", "root=/dev/ram0 console=ttyS0 quiet ip=dhcp alpine_repo=http://dl-cdn.alpinelinux.org/alpine/latest-stable/main/",
            "-m", "512",
            "-smp", "2",
            "-nographic",
            "-serial", "stdio",
            "-monitor", "none",
            "-net", "nic,model=virtio",
            "-net", "user,hostfwd=tcp::2222-:22",
            "-no-reboot",
            "-nodefaults"
        )
    }

    /**
     * Extract a single asset from APK to filesystem.
     */
    private fun extractAsset(assets: android.content.res.AssetManager, assetPath: String, destFile: File) {
        try {
            assets.open(assetPath).use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException("Failed to extract $assetPath: ${e.message}", e)
        }
    }
}
