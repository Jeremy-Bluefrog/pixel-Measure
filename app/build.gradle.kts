import java.net.URL

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.aistudio.measureapp.uxcmqp"
    minSdk = 29
    targetSdk = 37
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
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
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }
  kotlin {
    jvmToolchain(17)
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.arcore)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  implementation(libs.retrofit.converter.serialization)
  implementation(libs.kotlinx.serialization.json)
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
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

tasks.register<Copy>("copyApkToBuildOutputs") {
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(rootProject.file("build-outputs"))
}

tasks.register("downloadFonts") {
    doLast {
        val fontDir = file("src/main/res/font")
        if (!fontDir.exists()) {
            fontDir.mkdirs()
        }
        val fonts = mapOf(
            "google_sans_rounded_regular.ttf" to listOf("GoogleSansRounded-Regular.ttf", "GoogleSans-Rounded-Regular.ttf"),
            "google_sans_rounded_medium.ttf" to listOf("GoogleSansRounded-Medium.ttf", "GoogleSans-Rounded-Medium.ttf"),
            "google_sans_rounded_bold.ttf" to listOf("GoogleSansRounded-Bold.ttf", "GoogleSans-Rounded-Bold.ttf")
        )
        fonts.forEach { (localName, remoteNameVariants) ->
            val destFile = file("src/main/res/font/$localName")
            if (!destFile.exists()) {
                var downloaded = false
                val branches = listOf("main", "master")
                for (branch in branches) {
                    for (remoteName in remoteNameVariants) {
                        try {
                            val urlString = "https://raw.githubusercontent.com/tiwa244/Google-Sans-Rounded/$branch/$remoteName"
                            println("Trying to download from: $urlString")
                            val url = URL(urlString)
                            val input = url.openStream()
                            val output = destFile.outputStream()
                            try {
                                input.copyTo(output)
                            } finally {
                                input.close()
                                output.close()
                            }
                            println("Successfully downloaded $localName as $remoteName from branch $branch")
                            downloaded = true
                            break
                        } catch (e: Exception) {
                            println("Skipping variant/branch ($remoteName on $branch): ${e.message}")
                        }
                    }
                    if (downloaded) break
                }
                if (!downloaded) {
                    println("WARNING: Could not download font $localName from any source. Creating placeholder empty file.")
                    destFile.writeText("") // Create placeholder so build doesn't fail on missing resource during build setups
                }
            }
        }
    }
}

// tasks.matching { it.name.startsWith("preBuild") }.all {
//     dependsOn("downloadFonts")
// }

tasks.matching { it.name == "assembleDebug" }.all {
    finalizedBy("copyApkToBuildOutputs")
}

