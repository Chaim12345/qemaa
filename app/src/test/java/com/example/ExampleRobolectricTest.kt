package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.RealNativeProcessEngine
import com.example.engine.VirtualizationDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Linux VM", appName)
  }

  @Test
  fun `verify virtualization detector probe`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val report = VirtualizationDetector.probeHardwareVirtualization(context)
    assertNotNull(report)
    assertTrue("Physical CPU cores should be >= 1", report.physicalCpuCores >= 1)
    assertNotNull("Host kernel version must not be null", report.hostKernelVersion)
    assertTrue("Recommendations list should not be empty", report.recommendations.isNotEmpty())
  }

  @Test
  fun `verify real native process engine file and help commands`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = RealNativeProcessEngine(context)
    val helpOut = engine.executeCommand("help")
    assertTrue(helpOut.any { it.text.contains("REAL NATIVE LINUX") })

    engine.writeFile("hello.txt", "Linux Kernel Virtualization")
    val content = engine.readFile("hello.txt")
    assertEquals("Linux Kernel Virtualization", content)

    val prompt = engine.getPrompt()
    assertTrue(prompt.contains("@android-host"))
  }
}
