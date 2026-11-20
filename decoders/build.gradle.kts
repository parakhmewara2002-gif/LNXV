plugins {
    alias(libs.plugins.android.library)
}

// Native decoders (FFmpeg audio, dav1d AV1) are built by decoders/tools/build_all.sh before Gradle runs.
// Without them the module still compiles; the Java wrappers then report "library not available" at runtime
// and the app falls back to MediaCodec only. CI passes -PrequireNativeDecoders=true so a missing build fails loudly.
val jniDir = file("src/main/jni")
val nativeBuilt = file("src/main/jni/third_party/out/ffmpeg").isDirectory &&
    file("src/main/jni/third_party/out/dav1d").isDirectory
// Only configure CMake for ABIs whose libraries were actually built (other ABIs have no prebuilt FFmpeg/dav1d).
val builtAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64").filter {
    file("src/main/jni/third_party/out/ffmpeg/$it/lib").isDirectory &&
        file("src/main/jni/third_party/out/dav1d/$it/libdav1d.a").isFile
}
val requireNative = providers.gradleProperty("requireNativeDecoders").orNull == "true"
if (requireNative && !nativeBuilt) {
    throw GradleException(
        "requireNativeDecoders=true but decoders/src/main/jni/third_party/out is missing. Run decoders/tools/build_all.sh first."
    )
}

android {
    namespace = "com.lnxv.player.decoders"
    compileSdk = 36
    ndkVersion = "26.1.10909125"

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("proguard-rules.txt")
        if (nativeBuilt) {
            ndk { abiFilters += builtAbis }
            externalNativeBuild {
                cmake {
                    targets("ffmpegJNI", "dav1dJNI")
                    arguments("-DANDROID_STL=c++_static")
                }
            }
        }
    }

    if (nativeBuilt) {
        externalNativeBuild {
            cmake {
                path = file("src/main/jni/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // Vendored upstream sources: do not fail the build on their style.
        abortOnError = false
    }
}

dependencies {
    api(libs.androidx.media3.decoder)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.common)
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("com.google.guava:guava:33.3.1-android")
    compileOnly("org.checkerframework:checker-qual:3.13.0")
}
