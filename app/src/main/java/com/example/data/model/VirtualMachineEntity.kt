package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class VmStatus {
  STOPPED,
  BOOTING,
  RUNNING,
  PAUSED,
  INSTALLING,
  ERROR
}

enum class DisplayMode {
  SERIAL_CONSOLE,
  VNC_GRAPHICS,
  VIRTIOGPU,
  HEADLESS
}

enum class NetworkMode {
  USER_SLIRP,
  BRIDGE,
  NONE
}

@Entity(tableName = "virtual_machines")
data class VirtualMachineEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val distroId: String,
  val arch: String = "x86_64", // x86_64, aarch64, i386, riscv64
  val cpuCores: Int = 2,
  val cpuModel: String = "qemu64", // host, qemu64, max, cortex-a72
  val ramMb: Int = 1024,
  val diskSizeGb: Double = 4.0,
  val diskFormat: String = "qcow2", // qcow2, raw
  val diskPath: String = "/data/vms/disk.qcow2",
  val isoPath: String = "",
  val displayMode: String = "SERIAL_CONSOLE",
  val networkMode: String = "USER_SLIRP",
  val portForwardsJson: String = "[{\"host\":2222,\"guest\":22,\"name\":\"SSH\"},{\"host\":8080,\"guest\":80,\"name\":\"HTTP Web\"}]",
  val status: String = "STOPPED",
  val customQemuArgs: String = "",
  val kernelParams: String = "console=ttyS0 root=/dev/vda rw quiet",
  val defaultUser: String = "root",
  val currentWorkDir: String = "/root",
  val installedPackagesJson: String = "[]",
  val fileSystemStateJson: String = "{}",
  val commandHistoryJson: String = "[]",
  val createdAt: Long = System.currentTimeMillis(),
  val lastBooted: Long = 0L,
  val uptimeSeconds: Long = 0L
)

@Entity(tableName = "vm_snapshots")
data class VmSnapshotEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val vmId: Long,
  val name: String,
  val description: String = "",
  val timestamp: Long = System.currentTimeMillis(),
  val ramUsageMb: Int = 0,
  val stateData: String = ""
)

data class PortForward(
  val host: Int,
  val guest: Int,
  val name: String = ""
)

data class TelemetryMetrics(
  val cpuPercent: Float = 0f,
  val ramUsedMb: Int = 0,
  val ramTotalMb: Int = 1024,
  val diskReadKbps: Float = 0f,
  val diskWriteKbps: Float = 0f,
  val netRxKbps: Float = 0f,
  val netTxKbps: Float = 0f,
  val uptimeSeconds: Long = 0L
)
