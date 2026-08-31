package com.example.ui

import android.app.Application
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.DistroManager
import com.example.engine.QemuEngine
import com.example.engine.VirtualizationDetector
import com.example.engine.VirtualizationReport
import com.example.engine.VmKeepAliveService
import com.example.engine.VmManagerService
import com.example.engine.VmState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Linux VM application.
 * Owns the QEMU engine, the raw terminal stream and the distro installer.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val qemuEngine = QemuEngine(application)
    private val vmService = VmManagerService(application, qemuEngine)
    private val distroManager = DistroManager(application)

    val vmState: StateFlow<VmState> = vmService.vmState
    val bootMode = vmService.bootMode
    val terminalChunks: SharedFlow<String> = vmService.terminalChunks

    private val _fontSizeSp = MutableStateFlow(DEFAULT_FONT_SIZE_SP)
    val fontSizeSp: StateFlow<Int> = _fontSizeSp.asStateFlow()

    private val _immersiveMode = MutableStateFlow(false)
    val immersiveMode: StateFlow<Boolean> = _immersiveMode.asStateFlow()

    private val _distroState = MutableStateFlow<DistroManager.DistroState>(
        DistroManager.DistroState.NotInstalled
    )
    val distroState: StateFlow<DistroManager.DistroState> = _distroState.asStateFlow()

    private val _virtualizationReport = MutableStateFlow(
        VirtualizationDetector.detect(application)
    )
    val virtualizationReport: StateFlow<VirtualizationReport> = _virtualizationReport.asStateFlow()

    private val _showVirtualizationReportDialog = MutableStateFlow(false)
    val showVirtualizationReportDialog: StateFlow<Boolean> =
        _showVirtualizationReportDialog.asStateFlow()

    init {
        // Reflect an already-installed distro (e.g. process restart).
        _distroState.value = distroManager.currentInstanceState()

        // Hardware probing can be relatively expensive, so refresh it off the main thread.
        viewModelScope.launch {
            _virtualizationReport.value = VirtualizationDetector.detect(application)
        }
    }

    fun startVm() {
        vmService.startVm()
        VmKeepAliveService.start(getApplication())
    }

    fun stopVm() {
        vmService.stopVm()
        VmKeepAliveService.stop(getApplication())
    }

    /**
     * Raw terminal input from xterm.js (base64-framed UTF-8 bytes). Sent to the
     * guest EXACTLY as-is: control characters, escape sequences, everything.
     */
    fun onTerminalData(base64Data: String) {
        runCatching { Base64.decode(base64Data, Base64.NO_WRAP) }
            .getOrNull()
            ?.let { vmService.sendRawBytes(it) }
    }

    fun onTerminalResized(cols: Int, rows: Int) {
        vmService.onTerminalResized(cols, rows)
    }

    fun setFontSizeSp(sp: Int) {
        _fontSizeSp.value = sp.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
    }

    fun increaseFontSize() = setFontSizeSp(_fontSizeSp.value + FONT_SIZE_STEP)

    fun decreaseFontSize() = setFontSizeSp(_fontSizeSp.value - FONT_SIZE_STEP)

    fun toggleImmersiveMode() {
        _immersiveMode.value = !_immersiveMode.value
    }

    // ── Distro management ────────────────────────────────────────────────────

    fun installDistro() {
        if (_distroState.value is DistroManager.DistroState.Downloading) return
        viewModelScope.launch {
            distroManager.install(assets = null) { state ->
                _distroState.value = state
                // Mirror progress into the terminal scrollback as well.
                when (state) {
                    is DistroManager.DistroState.Downloading ->
                        if (state.percent in listOf(0, 25, 50, 75)) {
                            vmService.emitSystem("distro download ${state.percent}%")
                        }
                    is DistroManager.DistroState.Verifying ->
                        vmService.emitSystem("verifying distro checksum…")
                    is DistroManager.DistroState.Installed ->
                        vmService.emitSystem(
                            "distro installed — press Start to boot it (node, go, pi, opencode included)"
                        )
                    is DistroManager.DistroState.Failed ->
                        vmService.emitSystem("distro install failed: ${state.error}")
                    else -> Unit
                }
            }
        }
    }

    fun uninstallDistro() {
        viewModelScope.launch {
            _distroState.value = DistroManager.DistroState.Uninstalling
            distroManager.uninstall()
            _distroState.value = DistroManager.DistroState.NotInstalled
            vmService.emitSystem("distro removed — next Start uses netboot Alpine")
        }
    }

    // ── Virtualization report dialog ─────────────────────────────────────────

    fun openVirtualizationReportDialog() {
        _virtualizationReport.value = VirtualizationDetector.detect(getApplication())
        _showVirtualizationReportDialog.value = true
    }

    fun closeVirtualizationReportDialog() {
        _showVirtualizationReportDialog.value = false
    }

    override fun onCleared() {
        super.onCleared()
        vmService.stopVm()
        VmKeepAliveService.stop(getApplication())
    }

    private companion object {
        const val DEFAULT_FONT_SIZE_SP = 14
        const val MIN_FONT_SIZE_SP = 8
        const val MAX_FONT_SIZE_SP = 28
        const val FONT_SIZE_STEP = 2
    }
}
