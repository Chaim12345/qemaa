package com.example.engine

import android.content.Context
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TerminalDimText
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalWhite
import com.example.ui.theme.TerminalYellow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VmManagerService(
  private val context: Context,
  private val scope: CoroutineScope
) {

  private val _terminalLines = MutableStateFlow<List<TerminalLine>>(emptyList())
  val terminalLines: StateFlow<List<TerminalLine>> = _terminalLines.asStateFlow()

  private val _virtualizationReport = MutableStateFlow(VirtualizationDetector.probeHardwareVirtualization(context))
  val virtualizationReport: StateFlow<VirtualizationReport> = _virtualizationReport.asStateFlow()

  private val realNativeEngine = RealNativeProcessEngine(context)

  init {
    val report = _virtualizationReport.value
    appendTerminalLine(TerminalLine("=== REAL NATIVE LINUX RUNTIME ===", TerminalCyan, isSystem = true))
    appendTerminalLine(TerminalLine("Host Kernel: ${report.hostKernelVersion.take(60)}", TerminalDimText))
    appendTerminalLine(TerminalLine("Physical Architecture: ${report.hostArchitecture} (${report.physicalCpuCores} cores, ${report.totalHostRamMb}MB RAM)", TerminalGreen))
    appendTerminalLine(TerminalLine("Hardware KVM (/dev/kvm): ${if (report.hasKvmDevice) "Present" else "Unavailable in user-space sandbox"}", TerminalYellow))
    appendTerminalLine(TerminalLine("Live Real Native Shell (/system/bin/sh) is READY. Type 'uname -a' or 'cat /proc/cpuinfo'", TerminalGreen, isSystem = true))
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
      _terminalLines.value = (if (current.size > 1000) current.drop(current.size - 900) else current) + line
    }
  }

  fun clearTerminal() {
    _terminalLines.value = emptyList()
  }

  fun executeTerminalInput(input: String) {
    val prompt = realNativeEngine.getPrompt()
    appendTerminalLine(TerminalLine("$prompt$input", TerminalGreen, isPrompt = true))

    val results = realNativeEngine.executeCommand(input)
    results.forEach { appendTerminalLine(it) }
  }

  fun getActivePrompt(): String {
    return realNativeEngine.getPrompt()
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
