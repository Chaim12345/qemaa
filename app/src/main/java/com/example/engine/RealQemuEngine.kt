package com.example.engine

import android.content.Context
import android.util.Log
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TerminalDimText
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
import java.util.concurrent.TimeUnit

/**
 * Real QEMU Process Engine - Executes actual QEMU virtual machines on Android
 * Requires: qemu-system-x86_64, qemu-system-aarch64, etc. binaries available in PATH or bundled
 */
class RealQemuEngine(
  private val context: Context,
  private val onOutputLine: (TerminalLine) -> Unit,
  private val onStatusChange: (Boolean) -> Unit
) {
  companion object {
    private const val TAG = "RealQemuEngine"
  }

  private var qemuProcess: Process? = null
  private var outputReader: BufferedReader? = null
  private var errorReader: BufferedReader? = null
  private var inputWriter: OutputStreamWriter? = null
  private var isRunning = false
  private var outputThread: Thread? = null
  private var errorThread: Thread? = null

  private val qemuDir: File by lazy {
    val dir = File(context.filesDir, "qemu")
    if (!dir.exists()) dir.mkdirs()
    dir
  }

  private val vmImagesDir: File by lazy {
    val dir = File(context.filesDir, "vm_images")
    if (!dir.exists()) dir.mkdirs()
    dir
  }

  /**
   * Start a real QEMU virtual machine
   */
  suspend fun startVm(cliArgs: String): Boolean = withContext(Dispatchers.IO) {
    try {
      stopVm() // Ensure any existing VM is stopped

      // Parse the CLI command into arguments
      val args = parseCommandLine(cliArgs)
      
      Log.d(TAG, "Starting QEMU with args: ${args.joinToString(" ")}")
      onOutputLine(TerminalLine("=== Starting Real QEMU Process ===", TerminalCyan, isSystem = true))
      onOutputLine(TerminalLine("$ ${args.joinToString(" ")}", TerminalDimText))

      // Build ProcessBuilder
      val argsArray = args.toTypedArray()
      val pb = ProcessBuilder(*argsArray)
      pb.directory(qemuDir)
      
      // Set up environment
      val env = pb.environment()
      env["HOME"] = qemuDir.absolutePath
      env["TMPDIR"] = context.cacheDir.absolutePath
      
      // Redirect stderr to stdout for unified output
      pb.redirectErrorStream(true)

      // Start the process
      qemuProcess = pb.start()
      isRunning = true
      onStatusChange(true)

      // Set up streams
      inputWriter = OutputStreamWriter(qemuProcess!!.outputStream)
      outputReader = BufferedReader(InputStreamReader(qemuProcess!!.inputStream))
      errorReader = BufferedReader(InputStreamReader(qemuProcess!!.errorStream))

      // Start output reading threads
      outputThread = Thread { readOutput() }
      outputThread?.start()

      errorThread = Thread { readError() }
      errorThread?.start()

      onOutputLine(TerminalLine("QEMU process started (PID: ${getProcessPid()})", TerminalGreen, isSystem = true))
      onOutputLine(TerminalLine("Waiting for VM to boot...", TerminalYellow))
      
      true
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start QEMU", e)
      onOutputLine(TerminalLine("Failed to start QEMU: ${e.message}", TerminalRed, isError = true))
      onStatusChange(false)
      false
    }
  }

  /**
   * Stop the running QEMU VM
   */
  suspend fun stopVm(): Unit = withContext(Dispatchers.IO) {
    try {
      if (!isRunning && qemuProcess == null) return@withContext

      onOutputLine(TerminalLine("Stopping QEMU VM...", TerminalYellow, isSystem = true))

      // Try graceful shutdown first via QEMU monitor command
      sendQemuCommand("system_powerdown")
      
      // Wait a bit for graceful shutdown
      kotlin.runCatching {
        qemuProcess?.waitFor(3, TimeUnit.SECONDS)
      }

      // Force destroy if still running
      if (qemuProcess?.isAlive == true) {
        onOutputLine(TerminalLine("Force terminating QEMU process...", TerminalRed))
        qemuProcess?.destroyForcibly()
      }

      // Clean up resources
      cleanupResources()
      
      isRunning = false
      onStatusChange(false)
      onOutputLine(TerminalLine("QEMU VM stopped.", TerminalGreen, isSystem = true))
    } catch (e: Exception) {
      Log.e(TAG, "Error stopping QEMU", e)
      onOutputLine(TerminalLine("Error stopping VM: ${e.message}", TerminalRed, isError = true))
    }
  }

  /**
   * Send a command to QEMU monitor (if supported)
   */
  fun sendQemuCommand(cmd: String) {
    try {
      inputWriter?.apply {
        write("$cmd\n")
        flush()
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to send command to QEMU: $cmd", e)
    }
  }

  /**
   * Pause VM execution (QEMU 'stop' command)
   */
  suspend fun pauseVm(): Unit = withContext(Dispatchers.IO) {
    if (isRunning) {
      sendQemuCommand("stop")
      onOutputLine(TerminalLine("VM execution paused (QEMU monitor 'stop')", TerminalYellow, isSystem = true))
    }
  }

  /**
   * Resume VM execution (QEMU 'cont' command)
   */
  suspend fun resumeVm(): Unit = withContext(Dispatchers.IO) {
    if (isRunning) {
      sendQemuCommand("cont")
      onOutputLine(TerminalLine("VM execution resumed (QEMU monitor 'cont')", TerminalGreen, isSystem = true))
    }
  }

  /**
   * Send ACPI power event to guest
   */
  suspend fun sendPowerEvent(event: String): Unit = withContext(Dispatchers.IO) {
    when (event.lowercase()) {
      "powerdown", "shutdown" -> sendQemuCommand("system_powerdown")
      "reset" -> sendQemuCommand("system_reset")
    }
  }

  /**
   * Check if VM is currently running
   */
  fun isVmRunning(): Boolean = isRunning && (qemuProcess?.isAlive == true)

  /**
   * Get QEMU process PID
   */
  private fun getProcessPid(): Long {
    return try {
      qemuProcess?.let { 
        it.javaClass.getDeclaredMethod("pid").invoke(it) as Long 
      } ?: -1L
    } catch (e: Exception) {
      -1L
    }
  }

  /**
   * Read from QEMU stdout
   */
  private fun readOutput() {
    try {
      outputReader?.use { reader ->
        var line: String?
        while (reader.readLine().also { line = it } != null && isRunning) {
          line?.let {
            onOutputLine(TerminalLine(it, TerminalWhite))
          }
        }
      }
    } catch (e: Exception) {
      if (isRunning) {
        Log.w(TAG, "Error reading QEMU output", e)
      }
    }
  }

  /**
   * Read from QEMU stderr
   */
  private fun readError() {
    try {
      errorReader?.use { reader ->
        var line: String?
        while (reader.readLine().also { line = it } != null && isRunning) {
          line?.let {
            onOutputLine(TerminalLine(it, TerminalRed, isError = true))
          }
        }
      }
    } catch (e: Exception) {
      if (isRunning) {
        Log.w(TAG, "Error reading QEMU error stream", e)
      }
    }
  }

  /**
   * Parse command line string into argument list
   * Handles quoted strings and spaces properly
   */
  private fun parseCommandLine(cli: String): List<String> {
    val args = mutableListOf<String>()
    val parts = cli.split(' ').filter { it.isNotBlank() }
    
    var i = 0
    while (i < parts.size) {
      var arg = parts[i]
      
      // Handle arguments with spaces in quotes
      if (arg.startsWith("\"") || arg.startsWith("'")) {
        val quoteChar = arg.first()
        val combinedArgs = mutableListOf(arg.removeSurrounding(quoteChar.toString()))
        
        var j = i + 1
        while (j < parts.size) {
          val nextArg = parts[j]
          combinedArgs.add(nextArg)
          
          if (nextArg.endsWith(quoteChar)) {
            break
          }
          j++
        }
        
        arg = combinedArgs.joinToString(" ").removeSurrounding(quoteChar.toString())
        i = j
      }
      
      args.add(arg)
      i++
    }
    
    return args
  }

  /**
   * Clean up all resources
   */
  private fun cleanupResources() {
    try {
      outputThread?.interrupt()
      errorThread?.interrupt()
      outputReader?.close()
      errorReader?.close()
      inputWriter?.close()
    } catch (e: Exception) {
      Log.w(TAG, "Error cleaning up resources", e)
    }
    
    outputThread = null
    errorThread = null
    outputReader = null
    errorReader = null
    inputWriter = null
    qemuProcess = null
  }

  /**
   * Get current QEMU CLI that would be used
   */
  fun getCurrentCli(vmName: String, arch: String): String {
    return when (arch.lowercase()) {
      "aarch64", "arm64" -> "qemu-system-aarch64"
      "riscv64" -> "qemu-system-riscv64"
      "i386", "x86" -> "qemu-system-i386"
      else -> "qemu-system-x86_64"
    }
  }

  /**
   * Check if QEMU binary is available
   */
  fun checkQemuAvailability(arch: String): QemuAvailabilityResult {
    val binary = getCurrentCli("", arch)
    return try {
      val pb = ProcessBuilder(binary, "-version")
      val process = pb.start()
      val exitCode = process.waitFor()
      
      if (exitCode == 0) {
        val versionOutput = BufferedReader(InputStreamReader(process.inputStream)).readLines().firstOrNull() ?: "Unknown version"
        QemuAvailabilityResult(available = true, version = versionOutput, binary = binary)
      } else {
        QemuAvailabilityResult(available = false, error = "QEMU returned exit code $exitCode", binary = binary)
      }
    } catch (e: Exception) {
      QemuAvailabilityResult(available = false, error = e.message ?: "Unknown error", binary = binary)
    }
  }
}

data class QemuAvailabilityResult(
  val available: Boolean,
  val version: String = "",
  val error: String = "",
  val binary: String = ""
)
