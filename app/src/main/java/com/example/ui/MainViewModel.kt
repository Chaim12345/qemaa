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

    private val _terminalLines: StateFlow<List<TerminalLine>> = vmService.terminalLines
    val terminalLines: StateFlow<List<TerminalLine>> = _terminalLines

    private val _vmState: StateFlow<VmState> = vmService.vmState
    val vmState: StateFlow<VmState> = _vmState

    private val _prompt: StateFlow<String> = vmService.prompt
    val prompt: StateFlow<String> = _prompt

    private val _terminalInput = MutableStateFlow("")
    val terminalInput: StateFlow<String> = _terminalInput

    private val _virtualizationReport = MutableStateFlow(
        VirtualizationDetector.detect(application)
    )
    val virtualizationReport: StateFlow<VirtualizationReport> = _virtualizationReport.asStateFlow()

    private val _showVirtualizationReport = MutableStateFlow(false)
    val showVirtualizationReport: StateFlow<Boolean> = _showVirtualizationReport.asStateFlow()

    private val inputHistory = mutableListOf<String>()
    private var historyIndex = -1

    init {
        // Update virtualization report on startup
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

    fun sendTerminalCommand(command: String?) {
        if (command.isNullOrBlank()) return

        // Add to history
        inputHistory.add(command)
        historyIndex = inputHistory.size

        // Clear input field
        _terminalInput.value = ""

        // Send to VM
        vmService.sendInput(command)
    }

    fun onTerminalInputChange(input: String) {
        _terminalInput.value = input
    }

    fun clearTerminal() {
        vmService.clearTerminal()
    }

    fun navigateHistoryUp() {
        if (historyIndex > 0) {
            historyIndex--
            _terminalInput.value = inputHistory.getOrNull(historyIndex) ?: ""
        }
    }

    fun navigateHistoryDown() {
        if (historyIndex < inputHistory.size - 1) {
            historyIndex++
            _terminalInput.value = inputHistory.getOrNull(historyIndex) ?: ""
        } else {
            historyIndex = inputHistory.size
            _terminalInput.value = ""
        }
    }

    fun handleCtrlC() {
        vmService.sendInput("") // Send empty line (Ctrl+C equivalent)
    }

    fun handleTab() {
        vmService.sendInput("\t")
    }

    fun showVirtualizationReport() {
        _virtualizationReport.value = VirtualizationDetector.detect(getApplication())
        _showVirtualizationReport.value = true
    }

    fun hideVirtualizationReport() {
        _showVirtualizationReport.value = false
    }

    override fun onCleared() {
        super.onCleared()
        // Cleanup QEMU when ViewModel is destroyed
        vmService.stopVm()
    }
}
