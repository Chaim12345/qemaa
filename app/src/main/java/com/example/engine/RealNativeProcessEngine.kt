package com.example.engine

import android.content.Context
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TerminalDimText
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TerminalWhite
import com.example.ui.theme.TerminalYellow
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class RealNativeProcessEngine(private val context: Context) {

  private val sandboxDir: File by lazy {
    val dir = File(context.filesDir, "linux_sandbox")
    if (!dir.exists()) {
      dir.mkdirs()
    }
    dir
  }

  private var currentDir: File = sandboxDir
  private val environment = mutableMapOf<String, String>()

  init {
    initSandbox()
  }

  private fun initSandbox() {
    currentDir = sandboxDir
    val binDir = File(sandboxDir, "bin")
    val homeDir = File(sandboxDir, "home/user")
    val tmpDir = File(sandboxDir, "tmp")
    binDir.mkdirs()
    homeDir.mkdirs()
    tmpDir.mkdirs()

    environment["HOME"] = homeDir.absolutePath
    environment["TMPDIR"] = tmpDir.absolutePath
    environment["PATH"] = "${binDir.absolutePath}:/system/bin:/system/xbin:/vendor/bin:/apex/com.android.runtime/bin"
    environment["USER"] = "u0_a" + (context.applicationInfo.uid % 100000)
    environment["SHELL"] = "/system/bin/sh"
    environment["TERM"] = "xterm-256color"

    // Create a readme file inside sandbox
    val readme = File(homeDir, "README.txt")
    if (!readme.exists()) {
      readme.writeText(
        """
        === REAL NATIVE ANDROID LINUX HOST SHELL ===
        This is a live native Linux process execution environment running directly on your Android device kernel.
        Working Directory: ${sandboxDir.absolutePath}
        Native Process UID: ${environment["USER"]}
        
        Supported:
        - Real Process Execution via /system/bin/sh (Toybox, Toolbox, native utilities)
        - Real file reading & writing in application sandbox
        - Direct hardware telemetry from /proc/cpuinfo, /proc/meminfo, /proc/version
        - Script execution (.sh files) and native shell commands
        """.trimIndent()
      )
    }
  }

  fun getPrompt(): String {
    val user = environment["USER"] ?: "user"
    val host = "android-host"
    val homePath = environment["HOME"] ?: ""
    val curPath = currentDir.absolutePath
    val displayDir = if (curPath == homePath) "~" else if (curPath.startsWith(sandboxDir.absolutePath)) {
      curPath.removePrefix(sandboxDir.absolutePath).ifEmpty { "/" }
    } else {
      curPath
    }
    return "$user@$host:$displayDir$ "
  }

  fun getCurrentDir(): String = currentDir.absolutePath

  fun executeCommand(rawInput: String): List<TerminalLine> {
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) return emptyList()

    val output = mutableListOf<TerminalLine>()

    // Built-in 'cd' command
    if (trimmed == "cd" || trimmed.startsWith("cd ")) {
      val target = trimmed.removePrefix("cd").trim()
      return handleCd(target)
    }

    // Built-in 'clear'
    if (trimmed == "clear") {
      return listOf(TerminalLine("__CLEAR__"))
    }

    if (trimmed == "help") {
      return handleHelp()
    }

    // Run real process using ProcessBuilder
    try {
      val pb = ProcessBuilder("/system/bin/sh", "-c", trimmed)
      pb.directory(if (currentDir.exists() && currentDir.isDirectory) currentDir else sandboxDir)
      
      val env = pb.environment()
      environment.forEach { (k, v) -> env[k] = v }
      
      val process = pb.start()

      // Read standard output
      val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
      var stdoutLine: String? = stdoutReader.readLine()
      while (stdoutLine != null) {
        output.add(TerminalLine(stdoutLine, TerminalWhite))
        stdoutLine = stdoutReader.readLine()
      }
      stdoutReader.close()

      // Read error output
      val stderrReader = BufferedReader(InputStreamReader(process.errorStream))
      var stderrLine: String? = stderrReader.readLine()
      while (stderrLine != null) {
        output.add(TerminalLine(stderrLine, TerminalRed, isError = true))
        stderrLine = stderrReader.readLine()
      }
      stderrReader.close()

      val exitCode = process.waitFor()
      if (exitCode != 0 && output.isEmpty()) {
        output.add(TerminalLine("Process exited with code $exitCode", TerminalYellow))
      }
    } catch (e: Exception) {
      output.add(TerminalLine("Execution Error: ${e.message}", TerminalRed, isError = true))
    }

    return output
  }

  private fun handleCd(target: String): List<TerminalLine> {
    val dest = when {
      target.isEmpty() || target == "~" -> File(environment["HOME"] ?: sandboxDir.absolutePath)
      target.startsWith("/") -> File(target)
      else -> File(currentDir, target)
    }

    val canonical = try {
      dest.canonicalFile
    } catch (e: Exception) {
      dest
    }

    return if (canonical.exists() && canonical.isDirectory) {
      currentDir = canonical
      emptyList()
    } else {
      listOf(TerminalLine("cd: $target: No such directory", TerminalRed, isError = true))
    }
  }

  private fun handleHelp(): List<TerminalLine> {
    return listOf(
      TerminalLine("=== REAL NATIVE LINUX PROCESS EXECUTION ===", TerminalCyan),
      TerminalLine("This environment executes actual native commands on Android's Linux kernel.", TerminalGreen),
      TerminalLine("Native Shell: /system/bin/sh", TerminalYellow),
      TerminalLine("Sandboxed Path: ${sandboxDir.absolutePath}", TerminalDimText),
      TerminalLine("Available live system commands:", TerminalYellow),
      TerminalLine("  uname -a, cat /proc/cpuinfo, cat /proc/meminfo, cat /proc/version", TerminalWhite),
      TerminalLine("  ls -la, pwd, whoami, id, df -h, ps, date, printenv, uptime", TerminalWhite),
      TerminalLine("  mkdir, touch, rm, cp, mv, echo, chmod, sh script.sh", TerminalWhite),
      TerminalLine("  nano <file> (Visual Text Editor)", TerminalGreen)
    )
  }

  fun readFile(path: String): String? {
    val file = if (path.startsWith("/")) File(path) else File(currentDir, path)
    return if (file.exists() && file.isFile && file.canRead()) {
      try {
        file.readText()
      } catch (e: Exception) {
        null
      }
    } else null
  }

  fun writeFile(path: String, content: String) {
    val file = if (path.startsWith("/")) File(path) else File(currentDir, path)
    try {
      file.parentFile?.mkdirs()
      file.writeText(content)
    } catch (e: Exception) {
      // ignore
    }
  }

  fun listFiles(): List<Pair<String, Boolean>> {
    val files = currentDir.listFiles() ?: return emptyList()
    return files.map { Pair(it.name, it.isDirectory) }.sortedWith(compareBy({ !it.second }, { it.first }))
  }
}
