package com.example.engine

import com.example.data.model.PortForward
import com.example.data.model.VirtualMachineEntity
import org.json.JSONArray

object QemuCliBuilder {

  fun parsePortForwards(json: String): List<PortForward> {
    return try {
      val array = JSONArray(json)
      val list = mutableListOf<PortForward>()
      for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        list.add(
          PortForward(
            host = obj.getInt("host"),
            guest = obj.getInt("guest"),
            name = obj.optString("name", "")
          )
        )
      }
      list
    } catch (e: Exception) {
      emptyList()
    }
  }

  fun generateQemuCli(vm: VirtualMachineEntity): String {
    val qemuBinary = when (vm.arch.lowercase()) {
      "aarch64", "arm64" -> "qemu-system-aarch64"
      "riscv64" -> "qemu-system-riscv64"
      "i386", "x86" -> "qemu-system-i386"
      else -> "qemu-system-x86_64"
    }

    val machineType = when (vm.arch.lowercase()) {
      "aarch64", "arm64" -> "virt,accel=tcg"
      "riscv64" -> "virt"
      "i386" -> "pc"
      else -> "q35,accel=tcg"
    }

    val sb = StringBuilder()
    sb.append(qemuBinary)
    sb.append(" -machine ").append(machineType)
    sb.append(" -cpu ").append(vm.cpuModel.ifBlank { "qemu64" })
    sb.append(" -smp ").append(vm.cpuCores)
    sb.append(" -m ").append(vm.ramMb).append("M")

    // Disk drive
    if (vm.diskPath.isNotBlank()) {
      sb.append(" -drive file=").append(vm.diskPath)
        .append(",if=virtio,format=").append(vm.diskFormat.ifBlank { "qcow2" })
    }

    // CD-ROM ISO
    if (vm.isoPath.isNotBlank()) {
      sb.append(" -cdrom ").append(vm.isoPath)
    }

    // Network & Port Forwarding
    val ports = parsePortForwards(vm.portForwardsJson)
    val hostFwd = if (ports.isNotEmpty()) {
      val fwdStr = ports.joinToString(",") { "hostfwd=tcp::${it.host}-:${it.guest}" }
      "-netdev user,id=net0,$fwdStr -device virtio-net-pci,netdev=net0"
    } else {
      "-netdev user,id=net0 -device virtio-net-pci,netdev=net0"
    }
    sb.append(" ").append(hostFwd)

    // Display & Graphics
    when (vm.displayMode) {
      "VNC_GRAPHICS" -> sb.append(" -vnc :1 -device virtio-vga")
      "VIRTIOGPU" -> sb.append(" -device virtio-gpu-pci -display default")
      "HEADLESS" -> sb.append(" -nographic")
      else -> sb.append(" -nographic -serial mon:stdio")
    }

    // Kernel cmdline
    if (vm.kernelParams.isNotBlank()) {
      sb.append(" -append \"").append(vm.kernelParams).append("\"")
    }

    // Custom arguments
    if (vm.customQemuArgs.isNotBlank()) {
      sb.append(" ").append(vm.customQemuArgs.trim())
    }

    return sb.toString()
  }
}
