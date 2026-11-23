# Android compatibility notes

## Declared compatibility

- `minSdk = 23` (Android 6.0 Marshmallow).
- No ABI filter is applied to the `sideload` flavor. The APK/AAB can include the ABIs supplied by the app and its dependencies.
- The current app module declares `compileSdk = 36` and `targetSdk = 34`. These are separate from the `minSdk` install floor.

## ABI coverage

The current `app/build.gradle.kts` explicitly packages `arm64-v8a` and `armeabi-v7a` in release builds. Debug builds additionally allow `x86_64` for emulators. This is not universal ABI support: verify that every required native decoder library exists for both release ABIs in the produced APK, and test on physical 32-bit ARM and 64-bit ARM devices before advertising compatibility.

## OS coverage

Android 6.0/API 23 is the declared floor. Android 13/API 33 (TECNO Camon 19) is inside the supported range. Newer Android releases generally maintain backward app compatibility, but this project has not been built or device-tested here against every release. Some platform behaviors (permissions, foreground services, edge-to-edge, storage access, notifications, PiP, and media controls) vary by Android version and OEM.

## Important: target SDK is not the minimum OS version

`minSdk` controls the oldest installable Android release. `compileSdk` controls which platform APIs can be compiled against. `targetSdk` opts the app into platform behavior changes. The current build explicitly limits release ABIs to `arm64-v8a` and `armeabi-v7a`; it does not claim universal ABI coverage. The target SDK remains 34 and needs a separate platform-behavior upgrade and device test pass. A separate upgrade should move AGP, Gradle, Kotlin/KSP and compile/target SDK together, then run the full build and device test matrix.

## Verification checklist

1. Sync Gradle and build `assembleSideloadDebug` and `assembleSideloadRelease`.
2. Inspect APK ABI contents; confirm `armeabi-v7a` is present only if all native dependencies provide it.
3. Install and launch on Android 6/API 23, Android 8/API 26, Android 13/API 33, and a current Android release.
4. Test media scanning, SAF file access, notification permission, foreground playback service, PiP, biometric vault (with non-biometric fallback), and app update flow.
5. Capture `adb logcat` on each startup failure; changing `minSdk` does not fix an app crash on a device already above the minimum.

## API 23-25 launcher icon fallback

The adaptive icon lives in `mipmap-anydpi-v26`, which does not exist for API 23-25. `res/mipmap-anydpi/ic_launcher*.xml` adds a `layer-list` of the same background/foreground drawables so `@mipmap/ic_launcher` resolves on those versions (API 26+ still uses the adaptive icon).
