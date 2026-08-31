plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.roborazzi)
}

// TEMP DIAGNOSTIC (remove once green): surface task failures as GitHub
// annotations (::error::) so build problems are visible without raw logs.
gradle.addListener(object : TaskExecutionListener {
  override fun beforeExecute(task: org.gradle.api.Task) {}
  override fun afterExecute(task: org.gradle.api.Task, state: org.gradle.api.tasks.TaskState) {
    if (state.failure != null) {
      val sw = java.io.StringWriter()
      state.failure?.printStackTrace(java.io.PrintWriter(sw))
      val detail = sw.toString().lineSequence()
        .filter { it.contains("e: ") || it.contains("Caused by") || it.contains("at org.jetbrains.kotlin") || it.contains("error:") }
        .take(24)
        .joinToString(" | ")
      println("::error ::TASK FAILED ${task.path} :: ${state.failure?.message?.take(300)} :: ${detail.take(1800)}")
    }
  }
})

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.linuxvm.qemu"
    minSdk = 36
    targetSdk = 36
    versionCode = 3
    versionName = "1.2"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH")
      if (keystorePath != null && file(keystorePath).exists()) {
        storeFile = file(keystorePath)
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      } else {
        // Fallback to debug keystore so assembleRelease always succeeds and generates installable APK quickly
        storeFile = file("${rootDir}/debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      // Full R8 optimization: the APK is dominated by the QEMU binaries, but
      // shrinking still cuts the Kotlin/Compose layer meaningfully.
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
      // The emulator test seeds the distro through run-as; keep release
      // semantics but allow debugging in that phase.
      isMinifyEnabled = false
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }

  packaging {
    jniLibs {
      // The QEMU executable ships as a native library
      // (app/src/main/jniLibs/x86_64/libqemu-system-x86_64.so) so that the installer
      // extracts it into the app's nativeLibraryDir. Executables must live there
      // because Android 10+ (API 29+) denies exec() on files inside the app's
      // writable data directory. useLegacyPackaging forces install-time extraction,
      // which is required for the binary to be executable from nativeLibraryDir.
      useLegacyPackaging = true
    }
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
