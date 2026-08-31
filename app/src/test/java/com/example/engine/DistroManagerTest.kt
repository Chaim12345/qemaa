package com.example.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DistroManagerTest {

  private val releaseJson = """
    {
      "tag_name": "android-qemu-v1.2.101",
      "assets": [
        {"name": "app-release.apk", "browser_download_url": "https://example.com/app-release.apk"},
        {"name": "linux-vm-rootfs.img.gz", "browser_download_url": "https://example.com/linux-vm-rootfs.img.gz"},
        {"name": "linux-vm-rootfs.img.gz.sha256", "browser_download_url": "https://example.com/linux-vm-rootfs.img.gz.sha256"},
        {"name": "linux-vm-vmlinuz-lts", "browser_download_url": "https://example.com/linux-vm-vmlinuz-lts"},
        {"name": "linux-vm-initramfs-lts", "browser_download_url": "https://example.com/linux-vm-initramfs-lts"},
        {"name": "linux-vm-distro-manifest.txt", "browser_download_url": "https://example.com/linux-vm-distro-manifest.txt"}
      ]
    }
  """.trimIndent()

  @Test
  fun `release json is parsed into distro asset urls`() {
    val manager = DistroManager(ApplicationProvider.getApplicationContext<Context>())
    val assets = manager.parseReleaseJson(releaseJson)

    assertEquals("https://example.com/linux-vm-rootfs.img.gz", assets.imageUrl)
    assertEquals("https://example.com/linux-vm-rootfs.img.gz.sha256", assets.sha256Url)
    assertEquals("https://example.com/linux-vm-vmlinuz-lts", assets.kernelUrl)
    assertEquals("https://example.com/linux-vm-initramfs-lts", assets.initrdUrl)
    assertEquals("https://example.com/linux-vm-distro-manifest.txt", assets.manifestUrl)
  }

  @Test
  fun `release without distro assets is rejected with a clear error`() {
    val manager = DistroManager(ApplicationProvider.getApplicationContext<Context>())
    val error = runCatching {
      manager.parseReleaseJson("""{"assets":[{"name":"app-release.apk","browser_download_url":"x"}]}""")
    }.exceptionOrNull()

    assertTrue(error is IllegalStateException)
    assertTrue(error!!.message.orEmpty().contains("linux-vm-rootfs.img.gz"))
  }

  @Test
  fun `installed state reflects the distro files on disk`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = DistroManager(context)

    assertFalse(manager.isInstalled())
    assertTrue(manager.currentInstanceState() is DistroManager.DistroState.NotInstalled)

    val distroDir = File(context.filesDir, "qemu/distro").apply { mkdirs() }
    File(distroDir, "rootfs.img").writeText("image")
    File(distroDir, "vmlinuz-lts").writeText("kernel")
    File(distroDir, "initramfs-lts").writeText("initrd")
    File(distroDir, "manifest.txt").writeText("node v22")

    assertTrue(manager.isInstalled())
    val state = manager.currentInstanceState()
    assertTrue("expected Installed, was $state", state is DistroManager.DistroState.Installed)
    assertEquals("node v22", (state as DistroManager.DistroState.Installed).manifest)

    manager.uninstall()
    assertFalse(manager.isInstalled())
  }

  @Test
  fun `terminal framing helper encodes utf8 bytes`() {
    val encoded = DistroManager.encodeForTerminal("stty cols 80 rows 24")
    val decoded = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
        .toString(Charsets.UTF_8)
    assertEquals("stty cols 80 rows 24", decoded)
    assertNull(null) // keeps junit import meaningful
  }
}
