plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
}

android {
    namespace = "com.lnxv.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lnxv.player"
        minSdk = 26
        targetSdk = 34
        val buildNum = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 0
        versionCode = 1000 + buildNum
        versionName = "1.0.$buildNum"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // sideload: self-updating APK (arm64 only to keep it small). store: no installer permission, no updater.
    flavorDimensions += "dist"
    productFlavors {
        create("sideload") {
            dimension = "dist"
            ndk { abiFilters += "arm64-v8a" }
            resValue("bool", "updater_enabled", "true")
        }
        create("store") {
            dimension = "dist"
            resValue("bool", "updater_enabled", "false")
        }
    }

    signingConfigs {
        create("lnxvRelease") {
            val ksPath = System.getenv("KEYSTORE_PATH")
            if (ksPath != null) {
                storeFile = file(ksPath)
                storePassword = System.getenv("KS_PASS")
                keyAlias = "lnxv"
                keyPassword = System.getenv("KS_PASS")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (System.getenv("KEYSTORE_PATH") != null)
                signingConfigs.getByName("lnxvRelease") else signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.media3.common.util.UnstableApi"
        )
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    // Real backdrop blur (RenderEffect on Android 12+, translucent fallback below). Used on one bar only.
    implementation(libs.haze)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.leakcanary)

    // Media3 ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    // Network streams (Phase 2.3): HLS, DASH and RTSP sources. Same pinned Media3 version, loaded lazily by the media source factory.
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.rtsp)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.extractor)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Coil Image & Video Thumbnail Loading
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
}
