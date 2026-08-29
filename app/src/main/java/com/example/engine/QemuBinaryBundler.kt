package com.example.engine

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * QEMU Binary Bundler - Extracts bundled QEMU binaries from APK assets to app storage
 * Enables real QEMU VM execution without requiring external installation
 */
class QemuBinaryBundler(private val context: Context) {
    companion object {
        private const val TAG = "QemuBinaryBundler"
        private const val ASSETS_QEMU_DIR = "qemu_binaries"
    }

    private val qemuDir: File by lazy {
        val dir = File(context.filesDir, "qemu")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    /**
     * Check if QEMU binaries are already extracted
     */
    fun isExtractionComplete(): Boolean {
        val binaries = listOf(
            "qemu-system-x86_64",
            "qemu-system-aarch64", 
            "qemu-system-riscv64",
            "qemu-system-i386"
        )
        return binaries.any { binary ->
            File(qemuDir, binary).exists() && File(qemuDir, binary).canExecute()
        }
    }

    /**
     * Extract all bundled QEMU binaries from assets
     * Returns list of successfully extracted binaries
     */
    suspend fun extractBundledBinaries(): List<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val extractedBinaries = mutableListOf<String>()
        
        try {
            Log.d(TAG, "Starting extraction of bundled QEMU binaries...")
            
            // Get list of files in assets/qemu_binaries/
            val assetManager = context.assets
            val assetFiles = try {
                assetManager.list(ASSETS_QEMU_DIR)?.toList() ?: emptyList()
            } catch (e: Exception) {
                Log.w(TAG, "No bundled QEMU binaries found in assets", e)
                emptyList()
            }
            
            if (assetFiles.isEmpty()) {
                Log.i(TAG, "No QEMU binaries bundled in APK - will use simulation mode or system QEMU")
                return@withContext extractedBinaries
            }
            
            Log.d(TAG, "Found ${assetFiles.size} files in assets/$ASSETS_QEMU_DIR: ${assetFiles.joinToString(", ")}")
            
            // Filter out non-binary files (like BUNDLE_INFO.txt)
            val binaryFiles = assetFiles.filter { 
                it.startsWith("qemu-system-") && !it.endsWith(".txt") 
            }
            
            Log.d(TAG, "Extracting ${binaryFiles.size} QEMU binaries...")
            
            for (binaryName in binaryFiles) {
                try {
                    val destFile = File(qemuDir, binaryName)
                    
                    // Skip if already exists and is executable
                    if (destFile.exists() && destFile.canExecute()) {
                        Log.d(TAG, "Binary $binaryName already extracted and executable")
                        extractedBinaries.add(binaryName)
                        continue
                    }
                    
                    // Extract from assets
                    assetManager.open("$ASSETS_QEMU_DIR/$binaryName").use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    // Make executable
                    val chmodSuccess = destFile.setExecutable(true, false)
                    if (chmodSuccess) {
                        Log.i(TAG, "Successfully extracted $binaryName (${destFile.length() / 1024 / 1024} MB)")
                        extractedBinaries.add(binaryName)
                    } else {
                        Log.e(TAG, "Failed to make $binaryName executable")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to extract $binaryName", e)
                }
            }
            
            Log.i(TAG, "Extraction complete: ${extractedBinaries.size}/${binaryFiles.size} binaries extracted")
            
            // Log bundle info if available
            try {
                assetManager.open("$ASSETS_QEMU_DIR/BUNDLE_INFO.txt").use { input ->
                    val info = input.bufferedReader().use { it.readText() }
                    Log.i(TAG, "QEMU Bundle Info:\n$info")
                }
            } catch (e: Exception) {
                Log.d(TAG, "No BUNDLE_INFO.txt found")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during QEMU binary extraction", e)
        }
        
        extractedBinaries
    }

    /**
     * Get path to extracted QEMU binary for specific architecture
     */
    fun getQemuBinaryPath(arch: String): String? {
        val binaryName = when (arch.lowercase()) {
            "aarch64", "arm64" -> "qemu-system-aarch64"
            "riscv64" -> "qemu-system-riscv64"
            "i386", "x86" -> "qemu-system-i386"
            else -> "qemu-system-x86_64"
        }
        
        val binaryFile = File(qemuDir, binaryName)
        return if (binaryFile.exists() && binaryFile.canExecute()) {
            binaryFile.absolutePath
        } else {
            null
        }
    }

    /**
     * Check if a specific architecture's QEMU binary is available
     */
    fun isBinaryAvailable(arch: String): Boolean {
        return getQemuBinaryPath(arch) != null
    }

    /**
     * Get list of all available QEMU binaries
     */
    fun getAvailableBinaries(): List<String> {
        return qemuDir.listFiles { file ->
            file.isFile && file.name.startsWith("qemu-system-") && file.canExecute()
        }?.map { it.name } ?: emptyList()
    }

    /**
     * Clean up extracted binaries (force re-extraction on next run)
     */
    fun cleanupExtractedBinaries(): Boolean {
        return try {
            qemuDir.deleteRecursively()
            Log.i(TAG, "Cleaned up extracted QEMU binaries")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean up QEMU binaries", e)
            false
        }
    }

    /**
     * Get total size of extracted binaries in MB
     */
    fun getExtractedSizeMb(): Double {
        return qemuDir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
            .toDouble() / 1024.0 / 1024.0
    }
}
