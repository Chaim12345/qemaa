# Linux VM - QEMU-based Virtual Machine for Android

A fully functional Android app that runs a real Linux virtual machine using QEMU system emulation. The app boots Alpine Linux inside a QEMU VM on your Android device, providing a complete Linux environment with package management, networking, and a full terminal.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Android App (Kotlin)                   │
│  ┌────────────────────────────────────────────────────┐  │
│  │              Terminal UI (xterm.js)                 │  │
│  │         Compose + WebView + Touch Keyboard         │  │
│  └────────────────────┬───────────────────────────────┘  │
│                       │ stdin/stdout                      │
│  ┌────────────────────▼───────────────────────────────┐  │
│  │           QemuEngine (Process Manager)             │  │
│  │    Launches qemu-system-x86_64 as subprocess       │  │
│  │    Pipes serial console output to terminal         │  │
│  └────────────────────┬───────────────────────────────┘  │
│                       │                                   │
│  ┌────────────────────▼───────────────────────────────┐  │
│  │              QEMU Binary (Native)                   │  │
│  │    Cross-compiled with Android NDK for ARM64/x86   │  │
│  │    Static binary bundled in APK assets             │  │
│  │    Uses TCG (Tiny Code Generator) for emulation    │  │
│  └────────────────────┬───────────────────────────────┘  │
│                       │                                   │
│  ┌────────────────────▼───────────────────────────────┐  │
│  │           Alpine Linux (Guest OS)                   │  │
│  │    Kernel: vmlinuz-lts (Linux 6.6)                 │  │
│  │    Initrd: initramfs-lts (musl-based rootfs)       │  │
│  │    Boots via -kernel/-initrd (no disk image)       │  │
│  │    Serial console on ttyS0                         │  │
│  └────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

## How It Works

1. **QEMU Cross-Compilation**: The GitHub Actions workflow downloads QEMU source code and cross-compiles it for Android using the Android NDK. All dependencies (zlib, libffi, pixman, glib) are also cross-compiled. The result is a static `qemu-system-x86_64` binary that runs natively on Android.

2. **Alpine Linux Assets**: The workflow downloads Alpine Linux's netboot kernel (`vmlinuz-lts`) and initramfs (`initramfs-lts`). These are lightweight enough to boot entirely in RAM.

3. **APK Bundling**: The QEMU binary, kernel, and initramfs are bundled as APK assets. When the app first runs, they're extracted to the app's private directory.

4. **VM Execution**: When the user taps "Start", the app launches QEMU as a subprocess using Android's `ProcessBuilder`. QEMU boots Alpine Linux using software emulation (TCG), with the serial console connected to stdin/stdout pipes.

5. **Terminal Integration**: The app reads QEMU's stdout and displays it in the terminal UI. User input from the terminal is written to QEMU's stdin, creating an interactive session.

## Building

### Via GitHub Actions (Recommended)

The GitHub Actions workflow handles everything:

```bash
# Push to trigger the build
git push origin main
```

The workflow:
1. Downloads Alpine Linux kernel + initramfs
2. Cross-compiles QEMU for Android using Docker + NDK
3. Bundles everything in the APK
4. Produces a signed APK artifact

Download the APK from the Actions tab or Releases page.

### Local Build

```bash
# Set up Android SDK/NDK
export ANDROID_NDK_HOME=/path/to/android-ndk-r26b

# Build the APK (without QEMU binary - will use native shell mode)
./gradlew assembleDebug
```

## Features

### When QEMU is Available (Full Build)
- **Real Linux VM**: Boots Alpine Linux with full package management (`apk add`)
- **Network Access**: User-mode networking via QEMU SLIRP
- **Serial Console**: Full interactive terminal with xterm.js
- **Process Management**: Start/Stop VM from the UI
- **File Editor**: Built-in nano editor for host files

### Without QEMU (Native Shell Mode)
- **Direct Linux Commands**: Execute real commands on the Android Linux kernel
- **Hardware Info**: View CPU, memory, kernel version
- **File Operations**: Read/write files in the app sandbox
- **Script Execution**: Run shell scripts

## Technical Details

### QEMU Configuration
```bash
qemu-system-x86_64 \
  -kernel vmlinuz-lts \
  -initrd initramfs-lts \
  -append "console=ttyS0 quiet" \
  -m 256 \
  -smp 2 \
  -nographic \
  -serial stdio \
  -no-reboot \
  -nodefaults
```

### Cross-Compilation Flags
```bash
# Android NDK clang with bionic libc
CC=aarch64-linux-android24-clang
CFLAGS="-fPIC -DANDROID"
LDFLAGS="-pie"

# QEMU configured for minimal Android build
--static                    # Static linking (no .so dependencies)
--disable-sdl --disable-gtk # No GUI
--disable-vnc               # No VNC server
--disable-kvm               # Use TCG software emulation
--target-list=x86_64-softmmu # Only build x86_64 system emulation
```

### File Structure
```
app/src/main/
├── assets/
│   ├── alpine/
│   │   ├── vmlinuz-lts       # Alpine Linux kernel
│   │   └── initramfs-lts     # Alpine Linux rootfs
│   └── xterm/
│       └── xterm_terminal.html
├── java/com/example/
│   ├── engine/
│   │   ├── QemuEngine.kt         # QEMU process management
│   │   ├── RealNativeProcessEngine.kt  # Native shell execution
│   │   ├── VirtualizationDetector.kt   # Hardware detection
│   │   └── VmManagerService.kt        # Main service
│   └── ui/
│       ├── components/
│       │   ├── TerminalScreen.kt       # Terminal UI
│       │   ├── XtermTerminalView.kt    # xterm.js integration
│       │   └── VirtualizationReportDialog.kt
│       └── MainViewModel.kt
└── jniLibs/                    # Native libraries (if using JNI)
```

## Performance

- **TCG Emulation**: QEMU uses software emulation (Tiny Code Generator) since KVM is typically not available to Android apps
- **RAM**: The VM uses 256MB of the host's RAM
- **Boot Time**: ~5-10 seconds for Alpine Linux to reach login prompt
- **Speed**: Expect ~10-30% of native speed for CPU-intensive tasks (depends on host device)

## Requirements

- Android 7.0+ (API 24)
- 512MB+ free RAM (for VM)
- ARM64 or x86_64 device

## Credits

- [QEMU](https://www.qemu.org/) - The underlying emulator
- [Alpine Linux](https://alpinelinux.org/) - Lightweight Linux distribution
- [Limbo Emulator](https://github.com/limboemu/limbo) - Reference implementation for QEMU on Android
- [Android NDK](https://developer.android.com/ndk) - Cross-compilation toolchain

## License

This project is open source. QEMU is licensed under GPLv2. Alpine Linux is licensed under MIT/GPLv2.
