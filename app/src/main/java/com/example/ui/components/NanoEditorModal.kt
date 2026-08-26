package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.NanoEditorState
import com.example.ui.theme.CyberBackground
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberSurface
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.SecondaryEmerald
import com.example.ui.theme.TerminalBlack
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TerminalDimText
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalWhite
import com.example.ui.theme.TerminalYellow

@Composable
fun NanoEditorModal(
  state: NanoEditorState,
  onContentChange: (String) -> Unit,
  onSaveAndExit: () -> Unit,
  onCancel: () -> Unit
) {
  if (!state.isOpen) return

  Dialog(
    onDismissRequest = onCancel,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxSize()
        .padding(12.dp),
      shape = RoundedCornerShape(12.dp),
      color = TerminalBlack,
      border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(TerminalBlack)
      ) {
        // Nano Top Header
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(CyberSurfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "GNU nano 7.2",
            color = TerminalCyan,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = state.filePath + (if (state.isModified) " [Modified]" else ""),
            color = if (state.isModified) TerminalYellow else TerminalWhite,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = onSaveAndExit,
              modifier = Modifier.padding(end = 4.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Save,
                contentDescription = "Save and exit",
                tint = SecondaryEmerald
              )
            }
            IconButton(onClick = onCancel) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close editor",
                tint = TerminalDimText
              )
            }
          }
        }

        // Text Editor Area
        OutlinedTextField(
          value = state.content,
          onValueChange = onContentChange,
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
          textStyle = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = TerminalWhite,
            lineHeight = 18.sp
          ),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = TerminalBlack,
            unfocusedContainerColor = TerminalBlack
          ),
          placeholder = {
            Text(
              "Type file content here...",
              color = TerminalDimText,
              fontFamily = FontFamily.Monospace,
              fontSize = 13.sp
            )
          }
        )

        // Bottom Nano Shortcut Bar
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(CyberSurface)
            .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            NanoShortcutItem(key = "^G", label = "Get Help")
            NanoShortcutItem(key = "^O", label = "WriteOut")
            NanoShortcutItem(key = "^W", label = "Where Is")
            NanoShortcutItem(key = "^K", label = "Cut")
            NanoShortcutItem(key = "^U", label = "Paste")
            NanoShortcutItem(key = "^X", label = "Exit")
          }

          Spacer(modifier = Modifier.height(8.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
          ) {
            OutlinedButton(
              onClick = onCancel,
              colors = ButtonDefaults.outlinedButtonColors(contentColor = TerminalDimText),
              modifier = Modifier.padding(end = 8.dp)
            ) {
              Text("Discard", fontSize = 12.sp)
            }
            Button(
              onClick = onSaveAndExit,
              colors = ButtonDefaults.buttonColors(
                containerColor = SecondaryEmerald,
                contentColor = TerminalBlack
              )
            ) {
              Icon(
                imageVector = Icons.Default.Save,
                contentDescription = null,
                modifier = Modifier.padding(end = 6.dp)
              )
              Text("Save & Exit (^O)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun NanoShortcutItem(key: String, label: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
      text = key,
      color = TerminalBlack,
      fontSize = 10.sp,
      fontWeight = FontWeight.Bold,
      fontFamily = FontFamily.Monospace,
      modifier = Modifier
        .background(TerminalWhite, RoundedCornerShape(2.dp))
        .padding(horizontal = 3.dp, vertical = 1.dp)
    )
    Spacer(modifier = Modifier.width(3.dp))
    Text(
      text = label,
      color = TerminalDimText,
      fontSize = 10.sp,
      fontFamily = FontFamily.Monospace
    )
  }
}
