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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.VirtualMachineEntity
import com.example.data.model.VmSnapshotEntity
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
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TerminalWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SnapshotDialog(
  vm: VirtualMachineEntity?,
  snapshots: List<VmSnapshotEntity>,
  onCreateSnapshot: (String, String) -> Unit,
  onRestoreSnapshot: (VmSnapshotEntity) -> Unit,
  onDeleteSnapshot: (VmSnapshotEntity) -> Unit,
  onDismiss: () -> Unit
) {
  if (vm == null) return

  var showCreateForm by remember { mutableStateOf(false) }
  var snapshotName by remember { mutableStateOf("") }
  var snapshotDesc by remember { mutableStateOf("") }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .padding(vertical = 24.dp),
      shape = RoundedCornerShape(16.dp),
      color = CyberSurface,
      border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.CameraAlt,
              contentDescription = null,
              tint = PrimaryCyan,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "VM Snapshots",
                color = TerminalWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
              )
              Text(
                text = vm.name,
                color = TerminalGreen,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
              )
            }
          }

          IconButton(onClick = onDismiss) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close",
              tint = TerminalDimText
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = CyberBorder)
        Spacer(modifier = Modifier.height(12.dp))

        if (showCreateForm) {
          Card(
            colors = CardDefaults.cardColors(containerColor = CyberSurfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text(
                text = "Create Point-in-Time Snapshot (savevm)",
                color = PrimaryCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
              )
              Spacer(modifier = Modifier.height(8.dp))

              OutlinedTextField(
                value = snapshotName,
                onValueChange = { snapshotName = it },
                label = { Text("Snapshot Name (e.g. clean-install)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = PrimaryCyan,
                  unfocusedBorderColor = CyberBorder
                )
              )

              Spacer(modifier = Modifier.height(8.dp))

              OutlinedTextField(
                value = snapshotDesc,
                onValueChange = { snapshotDesc = it },
                label = { Text("Description (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = PrimaryCyan,
                  unfocusedBorderColor = CyberBorder
                )
              )

              Spacer(modifier = Modifier.height(12.dp))

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
              ) {
                OutlinedButton(
                  onClick = { showCreateForm = false },
                  modifier = Modifier.padding(end = 8.dp)
                ) {
                  Text("Cancel", color = TerminalDimText)
                }

                Button(
                  onClick = {
                    if (snapshotName.isNotBlank()) {
                      onCreateSnapshot(snapshotName, snapshotDesc)
                      snapshotName = ""
                      snapshotDesc = ""
                      showCreateForm = false
                    }
                  },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = SecondaryEmerald,
                    contentColor = TerminalBlack
                  )
                ) {
                  Text("Save Snapshot", fontWeight = FontWeight.Bold)
                }
              }
            }
          }
          Spacer(modifier = Modifier.height(12.dp))
        } else {
          Button(
            onClick = {
              snapshotName = "snap-${SimpleDateFormat("MMdd-HHmm", Locale.US).format(Date())}"
              showCreateForm = true
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = PrimaryCyan,
              contentColor = TerminalBlack
            ),
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Take New Snapshot", fontWeight = FontWeight.Bold)
          }
          Spacer(modifier = Modifier.height(12.dp))
        }

        if (snapshots.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(32.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "No snapshots recorded yet.\nTake a snapshot to freeze VM memory and disk state.",
              color = TerminalDimText,
              fontSize = 13.sp,
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
          }
        } else {
          LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f, fill = false)
          ) {
            items(snapshots) { snap ->
              Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurfaceVariant),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = snap.name,
                      color = TerminalCyan,
                      fontWeight = FontWeight.Bold,
                      fontSize = 14.sp,
                      fontFamily = FontFamily.Monospace
                    )
                    if (snap.description.isNotBlank()) {
                      Text(
                        text = snap.description,
                        color = TerminalWhite,
                        fontSize = 12.sp
                      )
                    }
                    Text(
                      text = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(snap.timestamp)) + " | RAM: ${snap.ramUsageMb}MB",
                      color = TerminalDimText,
                      fontSize = 11.sp,
                      fontFamily = FontFamily.Monospace
                    )
                  }

                  Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                      onClick = { onRestoreSnapshot(snap) },
                      modifier = Modifier.size(36.dp)
                    ) {
                      Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = "Restore",
                        tint = SecondaryEmerald
                      )
                    }
                    IconButton(
                      onClick = { onDeleteSnapshot(snap) },
                      modifier = Modifier.size(36.dp)
                    ) {
                      Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = TerminalRed
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
