package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.DistroCatalog
import com.example.data.model.DistroTemplate
import com.example.data.model.PortForward
import com.example.data.model.TelemetryMetrics
import com.example.data.model.VirtualMachineEntity
import com.example.data.model.VmSnapshotEntity
import com.example.data.model.VmStatus
import com.example.data.repository.VmRepository
import com.example.engine.EngineMode
import com.example.engine.LinuxShellEngine
import com.example.engine.TerminalLine
import com.example.engine.VirtualizationReport
import com.example.engine.VmManagerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NanoEditorState(
  val isOpen: Boolean = false,
  val filePath: String = "",
  val content: String = "",
  val isModified: Boolean = false
)

data class WebPreviewState(
  val isOpen: Boolean = false,
  val port: Int = 8080,
  val serviceName: String = "Web Service"
)

enum class AppTab(val title: String) {
  TERMINAL("Terminal"),
  VMS("VM Manager"),
  DISTRO_HUB("Distro Hub"),
  CONFIG("QEMU Config")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

  private val repository: VmRepository
  val vmService: VmManagerService

  val allVms: StateFlow<List<VirtualMachineEntity>>
  val activeVm: StateFlow<VirtualMachineEntity?>
  val terminalLines: StateFlow<List<TerminalLine>>
  val telemetry: StateFlow<TelemetryMetrics>
  val installProgress: StateFlow<Float?>
  val installStatusText: StateFlow<String>
  val engineMode: StateFlow<EngineMode>
  val virtualizationReport: StateFlow<VirtualizationReport>

  private val _showVirtualizationReportDialog = MutableStateFlow(false)
  val showVirtualizationReportDialog: StateFlow<Boolean> = _showVirtualizationReportDialog.asStateFlow()

  private val _currentTab = MutableStateFlow(AppTab.TERMINAL)
  val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

  private val _terminalInput = MutableStateFlow("")
  val terminalInput: StateFlow<String> = _terminalInput.asStateFlow()

  private val _fontSizeSp = MutableStateFlow(12)
  val fontSizeSp: StateFlow<Int> = _fontSizeSp.asStateFlow()

  private val _nanoState = MutableStateFlow(NanoEditorState())
  val nanoState: StateFlow<NanoEditorState> = _nanoState.asStateFlow()

  private val _webPreviewState = MutableStateFlow(WebPreviewState())
  val webPreviewState: StateFlow<WebPreviewState> = _webPreviewState.asStateFlow()

  private val _showSnapshotDialog = MutableStateFlow(false)
  val showSnapshotDialog: StateFlow<Boolean> = _showSnapshotDialog.asStateFlow()

  private val _vmSnapshots = MutableStateFlow<List<VmSnapshotEntity>>(emptyList())
  val vmSnapshots: StateFlow<List<VmSnapshotEntity>> = _vmSnapshots.asStateFlow()

  private val _historyIndex = MutableStateFlow(-1)
  private val inputHistory = mutableListOf<String>()

  init {
    val database = AppDatabase.getDatabase(application)
    repository = VmRepository(database.vmDao())
    vmService = VmManagerService(application, repository, viewModelScope)

    allVms = repository.allVms.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList()
    )

    activeVm = vmService.activeVm
    terminalLines = vmService.terminalLines
    telemetry = vmService.telemetry
    installProgress = vmService.installProgress
    installStatusText = vmService.installStatusText
    engineMode = vmService.engineMode
    virtualizationReport = vmService.virtualizationReport

    viewModelScope.launch {
      repository.seedInitialDistroIfEmpty()
      repository.allVms.collect { list ->
        if (activeVm.value == null && list.isNotEmpty()) {
          vmService.setActiveVm(list.first())
        }
      }
    }
  }

  fun setEngineMode(mode: EngineMode) {
    vmService.setEngineMode(mode)
  }

  fun openVirtualizationReportDialog() {
    vmService.refreshVirtualizationReport()
    _showVirtualizationReportDialog.value = true
  }

  fun closeVirtualizationReportDialog() {
    _showVirtualizationReportDialog.value = false
  }

  fun setTab(tab: AppTab) {
    _currentTab.value = tab
  }

  fun selectVm(vm: VirtualMachineEntity) {
    vmService.setActiveVm(vm)
    loadSnapshotsForVm(vm.id)
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

    // Check for nano command
    if (text.startsWith("nano ")) {
      val file = text.removePrefix("nano ").trim()
      openNanoEditor(file)
      return
    }

    vmService.executeTerminalInput(text)
  }

  fun startActiveVm() {
    activeVm.value?.let { vmService.startVm(it) }
  }

  fun pauseActiveVm() {
    activeVm.value?.let { vmService.pauseVm(it) }
  }

  fun resumeActiveVm() {
    activeVm.value?.let { vmService.resumeVm(it) }
  }

  fun shutdownActiveVm() {
    activeVm.value?.let { vmService.shutdownVm(it) }
  }

  fun resetActiveVm() {
    activeVm.value?.let { vmService.forceResetVm(it) }
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

  // Touch Accessory Bar Key Actions
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
  }

  fun handleKeyInsert(text: String) {
    _terminalInput.value += text
  }

  // 1-Click Distro Install
  fun installDistro(
    template: DistroTemplate,
    arch: String,
    cpuCores: Int,
    ramMb: Int,
    diskGb: Double
  ) {
    _currentTab.value = AppTab.TERMINAL
    vmService.setEngineMode(EngineMode.QEMU_GUEST)
    vmService.installDistroOneClick(
      template = template,
      chosenArch = arch,
      cpuCores = cpuCores,
      ramMb = ramMb,
      diskGb = diskGb
    ) { newVmId ->
      // Distro installed and booted
    }
  }

  // Nano Editor
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
          com.example.ui.theme.TerminalGreen
        )
      )
    }
    _nanoState.value = NanoEditorState(isOpen = false)
  }

  fun closeNanoWithoutSaving() {
    _nanoState.value = NanoEditorState(isOpen = false)
  }

  // VM Hardware Config update
  fun updateVmConfig(updated: VirtualMachineEntity) {
    viewModelScope.launch {
      repository.updateVm(updated)
      if (activeVm.value?.id == updated.id) {
        vmService.setActiveVm(updated)
      }
    }
  }

  fun deleteVm(vm: VirtualMachineEntity) {
    viewModelScope.launch {
      repository.deleteVm(vm.id)
      if (activeVm.value?.id == vm.id) {
        val remaining = allVms.value.filter { it.id != vm.id }
        if (remaining.isNotEmpty()) {
          selectVm(remaining.first())
        }
      }
    }
  }

  // Snapshots
  fun openSnapshotDialog() {
    activeVm.value?.let { loadSnapshotsForVm(it.id) }
    _showSnapshotDialog.value = true
  }

  fun closeSnapshotDialog() {
    _showSnapshotDialog.value = false
  }

  private fun loadSnapshotsForVm(vmId: Long) {
    viewModelScope.launch {
      repository.getSnapshots(vmId).collect { list ->
        _vmSnapshots.value = list
      }
    }
  }

  fun createSnapshot(name: String, desc: String) {
    activeVm.value?.let { vm ->
      vmService.createSnapshot(vm, name, desc)
    }
  }

  fun restoreSnapshot(snapshot: VmSnapshotEntity) {
    activeVm.value?.let { vm ->
      vmService.restoreSnapshot(vm, snapshot)
      _showSnapshotDialog.value = false
    }
  }

  fun deleteSnapshot(snapshot: VmSnapshotEntity) {
    viewModelScope.launch {
      repository.deleteSnapshot(snapshot.id)
    }
  }

  // Web Preview
  fun openWebPreview(port: Int, serviceName: String) {
    _webPreviewState.value = WebPreviewState(isOpen = true, port = port, serviceName = serviceName)
  }

  fun closeWebPreview() {
    _webPreviewState.value = WebPreviewState(isOpen = false)
  }
}
