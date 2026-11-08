plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// GitHub Secrets se aayi keystore (agar nahi hai to normal debug key use hogi)
val ksFile = System.getenv("KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() && it.length() > 0 }

android {
    namespace = "com.enova.launcher"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.enova.launcher"
        minSdk = 24
        targetSdk = 34
        versionCode = 6
        versionName = "1.5"
    }
    signingConfigs {
        if (ksFile != null) {
            create("enova") {
                storeFile = ksFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        if (ksFile != null) {
            getByName("debug") { signingConfig = signingConfigs.getByName("enova") }
            getByName("release") { signingConfig = signingConfigs.getByName("enova") }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
