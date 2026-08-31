package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.engine.VmState
import com.example.ui.MainViewModel
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

  private val notificationPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* granted or not: the service still runs */ }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    handleStopIntent(intent)
    setContent {
      LinuxVMTheme {
        MainApp(viewModel = viewModel, onRequestNotificationPermission = {
          requestNotificationPermissionIfNeeded()
        })
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleStopIntent(intent)
  }

  private fun handleStopIntent(intent: Intent?) {
    if (intent?.getBooleanExtra(EXTRA_STOP_VM, false) == true) {
      viewModel.stopVm()
    }
  }

  private fun requestNotificationPermissionIfNeeded() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
      PackageManager.PERMISSION_GRANTED
    ) {
      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  private companion object {
    const val EXTRA_STOP_VM = "com.example.extra.STOP_VM"
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: MainViewModel, onRequestNotificationPermission: () -> Unit = {}) {
  val fontSizeSp by viewModel.fontSizeSp.collectAsState()
  val virtualizationReport by viewModel.virtualizationReport.collectAsState()
  val showVirtualizationReportDialog by viewModel.showVirtualizationReportDialog.collectAsState()
  val vmState by viewModel.vmState.collectAsState()
  val bootMode by viewModel.bootMode.collectAsState()
  val distroState by viewModel.distroState.collectAsState()
  val immersiveMode by viewModel.immersiveMode.collectAsState()
  val chunks = viewModel.terminalChunks
  val view = LocalView.current

  // Immersive fullscreen: hide the system bars, terminal gets every pixel.
  DisposableEffect(immersiveMode) {
    val window = (view.context as? ComponentActivity)?.window
    val controller = window?.let { WindowCompat.getInsetsController(it, view) }
    controller?.systemBarsBehavior =
      WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (immersiveMode) controller?.hide(WindowInsetsCompat.Type.systemBars())
    else controller?.show(WindowInsetsCompat.Type.systemBars())
    onDispose { }
  }

  // Keep the screen on while the VM is running.
  DisposableEffect(vmState) {
    val window = (view.context as? ComponentActivity)?.window
    if (vmState == VmState.RUNNING || vmState == VmState.BOOTING) {
      window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      onRequestNotificationPermission()
    } else {
      window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
  }

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
        vmStatus = vmState,
        bootMode = bootMode,
        distroState = distroState,
        terminalChunks = chunks,
        fontSizeSp = fontSizeSp,
        immersiveMode = immersiveMode,
        onStartVm = viewModel::startVm,
        onStopVm = viewModel::stopVm,
        onInstallDistro = viewModel::installDistro,
        onUninstallDistro = viewModel::uninstallDistro,
        onTerminalData = viewModel::onTerminalData,
        onTerminalResized = viewModel::onTerminalResized,
        onFontSizeChanged = viewModel::setFontSizeSp,
        onToggleImmersive = viewModel::toggleImmersiveMode,
        onOpenVirtualizationReport = viewModel::openVirtualizationReportDialog
      )

      // Hardware Virtualization Info Dialog
      if (showVirtualizationReportDialog) {
        VirtualizationReportDialog(
          report = virtualizationReport,
          onRunDiagnosticCommand = { cmd ->
            // Commands run inside the guest terminal now; type them via the stream.
            viewModel.onTerminalData(
              com.example.engine.DistroManager.encodeForTerminal(cmd)
            )
          },
          onDismiss = viewModel::closeVirtualizationReportDialog
        )
      }
    }
  }
}
