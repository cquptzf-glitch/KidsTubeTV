plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Preview APKs use a separate app ID so a test install does not overwrite
// an older build with an unrelated, ephemeral GitHub-hosted debug signature.
// The package suffix is injected only by CI; normal release IDs are unchanged.
val previewPackageSuffix = System.getenv("KIDSTUBE_PREVIEW_PACKAGE_SUFFIX").orEmpty()
require(previewPackageSuffix.isEmpty() ||
    Regex("\\.[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*").matches(previewPackageSuffix)) {
    "Invalid preview package suffix"
}

android {
    namespace = "dev.kidstubetv"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.kidstubetv" + previewPackageSuffix
        manifestPlaceholders["kidsTubeAppLabel"] = if (previewPackageSuffix.isEmpty())
            "KidsTubeTV" else "KidsTubeTV 测试版"
        minSdk = 28
        targetSdk = 32 // Sideload-first TV build; target SDK upgrades audited separately
        versionCode = System.getenv("KIDSTUBE_VERSION_CODE")?.toIntOrNull() ?: 2
        versionName = System.getenv("KIDSTUBE_VERSION_NAME") ?: "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        val previewKeystore = System.getenv("KIDSTUBE_PREVIEW_KEYSTORE_FILE")
        if (!previewKeystore.isNullOrBlank()) {
            create("previewStable") {
                storeFile = file(previewKeystore)
                storePassword = System.getenv("KIDSTUBE_PREVIEW_STORE_PASSWORD")
                keyAlias = System.getenv("KIDSTUBE_PREVIEW_KEY_ALIAS")
                keyPassword = System.getenv("KIDSTUBE_PREVIEW_KEY_PASSWORD")
            }
        }
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
        debug {
            // GitHub Secrets can provide a stable, privately held preview key.
            // With no key the default Android debug signer remains available,
            // but CI must use a VERSION-SCOPED app ID to avoid update conflicts.
            signingConfigs.findByName("previewStable")?.let { signingConfig = it }
        }
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

    // Single native backend, verified SHA-256 via scripts/fetch-mihomo.sh/.ps1.
    implementation(files("libs/libmihomo-android-v0.3.13.aar"))

    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.yaml:snakeyaml:2.7")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
