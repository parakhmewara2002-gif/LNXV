# LNXV — Premium Android Video Player

LNXV is an advanced, production-ready Android video player built with **Jetpack Compose**, **Material 3**, and **AndroidX Media3 ExoPlayer**. It features an original, iOS-inspired **Liquid Glass** aesthetic, hardware-accelerated playback with software fallback, rich touch gestures, and a local Room-backed media library.

---

## 1. Technical Stack & Architecture

* **UI Framework**: 100% Jetpack Compose with Material 3 and custom Liquid Glass modifiers.
* **Media Playback Engine**: AndroidX Media3 ExoPlayer (`1.4.1`) with hardware `MediaCodec` acceleration and software decoder fallback.
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
* **Subtitles**: Embedded tracks and external `.srt`, `.ass`, `.ssa`, and `.vtt` subtitles with styling and delay synchronization.
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
* `sideload` (default for CI/GitHub releases): arm64-v8a only, in-app updater with SHA-256 + signer verification, declares REQUEST_INSTALL_PACKAGES.
* `store`: no install permission, no updater UI. Build with `./gradlew assembleStoreRelease`.
* Both: `./gradlew testSideloadDebugUnitTest lintSideloadDebug assembleSideloadRelease`.

### Low-end mode
Settings > Appearance & performance: effects mode Auto/Full/Reduced. Reduced (auto-selected on low-RAM devices or when system animations are off) disables backdrop blur, shared-element and navigation transitions and scrub previews. Image caches are capped (memory 12%, disk 50 MB); Settings > Storage shows sizes and clears caches.

### Privacy
No analytics, no tracking, no ads. Only network use: the update check (sideload) and streams you open.

---

## 3. How to Build in Android Studio

### Prerequisites
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
