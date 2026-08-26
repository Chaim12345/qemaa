package com.example.engine

import com.example.data.model.VirtualMachineEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class QemuRunner(
  private val binaries: NativeBinaryExtractor.QemuBinaries,
  private val scope: CoroutineScope
) {

  enum class VmState {
    STOPPED,
    RUNNING,
    PAUSED,
    ERROR
  }

  private var qemuProcess: Process? = null
  private var writer: OutputStreamWriter? = null
  private var stdoutJob: Job? = null
  private var stderrJob: Job? = null

  var state: VmState = VmState.STOPPED
    private set

  var onError: ((String) -> Unit)? = null
  var onOutput: ((String) -> Unit)? = null
  var onStateChange: ((VmState) -> Unit)? = null

  fun start(vm: VirtualMachineEntity): Boolean {
    if (state == VmState.RUNNING) return true

    val cliCommand = QemuCliBuilder.generateQemuCli(vm)
    val args = parseArgs(cliCommand)

    return try {
      val pb = ProcessBuilder(args)
      pb.directory(File(binaries.baseDir))

      val env = pb.environment()
      env["HOME"] = binaries.baseDir
      env["TMPDIR"] = binaries.baseDir
      env["QEMU_AUDIO_DRV"] = "none"
      
      // Set library path for Termux shared libraries
      if (binaries.libDir.isNotEmpty()) {
        env["LD_LIBRARY_PATH"] = binaries.libDir
      }

      pb.redirectErrorStream(false)
      qemuProcess = pb.start()
      writer = OutputStreamWriter(qemuProcess!!.outputStream)

      state = VmState.RUNNING
      onStateChange?.invoke(VmState.RUNNING)

      startOutputReader()
      true
    } catch (e: Exception) {
      state = VmState.ERROR
      onError?.invoke("Failed to start QEMU: ${e.message}")
      false
    }
  }

  fun sendCommand(command: String) {
    if (state != VmState.RUNNING) return
    try {
      writer?.write("$command\n")
      writer?.flush()
    } catch (e: Exception) {
      // Process may have terminated
    }
  }

  fun sendRaw(bytes: ByteArray) {
    if (state != VmState.RUNNING) return
    try {
      qemuProcess?.outputStream?.write(bytes)
      qemuProcess?.outputStream?.flush()
    } catch (e: Exception) {
      // Process may have terminated
    }
  }

  fun stop() {
    if (state == VmState.STOPPED) return
    try {
      sendCommand("system_powerdown")
      Thread.sleep(500)
      qemuProcess?.destroy()
      qemuProcess?.waitFor()
    } catch (e: Exception) {
      qemuProcess?.destroyForcibly()
    } finally {
      cleanup()
      state = VmState.STOPPED
      onStateChange?.invoke(VmState.STOPPED)
    }
  }

  fun forceStop() {
    try {
      qemuProcess?.destroyForcibly()
    } catch (e: Exception) {
      // ignore
    } finally {
      cleanup()
      state = VmState.STOPPED
      onStateChange?.invoke(VmState.STOPPED)
    }
  }

  fun pause() {
    if (state != VmState.RUNNING) return
    try {
      sendMonitorCommand("stop")
      state = VmState.PAUSED
      onStateChange?.invoke(VmState.PAUSED)
    } catch (e: Exception) {
      // ignore
    }
  }

  fun resume() {
    if (state != VmState.PAUSED) return
    try {
      sendMonitorCommand("cont")
      state = VmState.RUNNING
      onStateChange?.invoke(VmState.RUNNING)
    } catch (e: Exception) {
      // ignore
    }
  }

  fun isRunning(): Boolean = state == VmState.RUNNING

  private fun sendMonitorCommand(cmd: String) {
    try {
      writer?.write("$cmd\n")
      writer?.flush()
    } catch (e: Exception) {
      // ignore
    }
  }

  private fun startOutputReader() {
    stdoutJob?.cancel()
    stderrJob?.cancel()

    val process = qemuProcess ?: return

    stdoutJob = scope.launch(Dispatchers.IO) {
      try {
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String? = reader.readLine()
        while (line != null && isActive) {
          onOutput?.invoke(line)
          line = reader.readLine()
        }
      } catch (e: Exception) {
        if (isActive) {
          onError?.invoke("stdout read error: ${e.message}")
        }
      }
    }

    stderrJob = scope.launch(Dispatchers.IO) {
      try {
        val reader = BufferedReader(InputStreamReader(process.errorStream))
        var line: String? = reader.readLine()
        while (line != null && isActive) {
          onOutput?.invoke(line)
          line = reader.readLine()
        }
      } catch (e: Exception) {
        if (isActive) {
          onError?.invoke("stderr read error: ${e.message}")
        }
      }
    }

    scope.launch(Dispatchers.IO) {
      try {
        val exitCode = process.waitFor()
        if (isActive) {
          state = VmState.STOPPED
          onStateChange?.invoke(VmState.STOPPED)
          onOutput?.invoke("QEMU process exited with code $exitCode")
        }
      } catch (e: Exception) {
        if (isActive) {
          state = VmState.ERROR
          onStateChange?.invoke(VmState.ERROR)
        }
      }
    }
  }

  private fun cleanup() {
    stdoutJob?.cancel()
    stderrJob?.cancel()
    stdoutJob = null
    stderrJob = null
    try {
      writer?.close()
    } catch (e: Exception) {
      // ignore
    }
    writer = null
    qemuProcess = null
  }

  private fun parseArgs(cli: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    var quoteChar = ' '

    for (c in cli) {
      if (inQuotes) {
        if (c == quoteChar) {
          inQuotes = false
        } else {
          current.append(c)
        }
      } else {
        when {
          c == '"' || c == '\'' -> {
            inQuotes = true
            quoteChar = c
          }
          c.isWhitespace() -> {
            if (current.isNotEmpty()) {
              tokens.add(current.toString())
              current.clear()
            }
          }
          else -> current.append(c)
        }
      }
    }
    if (current.isNotEmpty()) {
      tokens.add(current.toString())
    }
    return tokens
  }
}
