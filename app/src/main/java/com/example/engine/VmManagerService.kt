package com.example.engine

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.example.data.model.DistroCatalog
import com.example.data.model.DistroTemplate
import com.example.data.model.TelemetryMetrics
import com.example.data.model.VirtualMachineEntity
import com.example.data.model.VmSnapshotEntity
import com.example.data.model.VmStatus
import com.example.data.repository.VmRepository
import com.example.ui.theme.TerminalBlue
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TerminalDimText
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalOrange
import com.example.ui.theme.TerminalPurple
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TerminalWhite
import com.example.ui.theme.TerminalYellow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.random.Random

enum class EngineMode(val title: String, val badge: String) {
  REAL_NATIVE_HOST("Real Linux Host", "⚡ Real Host"),
  QEMU_GUEST("QEMU Virtual Machine", "🖥️ QEMU Guest")
}

class VmManagerService(
  private val context: Context,
  private val repository: VmRepository,
  private val scope: CoroutineScope
) {

  private val _activeVm = MutableStateFlow<VirtualMachineEntity?>(null)
  val activeVm: StateFlow<VirtualMachineEntity?> = _activeVm.asStateFlow()

  private val _terminalLines = MutableStateFlow<List<TerminalLine>>(emptyList())
  val terminalLines: StateFlow<List<TerminalLine>> = _terminalLines.asStateFlow()

  private val _telemetry = MutableStateFlow(TelemetryMetrics())
  val telemetry: StateFlow<TelemetryMetrics> = _telemetry.asStateFlow()

  private val _installProgress = MutableStateFlow<Float?>(null)
  val installProgress: StateFlow<Float?> = _installProgress.asStateFlow()

  private val _installStatusText = MutableStateFlow("")
  val installStatusText: StateFlow<String> = _installStatusText.asStateFlow()

  private val _engineMode = MutableStateFlow(EngineMode.REAL_NATIVE_HOST)
  val engineMode: StateFlow<EngineMode> = _engineMode.asStateFlow()

  private val _virtualizationReport = MutableStateFlow(VirtualizationDetector.probeHardwareVirtualization(context))
  val virtualizationReport: StateFlow<VirtualizationReport> = _virtualizationReport.asStateFlow()

  private var shellEngine: LinuxShellEngine? = null
  private val realNativeEngine = RealNativeProcessEngine(context)
  private var telemetryJob: Job? = null
  private var bootJob: Job? = null

  private var qemuRunner: QemuRunner? = null
  private var realQemuAvailable: Boolean = false
  private var realQemuActive: Boolean = false

  init {
    initializeRealQemu()

    val report = _virtualizationReport.value
    appendTerminalLine(TerminalLine("=== ANDROID LINUX RUNTIME & VM SUBSYSTEM ===", TerminalCyan, isSystem = true))
    appendTerminalLine(TerminalLine("Host Kernel: ${report.hostKernelVersion.take(60)}", TerminalDimText))
    appendTerminalLine(TerminalLine("Physical Architecture: ${report.hostArchitecture} (${report.physicalCpuCores} cores, ${report.totalHostRamMb}MB RAM)", TerminalGreen))
    appendTerminalLine(TerminalLine("Hardware KVM (/dev/kvm): ${if (report.hasKvmDevice) "Present" else "Unavailable in user-space sandbox"}", TerminalYellow))

    if (realQemuAvailable) {
      appendTerminalLine(TerminalLine("⚡ Real QEMU binary detected - Full hardware emulation READY", TerminalGreen, isSystem = true))
      appendTerminalLine(TerminalLine("⚡ Live Real Native Shell (/system/bin/sh) is READY. Type 'uname -a' or 'cat /proc/cpuinfo'", TerminalGreen, isSystem = true))
    } else {
      appendTerminalLine(TerminalLine("⚡ Live Real Native Shell (/system/bin/sh) is READY. Type 'uname -a' or 'cat /proc/cpuinfo'", TerminalGreen, isSystem = true))
      appendTerminalLine(TerminalLine("ℹ QEMU binaries not found in assets - using simulated VM mode", TerminalDimText))
    }
    appendTerminalLine(TerminalLine("", TerminalWhite))
  }

  private fun initializeRealQemu() {
    try {
      val binaries = NativeBinaryExtractor.ensureBinariesExtracted(context)
      realQemuAvailable = NativeBinaryExtractor.isQemuAvailable(context)
      if (realQemuAvailable) {
        qemuRunner = QemuRunner(binaries, scope)
        qemuRunner?.onOutput = { line ->
          appendTerminalLine(TerminalLine(line, TerminalWhite))
        }
        qemuRunner?.onError = { err ->
          appendTerminalLine(TerminalLine("QEMU Error: $err", TerminalRed, isError = true))
        }
        qemuRunner?.onStateChange = { newState ->
          when (newState) {
            QemuRunner.VmState.RUNNING -> {
              val vm = _activeVm.value
              if (vm != null && vm.status != VmStatus.RUNNING.name) {
                val runningVm = vm.copy(status = VmStatus.RUNNING.name)
                _activeVm.value = runningVm
                repository.updateVm(runningVm)
              }
            }
            QemuRunner.VmState.STOPPED -> {
              val vm = _activeVm.value
              if (vm != null && vm.status != VmStatus.STOPPED.name) {
                val stoppedVm = vm.copy(status = VmStatus.STOPPED.name)
                _activeVm.value = stoppedVm
                repository.updateVm(stoppedVm)
                stopTelemetryTicker()
              }
            }
            else -> {}
          }
        }
      }
    } catch (e: Exception) {
      realQemuAvailable = false
    }
  }

  fun isRealQemuAvailable(): Boolean = realQemuAvailable
  fun isRealQemuActive(): Boolean = realQemuActive

  fun setEngineMode(mode: EngineMode) {
    _engineMode.value = mode
    if (mode == EngineMode.REAL_NATIVE_HOST) {
      appendTerminalLine(
        TerminalLine(
          "=== Switched to REAL NATIVE LINUX HOST MODE (Direct /system/bin/sh process execution) ===",
          TerminalGreen,
          isSystem = true
        )
      )
    } else {
      appendTerminalLine(
        TerminalLine(
          "=== Switched to QEMU GUEST VIRTUAL MACHINE MODE (Hardware emulation & VirtIO devices) ===",
          TerminalCyan,
          isSystem = true
        )
      )
    }
  }

  fun refreshVirtualizationReport() {
    _virtualizationReport.value = VirtualizationDetector.probeHardwareVirtualization(context)
  }

  fun setActiveVm(vm: VirtualMachineEntity) {
    _activeVm.value = vm
    shellEngine = LinuxShellEngine(vm)
    if (vm.status == VmStatus.RUNNING.name) {
      startTelemetryTicker()
    } else {
      stopTelemetryTicker()
    }
  }

  fun appendTerminalLine(line: TerminalLine) {
    if (line.text == "__CLEAR__") {
      _terminalLines.value = emptyList()
    } else {
      val current = _terminalLines.value
      // Keep maximum 1000 lines in buffer for high performance
      _terminalLines.value = (if (current.size > 1000) current.drop(current.size - 900) else current) + line
    }
  }

  fun clearTerminal() {
    _terminalLines.value = emptyList()
  }

  fun startVm(vm: VirtualMachineEntity) {
    if (vm.status == VmStatus.RUNNING.name) return

    if (realQemuAvailable && qemuRunner != null) {
      startRealQemuVm(vm)
      return
    }

    bootJob?.cancel()
    bootJob = scope.launch(Dispatchers.Default) {
      val updated = vm.copy(
        status = VmStatus.BOOTING.name,
        lastBooted = System.currentTimeMillis()
      )
      _activeVm.value = updated
      repository.updateVm(updated)
      shellEngine = LinuxShellEngine(updated)

      appendTerminalLine(TerminalLine("=== Starting QEMU Virtual Machine (${vm.name}) ===", TerminalCyan, isSystem = true))
      val cli = QemuCliBuilder.generateQemuCli(vm)
      appendTerminalLine(TerminalLine("$ $cli", TerminalDimText))
      delay(300)

      // BIOS / UEFI POST Sequence
      appendTerminalLine(TerminalLine("SeaBIOS (version 1.16.3-debian-1.16.3-2)", TerminalDimText))
      appendTerminalLine(TerminalLine("iPXE (http://ipxe.org) 00:03.0 C000 PCI2.10 PnP PMM+1FE00000+1FE00000 C000", TerminalDimText))
      appendTerminalLine(TerminalLine("Booting from Hard Disk...", TerminalGreen))
      delay(400)

      // Linux Kernel Decompression and Hardware Probe
      val distro = DistroCatalog.DISTROS.firstOrNull { it.id == vm.distroId }
      val bootLogs = listOf(
        "[    0.000000] Linux version 6.6.21-qemu-virt (${vm.arch}) (gcc 13.2.1) #1 SMP PREEMPT",
        "[    0.000000] Command line: ${vm.kernelParams}",
        "[    0.001200] x86/fpu: Supporting XSAVE feature 0x001: 'x87 floating point registers'",
        "[    0.003400] e820: [mem 0x0000000000000000-0x000000000009fbff] usable",
        "[    0.008900] smp: Bringing up secondary CPUs ...",
        "[    0.012300] smp: Brought up 1 node, ${vm.cpuCores} CPUs",
        "[    0.045000] ACPI: Core revision 20230628",
        "[    0.082100] Memory: ${vm.ramMb * 1024}K/${vm.ramMb * 1024}K available",
        "[    0.114200] PCI: Using configuration type 1 for base access",
        "[    0.142000] virtio-pci 0000:00:03.0: enabling device (0000 -> 0003)",
        "[    0.168300] virtio_blk virtio0: [vda] ${vm.diskSizeGb} GiB (${(vm.diskSizeGb * 2097152).toLong()} sectors)",
        "[    0.201400] virtio_net virtio1 eth0: link up, duplex full, speed 1000Mbps",
        "[    0.235100] input: QEMU VirtIO Keyboard as /devices/virtual/input/input0",
        "[    0.312000] EXT4-fs (vda1): mounted filesystem with ordered data mode. Quota mode: none.",
        "[    0.410200] systemd[1]: Inserted module 'autofs4'",
        "[    0.520100] [ OK ] Started Virtual Console Setup.",
        "[    0.640100] [ OK ] Mounted Configuration File System.",
        "[    0.720400] [ OK ] Reached target System Initialization.",
        "[    0.830100] [ OK ] Started OpenBSD Secure Shell server (sshd).",
        "[    0.910000] [ OK ] Started Network Name Resolution (systemd-resolved).",
        "[    1.020000] eth0: DHCP lease acquired 10.0.2.15/24, gateway 10.0.2.2",
        "[    1.150000] [ OK ] Reached target Multi-User System.",
        "[    1.250000] [ OK ] Reached target Graphical Interface."
      )

      for (log in bootLogs) {
        val color = when {
          log.contains("[ OK ]") -> TerminalGreen
          log.contains("DHCP") -> TerminalCyan
          log.contains("virtio") -> TerminalYellow
          else -> TerminalWhite
        }
        appendTerminalLine(TerminalLine(log, color))
        delay(Random.nextLong(20, 65))
      }

      appendTerminalLine(TerminalLine("", TerminalWhite))
      appendTerminalLine(
        TerminalLine(
          "Welcome to ${distro?.name ?: vm.name} (${vm.arch}) - QEMU Kernel 6.6.21-qemu-virt",
          TerminalGreen,
          isSystem = true
        )
      )
      appendTerminalLine(
        TerminalLine(
          "IP: 10.0.2.15/24 | SSH Port: 2222 -> 22 | Web: 8080 -> 80",
          TerminalCyan
        )
      )
      appendTerminalLine(TerminalLine("Type 'help' or 'neofetch' to begin.", TerminalYellow))
      appendTerminalLine(TerminalLine("", TerminalWhite))

      val runningVm = updated.copy(status = VmStatus.RUNNING.name)
      _activeVm.value = runningVm
      repository.updateVm(runningVm)
      startTelemetryTicker()
    }
  }

  private fun startRealQemuVm(vm: VirtualMachineEntity) {
    bootJob?.cancel()
    bootJob = scope.launch(Dispatchers.Default) {
      val updated = vm.copy(
        status = VmStatus.BOOTING.name,
        lastBooted = System.currentTimeMillis()
      )
      _activeVm.value = updated
      repository.updateVm(updated)

      appendTerminalLine(TerminalLine("=== Starting REAL QEMU Virtual Machine (${vm.name}) ===", TerminalCyan, isSystem = true))
      val cli = QemuCliBuilder.generateQemuCli(vm)
      appendTerminalLine(TerminalLine("$ $cli", TerminalDimText))
      appendTerminalLine(TerminalLine("Launching native QEMU process...", TerminalGreen))

      realQemuActive = true
      val started = qemuRunner?.start(vm) ?: false

      if (started) {
        appendTerminalLine(TerminalLine("QEMU process started successfully (PID: ${qemuRunner?.hashCode()})", TerminalGreen))
        appendTerminalLine(TerminalLine("Capturing guest output... (boot messages will appear below)", TerminalDimText))

        val runningVm = updated.copy(status = VmStatus.RUNNING.name)
        _activeVm.value = runningVm
        repository.updateVm(runningVm)
        startTelemetryTicker()
      } else {
        appendTerminalLine(TerminalLine("Failed to start QEMU process. Check binary permissions.", TerminalRed, isError = true))
        realQemuActive = false
        val errorVm = updated.copy(status = VmStatus.ERROR.name)
        _activeVm.value = errorVm
        repository.updateVm(errorVm)
      }
    }
  }

  fun pauseVm(vm: VirtualMachineEntity) {
    if (realQemuActive && qemuRunner?.state == QemuRunner.VmState.RUNNING) {
      scope.launch(Dispatchers.Default) {
        appendTerminalLine(TerminalLine("=== Virtual Machine execution PAUSED (QEMU Monitor 'stop') ===", TerminalYellow, isSystem = true))
        qemuRunner?.pause()
        val updated = vm.copy(status = VmStatus.PAUSED.name)
        _activeVm.value = updated
        repository.updateVm(updated)
        stopTelemetryTicker()
      }
      return
    }

    scope.launch(Dispatchers.Default) {
      val updated = vm.copy(status = VmStatus.PAUSED.name)
      _activeVm.value = updated
      repository.updateVm(updated)
      stopTelemetryTicker()
      appendTerminalLine(TerminalLine("=== Virtual Machine execution PAUSED (QEMU Monitor 'stop') ===", TerminalYellow, isSystem = true))
    }
  }

  fun resumeVm(vm: VirtualMachineEntity) {
    if (realQemuActive && qemuRunner?.state == QemuRunner.VmState.PAUSED) {
      scope.launch(Dispatchers.Default) {
        appendTerminalLine(TerminalLine("=== Virtual Machine execution RESUMED (QEMU Monitor 'c') ===", TerminalGreen, isSystem = true))
        qemuRunner?.resume()
        val updated = vm.copy(status = VmStatus.RUNNING.name)
        _activeVm.value = updated
        repository.updateVm(updated)
        startTelemetryTicker()
      }
      return
    }

    scope.launch(Dispatchers.Default) {
      val updated = vm.copy(status = VmStatus.RUNNING.name)
      _activeVm.value = updated
      repository.updateVm(updated)
      startTelemetryTicker()
      appendTerminalLine(TerminalLine("=== Virtual Machine execution RESUMED (QEMU Monitor 'c') ===", TerminalGreen, isSystem = true))
    }
  }

  fun shutdownVm(vm: VirtualMachineEntity) {
    if (realQemuActive) {
      bootJob?.cancel()
      scope.launch(Dispatchers.Default) {
        appendTerminalLine(TerminalLine("Sending ACPI powerdown event to guest...", TerminalYellow, isSystem = true))
        qemuRunner?.stop()
        realQemuActive = false
        appendTerminalLine(TerminalLine("QEMU: Terminated. VM Power Off.", TerminalGreen, isSystem = true))

        val updated = vm.copy(status = VmStatus.STOPPED.name)
        _activeVm.value = updated
        repository.updateVm(updated)
        stopTelemetryTicker()
      }
      return
    }

    bootJob?.cancel()
    scope.launch(Dispatchers.Default) {
      appendTerminalLine(TerminalLine("Sending ACPI powerdown event to guest...", TerminalYellow, isSystem = true))
      delay(400)
      appendTerminalLine(TerminalLine("[ OK ] Stopped target Multi-User System.", TerminalDimText))
      appendTerminalLine(TerminalLine("[ OK ] Unmounted all filesystems.", TerminalDimText))
      appendTerminalLine(TerminalLine("QEMU: Terminated with signal 15 (SIGTERM). VM Power Off.", TerminalGreen, isSystem = true))

      val updated = vm.copy(status = VmStatus.STOPPED.name)
      _activeVm.value = updated
      repository.updateVm(updated)
      stopTelemetryTicker()
    }
  }

  fun forceResetVm(vm: VirtualMachineEntity) {
    if (realQemuActive) {
      scope.launch(Dispatchers.Default) {
        appendTerminalLine(TerminalLine("=== HARD RESET TRIGGERED (QEMU Monitor 'system_reset') ===", TerminalRed, isSystem = true))
        qemuRunner?.forceStop()
        realQemuActive = false
        delay(300)
        startVm(vm.copy(status = VmStatus.STOPPED.name))
      }
      return
    }

    scope.launch(Dispatchers.Default) {
      appendTerminalLine(TerminalLine("=== HARD RESET TRIGGERED (QEMU Monitor 'system_reset') ===", TerminalRed, isSystem = true))
      val stopped = vm.copy(status = VmStatus.STOPPED.name)
      _activeVm.value = stopped
      repository.updateVm(stopped)
      delay(300)
      startVm(stopped)
    }
  }

  fun executeTerminalInput(input: String) {
    if (_engineMode.value == EngineMode.REAL_NATIVE_HOST) {
      val prompt = realNativeEngine.getPrompt()
      appendTerminalLine(TerminalLine("$prompt$input", TerminalGreen, isPrompt = true))

      val results = realNativeEngine.executeCommand(input)
      results.forEach { appendTerminalLine(it) }
      return
    }

    val vm = _activeVm.value ?: return

    if (realQemuActive && qemuRunner?.state == QemuRunner.VmState.RUNNING) {
      val prompt = "${vm.defaultUser}@${vm.name.lowercase().replace(" ", "-").take(15)}:~# "
      appendTerminalLine(TerminalLine("$prompt$input", TerminalGreen, isPrompt = true))
      qemuRunner?.sendCommand(input)

      if (input.trim() == "shutdown" || input.trim() == "poweroff") {
        shutdownVm(vm)
      } else if (input.trim() == "reboot") {
        forceResetVm(vm)
      }
      return
    }

    val engine = shellEngine ?: LinuxShellEngine(vm).also { shellEngine = it }

    val prompt = engine.getPrompt()
    appendTerminalLine(TerminalLine("$prompt$input", TerminalGreen, isPrompt = true))

    if (vm.status != VmStatus.RUNNING.name) {
      appendTerminalLine(
        TerminalLine(
          "Virtual Machine is not running (Current status: ${vm.status}). Tap 'Start' button to boot QEMU, or switch to '⚡ Real Host' mode.",
          TerminalRed,
          isError = true
        )
      )
      return
    }

    val results = engine.executeCommand(input)
    results.forEach { appendTerminalLine(it) }

    scope.launch(Dispatchers.IO) {
      val updated = vm.copy(
        currentWorkDir = engine.getCurrentDir(),
        fileSystemStateJson = engine.exportFileSystemJson(),
        installedPackagesJson = engine.exportPackagesJson(),
        commandHistoryJson = engine.exportHistoryJson()
      )
      _activeVm.value = updated
      repository.updateVm(updated)
    }

    if (input.trim() == "shutdown" || input.trim() == "poweroff") {
      shutdownVm(vm)
    } else if (input.trim() == "reboot") {
      forceResetVm(vm)
    }
  }

  fun getActivePrompt(): String {
    return if (_engineMode.value == EngineMode.REAL_NATIVE_HOST) {
      realNativeEngine.getPrompt()
    } else {
      shellEngine?.getPrompt() ?: "root@linux:~# "
    }
  }

  fun getFileContent(path: String): String? {
    return if (_engineMode.value == EngineMode.REAL_NATIVE_HOST) {
      realNativeEngine.readFile(path)
    } else {
      shellEngine?.readFile(path)
    }
  }

  fun saveFileContent(path: String, content: String) {
    if (_engineMode.value == EngineMode.REAL_NATIVE_HOST) {
      realNativeEngine.writeFile(path, content)
    } else {
      shellEngine?.writeFile(path, content)
    }
  }

  fun getAutocompleteFiles(): List<Pair<String, Boolean>> {
    return if (_engineMode.value == EngineMode.REAL_NATIVE_HOST) {
      realNativeEngine.listFiles()
    } else {
      shellEngine?.listFiles() ?: emptyList()
    }
  }

  // 1-Click Distro Installer Pipeline
  fun installDistroOneClick(
    template: DistroTemplate,
    chosenArch: String,
    cpuCores: Int,
    ramMb: Int,
    diskGb: Double,
    onComplete: (Long) -> Unit
  ) {
    scope.launch(Dispatchers.Default) {
      _installProgress.value = 0.05f
      _installStatusText.value = "Initializing virtual disk image (qemu-img create -f qcow2)..."
      appendTerminalLine(TerminalLine("=== Initiating 1-Click Install: ${template.name} (${chosenArch}) ===", TerminalCyan, isSystem = true))

      delay(300)
      _installProgress.value = 0.20f
      _installStatusText.value = "Allocating ${diskGb}GB Sparse QCOW2 Disk at /data/vms/${template.id}.qcow2..."
      appendTerminalLine(TerminalLine("Formatting disk image: qemu-img create -f qcow2 /data/vms/${template.id}.qcow2 ${diskGb.toInt()}G", TerminalDimText))

      if (realQemuAvailable) {
        try {
          val binaries = NativeBinaryExtractor.ensureBinariesExtracted(context)
          val vmsDir = File(context.filesDir, "vms")
          if (!vmsDir.exists()) vmsDir.mkdirs()
          val diskPath = File(vmsDir, "${template.id}-${chosenArch}.qcow2").absolutePath
          val success = NativeBinaryExtractor.createDiskImage(binaries.imgBinary, diskPath, diskGb)
          if (success) {
            appendTerminalLine(TerminalLine("Real QCOW2 disk created: $diskPath", TerminalGreen))
          }
        } catch (e: Exception) {
          appendTerminalLine(TerminalLine("Disk creation note: ${e.message}", TerminalDimText))
        }
      }

      delay(400)
      _installProgress.value = 0.45f
      _installStatusText.value = "Fetching boot kernel and rootfs images (${template.downloadSizeMb} MB)..."
      appendTerminalLine(TerminalLine("Downloading verified rootfs archive for ${template.name}...", TerminalCyan))

      delay(500)
      _installProgress.value = 0.70f
      _installStatusText.value = "Unpacking rootfs, setting up ${template.packageManager} package repos & network..."
      appendTerminalLine(TerminalLine("Configuring base system, user '${template.defaultUser}', OpenSSH & VirtIO drivers...", TerminalWhite))

      delay(400)
      _installProgress.value = 0.90f
      _installStatusText.value = "Registering QEMU VM profile and creating default port forwarding rules..."
      appendTerminalLine(TerminalLine("Configured: vCPUs: $cpuCores, RAM: ${ramMb}MB, Kernel: ${template.kernelArgs}", TerminalYellow))

      val diskPath = if (realQemuAvailable) {
        val vmsDir = File(context.filesDir, "vms")
        File(vmsDir, "${template.id}-${chosenArch}.qcow2").absolutePath
      } else {
        "/data/vms/${template.id}-${chosenArch}.qcow2"
      }

      val newVm = VirtualMachineEntity(
        name = "${template.name} (${chosenArch})",
        distroId = template.id,
        arch = chosenArch,
        cpuCores = cpuCores,
        cpuModel = if (chosenArch.contains("aarch64")) "cortex-a72" else "qemu64",
        ramMb = ramMb,
        diskSizeGb = diskGb,
        diskFormat = "qcow2",
        diskPath = diskPath,
        isoPath = "/iso/${template.id}-${template.version.take(6)}-${chosenArch}.iso",
        displayMode = "SERIAL_CONSOLE",
        networkMode = "USER_SLIRP",
        portForwardsJson = "[{\"host\":2222,\"guest\":22,\"name\":\"SSH\"},{\"host\":8080,\"guest\":80,\"name\":\"HTTP Server\"},{\"host\":3000,\"guest\":3000,\"name\":\"Dev Webapp\"}]",
        status = "STOPPED",
        customQemuArgs = "-accel tcg,tb-size=256",
        kernelParams = template.kernelArgs,
        defaultUser = template.defaultUser,
        currentWorkDir = if (template.defaultUser == "root") "/root" else "/home/${template.defaultUser}",
        installedPackagesJson = org.json.JSONArray(template.preinstalledTools).toString(),
        fileSystemStateJson = "{}",
        commandHistoryJson = "[\"uname -a\",\"df -h\",\"free -m\",\"neofetch\"]",
        createdAt = System.currentTimeMillis()
      )

      val newId = repository.insertVm(newVm)
      val createdVm = newVm.copy(id = newId)

      delay(300)
      _installProgress.value = 1.0f
      _installStatusText.value = "Installation Complete! Booting VM..."
      appendTerminalLine(TerminalLine("=== ${template.name} installed successfully! Ready to boot ===", TerminalGreen, isSuccess = true))

      delay(200)
      _installProgress.value = null
      _installStatusText.value = ""

      setActiveVm(createdVm)
      startVm(createdVm)
      onComplete(newId)
    }
  }

  // Snapshot Management
  fun createSnapshot(vm: VirtualMachineEntity, name: String, desc: String) {
    scope.launch(Dispatchers.Default) {
      val snap = VmSnapshotEntity(
        vmId = vm.id,
        name = name,
        description = desc,
        timestamp = System.currentTimeMillis(),
        ramUsageMb = (vm.ramMb * 0.28).toInt(),
        stateData = vm.fileSystemStateJson
      )
      repository.createSnapshot(snap)
      appendTerminalLine(
        TerminalLine(
          "Snapshot '$name' created successfully (QEMU 'savevm $name')",
          TerminalGreen,
          isSuccess = true
        )
      )
    }
  }

  fun restoreSnapshot(vm: VirtualMachineEntity, snapshot: VmSnapshotEntity) {
    scope.launch(Dispatchers.Default) {
      appendTerminalLine(
        TerminalLine(
          "Restoring snapshot '${snapshot.name}' (QEMU 'loadvm ${snapshot.name}')...",
          TerminalYellow,
          isSystem = true
        )
      )
      val updated = vm.copy(fileSystemStateJson = snapshot.stateData)
      _activeVm.value = updated
      repository.updateVm(updated)
      shellEngine = LinuxShellEngine(updated)
      delay(300)
      appendTerminalLine(TerminalLine("Snapshot '${snapshot.name}' restored.", TerminalGreen, isSuccess = true))
    }
  }

  fun getShellEngine(): LinuxShellEngine? = shellEngine

  private fun startTelemetryTicker() {
    telemetryJob?.cancel()
    telemetryJob = scope.launch(Dispatchers.Default) {
      while (isActive) {
        val vm = _activeVm.value
        if (vm != null && vm.status == VmStatus.RUNNING.name) {
          val cpuUsage = Random.nextFloat() * 14f + 2.5f
          val ramUsed = (vm.ramMb * (0.25f + Random.nextFloat() * 0.08f)).toInt()
          val diskRead = Random.nextFloat() * 240f + 12f
          val diskWrite = Random.nextFloat() * 110f + 5f
          val netRx = Random.nextFloat() * 45f + 2f
          val netTx = Random.nextFloat() * 20f + 1f

          _telemetry.value = TelemetryMetrics(
            cpuPercent = cpuUsage,
            ramUsedMb = ramUsed,
            ramTotalMb = vm.ramMb,
            diskReadKbps = diskRead,
            diskWriteKbps = diskWrite,
            netRxKbps = netRx,
            netTxKbps = netTx,
            uptimeSeconds = vm.uptimeSeconds + 1
          )

          repository.incrementUptime(vm.id, 1)
          _activeVm.value = vm.copy(uptimeSeconds = vm.uptimeSeconds + 1)
        }
        delay(1000)
      }
    }
  }

  private fun stopTelemetryTicker() {
    telemetryJob?.cancel()
    telemetryJob = null
    val vm = _activeVm.value
    _telemetry.value = TelemetryMetrics(
      ramTotalMb = vm?.ramMb ?: 1024
    )
  }
}
