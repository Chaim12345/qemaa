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
import kotlin.io.path.createTempDirectory

/**
 * Unit tests for the QEMU engine's binary resolution, asset validation and
 * guest command line.
 *
 * These encode the two production regressions this app has already hit:
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
    val nativeDir = File(tempDir(), "lib/x86_64").apply { mkdirs() }
    File(nativeDir, "libqemu-system-x86_64.so").writeText("ELF")
    val context = contextWithNativeDir(nativeDir)

    val qemuDir = File(context.filesDir, "qemu").apply { mkdirs() }
    File(qemuDir, "vmlinuz-lts").writeText("kernel")
    File(qemuDir, "initramfs-lts").writeText("initrd")

    QemuEngine(context).validateAssets() // must not throw
  }

  @Test
  fun `guest command line boots Alpine from the network, not dev ram0`() {
    val nativeDir = File(tempDir(), "lib/x86_64").apply { mkdirs() }
    File(nativeDir, "libqemu-system-x86_64.so").writeText("ELF")
    val context = contextWithNativeDir(nativeDir)

    val qemuDir = File(context.filesDir, "qemu").apply { mkdirs() }
    File(qemuDir, "vmlinuz-lts").writeText("kernel")
    File(qemuDir, "initramfs-lts").writeText("initrd")

    val command = QemuEngine(context).buildQemuCommand()
    val cmdline = command[command.indexOf("-append") + 1]

    // root=/dev/ram0 is the emergency-shell regression.
    assertFalse("must not pass root=/dev/ram0: $cmdline", cmdline.contains("root=/dev/ram0"))
    // Netboot path: DHCP over slirp + package install from the Alpine repo.
    assertTrue("must request DHCP: $cmdline", cmdline.contains("ip=dhcp"))
    assertTrue("must point at the Alpine repository: $cmdline",
      cmdline.contains("alpine_repo=http"))
    assertTrue("must use the serial console: $cmdline", cmdline.contains("console=ttyS0"))

    // Firmware directory is passed via -L (SeaBIOS must be found).
    val firmwareDir = command[command.indexOf("-L") + 1]
    assertTrue(firmwareDir.endsWith("pc-bios"))

    // Kernel and initrd are the extracted assets.
    assertEquals(File(qemuDir, "vmlinuz-lts").absolutePath,
      command[command.indexOf("-kernel") + 1])
    assertEquals(File(qemuDir, "initramfs-lts").absolutePath,
      command[command.indexOf("-initrd") + 1])

    // The emulator itself comes from nativeLibraryDir.
    assertEquals(File(nativeDir, "libqemu-system-x86_64.so").absolutePath, command.first())

    // User-mode networking (slirp) must stay enabled.
    assertTrue(command.contains("user,hostfwd=tcp::2222-:22"))
  }
}
