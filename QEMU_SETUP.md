# QEMU Binary Setup for Android

To enable real QEMU VM execution (not just simulation), you need to provide QEMU binaries for Android.

## Option 1: Termux/QEMU on Android (Recommended)

1. Install Termux from F-Droid
2. Install QEMU in Termux:
   ```bash
   pkg install qemu-system-x86-64 qemu-img
   ```
3. Copy the binaries to the app's assets:
   ```bash
   # Find the binaries
   which qemu-system-x86_64
   which qemu-img
   
   # Get the architecture
   uname -m
   # aarch64 = ARM64 phone
   # x86_64 = emulator or rare x86 phone
   ```

## Option 2: Cross-compile QEMU for Android

Use Android NDK to cross-compile QEMU:

```bash
# Download QEMU source
wget https://download.qemu.org/qemu-9.0.0.tar.xz
tar xf qemu-emu-9.0.0.tar.xz

# Configure for Android target
./configure \
  --target-list=x86_64-softmmu,aarch64-softmmu \
  --prefix=/path/to/install \
  --disable-kvm \
  --disable-xen \
  --disable-docs \
  --disable-gtk \
  --disable-sdl \
  --disable-vnc \
  --enable-linux-aio

make -j$(nproc)
make install
```

## Option 3: Extract from existing Android QEMU apps

Apps like "Limbo PC Emulator" or "Mobox" bundle QEMU binaries.

## Adding Binaries to the App

Place your QEMU binaries in:
```
app/src/main/assets/nativebin/
  qemu-system-aarch64    (for ARM64 phones)
  qemu-system-x86_64     (for x86_64 devices/emulators)
  qemu-img               (disk image tool)
```

The app will:
1. Detect the device ABI (arm64/x86_64)
2. Extract the matching binary from assets
3. Make it executable
4. Launch QEMU via ProcessBuilder

## How It Works

- **NativeBinaryExtractor** - Deploys QEMU binaries from assets to app's files dir
- **QemuRunner** - Manages QEMU process lifecycle:
  - `start()` - Launches QEMU with configured args
  - `sendCommand()` - Sends keystrokes to guest
  - `stop()` - ACPI powerdown or SIGTERM
  - `pause()` / `resume()` - QEMU monitor commands
- **VmManagerService** - Orchestrates real vs simulated mode
  - Auto-detects QEMU binaries on startup
  - Uses real QEMU when available, falls back to simulated
  - Shows status in UI

## Architecture Support

| Device ABI | QEMU Binary | Guest Architectures |
|------------|-------------|---------------------|
| arm64-v8a  | qemu-system-aarch64 | aarch64, arm |
| x86_64     | qemu-system-x86_64 | x86_64, i386 |
| armeabi-v7a| qemu-system-arm | arm |

## Notes

- Without root, QEMU uses TCG (software emulation) - slower but works
- With root + KVM, hardware acceleration is possible
- The app falls back to simulated mode if binaries are missing
- Disk images are created in app's private storage (/data/data/...)
