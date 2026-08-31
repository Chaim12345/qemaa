package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.BootMode
import com.example.engine.DistroManager
import com.example.engine.VmState
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.SecondaryEmerald
import com.example.ui.theme.TerminalBlack
import com.example.ui.theme.TerminalDimText
import com.example.ui.theme.TerminalFontFamily
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TerminalWhite
import com.example.ui.theme.TerminalYellow
import kotlinx.coroutines.flow.SharedFlow

/**
 * The main screen: VM header + distro banner + the real xterm.js terminal.
 */
@Composable
fun TerminalScreen(
  vmStatus: VmState,
  bootMode: BootMode,
  distroState: DistroManager.DistroState,
  terminalChunks: SharedFlow<String>,
  fontSizeSp: Int,
  immersiveMode: Boolean,
  onStartVm: () -> Unit,
  onStopVm: () -> Unit,
  onInstallDistro: () -> Unit,
  onUninstallDistro: () -> Unit,
  onTerminalData: (String) -> Unit,
  onTerminalResized: (cols: Int, rows: Int) -> Unit,
  onFontSizeChanged: (sp: Int) -> Unit,
  onToggleImmersive: () -> Unit,
  onOpenVirtualizationReport: () -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(CyberBackground)
  ) {
    // Top Header Bar
    TerminalHeader(
      vmStatus = vmStatus,
      bootMode = bootMode,
      immersiveMode = immersiveMode,
      onStartVm = onStartVm,
      onStopVm = onStopVm,
      onToggleImmersive = onToggleImmersive,
      onOpenVirtualizationReport = onOpenVirtualizationReport
    )

    // Distro install / status banner
    DistroBanner(
      distroState = distroState,
      vmStatus = vmStatus,
      onInstallDistro = onInstallDistro,
      onUninstallDistro = onUninstallDistro
    )

    // The terminal
    XtermTerminalView(
      terminalChunks = terminalChunks,
      fontSizeSp = fontSizeSp,
      onTerminalData = onTerminalData,
      onTerminalResized = onTerminalResized,
      onFontSizeChanged = onFontSizeChanged,
      modifier = Modifier.weight(1f)
    )
  }
}

@Composable
private fun TerminalHeader(
  vmStatus: VmState,
  bootMode: BootMode,
  immersiveMode: Boolean,
  onStartVm: () -> Unit,
  onStopVm: () -> Unit,
  onToggleImmersive: () -> Unit,
  onOpenVirtualizationReport: () -> Unit
) {
  val statusColor = when (vmStatus) {
    VmState.RUNNING -> SecondaryEmerald
    VmState.BOOTING -> TerminalYellow
    VmState.ERROR -> TerminalRed
    VmState.STOPPED -> TerminalDimText
  }
  val statusText = when (vmStatus) {
    VmState.RUNNING -> "VM Running"
    VmState.BOOTING -> "Booting..."
    VmState.ERROR -> "Error"
    VmState.STOPPED -> "VM Stopped"
  }
  val modeLabel = when (bootMode) {
    BootMode.DISTRO -> "DISTRO"
    BootMode.NETBOOT -> "NETBOOT"
  }
  val modeColor = when (bootMode) {
    BootMode.DISTRO -> TerminalGreen
    BootMode.NETBOOT -> TerminalYellow
  }

  androidx.compose.material3.Surface(
    modifier = Modifier
      .fillMaxWidth()
      .background(CyberSurface),
    color = CyberSurface,
    border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Status indicator
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(statusColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "Linux VM",
              color = TerminalWhite,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(statusColor.copy(alpha = 0.2f))
                .border(1.dp, statusColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
              Text(
                text = statusText,
                color = statusColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = TerminalFontFamily
              )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(modeColor.copy(alpha = 0.15f))
                .border(1.dp, modeColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
              Text(
                text = modeLabel,
                color = modeColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = TerminalFontFamily
              )
            }
          }
          Text(
            text = "QEMU x86_64 TCG • Alpine • Node • Go",
            color = statusColor,
            fontSize = 10.sp,
            fontFamily = TerminalFontFamily
          )
        }
      }

      // VM Control & action buttons
      Row(verticalAlignment = Alignment.CenterVertically) {
        if (vmStatus == VmState.RUNNING || vmStatus == VmState.BOOTING) {
          Button(
            onClick = onStopVm,
            colors = ButtonDefaults.buttonColors(
              containerColor = TerminalRed,
              contentColor = TerminalWhite
            ),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.height(28.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Stop,
              contentDescription = null,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text("Stop", fontWeight = FontWeight.Bold, fontSize = 11.sp)
          }
        } else {
          Button(
            onClick = onStartVm,
            colors = ButtonDefaults.buttonColors(
              containerColor = SecondaryEmerald,
              contentColor = TerminalBlack
            ),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.height(28.dp)
          ) {
            Icon(
              imageVector = Icons.Default.PlayArrow,
              contentDescription = null,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text("Start", fontWeight = FontWeight.Bold, fontSize = 11.sp)
          }
        }

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(
          onClick = onToggleImmersive,
          modifier = Modifier.size(32.dp)
        ) {
          Icon(
            imageVector = if (immersiveMode) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
            contentDescription = "Toggle fullscreen",
            tint = PrimaryCyan,
            modifier = Modifier.size(20.dp)
          )
        }
        IconButton(onClick = onOpenVirtualizationReport, modifier = Modifier.size(32.dp)) {
          Icon(
            imageVector = Icons.Default.Memory,
            contentDescription = "Hardware Info",
            tint = PrimaryCyan,
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }
  }
}

/**
 * One-tap install of the prebuilt distro (Alpine + Node + Go + pi + opencode),
 * with live download progress.
 */
@Composable
private fun DistroBanner(
  distroState: DistroManager.DistroState,
  vmStatus: VmState,
  onInstallDistro: () -> Unit,
  onUninstallDistro: () -> Unit
) {
  androidx.compose.material3.Surface(
    modifier = Modifier
      .fillMaxWidth()
      .background(CyberSurfaceVariant),
    color = CyberSurfaceVariant,
    border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
  ) {
    when (distroState) {
      is DistroManager.DistroState.NotInstalled -> {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "No distro installed — currently boots Alpine via network into RAM.",
              color = TerminalDimText,
              fontSize = 10.sp,
              fontFamily = TerminalFontFamily
            )
            Text(
              text = "Install the prebuilt distro with Node.js, Go, π agent & opencode (persistent disk).",
              color = TerminalWhite,
              fontSize = 10.sp,
              fontFamily = TerminalFontFamily
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = onInstallDistro,
            colors = ButtonDefaults.buttonColors(
              containerColor = PrimaryCyan,
              contentColor = TerminalBlack
            ),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.height(30.dp)
          ) {
            Icon(
              imageVector = Icons.Default.CloudDownload,
              contentDescription = null,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Install Distro", fontWeight = FontWeight.Bold, fontSize = 10.sp)
          }
        }
      }

      is DistroManager.DistroState.Downloading -> {
        Column(modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
          Text(
            text = if (distroState.percent >= 0) {
              "Downloading distro… ${distroState.percent}%  (${distroState.downloadedMb} / ${distroState.totalMb} MB)"
            } else {
              "Downloading distro… ${distroState.downloadedMb} MB"
            },
            color = TerminalYellow,
            fontSize = 10.sp,
            fontFamily = TerminalFontFamily
          )
          Spacer(modifier = Modifier.height(4.dp))
          LinearProgressIndicator(
            progress = {
              if (distroState.percent >= 0) distroState.percent / 100f else 0f
            },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = PrimaryCyan,
            trackColor = CyberBorder
          )
        }
      }

      is DistroManager.DistroState.Verifying -> {
        Text(
          text = "Verifying checksum… (${distroState.downloadedMb} MB image)",
          color = TerminalYellow,
          fontSize = 10.sp,
          fontFamily = TerminalFontFamily,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
        )
      }

      is DistroManager.DistroState.Installed -> {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = "Distro ready (${String.format("%.1f", distroState.sizeGb)} GB, persistent) — node • go • pi • opencode",
            color = TerminalGreen,
            fontSize = 10.sp,
            fontFamily = TerminalFontFamily,
            modifier = Modifier.weight(1f)
          )
          if (vmStatus == VmState.STOPPED || vmStatus == VmState.ERROR) {
            IconButton(
              onClick = onUninstallDistro,
              modifier = Modifier.size(28.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Uninstall distro",
                tint = TerminalDimText,
                modifier = Modifier.size(15.dp)
              )
            }
          }
        }
      }

      is DistroManager.DistroState.Failed -> {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = "Distro install failed: ${distroState.error}",
            color = TerminalRed,
            fontSize = 10.sp,
            fontFamily = TerminalFontFamily,
            modifier = Modifier.weight(1f)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = onInstallDistro,
            colors = ButtonDefaults.buttonColors(
              containerColor = TerminalYellow,
              contentColor = TerminalBlack
            ),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.height(28.dp)
          ) {
            Text("Retry", fontWeight = FontWeight.Bold, fontSize = 10.sp)
          }
        }
      }

      is DistroManager.DistroState.Uninstalling -> {
        Text(
          text = "Removing distro…",
          color = TerminalDimText,
          fontSize = 10.sp,
          fontFamily = TerminalFontFamily,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
        )
      }
    }
  }
}
