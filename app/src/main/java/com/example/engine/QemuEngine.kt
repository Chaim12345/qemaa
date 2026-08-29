package com.example.engine

import android.content.Context
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TerminalWhite
import com.example.ui.theme.TerminalYellow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class QemuEngine(private val context: Context) {

  private val qemuDir: File by lazy {
    val dir = File(context.filesDir, "qemu")
    if (!dir.exists()) dir.mkdirs()
    dir
  }

  private val alpineDir: File by lazy {
    val dir = File(context.filesDir, "alpine")
    if (!dir.exists()) dir.mkdirs()
    dir
  }

  private var qemuProcess: Process? = null
  private var stdinWriter: OutputStreamWriter? = null
  private var isRunning = false
  private var outputThread: Thread? = null

  suspend fun startVm(
    onOutput: (String) -> Unit,
    onError: (String) -> Unit,
    onComplete: () -> Unit
  ) = withContext(Dispatchers.IO) {
    try {
      // Extract assets on first run
      if (!File(alpineDir, "vmlinuz-lts").exists()) {
        onOutput("Extracting Alpine Linux kernel...")
        extractAsset("alpine/vmlinuz-lts", File(alpineDir, "vmlinuz-lts"))
      }
      if (!File(alpineDir, "initramfs-lts").exists()) {
        onOutput("Extracting Alpine Linux initramfs...")
        extractAsset("alpine/initramfs-lts", File(alpineDir, "initramfs-lts"))
      }

      val kernel = File(alpineDir, "vmlinuz-lts")
      val initrd = File(alpineDir, "initramfs-lts")

      if (!kernel.exists() || !initrd.exists()) {
        onError("Alpine kernel/initrd not found in APK assets")
        return@withContext
      }

      // Try to find QEMU binary
      val qemuBinary = findQemuBinary()
      if (qemuBinary == null) {
        onError("QEMU binary not bundled in APK. Build with GitHub Actions to include it.")
        onOutput("")
        onOutput("This APK was built without the QEMU binary.")
        onOutput("The GitHub Actions workflow compiles QEMU from source")
        onOutput("and bundles it in the APK assets.")
        onOutput("")
        onOutput("For now, you can use the native shell mode to execute")
        onOutput("commands directly on the Android Linux kernel.")
        onOutput("Type 'uname -a' or 'cat /proc/cpuinfo' to see host info.")
        onComplete()
        return@withContext
      }

      qemuBinary.setExecutable(true)

      onOutput("Starting QEMU virtual machine...")
      onOutput("Binary: ${qemuBinary.absolutePath}")
      onOutput("Kernel: ${kernel.name} (${kernel.length() / 1024 / 1024}MB)")
      onOutput("Initrd: ${initrd.name} (${initrd.length() / 1024 / 1024}MB)")
      onOutput("")

      val cmd = listOf(
        qemuBinary.absolutePath,
        "-kernel", kernel.absolutePath,
        "-initrd", initrd.absolutePath,
        "-append", "console=ttyS0 quiet modules=loop,squashfs sd-mod usb-storage",
        "-m", "256",
        "-smp", "2",
        "-nographic",
        "-serial", "stdio",
        "-no-reboot",
        "-nodefaults",
        "-no-user-config"
      )

      val pb = ProcessBuilder(cmd)
      pb.redirectErrorStream(true)
      pb.directory(context.filesDir)

      qemuProcess = pb.start()
      stdinWriter = OutputStreamWriter(qemuProcess!!.outputStream)
      isRunning = true

      // Read output
      outputThread = Thread {
        try {
          val reader = BufferedReader(InputStreamReader(qemuProcess!!.inputStream))
          var line: String?
          while (isRunning && reader.readLine().also { line = it } != null) {
            line?.let { onOutput(it) }
          }
        } catch (e: Exception) {
          if (isRunning) onError("Read error: ${e.message}")
        } finally {
          isRunning = false
          onComplete()
        }
      }.also { it.start() }

    } catch (e: Exception) {
      onError("Failed to start QEMU: ${e.message}")
      isRunning = false
      onComplete()
    }
  }

  fun sendInput(input: String) {
    if (!isRunning || stdinWriter == null) return
    try {
      stdinWriter?.write("$input\n")
      stdinWriter?.flush()
    } catch (e: Exception) { /* ignore */ }
  }

  fun stopVm() {
    isRunning = false
    try {
      stdinWriter?.close()
      qemuProcess?.destroy()
      qemuProcess?.waitFor()
      outputThread?.join(2000)
    } catch (e: Exception) { /* ignore */ }
    qemuProcess = null
    stdinWriter = null
    outputThread = null
  }

  private fun findQemuBinary(): File? {
    // Check in assets directory
    val fromAssets = File(qemuDir, "qemu-system-x86_64")
    if (fromAssets.exists()) return fromAssets

    // Try to extract from APK assets
    try {
      val outFile = File(qemuDir, "qemu-system-x86_64")
      if (!outFile.exists()) {
        context.assets.open("qemu/qemu-system-x86_64").use { input ->
          outFile.outputStream().use { output ->
            input.copyTo(output)
          }
        }
      }
      if (outFile.exists()) return outFile
    } catch (e: Exception) {
      // QEMU binary not in assets
    }
    return null
  }

  private fun extractAsset(assetPath: String, outFile: File) {
    try {
      context.assets.open(assetPath).use { input ->
        outFile.outputStream().use { output ->
          input.copyTo(output)
        }
      }
    } catch (e: Exception) {
      // Asset not found
    }
  }
}
