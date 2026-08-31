package com.example.engine

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the QEMU virtual machine lifecycle and the raw terminal stream.
 *
 * The terminal is a REAL terminal: guest output flows through as raw bytes
 * (base64-framed for the JS bridge) so ANSI escapes, colors, cursor movement
 * and TUI applications render correctly in xterm.js. Input flows the same way
 * in reverse — no line buffering, no fake prompts.
 */
class VmManagerService(
    private val context: Context,
    private val qemuEngine: QemuEngine
) {
    private val _vmState = MutableStateFlow(VmState.STOPPED)
    val vmState: StateFlow<VmState> = _vmState.asStateFlow()

    private val _bootMode = MutableStateFlow(BootMode.NETBOOT)
    val bootMode: StateFlow<BootMode> = _bootMode.asStateFlow()

    /** Base64-framed raw guest output chunks, consumed by the xterm.js WebView. */
    private val _terminalChunks = MutableSharedFlow<String>(
        extraBufferCapacity = 512,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val terminalChunks: SharedFlow<String> = _terminalChunks.asSharedFlow()

    /** Rolling tail of guest output used for boot-readiness detection. */
    private val scanTail = StringBuilder()

    @Volatile
    private var lastCols: Int = 0

    @Volatile
    private var lastRows: Int = 0

    @Volatile
    private var sttySyncedForBoot = false

    init {
        _bootMode.value = qemuEngine.bootMode()
    }

    /**
     * Start the QEMU virtual machine.
     */
    fun startVm() {
        if (_vmState.value == VmState.BOOTING || _vmState.value == VmState.RUNNING) {
            return
        }

        _bootMode.value = qemuEngine.bootMode()
        _vmState.value = VmState.BOOTING
        scanTail.setLength(0)
        sttySyncedForBoot = false

        emitSystem(
            if (_bootMode.value == BootMode.DISTRO) {
                "Booting prebuilt distro (persistent disk)..."
            } else {
                "Booting Alpine Linux via netboot (no distro installed)..."
            }
        )

        qemuEngine.start(
            onChunk = { bytes -> emitChunk(bytes); scanForReadiness(bytes) },
            onError = { error ->
                // Stdred is diagnostics (QEMU warnings are common while the VM
                // is healthy). Only a failure during BOOTING that killed the
                // process is an actual error state.
                emitSystem("QEMU: $error")
                if (!qemuEngine.isRunning() && _vmState.value == VmState.BOOTING) {
                    _vmState.value = VmState.ERROR
                }
            },
            onComplete = {
                // Keep ERROR (a failed start) — everything else is a normal stop.
                if (_vmState.value != VmState.ERROR) {
                    _vmState.value = VmState.STOPPED
                }
                emitSystem("VM stopped.")
            }
        )
    }

    /**
     * Stop the QEMU virtual machine (graceful SIGTERM flush, then kill).
     */
    fun stopVm() {
        if (_vmState.value == VmState.STOPPED) {
            return
        }
        qemuEngine.stop()
        _vmState.value = VmState.STOPPED
    }

    /**
     * Send raw bytes to the guest terminal (input from xterm, extra keys, paste).
     */
    fun sendRawBytes(bytes: ByteArray) {
        qemuEngine.sendRaw(bytes)
    }

    /**
     * Terminal geometry changed (rotation, font size, layout). The serial
     * console has no out-of-band winsize channel, so keep the guest tty in
     * sync with `stty` — this makes vim/htop/the agents fill the real screen.
     */
    fun onTerminalResized(cols: Int, rows: Int) {
        lastCols = cols
        lastRows = rows
        syncTerminalSize()
    }

    private fun syncTerminalSize() {
        if (_vmState.value == VmState.RUNNING && lastCols > 0 && lastRows > 0) {
            qemuEngine.sendInput("stty cols $lastCols rows $lastRows")
            sttySyncedForBoot = true
        }
    }

    /**
     * Show a system message inside the terminal (dim cyan, prefixed).
     */
    fun emitSystem(message: String) {
        val framed = "\r\n\u001b[36m[vm] $message\u001b[0m\r\n".toByteArray(Charsets.UTF_8)
        _terminalChunks.tryEmit(Base64.encodeToString(framed, Base64.NO_WRAP))
    }

    private fun emitChunk(bytes: ByteArray) {
        _terminalChunks.tryEmit(Base64.encodeToString(bytes, Base64.NO_WRAP))
    }

    /**
     * Detect when the guest has finished booting: its login/shell prompt
     * appearing on the serial console.
     */
    private fun scanForReadiness(bytes: ByteArray) {
        if (_vmState.value != VmState.BOOTING) return

        val text = String(bytes, Charsets.UTF_8)
        scanTail.append(text)
        // Keep only a small rolling window; prompts appear at the very end.
        if (scanTail.length > 512) {
            scanTail.delete(0, scanTail.length - 512)
        }

        val tail = scanTail.toString()
        val ready = tail.contains("login:") ||
            tail.endsWith("# ") ||
            tail.endsWith("#\u0007") ||
            tail.contains("\n# ") ||
            tail.endsWith("$ ") ||
            tail.contains("Welcome to Alpine Linux")

        if (ready) {
            _vmState.value = VmState.RUNNING
            emitSystem(
                if (_bootMode.value == BootMode.DISTRO) {
                    "Distro ready. node, go, pi and opencode are preinstalled — try: node -v"
                } else {
                    "Guest ready."
                }
            )
            sttySyncedForBoot = false
            syncTerminalSize()
        }
    }

    /**
     * Get current VM state.
     */
    fun getVmState(): VmState = _vmState.value
}
