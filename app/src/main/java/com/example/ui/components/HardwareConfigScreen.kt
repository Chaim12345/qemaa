package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PortForward
import com.example.data.model.VirtualMachineEntity
import com.example.engine.QemuCliBuilder
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
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun HardwareConfigScreen(
  vm: VirtualMachineEntity?,
  onSaveConfig: (VirtualMachineEntity) -> Unit
) {
  if (vm == null) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(CyberBackground),
      contentAlignment = Alignment.Center
    ) {
      Text("No Virtual Machine selected for configuration.", color = TerminalDimText)
    }
    return
  }

  val context = LocalContext.current

  var vmName by remember(vm) { mutableStateOf(vm.name) }
  var arch by remember(vm) { mutableStateOf(vm.arch) }
  var cpuCores by remember(vm) { mutableIntStateOf(vm.cpuCores) }
  var cpuModel by remember(vm) { mutableStateOf(vm.cpuModel) }
  var ramMb by remember(vm) { mutableIntStateOf(vm.ramMb) }
  var diskSizeGb by remember(vm) { mutableDoubleStateOf(vm.diskSizeGb) }
  var diskFormat by remember(vm) { mutableStateOf(vm.diskFormat) }
  var diskPath by remember(vm) { mutableStateOf(vm.diskPath) }
  var isoPath by remember(vm) { mutableStateOf(vm.isoPath) }
  var displayMode by remember(vm) { mutableStateOf(vm.displayMode) }
  var networkMode by remember(vm) { mutableStateOf(vm.networkMode) }
  var kernelParams by remember(vm) { mutableStateOf(vm.kernelParams) }
  var customQemuArgs by remember(vm) { mutableStateOf(vm.customQemuArgs) }
  var defaultUser by remember(vm) { mutableStateOf(vm.defaultUser) }

  // Parse Port Forwards
  var portForwards by remember(vm) {
    mutableStateOf(parsePortForwards(vm.portForwardsJson))
  }

  var newHostPort by remember { mutableStateOf("") }
  var newGuestPort by remember { mutableStateOf("") }
  var newPortName by remember { mutableStateOf("") }

  val currentPreviewVm = vm.copy(
    name = vmName,
    arch = arch,
    cpuCores = cpuCores,
    cpuModel = cpuModel,
    ramMb = ramMb,
    diskSizeGb = diskSizeGb,
    diskFormat = diskFormat,
    diskPath = diskPath,
    isoPath = isoPath,
    displayMode = displayMode,
    networkMode = networkMode,
    portForwardsJson = serializePortForwards(portForwards),
    kernelParams = kernelParams,
    customQemuArgs = customQemuArgs,
    defaultUser = defaultUser
  )

  val generatedCli = remember(currentPreviewVm) {
    QemuCliBuilder.generateQemuCli(currentPreviewVm)
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(CyberBackground),
    contentPadding = PaddingValues(12.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Header & Save Button
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "QEMU Hardware Profile",
            color = TerminalWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
          )
          Text(
            text = "Fine-tune system architecture & devices",
            color = TerminalDimText,
            fontSize = 12.sp
          )
        }

        Button(
          onClick = {
            onSaveConfig(currentPreviewVm)
            Toast.makeText(context, "Hardware configuration saved!", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = SecondaryEmerald,
            contentColor = TerminalBlack
          ),
          shape = RoundedCornerShape(8.dp)
        ) {
          Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Save Profile", fontWeight = FontWeight.Bold)
        }
      }
    }

    // General Identity Card
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
        shape = RoundedCornerShape(12.dp)
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text(
            text = "Machine Identity",
            color = PrimaryCyan,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
          )
          Spacer(modifier = Modifier.height(10.dp))

          OutlinedTextField(
            value = vmName,
            onValueChange = { vmName = it },
            label = { Text("VM Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = PrimaryCyan,
              unfocusedBorderColor = CyberBorder
            )
          )

          Spacer(modifier = Modifier.height(10.dp))

          // Architecture Selector
          Text(text = "Target Architecture (QEMU Binary):", color = TerminalWhite, fontSize = 12.sp)
          Spacer(modifier = Modifier.height(6.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("x86_64", "aarch64", "riscv64", "i386").forEach { a ->
              val isSelected = a == arch
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(6.dp))
                  .background(if (isSelected) PrimaryCyan else CyberSurfaceVariant)
                  .border(1.dp, if (isSelected) PrimaryCyan else CyberBorder, RoundedCornerShape(6.dp))
                  .clickable {
                    arch = a
                    cpuModel = when (a) {
                      "aarch64" -> "cortex-a72"
                      "riscv64" -> "rv64"
                      else -> "qemu64"
                    }
                  }
                  .padding(horizontal = 10.dp, vertical = 6.dp)
              ) {
                Text(
                  text = a,
                  color = if (isSelected) TerminalBlack else TerminalWhite,
                  fontSize = 12.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                  fontFamily = TerminalFontFamily
                )
              }
            }
          }
        }
      }
    }

    // CPU & Memory Card
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
        shape = RoundedCornerShape(12.dp)
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text(
            text = "CPU & Virtual RAM",
            color = PrimaryCyan,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
          )
          Spacer(modifier = Modifier.height(10.dp))

          // vCPU Count
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(text = "vCPUs (-smp):", color = TerminalWhite, fontSize = 13.sp)
            Text(text = "$cpuCores Cores", color = SecondaryEmerald, fontWeight = FontWeight.Bold, fontFamily = TerminalFontFamily)
          }
          Slider(
            value = cpuCores.toFloat(),
            onValueChange = { cpuCores = it.toInt() },
            valueRange = 1f..8f,
            steps = 6,
            colors = SliderDefaults.colors(
              thumbColor = SecondaryEmerald,
              activeTrackColor = SecondaryEmerald,
              inactiveTrackColor = CyberBorder
            )
          )

          // RAM Slider
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(text = "RAM (-m):", color = TerminalWhite, fontSize = 13.sp)
            Text(text = "${ramMb}MB", color = PrimaryCyan, fontWeight = FontWeight.Bold, fontFamily = TerminalFontFamily)
          }
          Slider(
            value = ramMb.toFloat(),
            onValueChange = { ramMb = it.toInt() },
            valueRange = 128f..4096f,
            colors = SliderDefaults.colors(
              thumbColor = PrimaryCyan,
              activeTrackColor = PrimaryCyan,
              inactiveTrackColor = CyberBorder
            )
          )

          // CPU Model input
          OutlinedTextField(
            value = cpuModel,
            onValueChange = { cpuModel = it },
            label = { Text("CPU Model (-cpu)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = PrimaryCyan,
              unfocusedBorderColor = CyberBorder
            )
          )
        }
      }
    }

    // Storage & Drive Devices
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
        shape = RoundedCornerShape(12.dp)
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text(
            text = "Storage & Block Devices (VirtIO)",
            color = PrimaryCyan,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
          )
          Spacer(modifier = Modifier.height(10.dp))

          OutlinedTextField(
            value = diskPath,
            onValueChange = { diskPath = it },
            label = { Text("Primary Virtual Disk Image Path") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = PrimaryCyan,
              unfocusedBorderColor = CyberBorder
            )
          )

          Spacer(modifier = Modifier.height(8.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(text = "Virtual Disk Capacity:", color = TerminalWhite, fontSize = 13.sp)
            Text(text = "${diskSizeGb.toInt()} GB", color = TerminalYellow, fontWeight = FontWeight.Bold, fontFamily = TerminalFontFamily)
          }
          Slider(
            value = diskSizeGb.toFloat(),
            onValueChange = { diskSizeGb = it.toDouble() },
            valueRange = 1f..64f,
            steps = 62,
            colors = SliderDefaults.colors(
              thumbColor = TerminalYellow,
              activeTrackColor = TerminalYellow,
              inactiveTrackColor = CyberBorder
            )
          )

          Spacer(modifier = Modifier.height(8.dp))

          OutlinedTextField(
            value = isoPath,
            onValueChange = { isoPath = it },
            label = { Text("ISO / CDROM Image (-cdrom)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = PrimaryCyan,
              unfocusedBorderColor = CyberBorder
            )
          )
        }
      }
    }

    // Port Forwarding Table (SLIRP hostfwd)
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
        shape = RoundedCornerShape(12.dp)
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text(
            text = "Network Port Forwarding (hostfwd=tcp::host-:guest)",
            color = PrimaryCyan,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
          )
          Spacer(modifier = Modifier.height(8.dp))

          portForwards.forEach { pf ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(CyberSurfaceVariant, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = "${pf.name} (TCP)",
                  color = TerminalWhite,
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp
                )
                Text(
                  text = "Host 127.0.0.1:${pf.host} ➔ Guest VM:${pf.guest}",
                  color = SecondaryEmerald,
                  fontSize = 11.sp,
                  fontFamily = TerminalFontFamily
                )
              }

              IconButton(
                onClick = {
                  portForwards = portForwards.filter { it.host != pf.host }
                },
                modifier = Modifier.size(32.dp)
              ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = TerminalRed)
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          // Add port forward row
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            OutlinedTextField(
              value = newHostPort,
              onValueChange = { newHostPort = it },
              label = { Text("Host") },
              modifier = Modifier.weight(1f),
              singleLine = true,
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryCyan,
                unfocusedBorderColor = CyberBorder
              )
            )
            OutlinedTextField(
              value = newGuestPort,
              onValueChange = { newGuestPort = it },
              label = { Text("Guest") },
              modifier = Modifier.weight(1f),
              singleLine = true,
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryCyan,
                unfocusedBorderColor = CyberBorder
              )
            )
            OutlinedTextField(
              value = newPortName,
              onValueChange = { newPortName = it },
              label = { Text("Name") },
              modifier = Modifier.weight(1.5f),
              singleLine = true,
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryCyan,
                unfocusedBorderColor = CyberBorder
              )
            )

            Button(
              onClick = {
                val hp = newHostPort.toIntOrNull()
                val gp = newGuestPort.toIntOrNull()
                if (hp != null && gp != null) {
                  portForwards = portForwards + PortForward(host = hp, guest = gp, name = newPortName.ifBlank { "Service" })
                  newHostPort = ""
                  newGuestPort = ""
                  newPortName = ""
                }
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryCyan,
                contentColor = TerminalBlack
              ),
              shape = RoundedCornerShape(8.dp),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
              Icon(imageVector = Icons.Default.Add, contentDescription = null)
            }
          }
        }
      }
    }

    // Advanced Flags & Kernel Args
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CyberSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorder),
        shape = RoundedCornerShape(12.dp)
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text(
            text = "Kernel Cmdline & Custom QEMU Args",
            color = PrimaryCyan,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
          )
          Spacer(modifier = Modifier.height(10.dp))

          OutlinedTextField(
            value = kernelParams,
            onValueChange = { kernelParams = it },
            label = { Text("Kernel Parameters (-append)") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = PrimaryCyan,
              unfocusedBorderColor = CyberBorder
            )
          )

          Spacer(modifier = Modifier.height(8.dp))

          OutlinedTextField(
            value = customQemuArgs,
            onValueChange = { customQemuArgs = it },
            label = { Text("Custom QEMU CLI Flags (e.g. -accel tcg)") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = PrimaryCyan,
              unfocusedBorderColor = CyberBorder
            )
          )
        }
      }
    }

    // Live Generated QEMU CLI Box with Copy Button
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TerminalBlack),
        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Code,
                contentDescription = null,
                tint = PrimaryCyan,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Live QEMU CLI Command",
                color = PrimaryCyan,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
              )
            }

            IconButton(
              onClick = {
                val clipManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipManager.setPrimaryClip(ClipData.newPlainText("QEMU Command", generatedCli))
                Toast.makeText(context, "QEMU CLI copied to clipboard!", Toast.LENGTH_SHORT).show()
              },
              modifier = Modifier.size(32.dp)
            ) {
              Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy CLI",
                tint = SecondaryEmerald,
                modifier = Modifier.size(18.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          Text(
            text = generatedCli,
            color = TerminalGreen,
            fontFamily = TerminalFontFamily,
            fontSize = 11.sp,
            lineHeight = 16.sp
          )
        }
      }
    }
  }
}

private fun parsePortForwards(json: String): List<PortForward> {
  val list = mutableListOf<PortForward>()
  try {
    val arr = JSONArray(json)
    for (i in 0 until arr.length()) {
      val obj = arr.getJSONObject(i)
      list.add(
        PortForward(
          host = obj.getInt("host"),
          guest = obj.getInt("guest"),
          name = obj.optString("name", "Port")
        )
      )
    }
  } catch (e: Exception) {
    // fallback
  }
  return list
}

private fun serializePortForwards(list: List<PortForward>): String {
  val arr = JSONArray()
  list.forEach { pf ->
    val obj = JSONObject()
    obj.put("host", pf.host)
    obj.put("guest", pf.guest)
    obj.put("name", pf.name)
    arr.put(obj)
  }
  return arr.toString()
}
