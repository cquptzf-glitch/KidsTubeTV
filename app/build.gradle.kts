plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.kidstubetv"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.kidstubetv"
        minSdk = 28
        targetSdk = 32 // Sideload-first TV build; target SDK upgrades audited separately
        versionCode = System.getenv("KIDSTUBE_VERSION_CODE")?.toIntOrNull() ?: 2
        versionName = System.getenv("KIDSTUBE_VERSION_NAME") ?: "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        val keystore = System.getenv("KIDSTUBE_KEYSTORE_FILE")
        if (!keystore.isNullOrBlank()) {
            create("production") {
                storeFile = file(keystore)
                storePassword = System.getenv("KIDSTUBE_STORE_PASSWORD")
                keyAlias = System.getenv("KIDSTUBE_KEY_ALIAS")
                keyPassword = System.getenv("KIDSTUBE_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("production")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // Sideload-first Android 9 TV build. This variant is not eligible for Google Play
    // distribution until target-SDK migration, foreground-service review and TV testing.
    // Keep every other lint check enabled and blocking in CI.
    lint {
        disable.add("ExpiredTargetSdkVersion")
    }
    packaging {
        jniLibs.useLegacyPackaging = true // Clash.load(nativeLibraryDir) needs extracted .so files
    }
}

dependencies {
    // Maintained third-party YouTube IFrame wrapper; NOT BabyTube code.
    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:13.0.0")

    // GPLv3 Mihomo Android bridge. Run scripts/fetch-mihomo.sh or .ps1 before build.
    implementation(files("libs/libmihomo-android-v0.3.13.aar"))

    implementation("com.github.bumptech.glide:glide:4.16.0")
    testImplementation("junit:junit:4.13.2")
}
