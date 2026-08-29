package com.example.engine

import android.content.Context
import java.io.File

/**
 * Small native-shell engine used for diagnostics and host-side file operations.
 *
 * The QEMU path remains the primary VM implementation. This class provides the
 * lightweight command/file operations used when the app is running without a
 * bundled guest image.
 */
class RealNativeProcessEngine(context: Context) {
    private val workDir = File(context.filesDir, "native-shell").apply { mkdirs() }

    fun executeCommand(command: String): List<TerminalLine> {
        if (command.trim() == "help") {
            return listOf(
                TerminalLine.System("REAL NATIVE LINUX shell"),
                TerminalLine.System("Commands run in the Android app sandbox."),
                TerminalLine.System("Available commands: help, pwd, ls, cat, echo")
            )
        }

        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .directory(workDir)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readLines() }
            process.waitFor()
            output.map { TerminalLine.Output(it) }
        } catch (error: Exception) {
            listOf(TerminalLine.Error("Command failed: ${error.message ?: "unknown error"}"))
        }
    }

    fun writeFile(path: String, content: String) {
        resolvePath(path).writeText(content)
    }

    fun readFile(path: String): String = resolvePath(path).readText()

    fun getPrompt(): String = "root@android-host:${workDir.absolutePath}# "

    private fun resolvePath(path: String): File {
        val candidate = File(workDir, path).canonicalFile
        val root = workDir.canonicalFile
        require(candidate == root || candidate.path.startsWith(root.path + File.separator)) {
            "Path is outside the app sandbox"
        }
        return candidate
    }
}
