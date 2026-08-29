package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.QemuEngine
import com.example.engine.TerminalLine
import com.example.engine.VirtualizationDetector
import com.example.engine.VirtualizationReport
import com.example.engine.VmManagerService
import com.example.engine.VmState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Linux VM application.
 * Manages QEMU VM state and terminal interaction.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val qemuEngine = QemuEngine(application)
    private val vmService = VmManagerService(application, qemuEngine)

    val terminalLines: StateFlow<List<TerminalLine>> = vmService.terminalLines
    val vmState: StateFlow<VmState> = vmService.vmState
    val prompt: StateFlow<String> = vmService.prompt

    private val _terminalInput = MutableStateFlow("")
    val terminalInput: StateFlow<String> = _terminalInput.asStateFlow()

    private val _fontSizeSp = MutableStateFlow(DEFAULT_FONT_SIZE_SP)
    val fontSizeSp: StateFlow<Int> = _fontSizeSp.asStateFlow()

    private val _nanoState = MutableStateFlow(NanoEditorState())
    val nanoState: StateFlow<NanoEditorState> = _nanoState.asStateFlow()

    private val _virtualizationReport = MutableStateFlow(
        VirtualizationDetector.detect(application)
    )
    val virtualizationReport: StateFlow<VirtualizationReport> = _virtualizationReport.asStateFlow()

    private val _showVirtualizationReportDialog = MutableStateFlow(false)
    val showVirtualizationReportDialog: StateFlow<Boolean> =
        _showVirtualizationReportDialog.asStateFlow()

    private val inputHistory = mutableListOf<String>()
    private var historyIndex = 0

    init {
        // Hardware probing can be relatively expensive, so refresh it off the main thread.
        viewModelScope.launch {
            _virtualizationReport.value = VirtualizationDetector.detect(application)
        }
    }

    fun startVm() {
        vmService.startVm()
    }

    fun stopVm() {
        vmService.stopVm()
    }

    /**
     * Sends either the supplied command or the text currently in the command field.
     */
    fun sendTerminalCommand(command: String?) {
        val commandToSend = command ?: _terminalInput.value
        if (commandToSend.isBlank()) return

        inputHistory.add(commandToSend)
        historyIndex = inputHistory.size
        _terminalInput.value = ""
        vmService.sendInput(commandToSend)
    }

    fun onTerminalInputChange(input: String) {
        _terminalInput.value = input
    }

    fun clearTerminal() {
        vmService.clearTerminal()
    }

    fun increaseFontSize() {
        _fontSizeSp.value = (_fontSizeSp.value + FONT_SIZE_STEP).coerceAtMost(MAX_FONT_SIZE_SP)
    }

    fun decreaseFontSize() {
        _fontSizeSp.value = (_fontSizeSp.value - FONT_SIZE_STEP).coerceAtLeast(MIN_FONT_SIZE_SP)
    }

    fun navigateHistoryUp() {
        if (historyIndex > 0) {
            historyIndex--
            _terminalInput.value = inputHistory[historyIndex]
        }
    }

    fun navigateHistoryDown() {
        if (historyIndex < inputHistory.size - 1) {
            historyIndex++
            _terminalInput.value = inputHistory[historyIndex]
        } else {
            historyIndex = inputHistory.size
            _terminalInput.value = ""
        }
    }

    fun handleCtrlC() {
        vmService.sendInput("")
    }

    fun handleTab() {
        vmService.sendInput("\t")
    }

    // Names used by the terminal composables. Keeping these as small wrappers makes the
    // ViewModel API explicit while preserving the history/navigation methods above.
    fun handleKeyTab() = handleTab()

    fun handleKeyCtrlC() = handleCtrlC()

    fun handleKeyHistoryPrev() = navigateHistoryUp()

    fun handleKeyHistoryNext() = navigateHistoryDown()

    fun handleKeyInsert(text: String) {
        if (text.isNotEmpty()) {
            _terminalInput.value += text
        }
    }

    fun getActivePrompt(): String = prompt.value

    fun openVirtualizationReportDialog() {
        _virtualizationReport.value = VirtualizationDetector.detect(getApplication())
        _showVirtualizationReportDialog.value = true
    }

    fun closeVirtualizationReportDialog() {
        _showVirtualizationReportDialog.value = false
    }

    // Compatibility wrappers for callers using the original names.
    fun showVirtualizationReport() = openVirtualizationReportDialog()

    fun hideVirtualizationReport() = closeVirtualizationReportDialog()

    fun openNano(filePath: String, content: String = "") {
        _nanoState.value = NanoEditorState(
            isOpen = true,
            filePath = filePath,
            content = content,
            isModified = false
        )
    }

    fun updateNanoContent(content: String) {
        val current = _nanoState.value
        if (current.isOpen) {
            _nanoState.value = current.copy(
                content = content,
                isModified = content != current.content
            )
        }
    }

    fun saveAndExitNano() {
        // File persistence is handled by the VM/host command layer. Closing the modal here
        // keeps the editor state in sync with the UI until that layer is connected.
        _nanoState.value = NanoEditorState()
    }

    fun closeNanoWithoutSaving() {
        _nanoState.value = NanoEditorState()
    }

    override fun onCleared() {
        super.onCleared()
        vmService.stopVm()
    }

    private companion object {
        const val DEFAULT_FONT_SIZE_SP = 14
        const val MIN_FONT_SIZE_SP = 10
        const val MAX_FONT_SIZE_SP = 24
        const val FONT_SIZE_STEP = 2
    }
}
