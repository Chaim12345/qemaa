package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.VmState
import com.example.ui.MainViewModel
import com.example.ui.components.NanoEditorModal
import com.example.ui.components.TerminalScreen
import com.example.ui.components.VirtualizationReportDialog
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.LinuxVMTheme
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.SecondaryEmerald
import com.example.ui.theme.TerminalFontFamily
import com.example.ui.theme.TerminalWhite

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
  val terminalLines by viewModel.terminalLines.collectAsState()
  val terminalInput by viewModel.terminalInput.collectAsState()
  val fontSizeSp by viewModel.fontSizeSp.collectAsState()
  val nanoState by viewModel.nanoState.collectAsState()
  val virtualizationReport by viewModel.virtualizationReport.collectAsState()
  val showVirtualizationReportDialog by viewModel.showVirtualizationReportDialog.collectAsState()
  val vmState by viewModel.vmState.collectAsState()

  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .background(CyberBackground),
    topBar = {
      TopAppBar(
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.Terminal,
              contentDescription = null,
              tint = PrimaryCyan,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(SecondaryEmerald)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Real Linux VM",
                color = SecondaryEmerald,
                fontSize = 12.sp,
                fontFamily = TerminalFontFamily,
                fontWeight = FontWeight.Bold
              )
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = CyberSurface,
          titleContentColor = TerminalWhite
        )
      )
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(CyberBackground)
    ) {
      TerminalScreen(
        terminalLines = terminalLines,
        vmStatus = viewModel.vmState.collectAsState().value,
        activePrompt = viewModel.vmService.getActivePrompt(),
        terminalInput = terminalInput,
        fontSizeSp = fontSizeSp,
        onInputChange = viewModel::onTerminalInputChange,
        onSendCommand = { viewModel.sendTerminalCommand(it) },
        onStartVm = viewModel::startVm,
        onStopVm = viewModel::stopVm,
        onClearTerminal = viewModel::clearTerminal,
        onIncreaseFontSize = viewModel::increaseFontSize,
        onDecreaseFontSize = viewModel::decreaseFontSize,
        onKeyTab = viewModel::handleKeyTab,
        onKeyCtrlC = viewModel::handleKeyCtrlC,
        onKeyHistoryPrev = viewModel::handleKeyHistoryPrev,
        onKeyHistoryNext = viewModel::handleKeyHistoryNext,
        onKeyInsert = viewModel::handleKeyInsert,
        onOpenVirtualizationReport = viewModel::openVirtualizationReportDialog
      )

      // Nano Text Editor Modal
      NanoEditorModal(
        state = nanoState,
        onContentChange = viewModel::updateNanoContent,
        onSaveAndExit = viewModel::saveAndExitNano,
        onCancel = viewModel::closeNanoWithoutSaving
      )

      // Hardware Virtualization Info Dialog
      if (showVirtualizationReportDialog) {
        VirtualizationReportDialog(
          report = virtualizationReport,
          onRunDiagnosticCommand = { cmd ->
            viewModel.sendTerminalCommand(cmd)
          },
          onDismiss = viewModel::closeVirtualizationReportDialog
        )
      }
    }
  }
}
