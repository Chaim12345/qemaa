package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.TerminalLine
import com.example.engine.VirtualizationReport
import com.example.engine.VmManagerService
import com.example.engine.VmState
import com.example.ui.theme.TerminalGreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NanoEditorState(
  val isOpen: Boolean = false,
  val filePath: String = "",
  val content: String = "",
  val isModified: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

  val vmService: VmManagerService

  val terminalLines: StateFlow<List<TerminalLine>>
  val virtualizationReport: StateFlow<VirtualizationReport>
  val vmStatus: StateFlow<VmState>

  private val _showVirtualizationReportDialog = MutableStateFlow(false)
  val showVirtualizationReportDialog: StateFlow<Boolean> = _showVirtualizationReportDialog.asStateFlow()

  private val _terminalInput = MutableStateFlow("")
  val terminalInput: StateFlow<String> = _terminalInput.asStateFlow()

  private val _fontSizeSp = MutableStateFlow(12)
  val fontSizeSp: StateFlow<Int> = _fontSizeSp.asStateFlow()

  private val _nanoState = MutableStateFlow(NanoEditorState())
  val nanoState: StateFlow<NanoEditorState> = _nanoState.asStateFlow()

  private val _historyIndex = MutableStateFlow(-1)
  private val inputHistory = mutableListOf<String>()

  init {
    vmService = VmManagerService(application, viewModelScope)

    terminalLines = vmService.terminalLines
    virtualizationReport = vmService.virtualizationReport
    vmStatus = vmService.vmStatus
  }

  fun openVirtualizationReportDialog() {
    vmService.refreshVirtualizationReport()
    _showVirtualizationReportDialog.value = true
  }

  fun closeVirtualizationReportDialog() {
    _showVirtualizationReportDialog.value = false
  }

  fun onTerminalInputChange(text: String) {
    _terminalInput.value = text
  }

  fun sendTerminalCommand(overrideInput: String? = null) {
    val text = (overrideInput ?: _terminalInput.value).trim()
    if (text.isEmpty()) return

    if (!inputHistory.contains(text)) {
      inputHistory.add(text)
    }
    _historyIndex.value = -1
    _terminalInput.value = ""

    // Check for nano command (only in native mode)
    if (text.startsWith("nano ") && vmService.vmStatus.value != VmState.RUNNING) {
      val file = text.removePrefix("nano ").trim()
      openNanoEditor(file)
      return
    }

    vmService.executeTerminalInput(text)
  }

  fun startVm() {
    vmService.startVm()
  }

  fun stopVm() {
    vmService.stopVm()
  }

  fun clearTerminal() {
    vmService.clearTerminal()
  }

  fun increaseFontSize() {
    if (_fontSizeSp.value < 20) _fontSizeSp.value += 1
  }

  fun decreaseFontSize() {
    if (_fontSizeSp.value > 9) _fontSizeSp.value -= 1
  }

  fun handleKeyTab() {
    val current = _terminalInput.value
    val tokens = current.split(" ")
    val lastToken = tokens.lastOrNull() ?: ""
    val files = vmService.getAutocompleteFiles()
    val match = files.map { it.first }.firstOrNull { it.startsWith(lastToken, ignoreCase = true) }
    if (match != null) {
      val completed = if (tokens.size > 1) {
        tokens.dropLast(1).joinToString(" ") + " " + match
      } else {
        match
      }
      _terminalInput.value = completed
    }
  }

  fun handleKeyHistoryPrev() {
    if (inputHistory.isEmpty()) return
    val newIdx = if (_historyIndex.value == -1) inputHistory.size - 1 else maxOf(0, _historyIndex.value - 1)
    _historyIndex.value = newIdx
    _terminalInput.value = inputHistory[newIdx]
  }

  fun handleKeyHistoryNext() {
    if (inputHistory.isEmpty() || _historyIndex.value == -1) return
    val newIdx = _historyIndex.value + 1
    if (newIdx < inputHistory.size) {
      _historyIndex.value = newIdx
      _terminalInput.value = inputHistory[newIdx]
    } else {
      _historyIndex.value = -1
      _terminalInput.value = ""
    }
  }

  fun handleKeyCtrlC() {
    vmService.appendTerminalLine(TerminalLine("^C", com.example.ui.theme.TerminalRed))
    _terminalInput.value = ""
    // If VM is running, send Ctrl+C to VM
    if (vmService.vmStatus.value == VmState.RUNNING) {
      vmService.executeTerminalInput("") // handled by QemuEngine
    }
  }

  fun handleKeyInsert(text: String) {
    _terminalInput.value += text
  }

  fun openNanoEditor(path: String) {
    val content = vmService.getFileContent(path) ?: ""
    _nanoState.value = NanoEditorState(
      isOpen = true,
      filePath = path,
      content = content,
      isModified = false
    )
  }

  fun updateNanoContent(newContent: String) {
    _nanoState.value = _nanoState.value.copy(content = newContent, isModified = true)
  }

  fun saveAndExitNano() {
    val state = _nanoState.value
    if (state.filePath.isNotBlank()) {
      vmService.saveFileContent(state.filePath, state.content)
      vmService.appendTerminalLine(
        TerminalLine(
          "[ Wrote ${state.content.lines().size} lines to ${state.filePath} ]",
          TerminalGreen
        )
      )
    }
    _nanoState.value = NanoEditorState(isOpen = false)
  }

  fun closeNanoWithoutSaving() {
    _nanoState.value = NanoEditorState(isOpen = false)
  }
}
