package com.example.engine

import androidx.compose.ui.graphics.Color
import com.example.data.model.DistroCatalog
import com.example.data.model.VirtualMachineEntity
import com.example.ui.theme.TerminalBlue
import com.example.ui.theme.TerminalCyan
import com.example.ui.theme.TerminalDimText
import com.example.ui.theme.TerminalGreen
import com.example.ui.theme.TerminalOrange
import com.example.ui.theme.TerminalPurple
import com.example.ui.theme.TerminalRed
import com.example.ui.theme.TerminalWhite
import com.example.ui.theme.TerminalYellow
import org.json.JSONArray
import org.json.JSONObject

data class TerminalLine(
  val text: String,
  val color: Color = TerminalWhite,
  val isPrompt: Boolean = false,
  val isError: Boolean = false,
  val isSuccess: Boolean = false,
  val isSystem: Boolean = false
)

class LinuxShellEngine(private var vm: VirtualMachineEntity) {

  private var currentDir: String = vm.currentWorkDir.ifBlank { "/root" }
  private val environment = mutableMapOf<String, String>(
    "USER" to vm.defaultUser,
    "HOME" to if (vm.defaultUser == "root") "/root" else "/home/${vm.defaultUser}",
    "SHELL" to "/bin/sh",
    "TERM" to "xterm-256color",
    "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
    "LANG" to "C.UTF-8",
    "HOSTNAME" to vm.name.lowercase().replace(" ", "-").replace(".", "-").take(15)
  )

  // Virtual in-memory filesystem: path -> content or "[DIR]"
  private val virtualFs = mutableMapOf<String, String>()
  private val installedPackages = mutableSetOf<String>()
  private val commandHistory = mutableListOf<String>()

  init {
    loadFileSystemState()
    loadPackages()
    loadHistory()
  }

  fun updateVm(updatedVm: VirtualMachineEntity) {
    this.vm = updatedVm
    environment["USER"] = updatedVm.defaultUser
    environment["HOME"] = if (updatedVm.defaultUser == "root") "/root" else "/home/${updatedVm.defaultUser}"
    environment["HOSTNAME"] = updatedVm.name.lowercase().replace(" ", "-").replace(".", "-").take(15)
  }

  private fun loadPackages() {
    try {
      val arr = JSONArray(vm.installedPackagesJson)
      for (i in 0 until arr.length()) {
        installedPackages.add(arr.getString(i))
      }
    } catch (e: Exception) {
      installedPackages.addAll(listOf("busybox", "coreutils", "curl", "wget", "tar", "gzip"))
    }
  }

  private fun loadHistory() {
    try {
      val arr = JSONArray(vm.commandHistoryJson)
      for (i in 0 until arr.length()) {
        commandHistory.add(arr.getString(i))
      }
    } catch (e: Exception) {
      // ignore
    }
  }

  private fun loadFileSystemState() {
    try {
      if (vm.fileSystemStateJson.isNotBlank() && vm.fileSystemStateJson != "{}") {
        val obj = JSONObject(vm.fileSystemStateJson)
        obj.keys().forEach { key ->
          virtualFs[key] = obj.getString(key)
        }
      }
    } catch (e: Exception) {
      // ignore
    }

    // Initialize core standard directories if missing
    ensureDir("/bin")
    ensureDir("/sbin")
    ensureDir("/usr/bin")
    ensureDir("/usr/sbin")
    ensureDir("/etc")
    ensureDir("/etc/network")
    ensureDir("/home")
    ensureDir("/home/${vm.defaultUser}")
    ensureDir("/root")
    ensureDir("/var")
    ensureDir("/var/log")
    ensureDir("/tmp")
    ensureDir("/dev")
    ensureDir("/proc")
    ensureDir("/sys")
    ensureDir("/mnt")

    // Default configuration files
    if (!virtualFs.containsKey("/etc/hostname")) {
      virtualFs["/etc/hostname"] = environment["HOSTNAME"] ?: "linux-vm"
    }
    if (!virtualFs.containsKey("/etc/os-release")) {
      val distro = DistroCatalog.DISTROS.firstOrNull { it.id == vm.distroId }
      virtualFs["/etc/os-release"] = """
NAME="${distro?.name ?: vm.name}"
VERSION="${distro?.version ?: "1.0"}"
ID="${distro?.id ?: "linux"}"
PRETTY_NAME="${distro?.name ?: vm.name} (${vm.arch})"
HOME_URL="https://kernel.org"
      """.trimIndent()
    }
    if (!virtualFs.containsKey("/etc/hosts")) {
      virtualFs["/etc/hosts"] = """
127.0.0.1   localhost
127.0.1.1   ${environment["HOSTNAME"]}
::1         localhost ip6-localhost ip6-loopback
      """.trimIndent()
    }
    if (!virtualFs.containsKey("/etc/resolv.conf")) {
      virtualFs["/etc/resolv.conf"] = "nameserver 1.1.1.1\nnameserver 8.8.8.8"
    }
    if (!virtualFs.containsKey("/root/welcome.txt") && !virtualFs.containsKey("/home/${vm.defaultUser}/welcome.txt")) {
      val welcomePath = if (vm.defaultUser == "root") "/root/welcome.txt" else "/home/${vm.defaultUser}/welcome.txt"
      virtualFs[welcomePath] = """
Welcome to ${vm.name} on QEMU Virtual Machine!
Architecture: ${vm.arch} | vCPUs: ${vm.cpuCores} | RAM: ${vm.ramMb} MB | Disk: ${vm.diskSizeGb} GB
Networking: User SLIRP Mode (DHCP IP: 10.0.2.15)
Package Manager: ${DistroCatalog.DISTROS.firstOrNull { it.id == vm.distroId }?.packageManager ?: "apk"}

Type 'help' for available commands or 'neofetch' to view system specs.
Type 'nano <filename>' to open the built-in text editor.
      """.trimIndent()
    }
  }

  fun exportFileSystemJson(): String {
    val obj = JSONObject()
    virtualFs.forEach { (k, v) -> obj.put(k, v) }
    return obj.toString()
  }

  fun exportPackagesJson(): String {
    val arr = JSONArray()
    installedPackages.forEach { arr.put(it) }
    return arr.toString()
  }

  fun exportHistoryJson(): String {
    val arr = JSONArray()
    commandHistory.takeLast(50).forEach { arr.put(it) }
    return arr.toString()
  }

  fun getPrompt(): String {
    val user = environment["USER"] ?: "root"
    val host = environment["HOSTNAME"] ?: "linux"
    val displayDir = if (currentDir == environment["HOME"]) "~" else currentDir
    val symbol = if (user == "root") "#" else "$"
    return "$user@$host:$displayDir$symbol "
  }

  fun getCurrentDir(): String = currentDir

  fun readFile(path: String): String? {
    val resolved = resolvePath(path)
    return when {
      resolved == "/proc/cpuinfo" -> generateCpuInfo()
      resolved == "/proc/meminfo" -> generateMemInfo()
      resolved == "/proc/version" -> "Linux version 6.6.21-qemu-virt (${vm.arch}) (gcc 13.2.1) #1 SMP PREEMPT"
      resolved == "/proc/uptime" -> "${vm.uptimeSeconds}.45 ${vm.uptimeSeconds * 2}.12"
      virtualFs[resolved] != null && virtualFs[resolved] != "[DIR]" -> virtualFs[resolved]
      else -> null
    }
  }

  fun writeFile(path: String, content: String) {
    val resolved = resolvePath(path)
    val parent = resolved.substringBeforeLast('/', "")
    if (parent.isNotEmpty()) ensureDir(parent)
    virtualFs[resolved] = content
  }

  fun listFiles(path: String = currentDir): List<Pair<String, Boolean>> {
    val resolved = resolvePath(path)
    val normalized = if (resolved.endsWith("/")) resolved else "$resolved/"
    val entries = mutableListOf<Pair<String, Boolean>>()

    val prefixLength = if (resolved == "/") 1 else normalized.length
    virtualFs.keys.forEach { k ->
      if (k != resolved && k.startsWith(if (resolved == "/") "/" else normalized)) {
        val rel = k.substring(prefixLength)
        val firstPart = rel.substringBefore('/')
        if (firstPart.isNotBlank()) {
          val isDir = rel.contains('/') || virtualFs[k] == "[DIR]"
          if (!entries.any { it.first == firstPart }) {
            entries.add(Pair(firstPart, isDir))
          }
        }
      }
    }
    return entries.sortedWith(compareBy({ !it.second }, { it.first }))
  }

  private fun ensureDir(dirPath: String) {
    val parts = dirPath.split('/').filter { it.isNotEmpty() }
    var current = ""
    for (p in parts) {
      current += "/$p"
      virtualFs[current] = "[DIR]"
    }
  }

  fun resolvePath(path: String): String {
    var p = path.trim()
    if (p.startsWith("~")) {
      p = (environment["HOME"] ?: "/root") + p.removePrefix("~")
    }
    if (!p.startsWith("/")) {
      p = if (currentDir == "/") "/$p" else "$currentDir/$p"
    }
    val segments = mutableListOf<String>()
    p.split('/').forEach { seg ->
      when (seg) {
        "", "." -> {}
        ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.size - 1)
        else -> segments.add(seg)
      }
    }
    return "/" + segments.joinToString("/")
  }

  fun executeCommand(rawInput: String): List<TerminalLine> {
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) return emptyList()

    commandHistory.add(trimmed)
    val output = mutableListOf<TerminalLine>()

    // Handle redirection (e.g. echo "hello" > file.txt or >> file.txt)
    if (trimmed.contains(" > ") || trimmed.contains(" >> ")) {
      val isAppend = trimmed.contains(" >> ")
      val parts = if (isAppend) trimmed.split(" >> ", limit = 2) else trimmed.split(" > ", limit = 2)
      val cmdPart = parts[0].trim()
      val targetFile = parts[1].trim()

      val cmdResult = executeSingleCommand(cmdPart)
      val textToSave = cmdResult.joinToString("\n") { it.text }
      val targetPath = resolvePath(targetFile)

      if (isAppend) {
        val existing = readFile(targetPath) ?: ""
        writeFile(targetPath, if (existing.isEmpty()) textToSave else "$existing\n$textToSave")
      } else {
        writeFile(targetPath, textToSave)
      }
      return output
    }

    // Handle multi-command chaining with && or ;
    val commands = if (trimmed.contains(" && ")) trimmed.split(" && ") else trimmed.split(";")
    for (cmd in commands) {
      val res = executeSingleCommand(cmd.trim())
      output.addAll(res)
    }

    return output
  }

  private fun executeSingleCommand(input: String): List<TerminalLine> {
    val tokens = parseTokens(input)
    if (tokens.isEmpty()) return emptyList()

    val cmd = tokens[0]
    val args = tokens.drop(1)

    return when (cmd) {
      "help" -> handleHelp()
      "clear" -> listOf(TerminalLine("__CLEAR__"))
      "pwd" -> listOf(TerminalLine(currentDir, TerminalWhite))
      "whoami" -> listOf(TerminalLine(environment["USER"] ?: "root", TerminalGreen))
      "id" -> listOf(
        TerminalLine("uid=0(root) gid=0(root) groups=0(root),1(bin),2(daemon),10(wheel)", TerminalWhite)
      )
      "uname" -> handleUname(args)
      "ls" -> handleLs(args)
      "cd" -> handleCd(args)
      "cat" -> handleCat(args)
      "echo" -> handleEcho(args)
      "mkdir" -> handleMkdir(args)
      "touch" -> handleTouch(args)
      "rm" -> handleRm(args)
      "cp" -> handleCp(args)
      "mv" -> handleMv(args)
      "grep" -> handleGrep(args)
      "find" -> handleFind(args)
      "df" -> handleDf(args)
      "free" -> handleFree(args)
      "top", "htop" -> handleTop()
      "ps" -> handlePs()
      "kill" -> handleKill(args)
      "uptime" -> handleUptime()
      "dmesg" -> handleDmesg()
      "neofetch", "fastfetch" -> handleNeofetch()
      "ip", "ifconfig" -> handleNetConfig()
      "ping" -> handlePing(args)
      "curl", "wget" -> handleCurl(args)
      "env", "export" -> handleEnv(args)
      "history" -> handleHistory()
      "date" -> listOf(TerminalLine(java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy").format(java.util.Date()), TerminalWhite))
      "hostname" -> listOf(TerminalLine(environment["HOSTNAME"] ?: "linux-vm", TerminalWhite))
      "qemu-img" -> handleQemuImg(args)
      "apk" -> handleApk(args)
      "rc-service" -> handleRcService(args)
      "rc-update" -> handleRcUpdate(args)
      "lbu" -> handleLbu(args)
      "setup-alpine" -> handleSetupAlpine()
      "ssh", "sshd" -> handleSsh(args)
      "info", "system_reset", "system_powerdown", "savevm", "loadvm", "delvm", "screendump", "cont", "stop" -> handleQemuMonitorCmd(cmd, args)
      "apt", "apt-get" -> handleApt(args)
      "pacman" -> handlePacman(args)
      "xbps-install", "xbps-query" -> handleXbps(args)
      "dnf", "yum" -> handleDnf(args)
      "python3", "python" -> handlePython(args)
      "pip", "pip3" -> handlePip(args)
      "node", "nodejs" -> handleNode(args)
      "npm", "npx" -> handleNpm(args)
      "go" -> handleGo(args)
      "rustc", "cargo" -> handleRust(cmd, args)
      "gcc", "g++", "clang" -> handleGcc(cmd, args)
      "make" -> handleMake(args)
      "sh", "bash" -> handleScript(args)
      "shutdown", "poweroff" -> listOf(
        TerminalLine("[ OK ] Stopped target Multi-User System.", TerminalDimText),
        TerminalLine("[ OK ] Reached target System Power Off.", TerminalYellow),
        TerminalLine("System halted. Virtual machine powering down.", TerminalGreen, isSystem = true)
      )
      "reboot" -> listOf(
        TerminalLine("[ OK ] Unmounted all filesystems.", TerminalDimText),
        TerminalLine("Restarting virtual system...", TerminalYellow, isSystem = true)
      )
      else -> {
        // Check if executable file in current dir or PATH
        val fileContent = readFile(cmd)
        if (fileContent != null) {
          listOf(TerminalLine("Executing script: $cmd", TerminalDimText)) +
            fileContent.lines().map { TerminalLine(it, TerminalWhite) }
        } else {
          listOf(
            TerminalLine("sh: $cmd: command not found", TerminalRed, isError = true),
            TerminalLine("Type 'help' to see available commands or install tools using package manager.", TerminalDimText)
          )
        }
      }
    }
  }

  private fun parseTokens(line: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = java.lang.StringBuilder()
    var inQuotes = false
    var quoteChar = ' '

    for (c in line) {
      if (inQuotes) {
        if (c == quoteChar) {
          inQuotes = false
        } else {
          current.append(c)
        }
      } else {
        if (c == '"' || c == '\'') {
          inQuotes = true
          quoteChar = c
        } else if (c.isWhitespace()) {
          if (current.isNotEmpty()) {
            tokens.add(current.toString())
            current.setLength(0)
          }
        } else {
          current.append(c)
        }
      }
    }
    if (current.isNotEmpty()) {
      tokens.add(current.toString())
    }
    return tokens
  }

  private fun handleHelp(): List<TerminalLine> {
    val distro = DistroCatalog.DISTROS.firstOrNull { it.id == vm.distroId }
    val pkgMgr = distro?.packageManager ?: "apk"
    return listOf(
      TerminalLine("=== QEMU LINUX VIRTUAL MACHINE SHELL ===", TerminalCyan),
      TerminalLine("File & Directory:", TerminalYellow),
      TerminalLine("  ls, cd, pwd, cat, touch, mkdir, rm, cp, mv, grep, find", TerminalWhite),
      TerminalLine("Text Editing:", TerminalYellow),
      TerminalLine("  nano <file>      (Opens in-terminal visual nano text editor)", TerminalGreen),
      TerminalLine("  echo 'text' > file.txt / echo 'text' >> file.txt", TerminalWhite),
      TerminalLine("System & Telemetry:", TerminalYellow),
      TerminalLine("  uname, whoami, id, df, free, top/htop, ps, uptime, dmesg, date", TerminalWhite),
      TerminalLine("  neofetch         (Display system specs & ASCII logo banner)", TerminalGreen),
      TerminalLine("Network Tools:", TerminalYellow),
      TerminalLine("  ip, ifconfig, ping <host>, curl <url>, wget <url>", TerminalWhite),
      TerminalLine("Package Management ($pkgMgr):", TerminalYellow),
      TerminalLine("  $pkgMgr update / install <pkg> / search / remove", TerminalWhite),
      TerminalLine("Programming runtimes:", TerminalYellow),
      TerminalLine("  python3 -c '<code>' / python3 script.py", TerminalWhite),
      TerminalLine("  node -e '<code>' / node script.js", TerminalWhite),
      TerminalLine("VM Management:", TerminalYellow),
      TerminalLine("  qemu-img info /dev/vda, shutdown, reboot, clear, history, env", TerminalWhite)
    )
  }

  private fun handleLs(args: List<String>): List<TerminalLine> {
    val targetDir = if (args.isNotEmpty() && !args.last().startsWith("-")) args.last() else currentDir
    val showAll = args.any { it.contains("a") }
    val longFormat = args.any { it.contains("l") }

    val entries = listFiles(targetDir)
    if (entries.isEmpty()) return emptyList()

    val results = mutableListOf<TerminalLine>()
    if (longFormat) {
      results.add(TerminalLine("total ${entries.size * 4}", TerminalDimText))
      if (showAll) {
        results.add(TerminalLine("drwxr-xr-x 2 root root 4096 .", TerminalCyan))
        results.add(TerminalLine("drwxr-xr-x 4 root root 4096 ..", TerminalCyan))
      }
      for ((name, isDir) in entries) {
        if (!showAll && name.startsWith(".")) continue
        val perms = if (isDir) "drwxr-xr-x 2 root root 4096" else "-rw-r--r-- 1 root root ${name.length * 128}"
        val color = if (isDir) TerminalCyan else TerminalWhite
        results.add(TerminalLine("$perms $name", color))
      }
    } else {
      val names = entries.filter { showAll || !it.first.startsWith(".") }
        .map { (name, isDir) -> if (isDir) "$name/" else name }
      results.add(TerminalLine(names.joinToString("  "), TerminalCyan))
    }
    return results
  }

  private fun handleCd(args: List<String>): List<TerminalLine> {
    val dest = if (args.isEmpty()) environment["HOME"] ?: "/root" else args[0]
    val resolved = resolvePath(dest)
    if (resolved == "/" || virtualFs[resolved] == "[DIR]" || listFiles(resolved).isNotEmpty()) {
      currentDir = resolved
      return emptyList()
    }
    return listOf(TerminalLine("cd: $dest: No such file or directory", TerminalRed, isError = true))
  }

  private fun handleCat(args: List<String>): List<TerminalLine> {
    if (args.isEmpty()) return listOf(TerminalLine("cat: missing operand", TerminalRed, isError = true))
    val lines = mutableListOf<TerminalLine>()
    for (f in args) {
      val content = readFile(f)
      if (content != null) {
        content.lines().forEach { lines.add(TerminalLine(it, TerminalWhite)) }
      } else {
        lines.add(TerminalLine("cat: $f: No such file or directory", TerminalRed, isError = true))
      }
    }
    return lines
  }

  private fun handleEcho(args: List<String>): List<TerminalLine> {
    val text = args.joinToString(" ")
    // Expand env vars
    var expanded = text
    environment.forEach { (k, v) -> expanded = expanded.replace("$$k", v) }
    return listOf(TerminalLine(expanded, TerminalWhite))
  }

  private fun handleMkdir(args: List<String>): List<TerminalLine> {
    if (args.isEmpty()) return listOf(TerminalLine("mkdir: missing operand", TerminalRed, isError = true))
    for (arg in args) {
      if (!arg.startsWith("-")) {
        val path = resolvePath(arg)
        ensureDir(path)
      }
    }
    return emptyList()
  }

  private fun handleTouch(args: List<String>): List<TerminalLine> {
    if (args.isEmpty()) return listOf(TerminalLine("touch: missing file operand", TerminalRed, isError = true))
    for (arg in args) {
      val path = resolvePath(arg)
      if (!virtualFs.containsKey(path)) {
        writeFile(path, "")
      }
    }
    return emptyList()
  }

  private fun handleRm(args: List<String>): List<TerminalLine> {
    val targets = args.filter { !it.startsWith("-") }
    if (targets.isEmpty()) return listOf(TerminalLine("rm: missing operand", TerminalRed, isError = true))
    for (t in targets) {
      val path = resolvePath(t)
      val keysToRemove = virtualFs.keys.filter { it == path || it.startsWith("$path/") }
      if (keysToRemove.isEmpty()) {
        return listOf(TerminalLine("rm: cannot remove '$t': No such file or directory", TerminalRed, isError = true))
      }
      keysToRemove.forEach { virtualFs.remove(it) }
    }
    return emptyList()
  }

  private fun handleCp(args: List<String>): List<TerminalLine> {
    if (args.size < 2) return listOf(TerminalLine("cp: missing destination file operand", TerminalRed, isError = true))
    val src = resolvePath(args[0])
    val dest = resolvePath(args[1])
    val content = readFile(src)
      ?: return listOf(TerminalLine("cp: cannot stat '$src': No such file or directory", TerminalRed, isError = true))
    writeFile(dest, content)
    return emptyList()
  }

  private fun handleMv(args: List<String>): List<TerminalLine> {
    if (args.size < 2) return listOf(TerminalLine("mv: missing destination operand", TerminalRed, isError = true))
    val src = resolvePath(args[0])
    val dest = resolvePath(args[1])
    val content = readFile(src)
      ?: return listOf(TerminalLine("mv: cannot stat '$src': No such file or directory", TerminalRed, isError = true))
    writeFile(dest, content)
    virtualFs.remove(src)
    return emptyList()
  }

  private fun handleGrep(args: List<String>): List<TerminalLine> {
    if (args.size < 2) return listOf(TerminalLine("grep: pattern and file required", TerminalRed, isError = true))
    val pattern = args[0]
    val file = resolvePath(args[1])
    val content = readFile(file)
      ?: return listOf(TerminalLine("grep: $file: No such file or directory", TerminalRed, isError = true))
    val matches = content.lines().filter { it.contains(pattern, ignoreCase = true) }
    return matches.map { TerminalLine(it, TerminalYellow) }
  }

  private fun handleFind(args: List<String>): List<TerminalLine> {
    val dir = if (args.isNotEmpty() && !args[0].startsWith("-")) resolvePath(args[0]) else currentDir
    val pattern = if (args.contains("-name")) args.getOrNull(args.indexOf("-name") + 1)?.removeSurrounding("\"") else null

    val results = mutableListOf<TerminalLine>()
    virtualFs.keys.filter { it.startsWith(dir) }.forEach { p ->
      if (pattern == null || p.contains(pattern.replace("*", ""))) {
        results.add(TerminalLine(p, TerminalCyan))
      }
    }
    return results
  }

  private fun handleUname(args: List<String>): List<TerminalLine> {
    val all = args.contains("-a")
    return if (all) {
      listOf(
        TerminalLine(
          "Linux ${environment["HOSTNAME"]} 6.6.21-qemu-virt #1 SMP PREEMPT Fri Aug 25 2026 ${vm.arch} GNU/Linux",
          TerminalGreen
        )
      )
    } else {
      listOf(TerminalLine("Linux", TerminalGreen))
    }
  }

  private fun handleDf(args: List<String>): List<TerminalLine> {
    val totalK = (vm.diskSizeGb * 1024 * 1024).toLong()
    val usedK = (totalK * 0.22).toLong()
    val availK = totalK - usedK
    return listOf(
      TerminalLine("Filesystem     1K-blocks      Used Available Use% Mounted on", TerminalYellow),
      TerminalLine("/dev/vda1       ${totalK.toString().padStart(9)} ${usedK.toString().padStart(9)} ${availK.toString().padStart(9)}  22% /", TerminalWhite),
      TerminalLine("devtmpfs           241152         0    241152   0% /dev", TerminalDimText),
      TerminalLine("tmpfs              ${(vm.ramMb * 512).toString().padStart(6)}         0    ${(vm.ramMb * 512).toString().padStart(6)}   0% /tmp", TerminalDimText)
    )
  }

  private fun handleFree(args: List<String>): List<TerminalLine> {
    val total = vm.ramMb
    val used = (total * 0.28).toInt()
    val free = total - used - 42
    val buffCache = 42
    return listOf(
      TerminalLine("               total        used        free      shared  buff/cache   available", TerminalYellow),
      TerminalLine("Mem:       ${total.toString().padStart(9)}   ${used.toString().padStart(9)}   ${free.toString().padStart(9)}           0          ${buffCache.toString().padStart(2)}   ${(free + buffCache).toString().padStart(9)}", TerminalWhite),
      TerminalLine("Swap:              0           0           0", TerminalDimText)
    )
  }

  private fun handleTop(): List<TerminalLine> {
    val distro = DistroCatalog.DISTROS.firstOrNull { it.id == vm.distroId }
    val initProc = if (distro?.initSystem == "OpenRC") "init" else "systemd"
    return listOf(
      TerminalLine("top - 12:00:00 up ${vm.uptimeSeconds / 60} min, 1 user, load average: 0.08, 0.04, 0.01", TerminalCyan),
      TerminalLine("Tasks: 24 total, 1 running, 23 sleeping, 0 stopped, 0 zombie", TerminalWhite),
      TerminalLine("%Cpu(s):  1.2 us,  0.8 sy,  0.0 ni, 97.9 id,  0.1 wa,  0.0 hi", TerminalYellow),
      TerminalLine("MiB Mem : ${vm.ramMb}.0 total,  ${(vm.ramMb * 0.65).toInt()}.0 free,  ${(vm.ramMb * 0.28).toInt()}.0 used", TerminalWhite),
      TerminalLine("  PID USER      PR  NI    VIRT    RES    SHR S  %CPU  %MEM     TIME+ COMMAND", TerminalYellow),
      TerminalLine("    1 root      20   0   18420   6120   4200 S   0.0   0.6   0:01.12 $initProc", TerminalWhite),
      TerminalLine("  102 root      20   0    8200   2400   1800 S   0.0   0.2   0:00.08 sshd", TerminalWhite),
      TerminalLine("  245 root      20   0   12400   4100   3200 S   0.0   0.4   0:00.14 crond", TerminalWhite),
      TerminalLine("  320 root      20   0    5200   1800   1400 S   0.0   0.2   0:00.04 syslogd", TerminalWhite),
      TerminalLine("  412 root      20   0    6400   2100   1600 R   1.2   0.2   0:00.22 top", TerminalGreen)
    )
  }

  private fun handlePs(): List<TerminalLine> {
    val distro = DistroCatalog.DISTROS.firstOrNull { it.id == vm.distroId }
    val initProc = if (distro?.initSystem == "OpenRC") "/sbin/init" else "/lib/systemd/systemd"
    return listOf(
      TerminalLine("  PID TTY          TIME CMD", TerminalYellow),
      TerminalLine("    1 ?        00:00:01 $initProc", TerminalWhite),
      TerminalLine("   84 ?        00:00:00 /usr/sbin/sshd -D", TerminalWhite),
      TerminalLine("  112 ?        00:00:00 /sbin/syslogd -n", TerminalWhite),
      TerminalLine("  204 ttyS0    00:00:00 /bin/sh", TerminalGreen),
      TerminalLine("  310 ttyS0    00:00:00 ps", TerminalWhite)
    )
  }

  private fun handleKill(args: List<String>): List<TerminalLine> {
    if (args.isEmpty()) return listOf(TerminalLine("kill: usage: kill [-s sigspec | -n signum | -sigspec] pid | jobspec ...", TerminalRed, isError = true))
    return listOf(TerminalLine("Process ${args.last()} terminated.", TerminalGreen))
  }

  private fun handleUptime(): List<TerminalLine> {
    val hours = vm.uptimeSeconds / 3600
    val mins = (vm.uptimeSeconds % 3600) / 60
    val secs = vm.uptimeSeconds % 60
    return listOf(
      TerminalLine(
        " 12:00:00 up ${hours}h ${mins}m ${secs}s, 1 user, load average: 0.05, 0.03, 0.00",
        TerminalWhite
      )
    )
  }

  private fun handleDmesg(): List<TerminalLine> {
    return listOf(
      TerminalLine("[    0.000000] Linux version 6.6.21-qemu-virt (${vm.arch})", TerminalCyan),
      TerminalLine("[    0.000000] Command line: ${vm.kernelParams}", TerminalDimText),
      TerminalLine("[    0.004120] smp: Bringing up secondary CPUs ...", TerminalWhite),
      TerminalLine("[    0.008400] smp: Brought up 1 node, ${vm.cpuCores} CPUs", TerminalGreen),
      TerminalLine("[    0.042100] Memory: ${vm.ramMb * 1024}K/${vm.ramMb * 1024}K available", TerminalWhite),
      TerminalLine("[    0.110200] virtio_blk virtio0: [vda] ${vm.diskSizeGb} GiB disk", TerminalYellow),
      TerminalLine("[    0.142000] virtio_net virtio1 eth0: link up, 1000Mbps", TerminalGreen),
      TerminalLine("[    0.312000] EXT4-fs (vda1): mounted filesystem with ordered data mode.", TerminalWhite)
    )
  }

  private fun handleNeofetch(): List<TerminalLine> {
    val distro = DistroCatalog.DISTROS.firstOrNull { it.id == vm.distroId }
    val name = distro?.name ?: vm.name
    val logoLines = (distro?.asciiLogo ?: """
    .--.
   |o_o |
   |:_/ |
  //   \ \
 (|     | )
/'\_   _/`\
\___)=(___/
    """).trimIndent().lines()

    val infoLines = listOf(
      "${environment["USER"]}@${environment["HOSTNAME"]}",
      "-------------------------",
      "OS: $name ${vm.arch}",
      "Host: QEMU Virtual Machine (${vm.arch})",
      "Kernel: 6.6.21-qemu-virt",
      "Uptime: ${vm.uptimeSeconds / 60} mins",
      "Packages: ${installedPackages.size} (${distro?.packageManager ?: "apk"})",
      "Shell: /bin/sh (interactive)",
      "Terminal: VT100 / Serial mon:stdio",
      "CPU: QEMU Virtual CPU (${vm.cpuCores} cores @ 2.40GHz)",
      "GPU: VirtIO VGA Acceleration",
      "Memory: ${(vm.ramMb * 0.28).toInt()}MiB / ${vm.ramMb}MiB",
      "Disk: ${(vm.diskSizeGb * 0.22).toInt()}GiB / ${vm.diskSizeGb}GiB"
    )

    val combined = mutableListOf<TerminalLine>()
    val maxRows = maxOf(logoLines.size, infoLines.size)
    for (i in 0 until maxRows) {
      val logo = logoLines.getOrElse(i) { "".padEnd(16) }.padEnd(16)
      val info = infoLines.getOrElse(i) { "" }
      val color = if (i == 0) TerminalCyan else if (i == 1) TerminalDimText else TerminalWhite
      combined.add(TerminalLine("$logo  $info", color))
    }
    return combined
  }

  private fun handleNetConfig(): List<TerminalLine> {
    return listOf(
      TerminalLine("1: lo: <LOOPBACK,UP,LOWER_UP> mtu 65536 qdisc noqueue state UNKNOWN", TerminalCyan),
      TerminalLine("    inet 127.0.0.1/8 scope host lo", TerminalWhite),
      TerminalLine("2: eth0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc pfifo_fast state UP", TerminalCyan),
      TerminalLine("    link/ether 52:54:00:12:34:56 brd ff:ff:ff:ff:ff:ff", TerminalDimText),
      TerminalLine("    inet 10.0.2.15/24 brd 10.0.2.255 scope global dynamic eth0", TerminalGreen),
      TerminalLine("    inet6 fe80::5054:ff:fe12:3456/64 scope link", TerminalDimText)
    )
  }

  private fun handlePing(args: List<String>): List<TerminalLine> {
    val host = if (args.isNotEmpty()) args.first { !it.startsWith("-") } else "8.8.8.8"
    return listOf(
      TerminalLine("PING $host ($host) 56(84) bytes of data.", TerminalCyan),
      TerminalLine("64 bytes from $host: icmp_seq=1 ttl=116 time=18.4 ms", TerminalWhite),
      TerminalLine("64 bytes from $host: icmp_seq=2 ttl=116 time=14.2 ms", TerminalWhite),
      TerminalLine("64 bytes from $host: icmp_seq=3 ttl=116 time=16.8 ms", TerminalWhite),
      TerminalLine("--- $host ping statistics ---", TerminalDimText),
      TerminalLine("3 packets transmitted, 3 received, 0% packet loss, time 2003ms", TerminalGreen)
    )
  }

  private fun handleCurl(args: List<String>): List<TerminalLine> {
    val url = args.firstOrNull { it.startsWith("http://") || it.startsWith("https://") } ?: "https://api.github.com"
    return listOf(
      TerminalLine("HTTP/1.1 200 OK", TerminalGreen),
      TerminalLine("Server: nginx/1.24.0", TerminalDimText),
      TerminalLine("Content-Type: application/json; charset=utf-8", TerminalDimText),
      TerminalLine("Content-Length: 148", TerminalDimText),
      TerminalLine("", TerminalWhite),
      TerminalLine("{\"status\":\"success\",\"message\":\"Connected to $url via QEMU SLIRP user-net\",\"timestamp\":${System.currentTimeMillis()}}", TerminalCyan)
    )
  }

  private fun handleEnv(args: List<String>): List<TerminalLine> {
    if (args.isNotEmpty() && args[0].contains("=")) {
      val pair = args[0].split("=", limit = 2)
      environment[pair[0]] = pair[1]
      return listOf(TerminalLine("Exported ${pair[0]}=${pair[1]}", TerminalGreen))
    }
    return environment.map { (k, v) -> TerminalLine("$k=$v", TerminalWhite) }
  }

  private fun handleHistory(): List<TerminalLine> {
    return commandHistory.mapIndexed { idx, cmd ->
      TerminalLine("${(idx + 1).toString().padStart(4)}  $cmd", TerminalDimText)
    }
  }

  private fun handleQemuImg(args: List<String>): List<TerminalLine> {
    return listOf(
      TerminalLine("image: ${vm.diskPath}", TerminalCyan),
      TerminalLine("file format: ${vm.diskFormat}", TerminalWhite),
      TerminalLine("virtual size: ${vm.diskSizeGb} GiB (${(vm.diskSizeGb * 1024 * 1024 * 1024).toLong()} bytes)", TerminalGreen),
      TerminalLine("disk size: ${(vm.diskSizeGb * 0.22).toInt()} GiB (dynamically allocated)", TerminalWhite),
      TerminalLine("cluster_size: 65536", TerminalDimText),
      TerminalLine("Format specific information:", TerminalDimText),
      TerminalLine("    compat: 1.1", TerminalDimText),
      TerminalLine("    compression type: zlib", TerminalDimText)
    )
  }

  private fun handleApk(args: List<String>): List<TerminalLine> {
    if (args.isEmpty()) return listOf(TerminalLine("apk-tools 2.14.0, usage: apk <command> [options]", TerminalYellow))
    val sub = args[0]
    return when (sub) {
      "update" -> listOf(
        TerminalLine("fetch http://dl-cdn.alpinelinux.org/alpine/v3.20/main/x86_64/APKINDEX.tar.gz", TerminalCyan),
        TerminalLine("fetch http://dl-cdn.alpinelinux.org/alpine/v3.20/community/x86_64/APKINDEX.tar.gz", TerminalCyan),
        TerminalLine("v3.20.0-12-g3fa492 [http://dl-cdn.alpinelinux.org/alpine/v3.20/main]", TerminalDimText),
        TerminalLine("OK: 14500 distinct packages available", TerminalGreen, isSuccess = true)
      )
      "add" -> {
        val pkgs = args.drop(1)
        if (pkgs.isEmpty()) return listOf(TerminalLine("apk add: specify packages to install", TerminalRed, isError = true))
        pkgs.forEach { installedPackages.add(it) }
        listOf(
          TerminalLine("(1/${pkgs.size}) Installing ${pkgs.joinToString(", ")}...", TerminalCyan),
          TerminalLine("Executing busybox-1.36.1-r2.trigger", TerminalDimText),
          TerminalLine("OK: ${installedPackages.size} packages, ${vm.diskSizeGb.toInt() * 40} MiB in 850 files", TerminalGreen, isSuccess = true)
        )
      }
      "del" -> {
        val pkgs = args.drop(1)
        pkgs.forEach { installedPackages.remove(it) }
        listOf(TerminalLine("OK: ${pkgs.size} packages removed", TerminalGreen, isSuccess = true))
      }
      "search" -> {
        val q = args.drop(1).joinToString(" ")
        listOf(
          TerminalLine("alpine-base - Meta package for Alpine base system", TerminalCyan),
          TerminalLine("busybox-extras - Additional BusyBox applets", TerminalWhite),
          TerminalLine("curl - Command line tool for transferring data with URL syntax", TerminalWhite),
          TerminalLine("git - Fast, scalable, distributed revision control system", TerminalWhite),
          TerminalLine("htop - Interactive process viewer", TerminalWhite),
          TerminalLine("neofetch - Fast CLI system information tool", TerminalWhite),
          TerminalLine("nodejs - JavaScript runtime built on V8", TerminalWhite),
          TerminalLine("openssh - Free version of the SSH connectivity tools", TerminalWhite),
          TerminalLine("python3 - High-level scripting language", TerminalWhite)
        ).filter { it.text.contains(q, ignoreCase = true) }
      }
      "info" -> listOf(TerminalLine("Installed packages: ${installedPackages.joinToString(" ")}", TerminalWhite))
      else -> listOf(TerminalLine("apk: unknown action '$sub'", TerminalRed, isError = true))
    }
  }

  private fun handleRcService(args: List<String>): List<TerminalLine> {
    if (args.isEmpty()) {
      return listOf(TerminalLine("Usage: rc-service <service> <start|stop|restart|status>", TerminalYellow))
    }
    val service = args[0]
    val action = args.getOrNull(1) ?: "status"
    return when (action) {
      "start" -> listOf(
        TerminalLine(" * Starting $service ...", TerminalCyan),
        TerminalLine(" [ ok ]", TerminalGreen)
      )
      "stop" -> listOf(
        TerminalLine(" * Stopping $service ...", TerminalCyan),
        TerminalLine(" [ ok ]", TerminalGreen)
      )
      "restart" -> listOf(
        TerminalLine(" * Stopping $service ...", TerminalCyan),
        TerminalLine(" [ ok ]", TerminalGreen),
        TerminalLine(" * Starting $service ...", TerminalCyan),
        TerminalLine(" [ ok ]", TerminalGreen)
      )
      "status" -> listOf(
        TerminalLine("status: started", TerminalGreen),
        TerminalLine("service $service is running (pid ${100 + service.hashCode() % 800})", TerminalWhite)
      )
      else -> listOf(TerminalLine("rc-service: unknown action '$action'", TerminalRed, isError = true))
    }
  }

  private fun handleRcUpdate(args: List<String>): List<TerminalLine> {
    if (args.isEmpty() || args[0] == "show") {
      return listOf(
        TerminalLine("               boot | bootmisc hostname modules sysctl", TerminalDimText),
        TerminalLine("            default | local networking sshd crond", TerminalGreen),
        TerminalLine("           shutdown | killprocs mount-ro savecache", TerminalDimText)
      )
    }
    val action = args[0]
    val service = args.getOrNull(1) ?: "sshd"
    val runlevel = args.getOrNull(2) ?: "default"
    return listOf(
      TerminalLine(" * service $service added to runlevel $runlevel", TerminalGreen)
    )
  }

  private fun handleLbu(args: List<String>): List<TerminalLine> {
    val action = args.firstOrNull() ?: "status"
    return when (action) {
      "commit" -> listOf(
        TerminalLine("Saving 3 modified files to /media/vda/cache/localhost.apkovl.tar.gz", TerminalCyan),
        TerminalLine("Archive: /media/vda/cache/localhost.apkovl.tar.gz created successfully.", TerminalGreen, isSuccess = true)
      )
      "status" -> listOf(
        TerminalLine("A /etc/apk/world", TerminalCyan),
        TerminalLine("M /etc/network/interfaces", TerminalYellow),
        TerminalLine("M /etc/ssh/sshd_config", TerminalYellow)
      )
      else -> listOf(TerminalLine("Alpine Local Backup (lbu) v3.20.0. Usage: lbu <commit|status|package>", TerminalYellow))
    }
  }

  private fun handleSetupAlpine(): List<TerminalLine> {
    return listOf(
      TerminalLine("=== Alpine Linux Setup Wizard (vmConsole Profile) ===", TerminalCyan),
      TerminalLine("Keyboard layout: us [default]", TerminalWhite),
      TerminalLine("System hostname: ${environment["HOSTNAME"]}", TerminalWhite),
      TerminalLine("Network interface eth0: configured via DHCP (10.0.2.15/24)", TerminalGreen),
      TerminalLine("Timezone: UTC", TerminalWhite),
      TerminalLine("Proxy: none", TerminalWhite),
      TerminalLine("APK mirror: http://dl-cdn.alpinelinux.org/alpine/v3.20/main", TerminalWhite),
      TerminalLine("SSH daemon: OpenSSH started on port 22 (SLiRP hostfwd 2222 -> 22)", TerminalGreen),
      TerminalLine("Disk storage: /dev/vda (VirtIO QCOW2) mounted rw", TerminalGreen),
      TerminalLine("Alpine Linux setup complete. System ready for development.", TerminalGreen, isSuccess = true)
    )
  }

  private fun handleSsh(args: List<String>): List<TerminalLine> {
    return listOf(
      TerminalLine("OpenSSH_9.7p1, OpenSSL 3.3.1 (Alpine Linux)", TerminalCyan),
      TerminalLine("SSH Server is ACTIVE and listening on port 22.", TerminalGreen),
      TerminalLine("QEMU SLiRP User-Mode Port Forwarding: 127.0.0.1:2222 -> Guest:22", TerminalYellow),
      TerminalLine("Connect from host/termux: ssh -p 2222 ${environment["USER"]}@127.0.0.1", TerminalCyan)
    )
  }

  private fun handleQemuMonitorCmd(cmd: String, args: List<String>): List<TerminalLine> {
    val fullCmd = if (args.isEmpty()) cmd else "$cmd ${args.joinToString(" ")}"
    return when {
      fullCmd.startsWith("info cpus") -> listOf(
        TerminalLine("* CPU #0: pc=0x00000000fffffff0 thread_id=1420 (running)", TerminalGreen),
        if (vm.cpuCores > 1) TerminalLine("  CPU #1: pc=0x00000000fffffff0 thread_id=1421 (running)", TerminalGreen) else TerminalLine("", TerminalWhite)
      ).filter { it.text.isNotBlank() }
      fullCmd.startsWith("info block") -> listOf(
        TerminalLine("virtio0 (#block104): ${vm.diskPath} (${vm.diskFormat})", TerminalCyan),
        TerminalLine("    ro=0, drv=${vm.diskFormat}, encrypted=0, bps=0, ops=142", TerminalWhite),
        if (vm.isoPath.isNotBlank()) TerminalLine("ide1-cd0: ${vm.isoPath} (raw, ro)", TerminalYellow) else TerminalLine("", TerminalWhite)
      ).filter { it.text.isNotBlank() }
      fullCmd.startsWith("info kvm") -> listOf(
        TerminalLine("kvm support: disabled (using QEMU TCG dynamic binary translation for non-root Android user space)", TerminalYellow)
      )
      fullCmd.startsWith("info status") -> listOf(
        TerminalLine("VM status: running", TerminalGreen)
      )
      fullCmd.startsWith("info registers") -> listOf(
        TerminalLine("RAX=0000000000000000 RBX=0000000000000000 RCX=0000000000000000 RDX=0000000000000600", TerminalWhite),
        TerminalLine("RSI=0000000000000000 RDI=0000000000000000 RBP=0000000000000000 RSP=0000000000006fe0", TerminalWhite),
        TerminalLine("RIP=00000000000fd2e0 RFL=00000002 [-------] CPL=0 II=0 A20=1 SMM=0 HLT=0", TerminalDimText)
      )
      fullCmd.startsWith("info network") -> listOf(
        TerminalLine("net0: index=0,type=user,net=10.0.2.0,mask=255.255.255.0", TerminalCyan),
        TerminalLine(" \\ virtio-net-pci.0: index=0,type=nic,model=virtio-net-pci,macaddr=52:54:00:12:34:56", TerminalWhite)
      )
      fullCmd.startsWith("info mem") -> listOf(
        TerminalLine("0000000000000000-000000000009ffff 00000000000a0000 urw", TerminalDimText),
        TerminalLine("0000000000100000-000000001fffffff 000000001ff00000 urw (${vm.ramMb}M RAM)", TerminalGreen)
      )
      fullCmd.startsWith("info snapshots") -> listOf(
        TerminalLine("Snapshot list for block device '${vm.diskPath}':", TerminalCyan),
        TerminalLine("ID        TAG                 VM SIZE                DATE       VM CLOCK", TerminalYellow),
        TerminalLine("1         initial-boot           42M 2026-08-25 12:00:00   00:01:23.456", TerminalWhite)
      )
      fullCmd.startsWith("system_reset") -> listOf(
        TerminalLine("(qemu) System reset signal sent to guest CPU.", TerminalYellow, isSystem = true)
      )
      fullCmd.startsWith("system_powerdown") -> listOf(
        TerminalLine("(qemu) ACPI shutdown request dispatched to guest OS.", TerminalYellow, isSystem = true)
      )
      fullCmd.startsWith("stop") -> listOf(
        TerminalLine("(qemu) Virtual machine paused.", TerminalYellow, isSystem = true)
      )
      fullCmd.startsWith("cont") -> listOf(
        TerminalLine("(qemu) Virtual machine resumed.", TerminalGreen, isSuccess = true)
      )
      fullCmd.startsWith("savevm") -> {
        val snap = args.getOrNull(0) ?: "snap-${System.currentTimeMillis() % 10000}"
        listOf(
          TerminalLine("(qemu) Saving VM state to snapshot tag '$snap'...", TerminalCyan),
          TerminalLine("(qemu) Snapshot '$snap' committed to QCOW2 image successfully.", TerminalGreen, isSuccess = true)
        )
      }
      fullCmd.startsWith("loadvm") -> {
        val snap = args.getOrNull(0) ?: "1"
        listOf(
          TerminalLine("(qemu) Restoring VM state from snapshot tag '$snap'...", TerminalCyan),
          TerminalLine("(qemu) Restored state successfully.", TerminalGreen, isSuccess = true)
        )
      }
      else -> listOf(
        TerminalLine("QEMU 9.0.0 monitor - type 'help' or '?' for list of commands", TerminalCyan),
        TerminalLine("Supported monitor commands: info [cpus|block|kvm|status|registers|network|mem|snapshots], system_reset, system_powerdown, stop, cont, savevm, loadvm", TerminalDimText)
      )
    }
  }

  private fun handleApt(args: List<String>): List<TerminalLine> {
    if (args.isEmpty()) return listOf(TerminalLine("apt 2.6.1 (amd64) - commandline package manager", TerminalYellow))
    val sub = args[0]
    return when (sub) {
      "update" -> listOf(
        TerminalLine("Get:1 http://deb.debian.org/debian bookworm InRelease [151 kB]", TerminalCyan),
        TerminalLine("Get:2 http://deb.debian.org/debian bookworm-updates InRelease [55.4 kB]", TerminalCyan),
        TerminalLine("Reading package lists... Done", TerminalWhite),
        TerminalLine("Building dependency tree... Done", TerminalWhite),
        TerminalLine("All packages are up to date.", TerminalGreen, isSuccess = true)
      )
      "install" -> {
        val pkgs = args.drop(1).filter { !it.startsWith("-") }
        if (pkgs.isEmpty()) return listOf(TerminalLine("apt install: specify packages", TerminalRed, isError = true))
        pkgs.forEach { installedPackages.add(it) }
        listOf(
          TerminalLine("Reading package lists... Done", TerminalDimText),
          TerminalLine("The following NEW packages will be installed: ${pkgs.joinToString(" ")}", TerminalCyan),
          TerminalLine("Unpacking and configuring ${pkgs.joinToString(" ")}...", TerminalWhite),
          TerminalLine("Setting up ${pkgs.joinToString(" ")} (latest)...", TerminalWhite),
          TerminalLine("Processing triggers for man-db (2.11.2-2)...", TerminalDimText),
          TerminalLine("Installation complete.", TerminalGreen, isSuccess = true)
        )
      }
      "remove", "purge" -> {
        val pkgs = args.drop(1)
        pkgs.forEach { installedPackages.remove(it) }
        listOf(TerminalLine("Removing ${pkgs.joinToString(" ")}... Done.", TerminalGreen, isSuccess = true))
      }
      else -> listOf(TerminalLine("apt: command '$sub' completed.", TerminalWhite))
    }
  }

  private fun handlePacman(args: List<String>): List<TerminalLine> {
    return if (args.any { it.contains("S") }) {
      val pkg = args.lastOrNull { !it.startsWith("-") } ?: "system"
      installedPackages.add(pkg)
      listOf(
        TerminalLine(":: Synchronizing package databases...", TerminalCyan),
        TerminalLine(" core is up to date", TerminalDimText),
        TerminalLine(" extra is up to date", TerminalDimText),
        TerminalLine("resolving dependencies...", TerminalWhite),
        TerminalLine("Packages (1) $pkg-latest", TerminalYellow),
        TerminalLine(":: Proceed with installation? [Y/n] Y", TerminalDimText),
        TerminalLine("(1/1) checking keys in keyring", TerminalDimText),
        TerminalLine("(1/1) installing $pkg", TerminalGreen, isSuccess = true)
      )
    } else {
      listOf(TerminalLine("pacman v6.1.0 - Arch Linux package manager", TerminalCyan))
    }
  }

  private fun handleXbps(args: List<String>): List<TerminalLine> {
    val pkg = args.lastOrNull { !it.startsWith("-") } ?: "base-system"
    installedPackages.add(pkg)
    return listOf(
      TerminalLine("[*] Updating repository `https://repo-default.voidlinux.org/current' ...", TerminalCyan),
      TerminalLine("Package `$pkg' registered.", TerminalGreen, isSuccess = true)
    )
  }

  private fun handleDnf(args: List<String>): List<TerminalLine> {
    val pkg = args.lastOrNull { !it.startsWith("-") } ?: "fedora-release"
    installedPackages.add(pkg)
    return listOf(
      TerminalLine("Fedora 40 - x86_64 - Updates          1.2 MB/s |  14 MB     00:11", TerminalCyan),
      TerminalLine("Dependencies resolved. Installing $pkg...", TerminalWhite),
      TerminalLine("Complete!", TerminalGreen, isSuccess = true)
    )
  }

  private fun handlePython(args: List<String>): List<TerminalLine> {
    if (args.isEmpty()) {
      return listOf(
        TerminalLine("Python 3.12.3 (main, Apr 10 2024, 05:33:42) [GCC 13.2.1] on linux", TerminalGreen),
        TerminalLine("Type \"help\", \"copyright\", \"credits\" or \"license\" for more information.", TerminalDimText),
        TerminalLine(">>> print('Hello from Python on Linux VM!')", TerminalYellow),
        TerminalLine("Hello from Python on Linux VM!", TerminalWhite)
      )
    }
    if (args.contains("-c")) {
      val code = args.getOrNull(args.indexOf("-c") + 1) ?: "print('Python 3.12.3')"
      return try {
        listOf(TerminalLine("Python Executing: $code", TerminalDimText), TerminalLine(evaluateSimplePython(code), TerminalWhite))
      } catch (e: Exception) {
        listOf(TerminalLine("SyntaxError in Python: ${e.message}", TerminalRed, isError = true))
      }
    }
    val file = args.firstOrNull { it.endsWith(".py") }
    if (file != null) {
      val content = readFile(file)
        ?: return listOf(TerminalLine("python3: can't open file '$file': [Errno 2] No such file or directory", TerminalRed, isError = true))
      return listOf(TerminalLine("Running $file...", TerminalDimText)) +
        content.lines().map { TerminalLine(">> $it", TerminalCyan) }
    }
    return listOf(TerminalLine("Python 3.12.3 ready.", TerminalGreen))
  }

  private fun evaluateSimplePython(code: String): String {
    val clean = code.trim().removeSurrounding("\"").removeSurrounding("'")
    if (clean.startsWith("print(") && clean.endsWith(")")) {
      return clean.removePrefix("print(").removeSuffix(")").removeSurrounding("\"").removeSurrounding("'")
    }
    return "Output: $clean"
  }

  private fun handlePip(args: List<String>): List<TerminalLine> {
    if (args.isEmpty()) {
      return listOf(
        TerminalLine("pip 24.0 from /usr/lib/python3.12/site-packages/pip (python 3.12)", TerminalCyan),
        TerminalLine("Usage: pip <install|list|show|freeze> [options]", TerminalYellow)
      )
    }
    val action = args[0]
    return when (action) {
      "install" -> {
        val pkgs = args.drop(1).filter { !it.startsWith("-") }
        if (pkgs.isEmpty()) return listOf(TerminalLine("ERROR: You must give at least one requirement to install.", TerminalRed, isError = true))
        pkgs.forEach { installedPackages.add("python3-$it") }
        listOf(
          TerminalLine("Collecting ${pkgs.joinToString(", ")}...", TerminalCyan),
          TerminalLine("  Downloading wheel / tar.gz metadata: 100% [====================] done", TerminalDimText),
          TerminalLine("Installing collected packages: ${pkgs.joinToString(", ")}", TerminalWhite),
          TerminalLine("Successfully installed ${pkgs.joinToString("-1.0.0 ")}", TerminalGreen, isSuccess = true)
        )
      }
      "list", "freeze" -> listOf(
        TerminalLine("pip==24.0", TerminalWhite),
        TerminalLine("setuptools==69.5.1", TerminalWhite),
        TerminalLine("wheel==0.43.0", TerminalWhite),
        TerminalLine("requests==2.31.0", TerminalWhite),
        TerminalLine("flask==3.0.3", TerminalWhite)
      )
      else -> listOf(TerminalLine("pip: operation '$action' executed.", TerminalWhite))
    }
  }

  private fun handleNpm(args: List<String>): List<TerminalLine> {
    if (args.isEmpty()) return listOf(TerminalLine("npm 10.7.0 /usr/local/lib/node_modules/npm", TerminalCyan))
    val action = args[0]
    return when (action) {
      "install", "i", "add" -> {
        val pkgs = args.drop(1).filter { !it.startsWith("-") }
        val name = if (pkgs.isEmpty()) "dependencies from package.json" else pkgs.joinToString(" ")
        listOf(
          TerminalLine("npm warn deprecated har-validator@5.1.5: please upgrade", TerminalYellow),
          TerminalLine("added 42 packages, and audited 180 packages in 1.4s", TerminalGreen),
          TerminalLine("found 0 vulnerabilities", TerminalGreen, isSuccess = true)
        )
      }
      "run", "start", "test" -> {
        val script = args.getOrNull(1) ?: action
        listOf(
          TerminalLine("> app@1.0.0 $script", TerminalCyan),
          TerminalLine("> node index.js", TerminalDimText),
          TerminalLine("Server running at http://127.0.0.1:3000/", TerminalGreen, isSuccess = true)
        )
      }
      "-v", "--version" -> listOf(TerminalLine("10.7.0", TerminalGreen))
      else -> listOf(TerminalLine("npm: completed '$action'", TerminalWhite))
    }
  }

  private fun handleGo(args: List<String>): List<TerminalLine> {
    if (args.isEmpty()) {
      return listOf(
        TerminalLine("Go is a tool for managing Go source code.", TerminalCyan),
        TerminalLine("Usage: go <command> [arguments]", TerminalWhite),
        TerminalLine("The commands are: build, run, version, env, mod, get, test", TerminalYellow)
      )
    }
    val action = args[0]
    return when (action) {
      "version" -> listOf(
        TerminalLine("go version go1.22.4 linux/${vm.arch}", TerminalGreen, isSuccess = true)
      )
      "env" -> listOf(
        TerminalLine("GO111MODULE='on'", TerminalWhite),
        TerminalLine("GOARCH='${if (vm.arch == "aarch64") "arm64" else "amd64"}'", TerminalWhite),
        TerminalLine("GOOS='linux'", TerminalWhite),
        TerminalLine("GOPATH='/root/go'", TerminalWhite),
        TerminalLine("GOROOT='/usr/local/go'", TerminalWhite),
        TerminalLine("GOCACHE='/root/.cache/go-build'", TerminalDimText)
      )
      "run" -> {
        val file = args.drop(1).firstOrNull { it.endsWith(".go") }
        if (file != null) {
          val content = readFile(file)
            ?: return listOf(TerminalLine("go: cannot find '$file'", TerminalRed, isError = true))
          listOf(
            TerminalLine("[go compiler] Compiling $file (${if (vm.arch == "aarch64") "arm64" else "amd64"})...", TerminalDimText),
            TerminalLine("Hello, Go World from Linux VM! (Goroutines active)", TerminalGreen, isSuccess = true)
          )
        } else {
          listOf(
            TerminalLine("[go run] Compiling main.go...", TerminalDimText),
            TerminalLine("Server listening on :8080 (Go standard net/http)", TerminalGreen, isSuccess = true)
          )
        }
      }
      "build" -> {
        val target = args.drop(1).firstOrNull { !it.startsWith("-") } ?: "main"
        val outName = target.removeSuffix(".go")
        writeFile(outName, "#!/bin/sh\n# ELF 64-bit LSB executable, Go Build Output\necho 'Running compiled Go binary: $outName'")
        listOf(
          TerminalLine("[go build] Built binary '$outName' for linux/${if (vm.arch == "aarch64") "arm64" else "amd64"}", TerminalGreen, isSuccess = true)
        )
      }
      "mod" -> listOf(
        TerminalLine("go: creating new go.mod: module app", TerminalCyan),
        TerminalLine("go: to add module requirements and sums: go mod tidy", TerminalDimText)
      )
      else -> listOf(TerminalLine("go: command '$action' finished successfully.", TerminalGreen))
    }
  }

  private fun handleRust(cmd: String, args: List<String>): List<TerminalLine> {
    if (cmd == "rustc") {
      if (args.isEmpty() || args.contains("--version") || args.contains("-V")) {
        return listOf(TerminalLine("rustc 1.78.0 (9b00956e5 2024-04-29) (Arch Linux rust 1:1.78.0-1)", TerminalGreen, isSuccess = true))
      }
      val file = args.firstOrNull { it.endsWith(".rs") }
      return if (file != null) {
        val binName = file.removeSuffix(".rs")
        writeFile(binName, "#!/bin/sh\n# ELF 64-bit LSB pie executable, Rust binary\necho 'Rust binary $binName executing...'")
        listOf(
          TerminalLine("   Compiling $file v0.1.0", TerminalCyan),
          TerminalLine("    Finished `release` profile [optimized] in 0.82s", TerminalGreen, isSuccess = true)
        )
      } else {
        listOf(TerminalLine("rustc: no input files provided", TerminalRed, isError = true))
      }
    } else {
      // cargo
      if (args.isEmpty() || args.contains("--version")) {
        return listOf(TerminalLine("cargo 1.78.0 (54d8815d0 2024-03-26)", TerminalGreen))
      }
      val action = args[0]
      return when (action) {
        "new" -> {
          val proj = args.getOrNull(1) ?: "my_rust_app"
          writeFile("$proj/Cargo.toml", "[package]\nname = \"$proj\"\nversion = \"0.1.0\"\nedition = \"2021\"\n")
          writeFile("$proj/src/main.rs", "fn main() {\n    println!(\"Hello from Rust on Linux VM!\");\n}\n")
          listOf(
            TerminalLine("     Created binary (application) `$proj` package", TerminalGreen, isSuccess = true)
          )
        }
        "build", "check" -> listOf(
          TerminalLine("   Compiling app v0.1.0 (/root/app)", TerminalCyan),
          TerminalLine("    Finished `dev` profile [unoptimized + debuginfo] target(s) in 1.15s", TerminalGreen, isSuccess = true)
        )
        "run" -> listOf(
          TerminalLine("    Finished `dev` profile [unoptimized + debuginfo] target(s) in 0.04s", TerminalDimText),
          TerminalLine("     Running `target/debug/app`", TerminalCyan),
          TerminalLine("Hello from Rust on Linux VM! Memory-safe & zero-cost abstractions active.", TerminalGreen, isSuccess = true)
        )
        else -> listOf(TerminalLine("cargo $action complete.", TerminalGreen))
      }
    }
  }

  private fun handleGcc(cmd: String, args: List<String>): List<TerminalLine> {
    if (args.isEmpty() || args.contains("--version") || args.contains("-v")) {
      return listOf(TerminalLine("$cmd (GCC) 13.2.1 20230801 (Alpine 13.2.1_git20231014)", TerminalGreen, isSuccess = true))
    }
    val sourceFile = args.firstOrNull { it.endsWith(".c") || it.endsWith(".cpp") }
    return if (sourceFile != null) {
      val outIdx = args.indexOf("-o")
      val outName = if (outIdx != -1 && outIdx + 1 < args.size) args[outIdx + 1] else "a.out"
      writeFile(outName, "#!/bin/sh\n# ELF 64-bit executable\necho 'Executing compiled C/C++ binary: $outName'")
      listOf(
        TerminalLine("Compiled $sourceFile -> $outName [ELF 64-bit LSB executable, ${vm.arch}]", TerminalGreen, isSuccess = true)
      )
    } else {
      listOf(TerminalLine("$cmd: fatal error: no input files", TerminalRed, isError = true))
    }
  }

  private fun handleMake(args: List<String>): List<TerminalLine> {
    val target = args.firstOrNull() ?: "all"
    return listOf(
      TerminalLine("make: Entering directory '/root'", TerminalDimText),
      TerminalLine("gcc -O2 -Wall -c main.c -o main.o", TerminalWhite),
      TerminalLine("gcc main.o -o output_app", TerminalCyan),
      TerminalLine("make: Leaving directory '/root'", TerminalDimText),
      TerminalLine("make: Target '$target' built successfully.", TerminalGreen, isSuccess = true)
    )
  }

  private fun handleNode(args: List<String>): List<TerminalLine> {
    if (args.contains("-v") || args.contains("--version")) {
      return listOf(TerminalLine("v20.14.0", TerminalGreen))
    }
    if (args.contains("-e")) {
      val code = args.getOrNull(args.indexOf("-e") + 1) ?: "console.log('Node.js v20.14.0')"
      return listOf(
        TerminalLine("Node.js Executing: $code", TerminalDimText),
        TerminalLine(code.replace("console.log(", "").replace(")", "").removeSurrounding("'").removeSurrounding("\""), TerminalWhite)
      )
    }
    val jsFile = args.firstOrNull { it.endsWith(".js") || it.endsWith(".mjs") }
    if (jsFile != null) {
      val content = readFile(jsFile)
        ?: return listOf(TerminalLine("node: cannot open '$jsFile'", TerminalRed, isError = true))
      return listOf(TerminalLine("Running $jsFile...", TerminalDimText)) +
        content.lines().map { TerminalLine(">> $it", TerminalCyan) }
    }
    return listOf(
      TerminalLine("Welcome to Node.js v20.14.0 (V8 engine).", TerminalGreen),
      TerminalLine("Type \".help\" for more information.", TerminalDimText)
    )
  }

  private fun handleScript(args: List<String>): List<TerminalLine> {
    val file = args.firstOrNull { !it.startsWith("-") }
    if (file != null) {
      val content = readFile(file)
        ?: return listOf(TerminalLine("sh: cannot open '$file': No such file", TerminalRed, isError = true))
      val lines = mutableListOf<TerminalLine>()
      content.lines().forEach { l ->
        if (l.isNotBlank() && !l.startsWith("#")) {
          lines.addAll(executeCommand(l))
        }
      }
      return lines
    }
    return listOf(TerminalLine("sh interactive subshell initialized.", TerminalGreen))
  }

  private fun generateCpuInfo(): String {
    val sb = StringBuilder()
    for (i in 0 until vm.cpuCores) {
      sb.append("""
processor       : $i
vendor_id       : GenuineIntel
cpu family      : 6
model           : 142
model name      : QEMU Virtual CPU version 2.5+ (${vm.arch})
stepping        : 9
cpu MHz         : 2400.000
cache size      : 4096 KB
physical id     : 0
siblings        : ${vm.cpuCores}
core id         : $i
cpu cores       : ${vm.cpuCores}
flags           : fpu vme de pse tsc msr pae mce cx8 apic sep mtrr pge mca cmov pat pse36 clflush mmx fxsr sse sse2 ss ht syscall nx pdpe1gb rdtscp lm constant_tsc rep_good nopl xtopology cpuid tsc_known_freq pni pclmulqdq vmx ssse3 fma cx16 pcid sse4_1 sse4_2 x2apic movbe popcnt tsc_deadline_timer aes xsave avx f16c rdrand hypervisor lahf_lm abm 3dnowprefetch invpcid_single ssbd ibrs ibpb stibp tpr_shadow flexpriority ept vpid ept_ad fsgsbase tsc_adjust bmi1 avx2 smep bmi2 erms invpcid rdseed adx smap clflushopt xsaveopt xsavec xgetbv1 xsaves arat umip md_clear flush_l1d arch_capabilities
bogomips        : 4800.00
clflush size    : 64
cache_alignment : 64
address sizes   : 39 bits physical, 48 bits virtual

      """.trimIndent()).append("\n")
    }
    return sb.toString()
  }

  private fun generateMemInfo(): String {
    val total = vm.ramMb * 1024
    val free = (total * 0.65).toInt()
    val avail = (total * 0.72).toInt()
    return """
MemTotal:       ${total.toString().padStart(8)} kB
MemFree:        ${free.toString().padStart(8)} kB
MemAvailable:   ${avail.toString().padStart(8)} kB
Buffers:           14280 kB
Cached:           125400 kB
SwapCached:            0 kB
Active:           112400 kB
Inactive:          84200 kB
SwapTotal:             0 kB
SwapFree:              0 kB
Dirty:                32 kB
Writeback:             0 kB
AnonPages:         78400 kB
Mapped:            38200 kB
Shmem:              2400 kB
Slab:              28400 kB
    """.trimIndent()
  }
}
