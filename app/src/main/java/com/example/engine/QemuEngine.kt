package com.example.engine

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.*
import java.io.*
import java.util.concurrent.TimeUnit

/**
 * Pure QEMU VM engine.
 * Manages the QEMU process lifecycle with full system emulation.
 *
 * Two boot modes:
 *  - DISK: a prebuilt distro image (Alpine + Node + Go + coding agents) is
 *    installed under filesDir/qemu/distro. Boots from a persistent virtio disk.
 *  - NETBOOT (fallback): Alpine netboot kernel/initramfs from APK assets.
 */
class QemuEngine(
    private val context: Context
) {
    private var process: Process? = null
    private var outputThread: Thread? = null
    private var errorThread: Thread? = null
    private var scope: CoroutineScope? = null
    private val writeLock = Any()

    private val workDir: File by lazy {
        File(context.filesDir, "qemu").apply { mkdirs() }
    }

    /** Directory holding the downloaded prebuilt distro (image + matching kernel). */
    private val distroDir: File by lazy {
        File(workDir, "distro").apply { mkdirs() }
    }

    private val distroRootfs: File get() = File(distroDir, "rootfs.img")
    private val distroKernel: File get() = File(distroDir, "vmlinuz-lts")
    private val distroInitrd: File get() = File(distroDir, "initramfs-lts")

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
    internal fun resolveQemuBinary(): File {
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

    /** Which boot mode the next [start] will use. */
    fun bootMode(): BootMode =
        if (isDistroInstalled()) BootMode.DISTRO else BootMode.NETBOOT

    fun isDistroInstalled(): Boolean =
        distroRootfs.exists() && distroRootfs.length() > MIN_ROOTFS_BYTES &&
            distroKernel.exists() && distroKernel.length() > 0L &&
            distroInitrd.exists() && distroInitrd.length() > 0L

    /**
     * Start QEMU with full system emulation.
     *
     * @param onChunk raw bytes written by the guest to the serial console. The
     *        stream is NOT line-based: ANSI escapes and partial UTF-8 sequences
     *        must survive, exactly like a real terminal.
     * @param onError diagnostics from QEMU's stderr (does not imply a dead VM).
     * @param onComplete invoked once the QEMU process has exited.
     */
    fun start(
        onChunk: (ByteArray) -> Unit,
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

                val startedProcess = processBuilder.start()
                process = startedProcess

                // Raw byte pump: guest output -> terminal. Buffered per read,
                // never split by lines (escape sequences must stay intact).
                outputThread = Thread {
                    try {
                        val input = startedProcess.inputStream
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read > 0) onChunk(buffer.copyOf(read))
                        }
                    } catch (_: IOException) {
                        // Stream closed when the process dies - expected.
                    }
                }.apply { start() }

                // Stderr is diagnostics only (QEMU prints benign warnings there
                // while the VM runs perfectly fine).
                errorThread = Thread {
                    try {
                        BufferedReader(InputStreamReader(startedProcess.errorStream)).use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                line?.let { onError(it) }
                            }
                        }
                    } catch (_: Exception) {
                        // Ignore
                    }
                }.apply { start() }

                // Wait for process completion
                startedProcess.waitFor()
                onComplete()
            } catch (e: Exception) {
                onError("Failed to start QEMU: ${e.message}")
                onComplete()
            }
        }
    }

    /**
     * Send raw bytes to QEMU's stdin EXACTLY as given (no newline appended).
     * This is what makes Ctrl+C (0x03), Tab (0x09), escape sequences and
     * interactive TUI input work like a real terminal.
     */
    fun sendRaw(bytes: ByteArray) {
        val currentProcess = process ?: return
        try {
            synchronized(writeLock) {
                currentProcess.outputStream.let { stream ->
                    stream.write(bytes)
                    stream.flush()
                }
            }
        } catch (_: Exception) {
            // Ignore write errors (process exiting)
        }
    }

    /**
     * Send one line of input followed by a newline.
     */
    fun sendInput(input: String) {
        sendRaw((input + "\n").toByteArray(Charsets.UTF_8))
    }

    /**
     * Stop QEMU. SIGTERM is QEMU's graceful shutdown: it flushes the block
     * layer, so a distro disk stays consistent. Falls back to SIGKILL.
     */
    fun stop() {
        try {
            process?.let { p ->
                p.destroy() // SIGTERM -> QEMU flushes and exits
                if (!p.waitFor(3, TimeUnit.SECONDS)) {
                    p.destroyForcibly()
                    p.waitFor(2, TimeUnit.SECONDS)
                }
            }

            outputThread?.interrupt()
            errorThread?.interrupt()

            scope?.cancel()
        } catch (_: Exception) {
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
        // resolveQemuBinary); the kernel and initramfs are extracted here.
        // In DISK mode the distro's own kernel/initramfs are used instead.

        if (!kernel.exists() || kernel.length() == 0L) {
            extractAsset(assets, "alpine/vmlinuz-lts", kernel)
        }
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
     * Validate that all assets required for the active boot mode are present.
     */
    internal fun validateAssets() {
        val missing = mutableListOf<String>()

        if (!qemuBinary.exists()) {
            // Name the device ABI so an APK that lacks it is immediately obvious
            // (e.g. an x86_64-only build installed on an arm64 phone).
            val deviceAbi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
            missing.add("QEMU binary (no build bundled for this device's ABI: $deviceAbi)")
        }

        when (bootMode()) {
            BootMode.DISTRO -> {
                if (distroRootfs.length() <= MIN_ROOTFS_BYTES) missing.add("distro rootfs image")
                if (!distroKernel.exists()) missing.add("distro kernel")
                if (!distroInitrd.exists()) missing.add("distro initramfs")
            }
            BootMode.NETBOOT -> {
                if (!kernel.exists()) missing.add("Linux kernel")
                if (!initrd.exists()) missing.add("initramfs")
            }
        }

        if (missing.isNotEmpty()) {
            throw IllegalStateException("Missing required assets: ${missing.joinToString(", ")}")
        }
    }

    // ── Host-adaptive performance tuning ────────────────────────────────────

    /** vCPUs: the guest is CPU-heavy under TCG, but past ~6 there is no gain. */
    internal fun guestSmp(): Int =
        Runtime.getRuntime().availableProcessors().coerceIn(2, 6)

    /** Guest RAM scaled to the device (TCG allocates lazily, so being generous is safe). */
    internal fun guestMemoryMb(): Int {
        val actMgr = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actMgr?.getMemoryInfo(memInfo)
        val totalMb = (memInfo?.totalMem ?: 0L) / (1024L * 1024L)
        return when {
            totalMb >= 6 * 1024 -> 1536
            totalMb >= 4 * 1024 -> 1024
            else -> 768
        }
    }

    /**
     * Build the QEMU command line.
     *
     * Guest cmdline notes (validated against the Alpine netboot initramfs):
     * - NETBOOT mode has NO root= parameter. ip=dhcp + alpine_repo= activate the
     *   netboot path: the initramfs installs alpine-base into a RAM filesystem
     *   and boots it to the login prompt.
     * - dns0/dns1 pin EXTERNAL resolvers (fields 8 and 9 of ip=). The DNS
     *   server that slirp's DHCP hands out (10.0.2.3) is a virtual forwarder
     *   that reads /etc/resolv.conf on the HOST — which does not exist on
     *   Android, so name resolution silently fails there. Explicit
     *   8.8.8.8/1.1.1.1 entries make the guest query real servers through
     *   slirp's UDP NAT instead.
     * - DISK mode boots the prebuilt distro from a persistent virtio disk
     *   (root=/dev/vda, rw) — packages, files and agent installs survive reboot.
     */
    internal fun buildQemuCommand(): List<String> {
        val diskBoot = isDistroInstalled()
        val bootKernel = if (diskBoot) distroKernel else kernel
        val bootInitrd = if (diskBoot) distroInitrd else initrd

        val append = if (diskBoot) {
            // Keep boot visible: agetty's login prompt is also the readiness signal.
            "console=ttyS0 root=/dev/vda rw"
        } else {
            "console=ttyS0 quiet ip=dhcp:::::::8.8.8.8:1.1.1.1 " +
                "alpine_repo=http://dl-cdn.alpinelinux.org/alpine/latest-stable/main/"
        }

        return buildList {
            add(qemuBinary.absolutePath)
            // Point QEMU at the extracted firmware (SeaBIOS, linuxboot ROMs,
            // virtio option ROM) — without -L it cannot find bios-256k.bin.
            add("-L"); add(pcbiosDir.absolutePath)
            add("-kernel"); add(bootKernel.absolutePath)
            add("-initrd"); add(bootInitrd.absolutePath)
            add("-append"); add(append)
            // Performance: MTTCG (multi-threaded TCG) + the widest CPU model
            // TCG supports is significantly faster than the qemu64 default.
            add("-accel"); add("tcg,thread=multi")
            add("-cpu"); add("max")
            add("-smp"); add(guestSmp().toString())
            add("-m"); add(guestMemoryMb().toString())
            add("-rtc"); add("base=utc")
            add("-nographic")
            add("-serial"); add("stdio")
            add("-monitor"); add("none")
            add("-net"); add("nic,model=virtio")
            add("-net"); add("user,hostfwd=tcp::2222-:22")
            if (diskBoot) {
                // Persistent distro disk. cache=writeback keeps TCG I/O usable;
                // SIGTERM shutdown (see stop()) still flushes consistently.
                add("-drive")
                add("file=${distroRootfs.absolutePath},format=raw,if=virtio,cache=writeback")
                // Entropy for TLS handshakes (npm, apk, agent API calls).
                add("-device"); add("virtio-rng-pci")
            }
            add("-no-reboot")
            add("-nodefaults")
        }
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

    private companion object {
        /** A rootfs smaller than this is a truncated download, not a distro. */
        const val MIN_ROOTFS_BYTES = 64L * 1024L * 1024L
    }
}
