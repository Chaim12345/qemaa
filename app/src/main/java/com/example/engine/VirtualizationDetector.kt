package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.io.File

data class VirtualizationReport(
  val hasKvmDevice: Boolean,
  val isKvmReadable: Boolean,
  val isKvmWritable: Boolean,
  val kvmPath: String,
  val hasRootAccess: Boolean,
  val rootMethod: String,
  val hasAvfFramework: Boolean,
  val avfDetails: String,
  val hostKernelVersion: String,
  val hostArchitecture: String,
  val physicalCpuCores: Int,
  val totalHostRamMb: Long,
  val availableHostRamMb: Long,
  val androidSdkInt: Int,
  val androidRelease: String,
  val deviceModel: String,
  val isHypervisorSupported: Boolean,
  val hasQemuBinary: Boolean,
  val qemuBinaryPath: String,
  val recommendations: List<String>
)

object VirtualizationDetector {

  fun probeHardwareVirtualization(context: Context): VirtualizationReport {
    // 1. Probe /dev/kvm
    val kvmFile = File("/dev/kvm")
    val hasKvm = kvmFile.exists()
    val isKvmR = kvmFile.canRead()
    val isKvmW = kvmFile.canWrite()

    // 2. Check Root / SU
    val suPaths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/xbin/magisk", "/data/local/tmp/su")
    var hasRoot = false
    var rootMethod = "None detected (Standard Sandboxed Android)"
    for (p in suPaths) {
      if (File(p).exists()) {
        hasRoot = true
        rootMethod = "Found su binary at $p"
        break
      }
    }

    // 3. Check AVF (Android Virtualization Framework)
    var hasAvf = false
    var avfDetails = "AVF requires Android 13+ (API 33+) with protected KVM (pKVM)"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      try {
        val vmManagerClass = Class.forName("android.system.virtualmachine.VirtualMachineManager")
        hasAvf = true
        avfDetails = "Android Virtualization Framework (AVF) classes detected in platform runtime."
      } catch (e: ClassNotFoundException) {
        avfDetails = "AVF not exposed in standard user API or restricted to system privileges."
      }
    }

    // 4. Host OS & Kernel
    var kernelVersion = System.getProperty("os.version") ?: "Unknown"
    val procVersionFile = File("/proc/version")
    if (procVersionFile.exists() && procVersionFile.canRead()) {
      try {
        kernelVersion = procVersionFile.readText().trim()
      } catch (e: Exception) {
        // ignore
      }
    }

    // 5. Memory & Hardware
    val actMgr = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    actMgr?.getMemoryInfo(memInfo)
    val totalRamMb = memInfo.totalMem / (1024 * 1024)
    val availRamMb = memInfo.availMem / (1024 * 1024)
    val cpuCores = Runtime.getRuntime().availableProcessors()
    val hostArch = Build.SUPPORTED_ABIS.firstOrNull() ?: System.getProperty("os.arch") ?: "unknown"

    // 6. Check for QEMU binaries in app assets/files
    val hasQemuBinary = NativeBinaryExtractor.isQemuAvailable(context)
    val qemuBinaries = if (hasQemuBinary) {
      NativeBinaryExtractor.ensureBinariesExtracted(context)
    } else null

    // 7. Hypervisor properties
    val hypervisorProp = getSystemProperty("ro.boot.hypervisor.version")
    val vmSupportedProp = getSystemProperty("ro.virtual_machine.supported")
    val isHyp = hypervisorProp.isNotBlank() || vmSupportedProp == "true" || hasKvm

    val recommendations = mutableListOf<String>()
    if (hasKvm && isKvmW) {
      recommendations.add("✓ Direct hardware KVM acceleration (/dev/kvm) is fully accessible.")
    } else if (hasKvm && !isKvmW) {
      recommendations.add("⚠ /dev/kvm node exists but is restricted by Android SELinux. App uses Native Linux Host Shell & QEMU TCG.")
    } else {
      recommendations.add("ℹ Running on standard Android Linux kernel with Native Process Engine & multi-arch QEMU JNI/TCG.")
    }

    if (hasQemuBinary) {
      recommendations.add("✓ QEMU binary detected at ${qemuBinaries?.baseDir ?: "app files"} - Real VM hardware emulation enabled.")
    } else {
      recommendations.add("ℹ QEMU binaries not found. Place qemu-system-* and qemu-img in assets/nativebin/ for real VM execution.")
    }

    if (Build.VERSION.SDK_INT >= 33) {
      recommendations.add("✓ Android 13+ pKVM / Microdroid subsystem supported on compatible hardware.")
    }
    recommendations.add("✓ Real native shell execution active for direct Linux process execution.")

    return VirtualizationReport(
      hasKvmDevice = hasKvm,
      isKvmReadable = isKvmR,
      isKvmWritable = isKvmW,
      kvmPath = if (hasKvm) "/dev/kvm" else "Not present",
      hasRootAccess = hasRoot,
      rootMethod = rootMethod,
      hasAvfFramework = hasAvf,
      avfDetails = avfDetails,
      hostKernelVersion = kernelVersion,
      hostArchitecture = hostArch,
      physicalCpuCores = cpuCores,
      totalHostRamMb = totalRamMb,
      availableHostRamMb = availRamMb,
      androidSdkInt = Build.VERSION.SDK_INT,
      androidRelease = Build.VERSION.RELEASE ?: "Unknown",
      deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
      isHypervisorSupported = isHyp,
      hasQemuBinary = hasQemuBinary,
      qemuBinaryPath = qemuBinaries?.systemBinary ?: "Not found",
      recommendations = recommendations
    )
  }

  private fun getSystemProperty(key: String): String {
    return try {
      val c = Class.forName("android.os.SystemProperties")
      val get = c.getMethod("get", String::class.java)
      get.invoke(null, key) as? String ?: ""
    } catch (e: Exception) {
      ""
    }
  }
}
