plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.parag.safetydesk"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.parag.safetydesk"
        minSdk = 29
        targetSdk = 34
        versionCode = runNumber
        versionName = "1.0.$runNumber"
    }

    signingConfigs {
        create("release") {
            // CI writes the keystore from the SIGNING_KEYSTORE secret so every build can update the last one.
            val ks = file("release.jks")
            if (ks.exists()) {
                storeFile = ks
                storePassword = System.getenv("SIGNING_PASSWORD")
                keyAlias = "safetydesk"
                keyPassword = System.getenv("SIGNING_PASSWORD")
            }
        }
    }

    // Sign with both the old (v1) and new (v2) schemes so every phone's installer accepts the APK.
    signingConfigs.getByName("debug") { enableV1Signing = true; enableV2Signing = true }
    signingConfigs.getByName("release") { enableV1Signing = true; enableV2Signing = true }

    buildTypes {
        release {
            // R8 strips unused code and resources; the app uses no reflection, so no keep rules are needed.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = if (file("release.jks").exists()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/*.kotlin_module", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*",
                "META-INF/versions/**", "META-INF/*.version", "META-INF/INDEX.LIST",
            )
        }
    }
}
