package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DistroCatalog
import com.example.data.model.VirtualMachineEntity
import com.example.engine.LinuxShellEngine
import com.example.engine.QemuCliBuilder
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
  fun `verify distro catalog entries`() {
    val distros = DistroCatalog.DISTROS
    assertTrue("Distro catalog should contain at least 8 distros", distros.size >= 8)
    val alpine = distros.firstOrNull { it.id == "alpine" }
    assertNotNull("Alpine Linux should exist in catalog", alpine)
    assertEquals("apk", alpine?.packageManager)
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

  @Test
  fun `verify qemu cli command builder`() {
    val testVm = VirtualMachineEntity(
      name = "Test Alpine VM",
      distroId = "alpine",
      arch = "x86_64",
      cpuCores = 4,
      ramMb = 2048,
      diskSizeGb = 8.0,
      diskPath = "/data/vms/test.qcow2"
    )

    val cli = QemuCliBuilder.generateQemuCli(testVm)
    assertTrue("CLI should target qemu-system-x86_64", cli.contains("qemu-system-x86_64"))
    assertTrue("CLI should specify 4 cores", cli.contains("-smp 4"))
    assertTrue("CLI should specify 2048M RAM", cli.contains("-m 2048M"))
    assertTrue("CLI should configure virtio drive", cli.contains("/data/vms/test.qcow2"))
  }

  @Test
  fun `verify linux shell engine operations`() {
    val testVm = VirtualMachineEntity(
      name = "Test VM",
      distroId = "alpine",
      arch = "x86_64",
      cpuCores = 2,
      ramMb = 1024,
      diskSizeGb = 4.0
    )
    val engine = LinuxShellEngine(testVm)

    // Test uname
    val unameOut = engine.executeCommand("uname -a")
    assertTrue(unameOut.any { it.text.contains("Linux") && it.text.contains("x86_64") })

    // Test file operations: touch, echo, cat, rm
    engine.executeCommand("echo 'QEMU Virtualization Engine' > /root/test.txt")
    val readContent = engine.readFile("/root/test.txt")
    assertEquals("QEMU Virtualization Engine", readContent)

    val catOut = engine.executeCommand("cat /root/test.txt")
    assertTrue(catOut.any { it.text.contains("QEMU Virtualization Engine") })

    // Test rm
    engine.executeCommand("rm /root/test.txt")
    val readDeleted = engine.readFile("/root/test.txt")
    assertEquals(null, readDeleted)

    // Test Alpine vmConsole commands: apk, rc-service, lbu, QEMU monitor
    val apkOut = engine.executeCommand("apk update")
    assertTrue(apkOut.any { it.text.contains("APKINDEX") || it.text.contains("distinct packages") })

    val rcOut = engine.executeCommand("rc-service sshd status")
    assertTrue(rcOut.any { it.text.contains("service sshd is running") })

    val qemuMonitorOut = engine.executeCommand("info cpus")
    assertTrue(qemuMonitorOut.any { it.text.contains("CPU #0") })
    // Test Language Runtimes: Python, Node, Go, Rust, GCC
    val pyOut = engine.executeCommand("python3 -c \"print('Hello Python')\"")
    assertTrue(pyOut.any { it.text.contains("Hello Python") })

    val nodeOut = engine.executeCommand("node -e \"console.log('Hello Node')\"")
    assertTrue(nodeOut.any { it.text.contains("Hello Node") })

    val goOut = engine.executeCommand("go version")
    assertTrue(goOut.any { it.text.contains("go version") })

    val rustOut = engine.executeCommand("rustc --version")
    assertTrue(rustOut.any { it.text.contains("rustc") })

    val gccOut = engine.executeCommand("gcc --version")
    assertTrue(gccOut.any { it.text.contains("GCC") || it.text.contains("gcc") })
  }
}

