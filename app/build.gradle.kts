plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.jag.snapwall"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.jag.snapwall"
        minSdk = 26
        targetSdk = 35
        // Each CI run gets a higher versionCode so Android accepts it as an update.
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = run
        versionName = "1.$run"
    }

    // One permanent key, so every build installs over the last one.
    // CI writes the keystore from a secret; without it the build falls back to a throwaway debug key.
    val storePath = System.getenv("SIGNING_STORE_FILE")
    signingConfigs {
        if (storePath != null) {
            create("permanent") {
                storeFile = file(storePath)
                storePassword = System.getenv("SIGNING_PASSWORD")
                keyAlias = "snapwall"
                keyPassword = System.getenv("SIGNING_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("permanent") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
