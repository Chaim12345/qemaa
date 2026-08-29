package com.example.engine

import android.content.Context
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TerminalDimText
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TerminalWhite
import com.example.ui.theme.TerminalYellow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VmManagerService(
  private val context: Context,
  private val scope: CoroutineScope
) {

  private val _terminalLines = MutableStateFlow<List<TerminalLine>>(emptyList())
  val terminalLines: StateFlow<List<TerminalLine>> = _terminalLines.asStateFlow()

  private val _vmStatus = MutableStateFlow(VmState.STOPPED)
  val vmStatus: StateFlow<VmState> = _vmStatus.asStateFlow()

  private val _virtualizationReport = MutableStateFlow(VirtualizationDetector.probeHardwareVirtualization(context))
  val virtualizationReport: StateFlow<VirtualizationReport> = _virtualizationReport.asStateFlow()

  private val realNativeEngine = RealNativeProcessEngine(context)
  private val qemuEngine = QemuEngine(context)
  private var qemuJob: Job? = null

  init {
    val report = _virtualizationReport.value
    appendTerminalLine(TerminalLine("=== REAL NATIVE LINUX VM RUNTIME ===", TerminalCyan, isSystem = true))
    appendTerminalLine(TerminalLine("Host: ${report.deviceModel}", TerminalDimText))
    appendTerminalLine(TerminalLine("Kernel: ${report.hostKernelVersion.take(60)}", TerminalDimText))
    appendTerminalLine(TerminalLine("CPU: ${report.hostArchitecture} (${report.physicalCpuCores} cores)", TerminalGreen))
    appendTerminalLine(TerminalLine("RAM: ${report.totalHostRamMb}MB total", TerminalGreen))
    appendTerminalLine(TerminalLine("KVM: ${if (report.hasKvmDevice) "Available" else "Not available (using TCG emulation)"}", TerminalYellow))
    appendTerminalLine(TerminalLine("", TerminalWhite))
    appendTerminalLine(TerminalLine("QEMU VM: Tap START to boot Alpine Linux", TerminalGreen, isSystem = true))
    appendTerminalLine(TerminalLine("Native Shell: Type commands to run on host", TerminalDimText))
    appendTerminalLine(TerminalLine("", TerminalWhite))
  }

  fun refreshVirtualizationReport() {
    _virtualizationReport.value = VirtualizationDetector.probeHardwareVirtualization(context)
  }

  fun appendTerminalLine(line: TerminalLine) {
    if (line.text == "__CLEAR__") {
      _terminalLines.value = emptyList()
    } else {
      val current = _terminalLines.value
      _terminalLines.value = (if (current.size > 2000) current.drop(current.size - 1800) else current) + line
    }
  }

  fun clearTerminal() {
    _terminalLines.value = emptyList()
  }

  fun startVm() {
    if (_vmStatus.value == VmState.RUNNING) return
    _vmStatus.value = VmState.BOOTING

    appendTerminalLine(TerminalLine("=== Booting QEMU Virtual Machine ===", TerminalCyan, isSystem = true))
    appendTerminalLine(TerminalLine("Target: Alpine Linux x86_64 (TCG software emulation)", TerminalDimText))
    appendTerminalLine(TerminalLine("", TerminalWhite))

    qemuJob = scope.launch(Dispatchers.IO) {
      qemuEngine.start(
        onOutput = { line ->
          appendTerminalLine(TerminalLine(line, TerminalWhite))
          if (line.contains("login:") || line.contains("Welcome to Alpine")) {
            _vmStatus.value = VmState.RUNNING
          }
        },
        onError = { error ->
          appendTerminalLine(TerminalLine("QEMU Error: $error", TerminalRed, isError = true))
          _vmStatus.value = VmState.ERROR
        },
        onComplete = {
          _vmStatus.value = VmState.STOPPED
          appendTerminalLine(TerminalLine("=== VM Stopped ===", TerminalYellow, isSystem = true))
        }
      )
    }
  }

  fun stopVm() {
    appendTerminalLine(TerminalLine("=== Stopping VM ===", TerminalYellow, isSystem = true))
    qemuEngine.stop()
    qemuJob?.cancel()
    qemuJob = null
    _vmStatus.value = VmState.STOPPED
  }

  fun executeTerminalInput(input: String) {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return

    // Handle VM control commands
    when (trimmed) {
      "vm:start" -> { startVm(); return }
      "vm:stop", "vm:shutdown" -> { stopVm(); return }
      "vm:status" -> {
        appendTerminalLine(TerminalLine("VM Status: ${_vmStatus.value}", TerminalCyan))
        return
      }
      "clear" -> { clearTerminal(); return }
    }

    // If VM is running, send input to QEMU
    if (_vmStatus.value == VmState.RUNNING) {
      qemuEngine.sendInput(trimmed)
      return
    }

    // Otherwise execute on native host
    val prompt = realNativeEngine.getPrompt()
    appendTerminalLine(TerminalLine("$prompt$trimmed", TerminalGreen, isPrompt = true))

    val results = realNativeEngine.executeCommand(trimmed)
    results.forEach { appendTerminalLine(it) }
  }

  fun getActivePrompt(): String {
    return if (_vmStatus.value == VmState.RUNNING) {
      "" // VM handles its own prompt
    } else {
      realNativeEngine.getPrompt()
    }
  }

  fun getFileContent(path: String): String? {
    return realNativeEngine.readFile(path)
  }

  fun saveFileContent(path: String, content: String) {
    realNativeEngine.writeFile(path, content)
  }

  fun getAutocompleteFiles(): List<Pair<String, Boolean>> {
    return realNativeEngine.listFiles()
  }
}

enum class VmState {
  STOPPED, BOOTING, RUNNING, ERROR
}
