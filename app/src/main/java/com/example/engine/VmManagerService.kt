package com.example.engine

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages QEMU virtual machine lifecycle.
 * No fallback modes - pure QEMU VM only.
 */
class VmManagerService(
    private val context: Context,
    private val qemuEngine: QemuEngine
) {
    private val _terminalLines = MutableStateFlow<List<TerminalLine>>(emptyList())
    val terminalLines: StateFlow<List<TerminalLine>> = _terminalLines

    private val _vmState = MutableStateFlow(VmState.STOPPED)
    val vmState: StateFlow<VmState> = _vmState

    private val _prompt = MutableStateFlow("root@linux:~# ")
    val prompt: StateFlow<String> = _prompt

    /**
     * Start the QEMU virtual machine.
     */
    fun startVm() {
        if (_vmState.value != VmState.STOPPED) {
            return
        }

        _vmState.value = VmState.BOOTING
        _terminalLines.value = emptyList()

        qemuEngine.start(
            onOutput = { line ->
                appendLine(TerminalLine.Output(line))
            },
            onError = { error ->
                appendLine(TerminalLine.Error(error))
                _vmState.value = VmState.ERROR
            },
            onComplete = {
                _vmState.value = VmState.STOPPED
            }
        )
    }

    /**
     * Stop the QEMU virtual machine.
     */
    fun stopVm() {
        if (_vmState.value == VmState.STOPPED) {
            return
        }

        qemuEngine.stop()
        _vmState.value = VmState.STOPPED
    }

    /**
     * Send command to the VM's stdin.
     */
    fun sendInput(input: String) {
        if (_vmState.value != VmState.RUNNING) {
            return
        }

        qemuEngine.sendInput(input)
    }

    /**
     * Clear terminal display.
     */
    fun clearTerminal() {
        _terminalLines.value = emptyList()
    }

    /**
     * Append a line to terminal output.
     */
    private fun appendLine(line: TerminalLine) {
        _terminalLines.value = _terminalLines.value + line

        // Detect when VM is fully booted
        if (line is TerminalLine.Output) {
            val text = line.text
            if (text.contains("Welcome to Alpine Linux") ||
                text.contains("login:") ||
                text.contains("# ") ||
                text.contains("$ ")) {
                _vmState.value = VmState.RUNNING
            }
        }
    }

    /**
     * Get current VM state.
     */
    fun getVmState(): VmState = _vmState.value
}
