package com.example.engine

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object NativeBinaryExtractor {

  private const val QEMU_ASSETS_DIR = "nativebin"
  private const val QEMU_DIRNAME = "qemu"

  data class QemuBinaries(
    val systemBinary: String,
    val imgBinary: String,
    val baseDir: String,
    val libDir: String
  )

  fun ensureBinariesExtracted(context: Context): QemuBinaries {
    val qemuDir = File(context.filesDir, QEMU_DIRNAME)
    if (!qemuDir.exists()) qemuDir.mkdirs()

    val libDir = File(qemuDir, "lib")
    if (!libDir.exists()) libDir.mkdirs()

    val systemArch = detectAndroidAbi()
    val qemuArch = mapToQemuArch(systemArch)

    val systemBinary = File(qemuDir, "qemu-system-$qemuArch")
    val imgBinary = File(qemuDir, "qemu-img")

    val assetList = context.assets.list(QEMU_ASSETS_DIR) ?: emptyArray()

    if (!systemBinary.exists() || systemBinary.length() == 0L) {
      val candidateNames = listOf(
        "qemu-system-$qemuArch",
        "qemu-system-$qemuArch-static",
        "qemu/qemu-system-$qemuArch",
        "qemu/qemu-system-$qemuArch-static"
      )
      for (name in candidateNames) {
        if (assetList.contains(name)) {
          extractAsset(context, "$QEMU_ASSETS_DIR/$name", systemBinary)
          break
        }
      }
    }

    if (!imgBinary.exists() || imgBinary.length() == 0L) {
      val candidateNames = listOf("qemu-img", "qemu/qemu-img")
      for (name in candidateNames) {
        if (assetList.contains(name)) {
          extractAsset(context, "$QEMU_ASSETS_DIR/$name", imgBinary)
          break
        }
      }
    }

    // Extract shared libraries
    val soFiles = assetList.filter { it.endsWith(".so") || it.contains(".so.") }
    for (soFile in soFiles) {
      val destFile = File(libDir, soFile)
      if (!destFile.exists()) {
        extractAsset(context, "$QEMU_ASSETS_DIR/$soFile", destFile)
      }
    }

    if (systemBinary.exists()) systemBinary.setExecutable(true, true)
    if (imgBinary.exists()) imgBinary.setExecutable(true, true)

    return QemuBinaries(
      systemBinary = systemBinary.absolutePath,
      imgBinary = imgBinary.absolutePath,
      baseDir = qemuDir.absolutePath,
      libDir = libDir.absolutePath
    )
  }

  fun isQemuAvailable(context: Context): Boolean {
    val qemuDir = File(context.filesDir, QEMU_DIRNAME)
    if (!qemuDir.exists()) return false
    return qemuDir.listFiles()?.any { it.canExecute() && it.name.startsWith("qemu-system-") } == true
  }

  private fun extractAsset(context: Context, assetPath: String, dest: File) {
    try {
      context.assets.open(assetPath).use { input ->
        FileOutputStream(dest).use { output ->
          input.copyTo(output)
        }
      }
      dest.setExecutable(true, true)
      dest.setReadable(true, true)
    } catch (e: Exception) {
      // Asset not found in this architecture - will fallback to simulated mode
    }
  }

  private fun detectAndroidAbi(): String {
    val abis = android.os.Build.SUPPORTED_ABIS
    return abis.firstOrNull() ?: "arm64-v8a"
  }

  private fun mapToQemuArch(abi: String): String {
    return when {
      abi.contains("arm64") -> "aarch64"
      abi.contains("armeabi") || abi.contains("arm-v7") -> "arm"
      abi.contains("x86_64") -> "x86_64"
      abi.contains("x86") -> "i386"
      else -> "aarch64"
    }
  }

  fun createDiskImage(imgBinary: String, path: String, sizeGb: Double): Boolean {
    return try {
      val sizeStr = "${sizeGb.toInt()}G"
      val process = ProcessBuilder(imgBinary, "create", "-f", "qcow2", path, sizeStr)
        .redirectErrorStream(true)
        .start()
      process.waitFor() == 0
    } catch (e: Exception) {
      false
    }
  }

  fun getDiskInfo(imgBinary: String, path: String): String {
    return try {
      val process = ProcessBuilder(imgBinary, "info", path)
        .redirectErrorStream(true)
        .start()
      val output = process.inputStream.bufferedReader().readText()
      process.waitFor()
      output
    } catch (e: Exception) {
      "Error: ${e.message}"
    }
  }
}
