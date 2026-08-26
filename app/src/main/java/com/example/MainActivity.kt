package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DistroCatalog
import com.example.data.model.VirtualMachineEntity
import com.example.data.model.VmStatus
import com.example.ui.AppTab
import com.example.ui.MainViewModel
import com.example.engine.EngineMode
import com.example.ui.components.DistroHubScreen
import com.example.ui.components.HardwareConfigScreen
import com.example.ui.components.NanoEditorModal
import com.example.ui.components.SnapshotDialog
import com.example.ui.components.TerminalScreen
import com.example.ui.components.VirtualizationReportDialog
import com.example.ui.components.VmListScreen
import com.example.ui.components.WebPreviewDialog
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.LinuxVMTheme
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.SecondaryEmerald
import com.example.ui.theme.TerminalBlack
import com.example.ui.theme.TerminalDimText
import com.example.ui.theme.TerminalFontFamily
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalWhite
import com.example.ui.theme.TerminalYellow

class MainActivity : ComponentActivity() {
  private val viewModel: MainViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      LinuxVMTheme {
        MainApp(viewModel = viewModel)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: MainViewModel) {
  val currentTab by viewModel.currentTab.collectAsState()
  val allVms by viewModel.allVms.collectAsState()
  val activeVm by viewModel.activeVm.collectAsState()
  val terminalLines by viewModel.terminalLines.collectAsState()
  val telemetry by viewModel.telemetry.collectAsState()
  val installProgress by viewModel.installProgress.collectAsState()
  val installStatusText by viewModel.installStatusText.collectAsState()
  val terminalInput by viewModel.terminalInput.collectAsState()
  val fontSizeSp by viewModel.fontSizeSp.collectAsState()
  val nanoState by viewModel.nanoState.collectAsState()
  val webPreviewState by viewModel.webPreviewState.collectAsState()
  val showSnapshotDialog by viewModel.showSnapshotDialog.collectAsState()
  val vmSnapshots by viewModel.vmSnapshots.collectAsState()
  val engineMode by viewModel.engineMode.collectAsState()
  val virtualizationReport by viewModel.virtualizationReport.collectAsState()
  val showVirtualizationReportDialog by viewModel.showVirtualizationReportDialog.collectAsState()

  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .background(CyberBackground),
    topBar = {
      TopAppBar(
        title = {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = null,
                tint = PrimaryCyan,
                modifier = Modifier.size(24.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text(
                  text = "Linux VM",
                  color = TerminalWhite,
                  fontWeight = FontWeight.Bold,
                  fontSize = 17.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                  val isRunning = activeVm?.status == VmStatus.RUNNING.name
                  val isPaused = activeVm?.status == VmStatus.PAUSED.name
                  val isBooting = activeVm?.status == VmStatus.BOOTING.name

                  val dotColor = when {
                    engineMode == EngineMode.REAL_NATIVE_HOST -> SecondaryEmerald
                    isRunning -> SecondaryEmerald
                    isPaused -> TerminalYellow
                    isBooting -> PrimaryCyan
                    else -> TerminalDimText
                  }

                  Box(
                    modifier = Modifier
                      .size(6.dp)
                      .clip(CircleShape)
                      .background(dotColor)
                  )
                  Spacer(modifier = Modifier.width(5.dp))
                  Text(
                    text = if (engineMode == EngineMode.REAL_NATIVE_HOST) "Real Host Active" else "${activeVm?.name ?: "No VM"} (${activeVm?.status ?: "STOPPED"})",
                    color = dotColor,
                    fontSize = 11.sp,
                    fontFamily = TerminalFontFamily
                  )
                }
              }
            }

            // Quick mode chip in TopAppBar
            Box(
              modifier = Modifier
                .padding(end = 8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (engineMode == EngineMode.REAL_NATIVE_HOST) SecondaryEmerald.copy(alpha = 0.2f) else PrimaryCyan.copy(alpha = 0.2f))
                .border(1.dp, if (engineMode == EngineMode.REAL_NATIVE_HOST) SecondaryEmerald else PrimaryCyan, RoundedCornerShape(6.dp))
                .clickable {
                  viewModel.setEngineMode(
                    if (engineMode == EngineMode.REAL_NATIVE_HOST) EngineMode.QEMU_GUEST else EngineMode.REAL_NATIVE_HOST
                  )
                }
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text(
                text = engineMode.badge,
                color = if (engineMode == EngineMode.REAL_NATIVE_HOST) SecondaryEmerald else PrimaryCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = TerminalFontFamily
              )
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = CyberSurface,
          titleContentColor = TerminalWhite
        )
      )
    },
    bottomBar = {
      NavigationBar(
        containerColor = CyberSurface,
        modifier = Modifier.testTag("bottom_nav_bar")
      ) {
        NavigationBarItem(
          selected = currentTab == AppTab.TERMINAL,
          onClick = { viewModel.setTab(AppTab.TERMINAL) },
          icon = { Icon(imageVector = Icons.Default.Terminal, contentDescription = "Terminal") },
          label = { Text("Terminal", fontFamily = TerminalFontFamily, fontSize = 11.sp) },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = TerminalBlack,
            selectedTextColor = PrimaryCyan,
            indicatorColor = PrimaryCyan,
            unselectedIconColor = TerminalDimText,
            unselectedTextColor = TerminalDimText
          ),
          modifier = Modifier.testTag("nav_tab_terminal")
        )

        NavigationBarItem(
          selected = currentTab == AppTab.VMS,
          onClick = { viewModel.setTab(AppTab.VMS) },
          icon = { Icon(imageVector = Icons.Default.Dns, contentDescription = "VM Manager") },
          label = { Text("VMs (${allVms.size})", fontFamily = TerminalFontFamily, fontSize = 11.sp) },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = TerminalBlack,
            selectedTextColor = PrimaryCyan,
            indicatorColor = PrimaryCyan,
            unselectedIconColor = TerminalDimText,
            unselectedTextColor = TerminalDimText
          ),
          modifier = Modifier.testTag("nav_tab_vms")
        )

        NavigationBarItem(
          selected = currentTab == AppTab.DISTRO_HUB,
          onClick = { viewModel.setTab(AppTab.DISTRO_HUB) },
          icon = { Icon(imageVector = Icons.Default.FlashOn, contentDescription = "Distro Hub") },
          label = { Text("Distros", fontFamily = TerminalFontFamily, fontSize = 11.sp) },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = TerminalBlack,
            selectedTextColor = SecondaryEmerald,
            indicatorColor = SecondaryEmerald,
            unselectedIconColor = TerminalDimText,
            unselectedTextColor = TerminalDimText
          ),
          modifier = Modifier.testTag("nav_tab_distros")
        )

        NavigationBarItem(
          selected = currentTab == AppTab.CONFIG,
          onClick = { viewModel.setTab(AppTab.CONFIG) },
          icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "QEMU Config") },
          label = { Text("Config", fontFamily = TerminalFontFamily, fontSize = 11.sp) },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = TerminalBlack,
            selectedTextColor = PrimaryCyan,
            indicatorColor = PrimaryCyan,
            unselectedIconColor = TerminalDimText,
            unselectedTextColor = TerminalDimText
          ),
          modifier = Modifier.testTag("nav_tab_config")
        )
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(CyberBackground)
    ) {
      when (currentTab) {
        AppTab.TERMINAL -> {
          TerminalScreen(
            activeVm = activeVm,
            terminalLines = terminalLines,
            telemetry = telemetry,
            installProgress = installProgress,
            installStatusText = installStatusText,
            terminalInput = terminalInput,
            fontSizeSp = fontSizeSp,
            engineMode = engineMode,
            activePrompt = viewModel.vmService.getActivePrompt(),
            onInputChange = viewModel::onTerminalInputChange,
            onSendCommand = { viewModel.sendTerminalCommand(it) },
            onStartVm = viewModel::startActiveVm,
            onPauseVm = viewModel::pauseActiveVm,
            onResumeVm = viewModel::resumeActiveVm,
            onShutdownVm = viewModel::shutdownActiveVm,
            onResetVm = viewModel::resetActiveVm,
            onClearTerminal = viewModel::clearTerminal,
            onIncreaseFontSize = viewModel::increaseFontSize,
            onDecreaseFontSize = viewModel::decreaseFontSize,
            onKeyTab = viewModel::handleKeyTab,
            onKeyCtrlC = viewModel::handleKeyCtrlC,
            onKeyHistoryPrev = viewModel::handleKeyHistoryPrev,
            onKeyHistoryNext = viewModel::handleKeyHistoryNext,
            onKeyInsert = viewModel::handleKeyInsert,
            onOpenSnapshots = viewModel::openSnapshotDialog,
            onOpenWebPreview = viewModel::openWebPreview,
            onToggleEngineMode = {
              viewModel.setEngineMode(
                if (engineMode == EngineMode.REAL_NATIVE_HOST) EngineMode.QEMU_GUEST else EngineMode.REAL_NATIVE_HOST
              )
            },
            onOpenVirtualizationReport = viewModel::openVirtualizationReportDialog
          )
        }

        AppTab.VMS -> {
          VmListScreen(
            vms = allVms,
            activeVm = activeVm,
            onSelectVm = { vm ->
              viewModel.selectVm(vm)
              viewModel.setTab(AppTab.TERMINAL)
            },
            onStartVm = { vm ->
              viewModel.selectVm(vm)
              viewModel.startActiveVm()
              viewModel.setTab(AppTab.TERMINAL)
            },
            onPauseVm = { vm ->
              viewModel.selectVm(vm)
              viewModel.pauseActiveVm()
            },
            onResumeVm = { vm ->
              viewModel.selectVm(vm)
              viewModel.resumeActiveVm()
            },
            onShutdownVm = { vm ->
              viewModel.selectVm(vm)
              viewModel.shutdownActiveVm()
            },
            onOpenConsole = { vm ->
              viewModel.selectVm(vm)
              viewModel.setTab(AppTab.TERMINAL)
            },
            onOpenConfig = { vm ->
              viewModel.selectVm(vm)
              viewModel.setTab(AppTab.CONFIG)
            },
            onOpenSnapshots = { vm ->
              viewModel.selectVm(vm)
              viewModel.openSnapshotDialog()
            },
            onDeleteVm = viewModel::deleteVm,
            onCreateNewVm = { viewModel.setTab(AppTab.DISTRO_HUB) }
          )
        }

        AppTab.DISTRO_HUB -> {
          DistroHubScreen(
            onInstallDistro = { template, arch, cpus, ram, disk ->
              viewModel.installDistro(template, arch, cpus, ram, disk)
            }
          )
        }

        AppTab.CONFIG -> {
          HardwareConfigScreen(
            vm = activeVm,
            onSaveConfig = viewModel::updateVmConfig
          )
        }
      }

      // In-Terminal GNU Nano Text Editor Modal
      NanoEditorModal(
        state = nanoState,
        onContentChange = viewModel::updateNanoContent,
        onSaveAndExit = viewModel::saveAndExitNano,
        onCancel = viewModel::closeNanoWithoutSaving
      )

      // Snapshot Management Modal
      if (showSnapshotDialog) {
        SnapshotDialog(
          vm = activeVm,
          snapshots = vmSnapshots,
          onCreateSnapshot = viewModel::createSnapshot,
          onRestoreSnapshot = viewModel::restoreSnapshot,
          onDeleteSnapshot = viewModel::deleteSnapshot,
          onDismiss = viewModel::closeSnapshotDialog
        )
      }

      // Forwarded Port Web Preview Dialog
      if (webPreviewState.isOpen) {
        WebPreviewDialog(
          state = webPreviewState,
          onClose = viewModel::closeWebPreview
        )
      }

      // Hardware Virtualization & Engine Diagnostics Dialog
      if (showVirtualizationReportDialog) {
        VirtualizationReportDialog(
          report = virtualizationReport,
          currentMode = engineMode,
          onSelectMode = viewModel::setEngineMode,
          onRunDiagnosticCommand = { cmd ->
            viewModel.sendTerminalCommand(cmd)
          },
          onDismiss = viewModel::closeVirtualizationReportDialog
        )
      }
    }
  }
}

