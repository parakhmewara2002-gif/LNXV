plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
}

android {
    testOptions {
        // Robolectric render tests need merged resources.
        unitTests.isIncludeAndroidResources = true
    }
    namespace = "com.lnxv.player"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lnxv.player"
        minSdk = 23 // Android 6.0 (Marshmallow) and newer
        targetSdk = 34
        val buildNum = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 0
        versionCode = 1000 + buildNum
        versionName = "1.0.$buildNum"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // sideload: self-updating APK. store: no installer permission, no updater. Both ship arm64-v8a + armeabi-v7a in release.
    // Only ship the languages LNXV is translated into (drops unused library locale resources).
    defaultConfig { resourceConfigurations += listOf("en", "hi") }

    flavorDimensions += "dist"
    productFlavors {
        create("sideload") {
            dimension = "dist"
            resValue("bool", "updater_enabled", "true")
            resValue("bool", "online_subtitles_enabled", "true")
            resValue("bool", "popup_window_enabled", "true")
        }
        create("store") {
            dimension = "dist"
            resValue("bool", "updater_enabled", "false")
            // Online subtitles contact a third party, so the store build leaves them out entirely.
            resValue("bool", "online_subtitles_enabled", "false")
            resValue("bool", "popup_window_enabled", "false")
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
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            signingConfig = if (System.getenv("KEYSTORE_PATH") != null)
                signingConfigs.getByName("lnxvRelease") else signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Used only by :benchmark (macrobenchmark / baseline-profile generation). Never shipped.
        create("benchmark") {
            initWith(getByName("release"))
            ndk { abiFilters.clear(); abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
        debug {
            // x86_64 (emulator) native decoders are only built for debug; release never ships them.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // FFmpeg .so files are copied into :decoders jniLibs and are also seen via the CMake imported targets.
            pickFirsts += listOf("**/libavcodec.so", "**/libavutil.so", "**/libswresample.so")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.media3.common.util.UnstableApi"
        )
    }
}

dependencies {
    // SMB browsing/playback: sideload build only (store build ships no SMB code). Apache-2.0; pulls in BouncyCastle (MIT-style).
    "sideloadImplementation"("com.hierynomus:smbj:0.13.0")
    // Chromecast for network URLs: sideload build only (Google Play services Cast framework is proprietary).
    "sideloadImplementation"("androidx.media3:media3-cast:${libs.versions.media3.get()}")
    "sideloadImplementation"("androidx.mediarouter:mediarouter:1.7.0")
    testImplementation("junit:junit:4.13.2")
    // org.json is a stub in android.jar; the real implementation is needed for JVM unit tests (tests only, never shipped).
    testImplementation("org.json:json:20240303")
    // Render tests (tests only, never shipped): Robolectric native graphics + Compose test rule.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    // Real backdrop blur (RenderEffect on Android 12+, translucent fallback below). Used on one bar only.
    implementation(libs.haze)
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
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
    // Declared explicitly: it used to arrive only transitively and disappeared from the classpath when the Cast dependencies were added.
    implementation("androidx.compose.material:material-icons-core")
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.leakcanary)

    // Media3 ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    // Network streams (Phase 2.3): HLS, DASH and RTSP sources. Same pinned Media3 version, loaded lazily by the media source factory.
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.rtsp)
    implementation(libs.androidx.biometric) // optional vault unlock (flag BIOMETRIC_UNLOCK); BiometricPrompt requires FragmentActivity
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.effect) // brightness/contrast/saturation (Phase 5, flag VIDEO_TRANSFORMS)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.extractor)
    // FFmpeg audio-only fallback (LGPL, dynamically linked) and dav1d AV1 software fallback (BSD-2). See docs/DECODERS.md.
    implementation(project(":decoders"))

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
