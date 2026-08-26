package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TelemetryMetrics
import com.example.data.model.VirtualMachineEntity
import com.example.data.model.VmStatus
import com.example.engine.EngineMode
import com.example.engine.TerminalLine
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
import com.example.ui.theme.TerminalOrange
import com.example.ui.theme.TerminalPurple
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TerminalWhite
import com.example.ui.theme.TerminalYellow
import kotlinx.coroutines.delay

@Composable
fun TerminalScreen(
  activeVm: VirtualMachineEntity?,
  terminalLines: List<TerminalLine>,
  telemetry: TelemetryMetrics,
  installProgress: Float?,
  installStatusText: String,
  terminalInput: String,
  fontSizeSp: Int,
  engineMode: EngineMode,
  activePrompt: String,
  onInputChange: (String) -> Unit,
  onSendCommand: (String?) -> Unit,
  onStartVm: () -> Unit,
  onPauseVm: () -> Unit,
  onResumeVm: () -> Unit,
  onShutdownVm: () -> Unit,
  onResetVm: () -> Unit,
  onClearTerminal: () -> Unit,
  onIncreaseFontSize: () -> Unit,
  onDecreaseFontSize: () -> Unit,
  onKeyTab: () -> Unit,
  onKeyCtrlC: () -> Unit,
  onKeyHistoryPrev: () -> Unit,
  onKeyHistoryNext: () -> Unit,
  onKeyInsert: (String) -> Unit,
  onOpenSnapshots: () -> Unit,
  onOpenWebPreview: (Int, String) -> Unit,
  onToggleEngineMode: () -> Unit,
  onOpenVirtualizationReport: () -> Unit
) {
  val listState = rememberLazyListState()

  LaunchedEffect(terminalLines.size) {
    if (terminalLines.isNotEmpty()) {
      listState.animateScrollToItem(terminalLines.size - 1)
    }
  }

  // Blinking cursor
  var cursorVisible by remember { mutableStateOf(true) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(500)
      cursorVisible = !cursorVisible
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(CyberBackground)
  ) {
    // Top VM Control & Telemetry Bar
    VmControlHeader(
      activeVm = activeVm,
      telemetry = telemetry,
      engineMode = engineMode,
      onStartVm = onStartVm,
      onPauseVm = onPauseVm,
      onResumeVm = onResumeVm,
      onShutdownVm = onShutdownVm,
      onResetVm = onResetVm,
      onOpenSnapshots = onOpenSnapshots,
      onOpenWebPreview = onOpenWebPreview,
      onToggleEngineMode = onToggleEngineMode,
      onOpenVirtualizationReport = onOpenVirtualizationReport
    )

    // Install / Boot Progress Banner
    AnimatedVisibility(
      visible = installProgress != null,
      enter = fadeIn(),
      exit = fadeOut()
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(CyberSurfaceVariant)
          .padding(horizontal = 16.dp, vertical = 8.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = installStatusText,
            color = PrimaryCyan,
            fontSize = 12.sp,
            fontFamily = TerminalFontFamily
          )
          Text(
            text = "${((installProgress ?: 0f) * 100).toInt()}%",
            color = SecondaryEmerald,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = TerminalFontFamily
          )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
          progress = { installProgress ?: 0f },
          modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp)),
          color = SecondaryEmerald,
          trackColor = CyberBorder
        )
      }
    }

    // Terminal Screen Canvas
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .background(TerminalBlack)
        .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
      if (terminalLines.isEmpty()) {
        Column(
          modifier = Modifier.align(Alignment.Center),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "QEMU Virtual Console Ready",
            color = TerminalGreen,
            fontFamily = TerminalFontFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = if (activeVm?.status == VmStatus.RUNNING.name) {
              "Linux kernel ready. Enter commands below."
            } else {
              "Virtual machine is ${activeVm?.status ?: "STOPPED"}.\nTap the 'Start' button above to boot Linux."
            },
            color = TerminalDimText,
            fontFamily = TerminalFontFamily,
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
          )
        }
      } else {
        LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(vertical = 4.dp)
        ) {
          items(terminalLines) { line ->
            Text(
              text = line.text,
              color = line.color,
              fontFamily = TerminalFontFamily,
              fontSize = fontSizeSp.sp,
              lineHeight = (fontSizeSp + 4).sp,
              fontWeight = if (line.isPrompt || line.isSystem) FontWeight.Bold else FontWeight.Normal
            )
          }

          // Live prompt line with blinking cursor
          val isPromptActive = engineMode == EngineMode.REAL_NATIVE_HOST || activeVm?.status == VmStatus.RUNNING.name
          if (isPromptActive) {
            item {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "$activePrompt$terminalInput",
                  color = if (engineMode == EngineMode.REAL_NATIVE_HOST) SecondaryEmerald else TerminalGreen,
                  fontFamily = TerminalFontFamily,
                  fontSize = fontSizeSp.sp,
                  lineHeight = (fontSizeSp + 4).sp,
                  fontWeight = FontWeight.Bold
                )
                if (cursorVisible) {
                  Box(
                    modifier = Modifier
                      .width(8.dp)
                      .height((fontSizeSp + 2).dp)
                      .background(if (engineMode == EngineMode.REAL_NATIVE_HOST) SecondaryEmerald else TerminalGreen)
                  )
                }
              }
            }
          }
        }
      }
    }

    // Quick Command Helper Chips
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(CyberSurface)
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 8.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      QuickChip(label = "neofetch") { onSendCommand("neofetch") }
      QuickChip(label = "apk update") { onSendCommand("apk update") }
      QuickChip(label = "apk add git") { onSendCommand("apk add git") }
      QuickChip(label = "rc-service sshd") { onSendCommand("rc-service sshd status") }
      QuickChip(label = "lbu commit") { onSendCommand("lbu commit") }
      QuickChip(label = "setup-alpine") { onSendCommand("setup-alpine") }
      QuickChip(label = "info cpus") { onSendCommand("info cpus") }
      QuickChip(label = "info block") { onSendCommand("info block") }
      QuickChip(label = "info kvm") { onSendCommand("info kvm") }
      QuickChip(label = "ssh info") { onSendCommand("ssh") }
      QuickChip(label = "top") { onSendCommand("top") }
      QuickChip(label = "ls -la") { onSendCommand("ls -la") }
      QuickChip(label = "df -h") { onSendCommand("df -h") }
      QuickChip(label = "free -m") { onSendCommand("free -m") }
      QuickChip(label = "ip a") { onSendCommand("ip a") }
      QuickChip(label = "curl") { onSendCommand("curl https://api.github.com") }
      QuickChip(label = "uname -a") { onSendCommand("uname -a") }
      QuickChip(label = "nano") { onSendCommand("nano welcome.txt") }
      QuickChip(label = "python3") { onSendCommand("python3 -c \"print('Hello Python!')\"") }
      QuickChip(label = "go run") { onSendCommand("go run main.go") }
      QuickChip(label = "cargo run") { onSendCommand("cargo run") }
      QuickChip(label = "node -e") { onSendCommand("node -e \"console.log('Hello Node.js!')\"") }
      QuickChip(label = "gcc -v") { onSendCommand("gcc -v") }
      QuickChip(label = "help") { onSendCommand("help") }
    }

    // Touch Keyboard Accessory Toolbar (ESC, TAB, CTRL, ALT, |, /, -, ~, Up, Down, Clear, Font +/-)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(CyberSurfaceVariant)
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 6.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      TouchKey(label = "ESC") { onKeyInsert("") }
      TouchKey(label = "TAB", isAccent = true) { onKeyTab() }
      TouchKey(label = "^C", isRed = true) { onKeyCtrlC() }
      TouchKey(label = "|") { onKeyInsert("| ") }
      TouchKey(label = "/") { onKeyInsert("/") }
      TouchKey(label = "-") { onKeyInsert("-") }
      TouchKey(label = "~") { onKeyInsert("~") }
      TouchKey(label = ">") { onKeyInsert(" > ") }
      TouchKey(label = "&&") { onKeyInsert(" && ") }
      TouchKey(label = "↑") { onKeyHistoryPrev() }
      TouchKey(label = "↓") { onKeyHistoryNext() }
      TouchKey(label = "CLR") { onClearTerminal() }
      TouchKey(label = "A+") { onIncreaseFontSize() }
      TouchKey(label = "A-") { onDecreaseFontSize() }
    }

    // Command Input Row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .background(CyberSurface)
        .padding(horizontal = 8.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      OutlinedTextField(
        value = terminalInput,
        onValueChange = onInputChange,
        modifier = Modifier.weight(1f),
        placeholder = {
          Text(
            "Enter Linux command...",
            color = TerminalDimText,
            fontFamily = TerminalFontFamily,
            fontSize = 13.sp
          )
        },
        textStyle = TextStyle(
          fontFamily = TerminalFontFamily,
          fontSize = 13.sp,
          color = TerminalWhite
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSendCommand(null) }),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = PrimaryCyan,
          unfocusedBorderColor = CyberBorder,
          focusedContainerColor = TerminalBlack,
          unfocusedContainerColor = TerminalBlack
        ),
        shape = RoundedCornerShape(8.dp)
      )

      Spacer(modifier = Modifier.width(6.dp))

      Button(
        onClick = { onSendCommand(null) },
        colors = ButtonDefaults.buttonColors(
          containerColor = SecondaryEmerald,
          contentColor = TerminalBlack
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Send,
          contentDescription = "Execute Command",
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}

@Composable
private fun VmControlHeader(
  activeVm: VirtualMachineEntity?,
  telemetry: TelemetryMetrics,
  engineMode: EngineMode,
  onStartVm: () -> Unit,
  onPauseVm: () -> Unit,
  onResumeVm: () -> Unit,
  onShutdownVm: () -> Unit,
  onResetVm: () -> Unit,
  onOpenSnapshots: () -> Unit,
  onOpenWebPreview: (Int, String) -> Unit,
  onToggleEngineMode: () -> Unit,
  onOpenVirtualizationReport: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = CyberSurface),
    shape = RoundedCornerShape(0.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
  ) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // VM Identity & Status Indicator
        Row(verticalAlignment = Alignment.CenterVertically) {
          val isRunning = activeVm?.status == VmStatus.RUNNING.name
          val isPaused = activeVm?.status == VmStatus.PAUSED.name
          val isBooting = activeVm?.status == VmStatus.BOOTING.name

          val statusColor = when {
            isRunning -> SecondaryEmerald
            isPaused -> TerminalYellow
            isBooting -> PrimaryCyan
            else -> TerminalDimText
          }

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
                text = activeVm?.name ?: "No VM Active",
                color = TerminalWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
              )
              Spacer(modifier = Modifier.width(6.dp))
              // Mode switcher badge
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(4.dp))
                  .background(if (engineMode == EngineMode.REAL_NATIVE_HOST) SecondaryEmerald.copy(alpha = 0.2f) else PrimaryCyan.copy(alpha = 0.2f))
                  .border(1.dp, if (engineMode == EngineMode.REAL_NATIVE_HOST) SecondaryEmerald else PrimaryCyan, RoundedCornerShape(4.dp))
                  .clickable(onClick = onToggleEngineMode)
                  .padding(horizontal = 5.dp, vertical = 2.dp)
              ) {
                Text(
                  text = engineMode.badge,
                  color = if (engineMode == EngineMode.REAL_NATIVE_HOST) SecondaryEmerald else PrimaryCyan,
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  fontFamily = TerminalFontFamily
                )
              }
            }
            Text(
              text = if (engineMode == EngineMode.REAL_NATIVE_HOST) "Real Host /system/bin/sh" else "${activeVm?.arch ?: "x86_64"} | ${activeVm?.status ?: "STOPPED"} | ${activeVm?.ramMb ?: 0}MB",
              color = statusColor,
              fontSize = 10.sp,
              fontFamily = TerminalFontFamily
            )
          }
        }

        // Action Buttons
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onOpenVirtualizationReport, modifier = Modifier.size(32.dp)) {
            Icon(
              imageVector = Icons.Default.Memory,
              contentDescription = "Hardware & Virtualization Info",
              tint = PrimaryCyan,
              modifier = Modifier.size(20.dp)
            )
          }

          when (activeVm?.status) {
            VmStatus.RUNNING.name -> {
              IconButton(onClick = onPauseVm, modifier = Modifier.size(32.dp)) {
                Icon(
                  imageVector = Icons.Default.Pause,
                  contentDescription = "Pause VM",
                  tint = TerminalYellow,
                  modifier = Modifier.size(20.dp)
                )
              }
              IconButton(onClick = onShutdownVm, modifier = Modifier.size(32.dp)) {
                Icon(
                  imageVector = Icons.Default.PowerSettingsNew,
                  contentDescription = "Shutdown VM",
                  tint = TerminalRed,
                  modifier = Modifier.size(20.dp)
                )
              }
            }
            VmStatus.PAUSED.name -> {
              IconButton(onClick = onResumeVm, modifier = Modifier.size(32.dp)) {
                Icon(
                  imageVector = Icons.Default.PlayArrow,
                  contentDescription = "Resume VM",
                  tint = SecondaryEmerald,
                  modifier = Modifier.size(20.dp)
                )
              }
              IconButton(onClick = onShutdownVm, modifier = Modifier.size(32.dp)) {
                Icon(
                  imageVector = Icons.Default.PowerSettingsNew,
                  contentDescription = "Shutdown VM",
                  tint = TerminalRed,
                  modifier = Modifier.size(20.dp)
                )
              }
            }
            else -> {
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
          }

          IconButton(onClick = onResetVm, modifier = Modifier.size(32.dp)) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Reset VM",
              tint = PrimaryCyan,
              modifier = Modifier.size(18.dp)
            )
          }

          IconButton(onClick = onOpenSnapshots, modifier = Modifier.size(32.dp)) {
            Icon(
              imageVector = Icons.Default.CameraAlt,
              contentDescription = "Snapshots",
              tint = TerminalPurple,
              modifier = Modifier.size(18.dp)
            )
          }

          IconButton(
            onClick = { onOpenWebPreview(8080, activeVm?.name ?: "Web") },
            modifier = Modifier.size(32.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Web,
              contentDescription = "Web Preview",
              tint = PrimaryCyan,
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }

      // Live Telemetry strip if running
      if (activeVm?.status == VmStatus.RUNNING.name) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(TerminalBlack, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = "CPU: ${String.format("%.1f", telemetry.cpuPercent)}%",
            color = PrimaryCyan,
            fontSize = 10.sp,
            fontFamily = TerminalFontFamily
          )
          Text(
            text = "RAM: ${telemetry.ramUsedMb}/${telemetry.ramTotalMb}MB",
            color = SecondaryEmerald,
            fontSize = 10.sp,
            fontFamily = TerminalFontFamily
          )
          Text(
            text = "DISK: ${telemetry.diskReadKbps.toInt()}KB/s",
            color = TerminalYellow,
            fontSize = 10.sp,
            fontFamily = TerminalFontFamily
          )
          Text(
            text = "NET: ↓${telemetry.netRxKbps.toInt()} ↑${telemetry.netTxKbps.toInt()}KB/s",
            color = TerminalPurple,
            fontSize = 10.sp,
            fontFamily = TerminalFontFamily
          )
        }
      }
    }
  }
}

@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(4.dp))
      .background(CyberSurfaceVariant)
      .border(1.dp, CyberBorder, RoundedCornerShape(4.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 8.dp, vertical = 4.dp)
  ) {
    Text(
      text = label,
      color = PrimaryCyan,
      fontSize = 11.sp,
      fontFamily = TerminalFontFamily
    )
  }
}

@Composable
private fun TouchKey(
  label: String,
  isAccent: Boolean = false,
  isRed: Boolean = false,
  onClick: () -> Unit
) {
  val bgColor = when {
    isAccent -> SecondaryEmerald.copy(alpha = 0.25f)
    isRed -> TerminalRed.copy(alpha = 0.25f)
    else -> CyberSurface
  }
  val textColor = when {
    isAccent -> SecondaryEmerald
    isRed -> TerminalRed
    else -> TerminalWhite
  }

  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(4.dp))
      .background(bgColor)
      .border(1.dp, CyberBorder, RoundedCornerShape(4.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 7.dp, vertical = 5.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = label,
      color = textColor,
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      fontFamily = TerminalFontFamily
    )
  }
}
