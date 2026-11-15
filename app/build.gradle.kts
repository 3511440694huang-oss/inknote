plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "com.inknote.app"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.inknote.app"
    minSdk = 23
    targetSdk = 34
    versionCode = 3
    versionName = "1.2.0"
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  signingConfigs {
    create("repoDebug") {
      storeFile = rootProject.file("keystore/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("repoDebug")
    }
    debug {
      signingConfig = signingConfigs.getByName("repoDebug")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }

  lint {
    checkReleaseBuilds = false
    abortOnError = false
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
      excludes += "/META-INF/DEPENDENCIES"
      excludes += "/META-INF/LICENSE*"
      excludes += "/META-INF/NOTICE*"
    }
  }
}

dependencies {
  val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
  implementation(composeBom)
  implementation("androidx.activity:activity-compose:1.8.2")
  implementation("androidx.core:core-ktx:1.15.0")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-core")
  implementation("androidx.compose.foundation:foundation")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
  debugImplementation("androidx.compose.ui:ui-tooling")
  testImplementation("junit:junit:4.13.2")
}
