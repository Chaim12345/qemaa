package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.model.DistroCatalog
import com.example.data.model.DistroTemplate
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
fun DistroHubScreen(
  onInstallDistro: (DistroTemplate, String, Int, Int, Double) -> Unit
) {
  val distros = DistroCatalog.DISTROS

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(CyberBackground),
    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item {
      // Banner Header
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.FlashOn,
              contentDescription = null,
              tint = PrimaryCyan,
              modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "1-Click Linux Distro Hub",
                color = TerminalWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
              )
              Text(
                text = "Instant QEMU image allocation, rootfs initialization & auto-boot",
                color = SecondaryEmerald,
                fontSize = 12.sp
              )
            }
          }
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "Choose from security-hardened, ultra-lightweight, or full development Linux environments with zero manual setup.",
            color = TerminalDimText,
            fontSize = 13.sp
          )
        }
      }
    }

    items(distros) { distro ->
      DistroInstallCard(
        distro = distro,
        onInstall = { arch, cpus, ram, disk ->
          onInstallDistro(distro, arch, cpus, ram, disk)
        }
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DistroInstallCard(
  distro: DistroTemplate,
  onInstall: (arch: String, cpus: Int, ram: Int, disk: Double) -> Unit
) {
  var selectedArch by remember { mutableStateOf(distro.defaultArch) }
  var cpuCores by remember { mutableIntStateOf(distro.defaultCpuCores) }
  var ramMb by remember { mutableIntStateOf(distro.defaultRamMb) }
  var diskGb by remember { mutableDoubleStateOf(distro.defaultDiskGb) }
  var showCustomizer by remember { mutableStateOf(false) }

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = CyberSurface),
    border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
    shape = RoundedCornerShape(12.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      // Header: Name, Tag, Version
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(distro.colorHex))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = distro.name,
              color = TerminalWhite,
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp
            )
          }
          Text(
            text = "${distro.version} • ${distro.category}",
            color = TerminalDimText,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 18.dp)
          )
        }

        // Tag Badge
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SecondaryEmerald.copy(alpha = 0.15f))
            .border(1.dp, SecondaryEmerald.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Text(
            text = distro.tag,
            color = SecondaryEmerald,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = TerminalFontFamily
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = distro.description,
        color = TerminalWhite.copy(alpha = 0.85f),
        fontSize = 13.sp,
        lineHeight = 18.sp
      )

      Spacer(modifier = Modifier.height(12.dp))

      // Architecture Selector
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Arch:",
          color = TerminalDimText,
          fontSize = 12.sp,
          fontFamily = TerminalFontFamily,
          modifier = Modifier.width(42.dp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          distro.supportedArchs.forEach { arch ->
            val isSelected = arch == selectedArch
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (isSelected) PrimaryCyan else CyberSurfaceVariant)
                .border(1.dp, if (isSelected) PrimaryCyan else CyberBorder, RoundedCornerShape(4.dp))
                .clickable { selectedArch = arch }
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text(
                text = arch,
                color = if (isSelected) TerminalBlack else TerminalWhite,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontFamily = TerminalFontFamily
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Hardware Specs Summary Strip
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(CyberSurfaceVariant, RoundedCornerShape(6.dp))
          .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        SpecItem(icon = Icons.Default.Memory, text = "$cpuCores vCPU")
        SpecItem(icon = Icons.Default.Tune, text = "${ramMb}MB RAM")
        SpecItem(icon = Icons.Default.Storage, text = "${diskGb.toInt()}GB Disk")
        SpecItem(icon = Icons.Default.Download, text = "${distro.downloadSizeMb}MB Net")
      }

      // Preinstalled Tools Chips
      Spacer(modifier = Modifier.height(8.dp))
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        distro.preinstalledTools.forEach { tool ->
          Box(
            modifier = Modifier
              .background(TerminalBlack, RoundedCornerShape(3.dp))
              .border(1.dp, CyberBorder, RoundedCornerShape(3.dp))
              .padding(horizontal = 6.dp, vertical = 2.dp)
          ) {
            Text(
              text = tool,
              color = TerminalCyan,
              fontSize = 10.sp,
              fontFamily = TerminalFontFamily
            )
          }
        }
      }

      // Collapsible Customizer (CPU/RAM/Disk Sliders)
      AnimatedVisibility(visible = showCustomizer) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(TerminalBlack, RoundedCornerShape(8.dp))
            .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
            .padding(12.dp)
        ) {
          Text(
            text = "Hardware Customizer",
            color = PrimaryCyan,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
          )
          Spacer(modifier = Modifier.height(8.dp))

          // CPU Slider
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("vCPUs: $cpuCores", color = TerminalWhite, fontSize = 12.sp, fontFamily = TerminalFontFamily)
          }
          Slider(
            value = cpuCores.toFloat(),
            onValueChange = { cpuCores = it.toInt() },
            valueRange = 1f..8f,
            steps = 6,
            colors = SliderDefaults.colors(
              thumbColor = PrimaryCyan,
              activeTrackColor = PrimaryCyan,
              inactiveTrackColor = CyberBorder
            )
          )

          // RAM Slider
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("RAM: ${ramMb}MB", color = TerminalWhite, fontSize = 12.sp, fontFamily = TerminalFontFamily)
          }
          Slider(
            value = ramMb.toFloat(),
            onValueChange = { ramMb = it.toInt() },
            valueRange = 128f..4096f,
            colors = SliderDefaults.colors(
              thumbColor = SecondaryEmerald,
              activeTrackColor = SecondaryEmerald,
              inactiveTrackColor = CyberBorder
            )
          )

          // Disk Slider
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Disk Size: ${diskGb.toInt()}GB", color = TerminalWhite, fontSize = 12.sp, fontFamily = TerminalFontFamily)
          }
          Slider(
            value = diskGb.toFloat(),
            onValueChange = { diskGb = it.toDouble() },
            valueRange = 1f..32f,
            steps = 30,
            colors = SliderDefaults.colors(
              thumbColor = TerminalYellow,
              activeTrackColor = TerminalYellow,
              inactiveTrackColor = CyberBorder
            )
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Action Row: Customize Specs & 1-Click Install Button
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = { showCustomizer = !showCustomizer },
          modifier = Modifier
            .background(CyberSurfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, CyberBorder, RoundedCornerShape(8.dp))
            .size(42.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Tune,
            contentDescription = "Customize",
            tint = if (showCustomizer) PrimaryCyan else TerminalDimText
          )
        }

        Button(
          onClick = { onInstall(selectedArch, cpuCores, ramMb, diskGb) },
          modifier = Modifier.weight(1f),
          colors = ButtonDefaults.buttonColors(
            containerColor = SecondaryEmerald,
            contentColor = TerminalBlack
          ),
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(vertical = 10.dp)
        ) {
          Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "1-Click Install & Boot",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
          )
        }
      }
    }
  }
}

@Composable
private fun SpecItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = TerminalCyan,
      modifier = Modifier.size(14.dp)
    )
    Spacer(modifier = Modifier.width(4.dp))
    Text(
      text = text,
      color = TerminalWhite,
      fontSize = 11.sp,
      fontFamily = TerminalFontFamily
    )
  }
}
