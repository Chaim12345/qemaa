package com.example.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile
import kotlin.io.path.createTempDirectory

/**
 * Unit tests for the QEMU engine's binary resolution, asset validation and
 * guest command line in BOTH boot modes (prebuilt distro disk + netboot).
 *
 * These encode the production regressions this app has already hit:
 *  1. an APK that only bundles one host ABI leaves arm64 phones without a
 *     QEMU binary (the error must name the device ABI), and
 *  2. root=/dev/ram0 in the guest cmdline makes the Alpine netboot initramfs
 *     drop into an emergency shell instead of booting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QemuEngineTest {

  private fun tempDir(): File = createTempDirectory("qemu-engine-test").toFile()

  private fun contextWithNativeDir(nativeDir: File): Context {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.applicationInfo.nativeLibraryDir = nativeDir.absolutePath
    return context
  }

  private fun installFakeNativeBinary(context: Context): File {
    val nativeDir = File(tempDir(), "lib/x86_64").apply { mkdirs() }
    File(nativeDir, "libqemu-system-x86_64.so").writeText("ELF")
    context.applicationInfo.nativeLibraryDir = nativeDir.absolutePath
    return nativeDir
  }

  private fun installNetbootAssets(context: Context): File {
    val qemuDir = File(context.filesDir, "qemu").apply { mkdirs() }
    File(qemuDir, "vmlinuz-lts").writeText("kernel")
    File(qemuDir, "initramfs-lts").writeText("initrd")
    return qemuDir
  }

  /** Create a fake installed distro (image + kernel + initramfs). */
  private fun installFakeDistro(context: Context): File {
    val distroDir = File(context.filesDir, "qemu/distro").apply { mkdirs() }
    File(distroDir, "rootfs.img").apply {
      writeByteArray(ByteArray(1024) { 0 })
      // Size above QemuEngine's plausibility threshold
      val filler = RandomAccessFile(this, "rw")
      filler.setLength(128L * 1024L * 1024L)
      filler.close()
    }
    File(distroDir, "vmlinuz-lts").writeText("distro-kernel")
    File(distroDir, "initramfs-lts").writeText("distro-initrd")
    return distroDir
  }

  @Test
  fun `resolveQemuBinary prefers the nativeLibraryDir binary`() {
    val nativeDir = File(tempDir(), "lib/x86_64").apply { mkdirs() }
    val soFile = File(nativeDir, "libqemu-system-x86_64.so").apply { writeText("ELF") }
    val context = contextWithNativeDir(nativeDir)

    val resolved = QemuEngine(context).resolveQemuBinary()

    assertEquals(soFile.absolutePath, resolved.absolutePath)
  }

  @Test
  fun `resolveQemuBinary ignores an empty nativeLibraryDir file`() {
    val nativeDir = File(tempDir(), "lib/x86_64").apply { mkdirs() }
    File(nativeDir, "libqemu-system-x86_64.so").apply { createNewFile() } // 0 bytes
    val context = contextWithNativeDir(nativeDir)

    val resolved = QemuEngine(context).resolveQemuBinary()

    // Falls back to the legacy filesDir location, never to the empty .so.
    assertTrue(resolved.absolutePath.contains("qemu"))
    assertFalse(resolved.absolutePath.contains("lib/x86_64"))
  }

  @Test
  fun `validateAssets names the device ABI when the QEMU binary is missing`() {
    val context = contextWithNativeDir(File(tempDir(), "empty").apply { mkdirs() })

    val error = runCatching { QemuEngine(context).validateAssets() }.exceptionOrNull()

    assertTrue(error is IllegalStateException)
    val message = error!!.message.orEmpty()
    assertTrue("message should mention the QEMU binary: $message",
      message.contains("QEMU binary"))
    assertTrue("message should name the device ABI: $message",
      message.contains(android.os.Build.SUPPORTED_ABIS.firstOrNull().orEmpty()))
  }

  @Test
  fun `validateAssets passes when binary kernel and initrd exist`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    installFakeNativeBinary(context)
    installNetbootAssets(context)

    QemuEngine(context).validateAssets() // must not throw
  }

  @Test
  fun `netboot command line boots Alpine from the network, not dev ram0`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    installFakeNativeBinary(context)
    installNetbootAssets(context)

    val command = QemuEngine(context).buildQemuCommand()
    val cmdline = command[command.indexOf("-append") + 1]

    // root=/dev/ram0 is the emergency-shell regression.
    assertFalse("must not pass root=/dev/ram0: $cmdline", cmdline.contains("root=/dev/ram0"))
    // Netboot path: DHCP over slirp + package install from the Alpine repo.
    assertTrue("must request DHCP: $cmdline", cmdline.contains("ip=dhcp"))
    // External DNS must be pinned (fields 8/9 of ip=): slirp's virtual DNS
    // (10.0.2.3) depends on a host /etc/resolv.conf, which Android lacks.
    assertTrue("must pin external DNS servers: $cmdline",
      cmdline.contains("8.8.8.8") && cmdline.contains("1.1.1.1"))
    assertTrue("must point at the Alpine repository: $cmdline",
      cmdline.contains("alpine_repo=http"))
    assertTrue("must use the serial console: $cmdline", cmdline.contains("console=ttyS0"))

    // Firmware directory is passed via -L (SeaBIOS must be found).
    val firmwareDir = command[command.indexOf("-L") + 1]
    assertTrue(firmwareDir.endsWith("pc-bios"))

    // Kernel and initrd are the extracted assets.
    val qemuDir = File(context.filesDir, "qemu")
    assertEquals(File(qemuDir, "vmlinuz-lts").absolutePath,
      command[command.indexOf("-kernel") + 1])
    assertEquals(File(qemuDir, "initramfs-lts").absolutePath,
      command[command.indexOf("-initrd") + 1])

    // The emulator itself comes from nativeLibraryDir.
    val nativeDir = context.applicationInfo.nativeLibraryDir
    assertEquals(File(nativeDir, "libqemu-system-x86_64.so").absolutePath, command.first())

    // User-mode networking (slirp) must stay enabled.
    assertTrue(command.any { it.startsWith("user,hostfwd=tcp::2222-:22") })
  }

  @Test
  fun `distro mode boots the persistent image with tuned performance flags`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    installFakeNativeBinary(context)
    installNetbootAssets(context) // netboot assets may exist; distro takes priority
    val distroDir = installFakeDistro(context)

    val engine = QemuEngine(context)
    assertEquals(BootMode.DISTRO, engine.bootMode())

    val command = engine.buildQemuCommand()
    val cmdline = command[command.indexOf("-append") + 1]

    // Boots from the persistent virtio disk, not the network.
    assertTrue("must set root=/dev/vda: $cmdline", cmdline.contains("root=/dev/vda"))
    assertTrue("must mount rw: $cmdline", cmdline.contains("rw"))
    assertFalse("must not netboot in distro mode: $cmdline", cmdline.contains("alpine_repo"))
    assertTrue(cmdline.contains("console=ttyS0"))

    // The distro's own kernel/initramfs (version-matched to the image).
    assertEquals(File(distroDir, "vmlinuz-lts").absolutePath,
      command[command.indexOf("-kernel") + 1])
    assertEquals(File(distroDir, "initramfs-lts").absolutePath,
      command[command.indexOf("-initrd") + 1])

    // The persistent disk is attached as a virtio drive.
    val driveIndex = command.indexOf("-drive")
    assertTrue("must attach the rootfs via -drive", driveIndex >= 0)
    val driveArg = command.getOrNull(driveIndex + 1).orEmpty()
    assertTrue("drive must point at the rootfs image: $driveArg",
      driveArg.contains("rootfs.img") && driveArg.contains("if=virtio"))

    // Performance tuning: MTTCG + widest TCG CPU model.
    assertEquals("tcg,thread=multi", command.getOrNull(command.indexOf("-accel") + 1))
    assertEquals("max", command.getOrNull(command.indexOf("-cpu") + 1))

    // Host-adaptive sizing.
    val smp = command.getOrNull(command.indexOf("-smp") + 1)?.toIntOrNull() ?: 0
    assertTrue("smp must be 2..6: $smp", smp in 2..6)
    val mem = command.getOrNull(command.indexOf("-m") + 1)?.toIntOrNull() ?: 0
    assertTrue("memory must be >= 768MB: $mem", mem >= 768)

    // Network stays available (agents need API access).
    assertTrue(command.any { it.startsWith("user,hostfwd=tcp::2222-:22") })
  }

  @Test
  fun `netboot mode is used when no distro is installed`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    installFakeNativeBinary(context)
    installNetbootAssets(context)

    val engine = QemuEngine(context)
    assertEquals(BootMode.NETBOOT, engine.bootMode())
    assertFalse(engine.buildQemuCommand().any { it.contains("rootfs.img") })
  }
}
