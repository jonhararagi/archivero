plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes the keystore path explicitly to make debug APK signatures stable across runners.
// Local builds leave this unset and continue using Android's normal debug-signing behavior.
val ciDebugKeystorePath = System.getenv("ARCHIVERO_CI_DEBUG_KEYSTORE")
android {
    signingConfigs {
        create("ciDebug") {
            if (!ciDebugKeystorePath.isNullOrBlank()) {
                storeFile = file(ciDebugKeystorePath)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }
    buildTypes {
        getByName("debug") {
            if (!ciDebugKeystorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("ciDebug")
            }
        }
    }
    namespace = "com.jonhararagi.archivero"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.jonhararagi.archivero"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.3.4"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.core:core:1.15.0")
    // Keep ExoPlayer, MediaSession and PlayerView on the same Media3 release.
    implementation("androidx.media3:media3-exoplayer:1.10.0")
    implementation("androidx.media3:media3-session:1.10.0")
    implementation("androidx.media3:media3-ui:1.10.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
