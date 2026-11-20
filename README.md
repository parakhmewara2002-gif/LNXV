# LNXV — Premium Android Video Player

LNXV is an advanced, production-ready Android video player built with **Jetpack Compose**, **Material 3**, and **AndroidX Media3 ExoPlayer**. It features an original, iOS-inspired **Liquid Glass** aesthetic, hardware-accelerated playback with software fallback, rich touch gestures, and a local Room-backed media library.

---

## 1. Technical Stack & Architecture

* **UI Framework**: 100% Jetpack Compose with Material 3 and custom Liquid Glass modifiers.
* **Media Playback Engine**: AndroidX Media3 ExoPlayer (`1.11.0`) with hardware `MediaCodec` acceleration, a selectable decoder mode, an audio-only FFmpeg fallback and a capped dav1d AV1 software fallback (see below).
* **Background Service**: AndroidX `MediaSessionService` (`LNXVPlaybackService`) for background audio and lock screen controls.
* **Architecture**: Clean Architecture & MVVM with Unidirectional Data Flow (UDF).
* **Local Persistence**: Room Database 2.6.1 with reactive Kotlin `Flow` queries.
* **Preferences**: Jetpack DataStore Preferences.
* **Image & Thumbnail Pipeline**: Coil 2.7.0 with `VideoFrameDecoder` for asynchronous video frame thumbnail generation.
* **Language & SDK**: Kotlin 2.0, Target SDK 34 (Android 14+), Min SDK 26 (Android 8.0+).

---

## 2. Key Features

### Playback Engine
* **Decoding Pipeline**: Hardware acceleration (`HW`) with automatic fallback to Software decoding (`SW`).
* **Aspect Ratio Modes**: Fit to Screen, Original 100%, Fill (Crop), Stretch, 16:9, 4:3, 21:9, 18:9, 2.35:1.
* **Playback Speed**: Discrete presets and continuous slider (0.25x to 4.0x) with long-press 2x preview.
* **Multi-Track Audio**: Switch between embedded alternate audio streams.
* **Subtitles**: Embedded tracks and external `.srt`, `.vtt`, `.ass`/`.ssa` and `.ttml` subtitles, text-encoding auto-detect with manual override, styling, delay sync in 0.1 s steps, and optional online search via OpenSubtitles (sideload build, your own API key).
* **AB Repeat**: Enforce custom looping between marker point A and marker point B.
* **Sleep Timer**: Coroutine countdown timer with auto-pause.
* **Multitasking**: Native Picture-in-Picture (PiP) and background audio playback.

### Gestures & HUD
* **Left vertical swipe**: Screen brightness control (0% to 100%) with animated HUD overlay.
* **Right vertical swipe**: Volume control with 200% volume boost capability.
* **Horizontal swipe**: Position seeking with target timestamp and seek delta indicator.
* **Double-tap**: Left seeks -10s, Right seeks +10s, Center toggles Play/Pause.
* **Two-finger pinch**: Zoom and pan up to 400%.

### Video Library
* Automatic discovery of local video files via Android MediaStore.
* Folder grouping with video count and storage usage.
* Playback history tracking with resume position.
* Sorting (Date, Title, Size, Duration) and real-time search.

### Private Vault & Security Model
* **Encryption:** videos moved to the vault are copied into `filesDir/lnxv_vault/<uuid>.lnxv` as chunked AES-256-GCM (64 KB chunks, per-chunk nonce and AAD, truncation/reordering detected). The original is deleted from MediaStore after a full decrypt-and-hash verification. Playback decrypts in memory through a custom Media3 `DataSource`; plaintext is never written to disk or cache.
* **Keys:** a random 32-byte master key is wrapped by an Android Keystore AES-GCM key (StrongBox when available). The key is only in memory while the vault is unlocked and is zeroed on lock.
* **PIN:** 4-8 digits (6 recommended), PBKDF2WithHmacSHA256 (210 000 iterations) with a random per-install salt, constant-time comparison, persisted lockout after 5 wrong attempts (30 s, 1 m, 5 m, 15 m, 1 h).
* **Session:** auto-lock on background (immediately / 30 s / 1 m / 5 m) and screen-off; FLAG_SECURE on vault screens and vault playback; optional "Block screenshots everywhere".
* **Forgot PIN** permanently deletes all vault videos (the PIN gates access, the key is not recoverable).
* **Threat model / limits:** protects against casual access, file-manager browsing, backups and offline copying of app storage. It does **not** protect against a rooted device or malware running as root while the vault is unlocked (the key is in process memory), a compromised OS, or someone who knows the PIN. The lockout uses wall-clock time, so changing the system clock can shorten a delay. Legacy (pre-encryption) vault items are only hidden until you tap "Encrypt now".
* NOT DEVICE-TESTED yet.

### Build flavors
* `sideload` (default for CI/GitHub releases): arm64-v8a and armeabi-v7a, in-app updater with SHA-256 + signer verification, declares REQUEST_INSTALL_PACKAGES.
* `store`: same ABIs; no install permission, no updater UI. Build with `./gradlew assembleStoreRelease`.
* Both: `./gradlew testSideloadDebugUnitTest lintSideloadDebug assembleSideloadRelease`.

### Low-end mode
Settings > Appearance & performance: effects mode Auto/Full/Reduced. Reduced (auto-selected on low-RAM devices or when system animations are off) disables backdrop blur, shared-element and navigation transitions and scrub previews. Image caches are capped (memory 12%, disk 50 MB); Settings > Storage shows sizes and clears caches.

### Privacy
No analytics, no tracking, no ads. Only network use: the update check (sideload), streams you open, and, in the sideload build only, OpenSubtitles when you tap Search or Download for subtitles (your own API key; the file hash and name are sent only at that moment).

---

### Native decoders and third-party licenses
* **FFmpeg (LGPL 2.1 or later), audio only.** Used as a fallback when the device has no MediaCodec decoder for an audio track (AC-3, E-AC-3, DTS, TrueHD, and others). Built from the pinned tag in `decoders/tools/versions.env` with `--disable-everything` plus an explicit decoder list; the build script refuses to continue unless FFmpeg reports an LGPL-only configuration (no GPL, no nonfree). It is **dynamically linked**: `libavcodec.so`, `libavutil.so` and `libswresample.so` are separate files in the APK's `lib/<abi>/` and can be replaced with your own build. Video never goes through FFmpeg.
* **dav1d (BSD-2-Clause), AV1 software decoding.** Used only when no hardware AV1 decoder can play the video, never in "Hardware only" mode, and capped (1080p30; 720p30 on low-end devices). Above the cap the player shows a message instead of stuttering.
* Some audio formats (AC-3, DTS, TrueHD) may carry patent or licensing obligations in some jurisdictions. This is not legal advice; check before distributing.
* All libraries and license texts are listed in the app under **Settings > Open-source licenses**. Details and build steps: `docs/DECODERS.md`.

## 3. How to Build in Android Studio

### Prerequisites
* **Native decoders (optional locally):** run `decoders/tools/build_all.sh` (needs NDK `26.1.10909125`, CMake `3.22.1`, `meson`, `ninja`, `git`). Without it the app still builds and runs, using MediaCodec only. CI always builds them.
* **Android Studio**: Android Studio Koala (2024.1+) or newer.
* **JDK**: OpenJDK 17.
* **Android SDK**: Android API 34 SDK platform and build-tools installed.

### Steps
1. **Open the Project**:
   * Launch Android Studio.
   * Select **File > Open** and choose the `LNXV` directory.
2. **Gradle Sync**:
   * Android Studio will automatically recognize `gradle/libs.versions.toml` and download dependencies.
3. **Build the Debug APK**:
   * In Android Studio, select **Build > Build Bundle(s) / APK(s) > Build APK(s)**, or run the following terminal command:
     ```bash
     ./gradlew assembleDebug
     ```
   * The compiled debug APK will be located at:
     ```
     app/build/outputs/apk/debug/app-debug.apk
     ```
4. **Deploy to Device**:
   * Connect an Android device with USB debugging enabled.
   * Run:
     ```bash
     adb install -r app/build/outputs/apk/debug/app-debug.apk
     ```

---

## 4. How to Generate a Signed Release APK

1. In Android Studio, navigate to **Build > Generate Signed Bundle / APK**.
2. Select **APK** and click **Next**.
3. Create or specify your production KeyStore file and enter credentials.
4. Select the **release** build variant and click **Finish**.
5. The signed APK will be output to `app/build/outputs/apk/release/app-release.apk`.


## Android compatibility (compatibility pass)

- Minimum supported Android version is Android 6.0 (API 23).
- Release builds package `arm64-v8a` and `armeabi-v7a` (x86_64 native decoders are only packaged in debug builds, for emulators).
- Android 13 (API 33), including TECNO Camon 19, remains within the declared OS range. This does not by itself diagnose or fix a launch crash on a particular device.
- The project currently retains its existing `compileSdk = 34` and `targetSdk = 34` toolchain settings. Apps can run on newer Android releases, but adopting the newest platform behavior requires a separate compile/target SDK and Android Gradle Plugin upgrade plus testing.
- Android 5.x and older are not supported by this configuration.

See `docs/ANDROID_COMPATIBILITY.md` for the compatibility notes and verification checklist.

## Documentation
[Architecture](docs/ARCHITECTURE.md) | [Testing](docs/TESTING.md) | [Design system](docs/DESIGN_SYSTEM.md) |
[Decoders](docs/DECODERS.md) | [Changelog](docs/CHANGELOG.md) | [Roadmap](docs/ROADMAP.md) | [Device test plan](docs/DEVICE_TEST_PLAN.md)
