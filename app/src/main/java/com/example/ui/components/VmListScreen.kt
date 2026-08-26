package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VirtualMachineEntity
import com.example.data.model.VmStatus
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.SecondaryEmerald
import com.example.ui.theme.TerminalBlack
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TerminalDimText
import com.example.ui.theme.TerminalFontFamily
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalPurple
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TerminalWhite
import com.example.ui.theme.TerminalYellow

@Composable
fun VmListScreen(
  vms: List<VirtualMachineEntity>,
  activeVm: VirtualMachineEntity?,
  onSelectVm: (VirtualMachineEntity) -> Unit,
  onStartVm: (VirtualMachineEntity) -> Unit,
  onPauseVm: (VirtualMachineEntity) -> Unit,
  onResumeVm: (VirtualMachineEntity) -> Unit,
  onShutdownVm: (VirtualMachineEntity) -> Unit,
  onOpenConsole: (VirtualMachineEntity) -> Unit,
  onOpenConfig: (VirtualMachineEntity) -> Unit,
  onOpenSnapshots: (VirtualMachineEntity) -> Unit,
  onDeleteVm: (VirtualMachineEntity) -> Unit,
  onCreateNewVm: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(CyberBackground)
  ) {
    if (vms.isEmpty()) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Icon(
          imageVector = Icons.Default.Dns,
          contentDescription = null,
          tint = PrimaryCyan,
          modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          text = "No Virtual Machines Found",
          color = TerminalWhite,
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "Visit the Distro Hub for 1-click installation or create a custom QEMU machine.",
          color = TerminalDimText,
          fontSize = 13.sp,
          textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
          onClick = onCreateNewVm,
          colors = ButtonDefaults.buttonColors(
            containerColor = SecondaryEmerald,
            contentColor = TerminalBlack
          )
        ) {
          Icon(imageVector = Icons.Default.Add, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Create New VM", fontWeight = FontWeight.Bold)
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Configured Machines (${vms.size})",
              color = TerminalWhite,
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp
            )
            Text(
              text = "QEMU Virtualizer",
              color = PrimaryCyan,
              fontSize = 12.sp,
              fontFamily = TerminalFontFamily
            )
          }
        }

        items(vms) { vm ->
          VmCard(
            vm = vm,
            isActive = activeVm?.id == vm.id,
            onSelect = { onSelectVm(vm) },
            onStart = { onStartVm(vm) },
            onPause = { onPauseVm(vm) },
            onResume = { onResumeVm(vm) },
            onShutdown = { onShutdownVm(vm) },
            onOpenConsole = { onOpenConsole(vm) },
            onOpenConfig = { onOpenConfig(vm) },
            onOpenSnapshots = { onOpenSnapshots(vm) },
            onDelete = { onDeleteVm(vm) }
          )
        }
      }
    }
  }
}

@Composable
private fun VmCard(
  vm: VirtualMachineEntity,
  isActive: Boolean,
  onSelect: () -> Unit,
  onStart: () -> Unit,
  onPause: () -> Unit,
  onResume: () -> Unit,
  onShutdown: () -> Unit,
  onOpenConsole: () -> Unit,
  onOpenConfig: () -> Unit,
  onOpenSnapshots: () -> Unit,
  onDelete: () -> Unit
) {
  val isRunning = vm.status == VmStatus.RUNNING.name
  val isPaused = vm.status == VmStatus.PAUSED.name

  val statusColor = when {
    isRunning -> SecondaryEmerald
    isPaused -> TerminalYellow
    vm.status == VmStatus.BOOTING.name -> PrimaryCyan
    else -> TerminalDimText
  }

  val borderColor = if (isActive) PrimaryCyan else CyberBorder

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onSelect),
    colors = CardDefaults.cardColors(containerColor = CyberSurface),
    border = androidx.compose.foundation.BorderStroke(if (isActive) 1.5.dp else 1.dp, borderColor),
    shape = RoundedCornerShape(12.dp)
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      // Header: Name & Status
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(10.dp)
              .clip(CircleShape)
              .background(statusColor)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = vm.name,
            color = TerminalWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
          )
        }

        // Active Badge
        if (isActive) {
          Box(
            modifier = Modifier
              .background(PrimaryCyan.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
              .border(1.dp, PrimaryCyan, RoundedCornerShape(4.dp))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = "ACTIVE",
              color = PrimaryCyan,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = TerminalFontFamily
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Specs Summary Row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(CyberSurfaceVariant, RoundedCornerShape(6.dp))
          .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = "Arch: ${vm.arch}",
          color = TerminalCyan,
          fontSize = 11.sp,
          fontFamily = TerminalFontFamily
        )
        Text(
          text = "${vm.cpuCores} vCPUs",
          color = TerminalWhite,
          fontSize = 11.sp,
          fontFamily = TerminalFontFamily
        )
        Text(
          text = "${vm.ramMb}MB RAM",
          color = SecondaryEmerald,
          fontSize = 11.sp,
          fontFamily = TerminalFontFamily
        )
        Text(
          text = "${vm.diskSizeGb.toInt()}GB Disk",
          color = TerminalYellow,
          fontSize = 11.sp,
          fontFamily = TerminalFontFamily
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Action Buttons Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          when {
            isRunning -> {
              Button(
                onClick = onPause,
                colors = ButtonDefaults.buttonColors(
                  containerColor = TerminalYellow,
                  contentColor = TerminalBlack
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp)
              ) {
                Icon(imageVector = Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Pause", fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
              IconButton(onClick = onShutdown, modifier = Modifier.size(36.dp)) {
                Icon(imageVector = Icons.Default.PowerSettingsNew, contentDescription = "Shutdown", tint = TerminalRed)
              }
            }
            isPaused -> {
              Button(
                onClick = onResume,
                colors = ButtonDefaults.buttonColors(
                  containerColor = SecondaryEmerald,
                  contentColor = TerminalBlack
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp)
              ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Resume", fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
              IconButton(onClick = onShutdown, modifier = Modifier.size(36.dp)) {
                Icon(imageVector = Icons.Default.PowerSettingsNew, contentDescription = "Shutdown", tint = TerminalRed)
              }
            }
            else -> {
              Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(
                  containerColor = SecondaryEmerald,
                  contentColor = TerminalBlack
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp)
              ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Start", fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
          }

          // Open Terminal Button
          OutlinedButton(
            onClick = onOpenConsole,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            shape = RoundedCornerShape(6.dp)
          ) {
            Icon(imageVector = Icons.Default.Terminal, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Console", color = PrimaryCyan, fontSize = 12.sp)
          }
        }

        // Config, Snapshot, and Delete Icons
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onOpenConfig, modifier = Modifier.size(36.dp)) {
            Icon(imageVector = Icons.Default.Settings, contentDescription = "Config", tint = TerminalWhite)
          }
          IconButton(onClick = onOpenSnapshots, modifier = Modifier.size(36.dp)) {
            Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "Snapshots", tint = TerminalPurple)
          }
          IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = TerminalRed)
          }
        }
      }
    }
  }
}
