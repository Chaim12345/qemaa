package com.example.data.repository

import com.example.data.local.VmDao
import com.example.data.model.DistroCatalog
import com.example.data.model.VirtualMachineEntity
import com.example.data.model.VmSnapshotEntity
import com.example.data.model.VmStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class VmRepository(private val vmDao: VmDao) {
  val allVms: Flow<List<VirtualMachineEntity>> = vmDao.getAllVms()

  fun getVmByIdFlow(id: Long): Flow<VirtualMachineEntity?> = vmDao.getVmByIdFlow(id)

  suspend fun getVmById(id: Long): VirtualMachineEntity? = vmDao.getVmById(id)

  suspend fun insertVm(vm: VirtualMachineEntity): Long = vmDao.insertVm(vm)

  suspend fun updateVm(vm: VirtualMachineEntity) = vmDao.updateVm(vm)

  suspend fun deleteVm(id: Long) = vmDao.deleteVm(id)

  suspend fun setVmStatus(id: Long, status: VmStatus) = vmDao.updateVmStatus(id, status.name)

  suspend fun incrementUptime(id: Long, seconds: Long) = vmDao.incrementUptime(id, seconds)

  fun getSnapshots(vmId: Long): Flow<List<VmSnapshotEntity>> = vmDao.getSnapshotsForVm(vmId)

  suspend fun createSnapshot(snapshot: VmSnapshotEntity): Long = vmDao.insertSnapshot(snapshot)

  suspend fun deleteSnapshot(snapshotId: Long) = vmDao.deleteSnapshot(snapshotId)

  suspend fun seedInitialDistroIfEmpty() {
    val existing = allVms.first()
    if (existing.isEmpty()) {
      val alpine = DistroCatalog.DISTROS.first { it.id == "alpine" }
      val defaultVm = VirtualMachineEntity(
        name = "Alpine Linux 3.20",
        distroId = alpine.id,
        arch = "x86_64",
        cpuCores = 2,
        cpuModel = "qemu64",
        ramMb = 512,
        diskSizeGb = 4.0,
        diskFormat = "qcow2",
        diskPath = "/data/vms/alpine-3.20.qcow2",
        isoPath = "/iso/alpine-virt-3.20.0-x86_64.iso",
        displayMode = "SERIAL_CONSOLE",
        networkMode = "USER_SLIRP",
        portForwardsJson = "[{\"host\":2222,\"guest\":22,\"name\":\"SSH\"},{\"host\":8080,\"guest\":80,\"name\":\"Web HTTP\"}]",
        status = "STOPPED",
        customQemuArgs = "-accel tcg,tb-size=128",
        kernelParams = alpine.kernelArgs,
        defaultUser = "root",
        currentWorkDir = "/root",
        installedPackagesJson = "[\"busybox\",\"apk-tools\",\"openrc\",\"curl\",\"wget\"]",
        fileSystemStateJson = "{}",
        commandHistoryJson = "[\"uname -a\",\"df -h\",\"free -m\",\"neofetch\"]",
        createdAt = System.currentTimeMillis()
      )
      vmDao.insertVm(defaultVm)
    }
  }
}
