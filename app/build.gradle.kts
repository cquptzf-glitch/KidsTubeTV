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
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
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
