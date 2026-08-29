package com.example.ui.components

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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.TerminalLine
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
import kotlinx.coroutines.delay

@Composable
fun TerminalScreen(
  terminalLines: List<TerminalLine>,
  vmStatus: VmState,
  activePrompt: String,
  terminalInput: String,
  fontSizeSp: Int,
  onInputChange: (String) -> Unit,
  onSendCommand: (String?) -> Unit,
  onStartVm: () -> Unit,
  onStopVm: () -> Unit,
  onClearTerminal: () -> Unit,
  onIncreaseFontSize: () -> Unit,
  onDecreaseFontSize: () -> Unit,
  onKeyTab: () -> Unit,
  onKeyCtrlC: () -> Unit,
  onKeyHistoryPrev: () -> Unit,
  onKeyHistoryNext: () -> Unit,
  onKeyInsert: (String) -> Unit,
  onOpenVirtualizationReport: () -> Unit
) {
  var useXtermEmulator by remember { mutableStateOf(true) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(CyberBackground)
  ) {
    // Top Header Bar
    TerminalHeader(
      vmStatus = vmStatus,
      useXtermEmulator = useXtermEmulator,
      onToggleXtermEmulator = { useXtermEmulator = !useXtermEmulator },
      onStartVm = onStartVm,
      onStopVm = onStopVm,
      onClearTerminal = onClearTerminal,
      onIncreaseFontSize = onIncreaseFontSize,
      onDecreaseFontSize = onDecreaseFontSize,
      onOpenVirtualizationReport = onOpenVirtualizationReport
    )

    if (useXtermEmulator) {
      XtermTerminalView(
        terminalLines = terminalLines,
        activePrompt = activePrompt,
        terminalInput = terminalInput,
        fontSizeSp = fontSizeSp,
        onInputChange = onInputChange,
        onSendCommand = onSendCommand,
        onClearTerminal = onClearTerminal,
        onIncreaseFontSize = onIncreaseFontSize,
        onDecreaseFontSize = onDecreaseFontSize,
        onKeyTab = onKeyTab,
        onKeyCtrlC = onKeyCtrlC,
        onKeyHistoryPrev = onKeyHistoryPrev,
        onKeyHistoryNext = onKeyHistoryNext,
        onInsertText = onKeyInsert,
        modifier = Modifier.weight(1f)
      )
    } else {
      ClassicTerminalView(
        terminalLines = terminalLines,
        activePrompt = activePrompt,
        terminalInput = terminalInput,
        fontSizeSp = fontSizeSp,
        onInputChange = onInputChange,
        onSendCommand = onSendCommand,
        onKeyTab = onKeyTab,
        onKeyCtrlC = onKeyCtrlC,
        onKeyHistoryPrev = onKeyHistoryPrev,
        onKeyHistoryNext = onKeyHistoryNext,
        modifier = Modifier.weight(1f)
      )
    }

    // Touch Accessory Bar
    TouchAccessoryBar(
      terminalInput = terminalInput,
      onInputChange = onInputChange,
      onKeyTab = onKeyTab,
      onKeyCtrlC = onKeyCtrlC,
      onKeyHistoryPrev = onKeyHistoryPrev,
      onKeyHistoryNext = onKeyHistoryNext,
      onInsertText = onKeyInsert,
      onSendCommand = onSendCommand
    )
  }
}

@Composable
private fun TerminalHeader(
  vmStatus: VmState,
  useXtermEmulator: Boolean,
  onToggleXtermEmulator: () -> Unit,
  onStartVm: () -> Unit,
  onStopVm: () -> Unit,
  onClearTerminal: () -> Unit,
  onIncreaseFontSize: () -> Unit,
  onDecreaseFontSize: () -> Unit,
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

  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .background(CyberSurface),
    color = CyberSurface,
    border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
  ) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
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
              // Status Badge
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
              // Xterm.js / Classic Toggle Badge
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(4.dp))
                  .background(if (useXtermEmulator) PrimaryCyan.copy(alpha = 0.18f) else CyberBorder)
                  .border(1.dp, if (useXtermEmulator) PrimaryCyan else CyberBorder, RoundedCornerShape(4.dp))
                  .clickable(onClick = onToggleXtermEmulator)
                  .padding(horizontal = 4.dp, vertical = 2.dp)
              ) {
                Text(
                  text = if (useXtermEmulator) "xterm.js" else "Classic",
                  color = if (useXtermEmulator) PrimaryCyan else TerminalDimText,
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  fontFamily = TerminalFontFamily
                )
              }
            }
            Text(
              text = "QEMU x86_64 TCG Emulation",
              color = statusColor,
              fontSize = 10.sp,
              fontFamily = TerminalFontFamily
            )
          }
        }

        // VM Control & Action Buttons
        Row(verticalAlignment = Alignment.CenterVertically) {
          // Start/Stop VM Button
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

          IconButton(onClick = onOpenVirtualizationReport, modifier = Modifier.size(32.dp)) {
            Icon(
              imageVector = Icons.Default.Memory,
              contentDescription = "Hardware Info",
              tint = PrimaryCyan,
              modifier = Modifier.size(20.dp)
            )
          }
          IconButton(onClick = onClearTerminal, modifier = Modifier.size(32.dp)) {
            Icon(
              imageVector = Icons.Default.Clear,
              contentDescription = "Clear Terminal",
              tint = TerminalRed,
              modifier = Modifier.size(18.dp)
            )
          }
          IconButton(onClick = onDecreaseFontSize, modifier = Modifier.size(28.dp)) {
            Icon(
              imageVector = Icons.Default.FormatSize,
              contentDescription = "Decrease Font",
              tint = TerminalDimText,
              modifier = Modifier.size(16.dp)
            )
          }
          IconButton(onClick = onIncreaseFontSize, modifier = Modifier.size(28.dp)) {
            Icon(
              imageVector = Icons.Default.FormatSize,
              contentDescription = "Increase Font",
              tint = TerminalWhite,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ClassicTerminalView(
  terminalLines: List<TerminalLine>,
  activePrompt: String,
  terminalInput: String,
  fontSizeSp: Int,
  onInputChange: (String) -> Unit,
  onSendCommand: (String?) -> Unit,
  onKeyTab: () -> Unit,
  onKeyCtrlC: () -> Unit,
  onKeyHistoryPrev: () -> Unit,
  onKeyHistoryNext: () -> Unit,
  modifier: Modifier = Modifier
) {
  val listState = rememberLazyListState()
  var cursorVisible by remember { mutableStateOf(true) }
  LaunchedEffect(terminalLines.size) {
    if (terminalLines.isNotEmpty()) {
      listState.animateScrollToItem(terminalLines.size - 1)
    }
  }
  LaunchedEffect(Unit) {
    while (true) {
      delay(500)
      cursorVisible = !cursorVisible
    }
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .background(TerminalBlack)
      .padding(horizontal = 10.dp, vertical = 8.dp)
  ) {
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
          lineHeight = (fontSizeSp + 4).sp
        )
      }
      item {
        Row {
          Text(
            text = activePrompt,
            color = TerminalGreen,
            fontFamily = TerminalFontFamily,
            fontSize = fontSizeSp.sp
          )
          Text(
            text = terminalInput + if (cursorVisible) "█" else " ",
            color = TerminalWhite,
            fontFamily = TerminalFontFamily,
            fontSize = fontSizeSp.sp
          )
        }
      }
    }
  }
}

@Composable
private fun TouchAccessoryBar(
  terminalInput: String,
  onInputChange: (String) -> Unit,
  onKeyTab: () -> Unit,
  onKeyCtrlC: () -> Unit,
  onKeyHistoryPrev: () -> Unit,
  onKeyHistoryNext: () -> Unit,
  onInsertText: (String) -> Unit,
  onSendCommand: (String?) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(CyberSurface)
      .navigationBarsPadding()
      .imePadding()
  ) {
    // Quick Insert Keys Row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 6.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      TouchKey(label = "TAB", onClick = onKeyTab)
      TouchKey(label = "↑", onClick = onKeyHistoryPrev)
      TouchKey(label = "↓", onClick = onKeyHistoryNext)
      TouchKey(label = "/", onClick = { onInsertText("/") })
      TouchKey(label = "-", onClick = { onInsertText("-") })
      TouchKey(label = "_", onClick = { onInsertText("_") })
      TouchKey(label = "|", onClick = { onInsertText("| ") })
      TouchKey(label = ">", onClick = { onInsertText("> ") })
      TouchKey(label = "&", onClick = { onInsertText("&") })
      TouchKey(label = "~", onClick = { onInsertText("~") })
      TouchKey(label = "Ctrl+C", isRed = true, onClick = onKeyCtrlC)
    }

    // Command Input Row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      OutlinedTextField(
        value = terminalInput,
        onValueChange = { newText -> onInputChange(newText) },
        modifier = Modifier.weight(1f),
        placeholder = {
          Text(
            "Enter command...",
            color = TerminalDimText,
            fontFamily = TerminalFontFamily,
            fontSize = 12.sp
          )
        },
        textStyle = TextStyle(
          fontFamily = TerminalFontFamily,
          fontSize = 13.sp,
          color = TerminalWhite
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(
          onSend = { onSendCommand(null) }
        ),
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
          imageVector = Icons.AutoMirrored.Filled.Send,
          contentDescription = "Execute",
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}

@Composable
private fun TouchKey(
  label: String,
  isRed: Boolean = false,
  onClick: () -> Unit
) {
  val bgColor = if (isRed) TerminalRed.copy(alpha = 0.25f) else CyberSurfaceVariant
  val textColor = if (isRed) TerminalRed else TerminalWhite

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
