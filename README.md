# Linux VM — QEMU Virtual Machine for Android

A fully functional Android app that runs a **real Linux virtual machine** using QEMU
system emulation — with a **prebuilt distro** (Alpine + Node.js + Go + the **π coding
agent** + **opencode**), a **Termux-grade terminal** (real xterm.js, raw byte stream,
Ctrl/Tab/arrows, extra keys row, copy/paste), and a **persistent disk**.

## Architecture

```
┌───────────────────────────────────────────────────────────────┐
│                     Android App (Kotlin/Compose)               │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │        REAL xterm.js terminal (WebView)                 │  │
│  │  raw byte stream ⇄ base64 frames ⇄ stdin/stdout         │  │
│  │  Termux-style extra keys: CTRL/ALT/TAB/arrows/symbols   │  │
│  └────────────────────────┬────────────────────────────────┘  │
│                           │                                    │
│  ┌────────────────────────▼────────────────────────────────┐  │
│  │  QemuEngine — process manager + distro manager           │  │
│  │  MTTCG (-accel tcg,thread=multi), -cpu max, host-adaptive│  │
│  │  RAM/vCPUs, foreground keep-alive service + wake lock    │  │
│  └────────────────────────┬────────────────────────────────┘  │
│                           │                                    │
│  ┌────────────────────────▼────────────────────────────────┐  │
│  │  QEMU 9.0.0 (x86_64 + arm64-v8a, NDK cross-compiled,     │  │
│  │  TCG) from nativeLibraryDir (API 29+ W^X compliant)      │  │
│  └────────────────────────┬────────────────────────────────┘  │
│                           │                                    │
│  ┌────────────────────────▼────────────────────────────────┐  │
│  │  Guest — pick one:                                       │  │
│  │  • DISTRO: persistent ext4 disk (Alpine + Node + Go +    │  │
│  │    pi + opencode + tmux/vim/htop), autologin root shell  │  │
│  │  • NETBOOT: Alpine netboot into RAM (zero-download)      │  │
│  └─────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────┘
```

## The Prebuilt Distro (one tap: "Install Distro")

CI builds a ready-to-use distro image and publishes it with every release. The app
downloads it with live progress, SHA-256-verifies it while streaming, decompresses
on the fly (gzip) and boots it as a **persistent virtio disk** — everything you
install survives reboots.

Preinstalled and verified by CI *inside the booted guest*:

| Tool | Notes |
|---|---|
| **Node.js + npm** | run `node -v`, `npm install -g <pkg>` |
| **Go** | full toolchain, `GOPATH=/root/go` |
| **π agent** (`pi`) | `npm i -g @mariozechner/pi-coding-agent` — runs `pi` |
| **opencode** | installed via the official musl-aware installer |
| **git, tmux, vim, nano, htop, openssh** | the everyday toolkit |
| **apk (main + community)** | install any Alpine package |

Release assets: `linux-vm-rootfs.img.gz` (~1 GB download → 3 GB disk), plus the
version-matched kernel, initramfs, checksum and manifest. The agent CLIs need your
own API keys (`export ANTHROPIC_API_KEY=…`, or run `opencode` and `/connect`).

## The Terminal (Termux-grade)

- **Real xterm.js** — full ANSI/256-color, cursor addressing, scrollback (5000 lines).
  TUI apps work: `vim`, `htop`, `tmux`, `pi`, `opencode`.
- **Raw byte I/O** — no line buffering: Ctrl+C (0x03), Tab-completion, arrows, Enter,
  escape sequences are transmitted exactly.
- **Extra keys rows** — ESC / CTRL / ALT (sticky modifiers that chord with the soft
  keyboard: tap CTRL then `c` = SIGINT) / TAB / arrows / HOME / END / PGUP / PGDN
  and a symbols row (`| / \ - ~ $ " ' ( ) : ;`), plus `^C ^D ^L ^Z DEL BKSP`.
- **Selection & paste** — copy selection, bracketed paste from clipboard.
- **Pinch-to-zoom** font size (and A-/A+ buttons), 6 color themes.
- **Terminal size sync** — the guest tty follows your real terminal geometry
  (`stty cols/rows`), so full-screen apps fill the display.
- **Foreground service + wake lock** — the VM keeps running when you switch apps or
  turn the screen off; a low-priority notification brings you back.
- **Immersive fullscreen** toggle; screen stays on while the VM runs.

## Building

### Via GitHub Actions (recommended)

The `Android CI & QEMU Build` workflow builds **two jobs in parallel**:

1. **Build QEMU and Android APK** — cross-compiles QEMU 9.0.0 for `x86_64` +
   `arm64-v8a` (NDK, Docker), packages binaries as native libraries, runs the unit
   tests and produces release + debug APKs.
2. **Build prebuilt distro** — builds the Alpine rootfs in an `alpine:3.22`
   container (Node, Go, pi, opencode, tooling), generates a version-matched
   initramfs (virtio+ext4 guaranteed) and packs the 3 GB ext4 image.

Then the **Android Emulator Test** runs the FULL gauntlet on an API 36 emulator:

1. APK bundles QEMU for both ABIs; binary executes standalone and from
   `nativeLibraryDir`.
2. App installs, launches, taps **Start** → QEMU spawns → the netboot guest
   **boots to the login prompt** → **Stop** terminates it.
3. **Standalone distro probe** — boots the built image with the app's exact disk
   arguments, waits for the autologin shell and *types* `node -v`, `npm -v`,
   `go version`, `pi --version`, `opencode --version` over the serial console.
4. **In-app distro boot** — seeds the image into the app (debug APK + `run-as`),
   verifies the banner flips to "Distro ready", QEMU runs with
   `-drive …rootfs.img…if=virtio` + `root=/dev/vda`, the guest reaches its shell
   (terminal prompt or sshd on the forwarded port), and Stop flushes and kills it.

On green, **Tag & Release** publishes the APK **and** the distro assets with the
emulator evidence. Every release has passed the full test.

```bash
git push origin main        # triggers everything
```

### Local build

```bash
export ANDROID_NDK_HOME=/path/to/android-ndk
./gradlew assembleDebug     # no QEMU binary bundled → netboot-only, no exec
```

The Gradle wrapper is not committed; CI regenerates it (`gradle wrapper --gradle-version 9.3.1`).

## QEMU runtime (distro mode)

```
qemu-system-x86_64 \
  -L <pc-bios> \
  -kernel vmlinuz-lts -initrd initramfs-lts \
  -append "console=ttyS0 root=/dev/vda rw" \
  -accel tcg,thread=multi -cpu max \
  -smp <2..6 host cores> -m <768..1536 by device RAM> \
  -serial stdio -monitor none \
  -net nic,model=virtio -net user,hostfwd=tcp::2222-:22 \
  -drive file=rootfs.img,format=raw,if=virtio,cache=writeback \
  -device virtio-rng-pci -no-reboot -nodefaults
```

Stop sends SIGTERM first — QEMU flushes the disk — then SIGKILL after a timeout.

## Requirements

- Android 16+ (API 36), 64-bit (arm64 or x86_64)
- Netboot: ~512 MB RAM free. Distro: ~1 GB download, 3 GB disk, 2 GB+ RAM
- First boot takes a few minutes (pure-software TCG emulation); subsequent boots
  of the distro are faster and keep your files.

## Credits

- [QEMU](https://www.qemu.org/) — GPLv2. [Alpine Linux](https://alpinelinux.org/).
- [xterm.js](https://github.com/xtermjs/xterm.js) — MIT (vendored in
  `app/src/main/assets/xterm/`, see `xterm.LICENSE`).
- [pi coding agent](https://www.npmjs.com/package/@mariozechner/pi-coding-agent) ·
  [opencode](https://opencode.ai).
- [Limbo Emulator](https://github.com/limboemu/limbo) — reference for QEMU on Android.
