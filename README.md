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
* 4-digit PIN authentication verified against a salted SHA-256 hash.
* Private videos are isolated in an app-internal sandbox directory (`context.filesDir/lnxv_vault`) and shielded with a `.nomedia` file to exclude them from device gallery indexing.
* *Security Note*: Vault security relies on Android's Linux UID sandboxing and app-level PIN gating. File content is not AES/file-level encrypted on disk.

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
