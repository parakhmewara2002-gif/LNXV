package com.lnxv.player.core.model

enum class LicenseType(val assetPath: String, val displayName: String) {
    APACHE_2("licenses/apache-2.0.txt", "Apache License 2.0"),
    LGPL_2_1("licenses/lgpl-2.1.txt", "GNU LGPL 2.1 or later"),
    BSD_2("licenses/bsd-2-clause.txt", "BSD 2-Clause")
}

/**
 * One third-party component shipped in (or built into) the app.
 * [tomlKey] names the version entry in gradle/libs.versions.toml that [version] must match (checked by a unit test),
 * so the screen cannot silently show a stale version.
 */
data class OpenSourceLibrary(
    val name: String,
    val version: String,
    val license: LicenseType,
    val url: String,
    val note: String = "",
    val tomlKey: String? = null
)

object OpenSourceLibraries {
    val ALL: List<OpenSourceLibrary> = listOf(
        OpenSourceLibrary(
            "FFmpeg (libavcodec, libavutil, libswresample)", "n6.0.1", LicenseType.LGPL_2_1, "https://ffmpeg.org",
            note = "Audio decoding only, LGPL build (no GPL or nonfree parts), linked dynamically as separate .so files. " +
                "Build script and pinned source: decoders/ in the LNXV repository."
        ),
        OpenSourceLibrary("dav1d", "1.5.4", LicenseType.BSD_2, "https://code.videolan.org/videolan/dav1d", note = "AV1 software decoder, used only when no hardware AV1 decoder can play the video."),
        OpenSourceLibrary("cpu_features", "v0.9.0", LicenseType.APACHE_2, "https://github.com/google/cpu_features", note = "Used by the dav1d wrapper to choose a thread count."),
        OpenSourceLibrary("AndroidX Media3 (ExoPlayer, decoder extension sources)", "1.11.0", LicenseType.APACHE_2, "https://github.com/androidx/media", tomlKey = "media3"),
        OpenSourceLibrary("Jetpack Compose BOM", "2024.12.01", LicenseType.APACHE_2, "https://developer.android.com/jetpack/compose", tomlKey = "composeBom"),
        OpenSourceLibrary("AndroidX Core KTX", "1.13.1", LicenseType.APACHE_2, "https://developer.android.com/jetpack/androidx", tomlKey = "coreKtx"),
        OpenSourceLibrary("AndroidX Activity Compose", "1.9.3", LicenseType.APACHE_2, "https://developer.android.com/jetpack/androidx", tomlKey = "activityCompose"),
        OpenSourceLibrary("AndroidX Navigation Compose", "2.8.5", LicenseType.APACHE_2, "https://developer.android.com/jetpack/androidx", tomlKey = "navigationCompose"),
        OpenSourceLibrary("AndroidX Lifecycle", "2.8.7", LicenseType.APACHE_2, "https://developer.android.com/jetpack/androidx", tomlKey = "lifecycleRuntimeKtx"),
        OpenSourceLibrary("AndroidX Room", "2.6.1", LicenseType.APACHE_2, "https://developer.android.com/jetpack/androidx", tomlKey = "room"),
        OpenSourceLibrary("AndroidX DataStore", "1.1.1", LicenseType.APACHE_2, "https://developer.android.com/jetpack/androidx", tomlKey = "datastore"),
        OpenSourceLibrary("AndroidX Biometric", "1.1.0", LicenseType.APACHE_2, "https://developer.android.com/jetpack/androidx", tomlKey = "biometric"),
        OpenSourceLibrary("AndroidX SplashScreen", "1.0.1", LicenseType.APACHE_2, "https://developer.android.com/jetpack/androidx", tomlKey = "splashscreen"),
        OpenSourceLibrary("Kotlin and kotlinx.coroutines", "1.9.0", LicenseType.APACHE_2, "https://github.com/Kotlin/kotlinx.coroutines", tomlKey = "coroutines"),
        OpenSourceLibrary("Koin", "3.5.6", LicenseType.APACHE_2, "https://insert-koin.io", tomlKey = "koin"),
        OpenSourceLibrary("Coil", "2.7.0", LicenseType.APACHE_2, "https://coil-kt.github.io/coil/", tomlKey = "coilCompose"),
        OpenSourceLibrary("Haze", "1.0.2", LicenseType.APACHE_2, "https://github.com/chrisbanes/haze", tomlKey = "haze"),
        OpenSourceLibrary("Guava (Android flavor)", "33.3.1-android", LicenseType.APACHE_2, "https://github.com/google/guava", note = "Used by the vendored Media3 decoder wrappers."),
        OpenSourceLibrary("AndroidX Profile Installer", "1.3.1", LicenseType.APACHE_2, "https://developer.android.com/jetpack/androidx")
    )
}
