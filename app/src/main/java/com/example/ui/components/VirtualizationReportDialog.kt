package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.engine.EngineMode
import com.example.engine.VirtualizationReport
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

@Composable
fun VirtualizationReportDialog(
  report: VirtualizationReport,
  currentMode: EngineMode,
  onSelectMode: (EngineMode) -> Unit,
  onRunDiagnosticCommand: (String) -> Unit,
  onDismiss: () -> Unit
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .padding(vertical = 24.dp)
        .clip(RoundedCornerShape(12.dp))
        .border(1.dp, PrimaryCyan, RoundedCornerShape(12.dp)),
      color = CyberSurface
    ) {
      Column(
        modifier = Modifier
          .padding(16.dp)
          .verticalScroll(rememberScrollState())
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Memory,
              contentDescription = null,
              tint = PrimaryCyan,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(
                text = "Hardware Virtualization & Engine",
                color = TerminalWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
              )
              Text(
                text = "Real Linux Subsystem vs QEMU Emulation",
                color = TerminalDimText,
                fontSize = 11.sp,
                fontFamily = TerminalFontFamily
              )
            }
          }

          IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close",
              tint = TerminalDimText
            )
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Engine Mode Switcher Card
        Text(
          text = "CHOOSE EXECUTION ENGINE",
          color = SecondaryEmerald,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = TerminalFontFamily
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // Real Native Mode
          val isNative = currentMode == EngineMode.REAL_NATIVE_HOST
          Card(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(8.dp))
              .border(
                1.5.dp,
                if (isNative) SecondaryEmerald else CyberBorder,
                RoundedCornerShape(8.dp)
              )
              .clickable { onSelectMode(EngineMode.REAL_NATIVE_HOST) },
            colors = CardDefaults.cardColors(
              containerColor = if (isNative) SecondaryEmerald.copy(alpha = 0.15f) else CyberSurfaceVariant
            )
          ) {
            Column(modifier = Modifier.padding(10.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "⚡ Real Native Host",
                  color = if (isNative) SecondaryEmerald else TerminalWhite,
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp
                )
                if (isNative) {
                  Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SecondaryEmerald,
                    modifier = Modifier.size(16.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Direct process execution on Android's Linux kernel (/system/bin/sh). Real hardware, real CPU & memory.",
                color = TerminalDimText,
                fontSize = 10.sp,
                lineHeight = 13.sp
              )
            }
          }

          // QEMU Guest Mode
          val isQemu = currentMode == EngineMode.QEMU_GUEST
          Card(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(8.dp))
              .border(
                1.5.dp,
                if (isQemu) PrimaryCyan else CyberBorder,
                RoundedCornerShape(8.dp)
              )
              .clickable { onSelectMode(EngineMode.QEMU_GUEST) },
            colors = CardDefaults.cardColors(
              containerColor = if (isQemu) PrimaryCyan.copy(alpha = 0.15f) else CyberSurfaceVariant
            )
          ) {
            Column(modifier = Modifier.padding(10.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "🖥️ QEMU Guest VM",
                  color = if (isQemu) PrimaryCyan else TerminalWhite,
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp
                )
                if (isQemu) {
                  Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = PrimaryCyan,
                    modifier = Modifier.size(16.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Isolated virtual machine with multi-architecture support (x86_64, aarch64, riscv64), QCOW2 disks & VirtIO.",
                color = TerminalDimText,
                fontSize = 10.sp,
                lineHeight = 13.sp
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = CyberBorder)
        Spacer(modifier = Modifier.height(14.dp))

        // Hardware Probing Telemetry
        Text(
          text = "PHYSICAL HARDWARE & KERNEL STATUS",
          color = PrimaryCyan,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = TerminalFontFamily
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = TerminalBlack),
          shape = RoundedCornerShape(8.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder)
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            ReportRow("Host OS", "Android ${report.androidRelease} (API ${report.androidSdkInt})")
            ReportRow("Device Model", report.deviceModel)
            ReportRow("Host Architecture", "${report.hostArchitecture} (${report.physicalCpuCores} physical cores)")
            ReportRow("Physical RAM", "${report.availableHostRamMb}MB free / ${report.totalHostRamMb}MB total")
            ReportRow("Linux Kernel", report.hostKernelVersion.take(45))
            ReportRow(
              "Hardware KVM (/dev/kvm)",
              if (report.hasKvmDevice) "Present (${if (report.isKvmWritable) "Read/Write" else "SELinux Restricted"})" else "Not exposed in userland sandbox",
              color = if (report.hasKvmDevice && report.isKvmWritable) SecondaryEmerald else TerminalYellow
            )
            ReportRow(
              "Root / SU Privileges",
              if (report.hasRootAccess) "Available (${report.rootMethod})" else "Standard App Sandbox (No Root)",
              color = if (report.hasRootAccess) SecondaryEmerald else TerminalDimText
            )
            ReportRow("AVF Subsystem", report.avfDetails)
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Diagnostic Action Triggers
        Text(
          text = "QUICK REAL SYSTEM DIAGNOSTICS",
          color = TerminalYellow,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = TerminalFontFamily
        )
        Spacer(modifier = Modifier.height(6.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          DiagnosticButton("uname -a") {
            onSelectMode(EngineMode.REAL_NATIVE_HOST)
            onRunDiagnosticCommand("uname -a")
            onDismiss()
          }
          DiagnosticButton("cat /proc/cpuinfo") {
            onSelectMode(EngineMode.REAL_NATIVE_HOST)
            onRunDiagnosticCommand("cat /proc/cpuinfo")
            onDismiss()
          }
          DiagnosticButton("cat /proc/meminfo") {
            onSelectMode(EngineMode.REAL_NATIVE_HOST)
            onRunDiagnosticCommand("cat /proc/meminfo")
            onDismiss()
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          DiagnosticButton("df -h") {
            onSelectMode(EngineMode.REAL_NATIVE_HOST)
            onRunDiagnosticCommand("df -h")
            onDismiss()
          }
          DiagnosticButton("whoami && id") {
            onSelectMode(EngineMode.REAL_NATIVE_HOST)
            onRunDiagnosticCommand("whoami && id")
            onDismiss()
          }
          DiagnosticButton("ls -la /system/bin") {
            onSelectMode(EngineMode.REAL_NATIVE_HOST)
            onRunDiagnosticCommand("ls -la /system/bin")
            onDismiss()
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Close Button
        Button(
          onClick = onDismiss,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("btn_close_virtualization_dialog"),
          colors = ButtonDefaults.buttonColors(
            containerColor = PrimaryCyan,
            contentColor = TerminalBlack
          ),
          shape = RoundedCornerShape(8.dp)
        ) {
          Text("Done", fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@Composable
private fun ReportRow(label: String, value: String, color: androidx.compose.ui.graphics.Color = TerminalWhite) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 3.dp),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(
      text = label,
      color = TerminalDimText,
      fontSize = 11.sp,
      fontFamily = TerminalFontFamily,
      modifier = Modifier.weight(0.42f)
    )
    Text(
      text = value,
      color = color,
      fontSize = 11.sp,
      fontFamily = TerminalFontFamily,
      modifier = Modifier.weight(0.58f),
      textAlign = androidx.compose.ui.text.style.TextAlign.End
    )
  }
}

@Composable
private fun DiagnosticButton(cmd: String, onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(6.dp))
      .background(CyberSurfaceVariant)
      .border(1.dp, CyberBorder, RoundedCornerShape(6.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 8.dp, vertical = 6.dp)
  ) {
    Text(
      text = "$ $cmd",
      color = SecondaryEmerald,
      fontSize = 11.sp,
      fontFamily = TerminalFontFamily
    )
  }
}
