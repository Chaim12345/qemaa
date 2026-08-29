# Real QEMU VM Support for Android

This project now includes **real QEMU virtual machine execution** capability on Android devices, in addition to the existing simulation mode.

## Overview

The app has been enhanced with a `RealQemuEngine` that can launch actual QEMU processes on Android, providing genuine hardware emulation and virtualization. When QEMU binaries are not available, it gracefully falls back to the original simulation mode.

## New Files

### `RealQemuEngine.kt`
A complete QEMU process management engine that:
- Spawns real QEMU emulator processes using Android's `ProcessBuilder`
- Captures and streams QEMU output (stdout/stderr) to the terminal UI
- Supports VM lifecycle operations: start, stop, pause, resume, reset, shutdown
- Sends QEMU monitor commands for VM control
- Checks QEMU binary availability before attempting to launch
- Handles process cleanup and resource management

### Updated `VmManagerService.kt`
Enhanced to integrate the real QEMU engine:
- Initializes `RealQemuEngine` when starting a VM
- Checks for QEMU binary availability (`qemu-system-x86_64`, `qemu-system-aarch64`, etc.)
- Falls back to simulation mode if QEMU is not installed
- Routes VM control commands (pause, resume, shutdown, reset) to real QEMU when active
- Maintains backward compatibility with simulation mode

## How It Works

### Real QEMU Mode (When QEMU is Available)

1. **QEMU Detection**: On VM start, the engine checks if the appropriate QEMU binary exists in PATH
2. **Process Launch**: Uses `ProcessBuilder` to spawn the QEMU process with generated CLI arguments
3. **Output Streaming**: Reads QEMU's stdout/stderr in real-time and displays in the terminal
4. **VM Control**: Sends QEMU monitor commands for power management (pause, resume, shutdown, reset)
5. **Process Management**: Tracks PID, handles graceful shutdown, force termination if needed

### Simulation Mode (Fallback)

When QEMU binaries are not found:
- Displays a warning message
- Falls back to simulated boot sequence
- Uses the existing `LinuxShellEngine` for command execution
- Maintains all original functionality

## Requirements for Real QEMU Execution

### Option 1: Rooted Device with QEMU Installed
```bash
# On rooted Android device via Termux or package manager
pkg install qemu-system-x86_64
# or
apt install qemu-system-arm
```

### Option 2: Bundle QEMU Binaries with APK
Include pre-compiled QEMU binaries in your app's `assets/` or `jniLibs/`:
```
app/src/main/assets/qemu/
  ├── qemu-system-x86_64
  ├── qemu-system-aarch64
  └── qemu-system-riscv64
```

Then copy them to the app's data directory on first launch and set executable permissions.

### Option 3: Custom ROM with QEMU Support
Some custom Android ROMs include QEMU in the system image.

## Architecture Support

The engine supports multiple architectures:
- **x86_64**: `qemu-system-x86_64`
- **i386/x86**: `qemu-system-i386`
- **ARM64/AARCH64**: `qemu-system-aarch64`
- **RISC-V 64**: `qemu-system-riscv64`

## Usage Example

```kotlin
// Initialize the engine
val qemuEngine = RealQemuEngine(
    context = context,
    onOutputLine = { line -> 
        // Handle QEMU output (display in terminal)
        appendTerminalLine(line)
    },
    onStatusChange = { running ->
        // Handle VM status changes
        if (!running) {
            // VM stopped
        }
    }
)

// Check if QEMU is available
val availability = qemuEngine.checkQemuAvailability("x86_64")
if (availability.available) {
    println("QEMU version: ${availability.version}")
    
    // Start VM with CLI arguments
    val cliArgs = "qemu-system-x86_64 -m 512M -hda /path/to/disk.qcow2"
    qemuEngine.startVm(cliArgs)
} else {
    println("QEMU not available: ${availability.error}")
    // Fall back to simulation mode
}

// Control the VM
qemuEngine.pauseVm()      // Pause execution
qemuEngine.resumeVm()     // Resume execution
qemuEngine.sendPowerEvent("shutdown")  // Graceful shutdown
qemuEngine.stopVm()       // Force stop
```

## QEMU Monitor Commands

The engine supports sending QEMU monitor commands:
- `stop` - Pause VM execution
- `cont` - Continue/resume VM execution
- `system_powerdown` - Send ACPI shutdown event
- `system_reset` - Hard reset the VM
- `info status` - Show VM status
- `info cpus` - Show CPU information
- `quit` - Exit QEMU

## Terminal Output

Real QEMU output includes:
- BIOS/UEFI POST messages
- Linux kernel boot logs
- systemd initialization messages
- Guest OS console output
- Error messages and warnings

## Limitations & Considerations

### Performance
- Real QEMU emulation is CPU-intensive on mobile devices
- TCG (software emulation) is significantly slower than KVM
- Consider limiting RAM and CPU cores for better performance

### Storage
- QEMU disk images can be large (several GB)
- Ensure adequate storage space in app data directory

### Permissions
- No special Android permissions required for user-space QEMU
- KVM acceleration (`/dev/kvm`) requires root or special SELinux policies

### Battery Impact
- Running QEMU VMs drains battery quickly
- Implement proper VM suspension when app goes to background

## Future Enhancements

Potential improvements:
1. **KVM Acceleration**: Leverage `/dev/kvm` on supported devices for near-native performance
2. **VNC/SPICE Display**: Add graphical display support via VNC viewer integration
3. **Shared Folders**: Implement 9p virtio filesystem for host-guest file sharing
4. **USB Passthrough**: Support USB device passthrough to guest VMs
5. **Network Bridging**: Advanced networking with TAP interfaces and bridging
6. **Snapshot Support**: Real QEMU snapshot save/restore functionality
7. **Multi-VM**: Run multiple VMs concurrently with resource isolation

## Testing

To test real QEMU functionality:

1. Install QEMU on your test device or emulator
2. Create a VM with a valid disk image
3. Start the VM - it should detect QEMU and launch real emulation
4. Observe real boot logs from actual QEMU process
5. Test VM controls (pause, resume, shutdown)
6. Verify process terminates cleanly on stop

## Troubleshooting

### "QEMU binary not found"
- Install QEMU via package manager
- Bundle binaries with the app
- Check PATH environment variable

### "Permission denied"
- Ensure QEMU binary has execute permissions: `chmod +x qemu-system-x86_64`
- Check Android sandbox restrictions

### "VM starts but no output"
- Verify `-nographic` or `-serial mon:stdio` in QEMU args
- Check stderr stream for errors
- Ensure disk image path is correct and accessible

### "QEMU crashes immediately"
- Check QEMU CLI arguments for syntax errors
- Verify disk image format matches (-drive format=qcow2/raw)
- Reduce RAM/CPU allocation if device has limited resources

## License

Same license as the main project.
