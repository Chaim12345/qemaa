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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.WebPreviewState
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
fun WebPreviewDialog(
  state: WebPreviewState,
  onClose: () -> Unit
) {
  if (!state.isOpen) return

  var refreshCount by remember { mutableStateOf(0) }

  Dialog(
    onDismissRequest = onClose,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp),
      shape = RoundedCornerShape(16.dp),
      color = CyberSurface,
      border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        // Browser Window Header Bar
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(CyberSurfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
              modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF5F56))
            )
            Box(
              modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFBD2E))
            )
            Box(
              modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(Color(0xFF27C93F))
            )
          }

          Spacer(modifier = Modifier.width(12.dp))

          // URL Address Bar
          Row(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(8.dp))
              .background(TerminalBlack)
              .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.Lock,
              contentDescription = "HTTP/TLS",
              tint = SecondaryEmerald,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "http://localhost:${state.port}/",
              color = TerminalWhite,
              fontSize = 12.sp,
              fontFamily = FontFamily.Monospace
            )
          }

          Spacer(modifier = Modifier.width(8.dp))

          IconButton(
            onClick = { refreshCount++ },
            modifier = Modifier.size(32.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Refresh",
              tint = PrimaryCyan,
              modifier = Modifier.size(18.dp)
            )
          }

          IconButton(
            onClick = onClose,
            modifier = Modifier.size(32.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close",
              tint = TerminalDimText,
              modifier = Modifier.size(18.dp)
            )
          }
        }

        HorizontalDivider(color = CyberBorder, thickness = 1.dp)

        // Web Content Body Simulation
        Column(
          modifier = Modifier
            .fillMaxSize()
            .background(CyberBackground)
            .padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          Card(
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(12.dp)
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.Web,
                  contentDescription = null,
                  tint = PrimaryCyan,
                  modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(
                    text = "QEMU Host Forwarded Service: ${state.serviceName}",
                    color = TerminalWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                  )
                  Text(
                    text = "Forwarded: Host Port ${state.port} ➔ Guest VM Port ${if (state.port == 8080) 80 else state.port}",
                    color = TerminalGreen,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                  )
                }
              }

              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = "Web server response is streaming live from the active guest Linux environment via QEMU SLIRP user network forwarding.",
                color = TerminalDimText,
                fontSize = 13.sp
              )
            }
          }

          // Sample HTTP Response Payload
          Card(
            colors = CardDefaults.cardColors(containerColor = TerminalBlack),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Text(
                text = "HTTP/1.1 200 OK",
                color = TerminalGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "Content-Type: text/html; charset=UTF-8\nServer: Linux-QEMU/6.6\nX-Powered-By: LinuxVM Engine\nCache-Control: no-cache",
                color = TerminalDimText,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
              )
              Spacer(modifier = Modifier.height(10.dp))
              HorizontalDivider(color = CyberBorder, thickness = 1.dp)
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = """
<!DOCTYPE html>
<html>
<head><title>Linux VM Web Root</title></head>
<body style="background:#090D14; color:#00E5FF;">
  <h1>Hello from QEMU Linux Virtual Machine!</h1>
  <p>Status: Healthy | Port: ${state.port} | Timestamp: ${System.currentTimeMillis()}</p>
</body>
</html>
                """.trimIndent(),
                color = TerminalCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
              )
            }
          }
        }
      }
    }
  }
}
