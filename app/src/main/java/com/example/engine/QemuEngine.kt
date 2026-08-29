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
        File(workDir, "qemu-system-x86_64")
    }

    private val kernel: File by lazy {
        File(workDir, "vmlinuz-lts")
    }

    private val initrd: File by lazy {
        File(workDir, "initramfs-lts")
    }

    private val diskImage: File by lazy {
        File(workDir, "alpine.img")
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

        // Extract QEMU binary
        if (!qemuBinary.exists() || qemuBinary.length() == 0L) {
            extractAsset(assets, "qemu/qemu-system-x86_64", qemuBinary)
            qemuBinary.setExecutable(true)
        }

        // Extract kernel
        if (!kernel.exists() || kernel.length() == 0L) {
            extractAsset(assets, "alpine/vmlinuz-lts", kernel)
        }

        // Extract initrd
        if (!initrd.exists() || initrd.length() == 0L) {
            extractAsset(assets, "alpine/initramfs-lts", initrd)
        }

        // Extract disk image
        if (!diskImage.exists() || diskImage.length() == 0L) {
            extractAsset(assets, "alpine/alpine.img", diskImage)
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
        if (!diskImage.exists()) missing.add("Disk image")

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
            "-kernel", kernel.absolutePath,
            "-initrd", initrd.absolutePath,
            "-drive", "file=${diskImage.absolutePath},format=raw,if=virtio",
            "-append", "root=/dev/vda console=ttyS0 quiet",
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
