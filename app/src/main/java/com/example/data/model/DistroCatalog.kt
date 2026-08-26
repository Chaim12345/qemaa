package com.example.data.model

data class DistroTemplate(
  val id: String,
  val name: String,
  val version: String,
  val category: String,
  val tag: String,
  val description: String,
  val defaultArch: String = "x86_64",
  val supportedArchs: List<String> = listOf("x86_64", "aarch64", "i386", "riscv64"),
  val defaultCpuCores: Int = 2,
  val defaultRamMb: Int = 512,
  val defaultDiskGb: Double = 4.0,
  val packageManager: String, // apk, apt, pacman, xbps, dnf
  val initSystem: String, // OpenRC, systemd, runit
  val defaultUser: String = "root",
  val defaultPass: String = "toor",
  val kernelArgs: String = "console=ttyS0 root=/dev/vda rw quiet",
  val downloadSizeMb: Int,
  val preinstalledTools: List<String>,
  val asciiLogo: String,
  val colorHex: Long
)

object DistroCatalog {
  val DISTROS = listOf(
    DistroTemplate(
      id = "alpine",
      name = "Alpine Linux (vmConsole)",
      version = "3.20.0 (virt/qemu-x86_64)",
      category = "Ultra Lightweight",
      tag = "vmConsole QEMU Core",
      description = "Security-oriented, ultra-fast Alpine Linux virtual machine based on musl libc, OpenRC, and BusyBox. Optimized for QEMU TCG user-mode emulation with instant 2-second boot and SLiRP networking.",
      defaultArch = "x86_64",
      defaultCpuCores = 1,
      defaultRamMb = 512,
      defaultDiskGb = 4.0,
      packageManager = "apk",
      initSystem = "OpenRC",
      defaultUser = "root",
      defaultPass = "alpine",
      kernelArgs = "console=ttyS0 root=/dev/vda rw modules=loop,squashfs,sd-mod,usb-storage quiet",
      downloadSizeMb = 16,
      preinstalledTools = listOf("busybox", "apk", "sshd", "wget", "curl", "openrc", "lbu"),
      colorHex = 0xFF0D597F,
      asciiLogo = """
   /\
  // \
 //   \
///    \
      """
    ),
    DistroTemplate(
      id = "debian",
      name = "Debian 12 'Bookworm'",
      version = "12.6 Standard",
      category = "General Purpose",
      tag = "Rock Solid",
      description = "The universal operating system. Stable, reliable with full APT repository ecosystem and GNU coreutils.",
      defaultArch = "x86_64",
      defaultCpuCores = 2,
      defaultRamMb = 1024,
      defaultDiskGb = 8.0,
      packageManager = "apt",
      initSystem = "systemd",
      defaultUser = "debian",
      defaultPass = "debian",
      kernelArgs = "console=ttyS0 root=/dev/vda1 rw quiet",
      downloadSizeMb = 340,
      preinstalledTools = listOf("apt", "dpkg", "bash", "systemd", "vim", "python3", "gcc"),
      colorHex = 0xFFA80030,
      asciiLogo = """
  _____
 / ____|
| (___
 \___ \
 ____) |
|_____/
      """
    ),
    DistroTemplate(
      id = "ubuntu",
      name = "Ubuntu Server 24.04 LTS",
      version = "24.04 LTS Noble Numbat",
      category = "Cloud & Server",
      tag = "Dev Standard",
      description = "Enterprise-grade cloud server with modern kernel, standard toolchain, docker readiness, and wide developer support.",
      defaultArch = "x86_64",
      defaultCpuCores = 2,
      defaultRamMb = 1024,
      defaultDiskGb = 10.0,
      packageManager = "apt",
      initSystem = "systemd",
      defaultUser = "ubuntu",
      defaultPass = "ubuntu",
      kernelArgs = "console=ttyS0 root=/dev/vda1 rw quiet",
      downloadSizeMb = 480,
      preinstalledTools = listOf("apt", "snapd", "netplan", "systemd", "python3", "git", "curl"),
      colorHex = 0xFFE95420,
      asciiLogo = """
         _
     ---(_)
 _/  ---  \
(_) |   |
  \  --- _/
     ---(_)
      """
    ),
    DistroTemplate(
      id = "arch",
      name = "Arch Linux",
      version = "Rolling Release",
      category = "Power User",
      tag = "Bleeding Edge",
      description = "A lightweight and flexible Linux distribution that tries to Keep It Simple. Pacman package manager with cutting-edge software.",
      defaultArch = "x86_64",
      defaultCpuCores = 2,
      defaultRamMb = 1024,
      defaultDiskGb = 8.0,
      packageManager = "pacman",
      initSystem = "systemd",
      defaultUser = "root",
      defaultPass = "arch",
      kernelArgs = "console=ttyS0 root=/dev/vda rw quiet",
      downloadSizeMb = 650,
      preinstalledTools = listOf("pacman", "systemd", "bash", "coreutils", "nano", "sudo"),
      colorHex = 0xFF1793D1,
      asciiLogo = """
      /\
     /  \
    /\   \
   /      \
  /   ,,   \
 /   |  |  -\
/_-''    ''-_\
      """
    ),
    DistroTemplate(
      id = "kali",
      name = "Kali Linux",
      version = "2024.2 Rolling",
      category = "Cybersecurity",
      tag = "Security Tools",
      description = "Advanced penetration testing and security auditing Linux distribution with integrated hacking tools.",
      defaultArch = "x86_64",
      defaultCpuCores = 2,
      defaultRamMb = 2048,
      defaultDiskGb = 12.0,
      packageManager = "apt",
      initSystem = "systemd",
      defaultUser = "kali",
      defaultPass = "kali",
      kernelArgs = "console=ttyS0 root=/dev/vda1 rw quiet",
      downloadSizeMb = 550,
      preinstalledTools = listOf("nmap", "metasploit", "wireshark", "hydra", "aircrack-ng", "python3"),
      colorHex = 0xFF557C93,
      asciiLogo = """
..............
            ..,
           ......
          .........
         ...........
      """
    ),
    DistroTemplate(
      id = "void",
      name = "Void Linux",
      version = "musl & glibc",
      category = "Independent",
      tag = "XBPS & runit",
      description = "Independent distribution created from scratch, featuring runit init system and fast XBPS package manager.",
      defaultArch = "x86_64",
      defaultCpuCores = 2,
      defaultRamMb = 512,
      defaultDiskGb = 4.0,
      packageManager = "xbps",
      initSystem = "runit",
      defaultUser = "root",
      defaultPass = "voidlinux",
      kernelArgs = "console=ttyS0 root=/dev/vda rw",
      downloadSizeMb = 90,
      preinstalledTools = listOf("xbps-install", "runit", "busybox", "bash", "nano"),
      colorHex = 0xFF478061,
      asciiLogo = """
    _______
  /         \
 /  \  ___/  \
|   \ \       |
 \   \/  /   /
  \_________/
      """
    ),
    DistroTemplate(
      id = "fedora",
      name = "Fedora CoreOS",
      version = "40 Cloud",
      category = "Container & Cloud",
      tag = "RPM / DNF",
      description = "Automatically updating, minimal, monolithic, container-focused operating system designed for server workloads.",
      defaultArch = "x86_64",
      defaultCpuCores = 2,
      defaultRamMb = 1536,
      defaultDiskGb = 8.0,
      packageManager = "dnf",
      initSystem = "systemd",
      defaultUser = "core",
      defaultPass = "core",
      kernelArgs = "console=ttyS0 root=/dev/vda1 rw quiet",
      downloadSizeMb = 420,
      preinstalledTools = listOf("podman", "systemd", "rpm-ostree", "bash", "curl"),
      colorHex = 0xFF51A2DA,
      asciiLogo = """
      /'''''''-.
     /          \
    /     .---.  \
   |     /  .  \  |
   |    |  | |  | |
    \    \  '-'  /
     \    '-----'
      """
    ),
    DistroTemplate(
      id = "tinycore",
      name = "Tiny Core Linux",
      version = "15.0 Core",
      category = "Minimalist",
      tag = "16MB RAM OS",
      description = "Extremely lightweight (16 MB) modular Linux running completely in RAM with FLTK/FLWM or pure CLI.",
      defaultArch = "x86_64",
      defaultCpuCores = 1,
      defaultRamMb = 128,
      defaultDiskGb = 1.0,
      packageManager = "tce",
      initSystem = "busybox init",
      defaultUser = "tc",
      defaultPass = "tc",
      kernelArgs = "console=ttyS0 quiet",
      downloadSizeMb = 16,
      preinstalledTools = listOf("busybox", "tce-load", "ash"),
      colorHex = 0xFF3E82F7,
      asciiLogo = """
  _   _
 ( ) ( )
  \ \ / /
   \ V /
    \_/
      """
    ),
    DistroTemplate(
      id = "freedos",
      name = "FreeDOS",
      version = "1.3 LiveCD",
      category = "Retro Computing",
      tag = "DOS 16/32-bit",
      description = "Complete, free, DOS-compatible operating system. Run classic DOS applications, utilities and retro compilers.",
      defaultArch = "i386",
      defaultCpuCores = 1,
      defaultRamMb = 64,
      defaultDiskGb = 0.5,
      packageManager = "fdimples",
      initSystem = "COMMAND.COM",
      defaultUser = "DOS",
      defaultPass = "",
      kernelArgs = "",
      downloadSizeMb = 40,
      preinstalledTools = listOf("command.com", "debug", "edlin", "fdisk", "format"),
      colorHex = 0xFF00AA00,
      asciiLogo = """
  _______  ____   _____
 |  ___/ |/ /\ \ / / _ \
 | |_ /| ' /  \ V / (_) |
 |_|   |_|\_\  \_/ \___/
      """
    )
  )
}
