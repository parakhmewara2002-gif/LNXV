# Android compatibility notes

## Declared compatibility

- `minSdk = 23` (Android 6.0 Marshmallow).
- No ABI filter is applied to the `sideload` flavor. The APK/AAB can include the ABIs supplied by the app and its dependencies.
- Existing `compileSdk = 34` and `targetSdk = 34` are intentionally unchanged in this pass to avoid an unverified build-toolchain migration.

## ABI coverage

Removing the explicit `arm64-v8a` filter allows Gradle to package available native libraries for supported ABIs. It does not manufacture native binaries for ABIs that a dependency does not publish. Verify the produced APK with `apkanalyzer` or `aapt dump badging` and test on physical 32-bit ARM and 64-bit ARM devices before advertising universal ABI support.

## OS coverage

Android 6.0/API 23 is the declared floor. Android 13/API 33 (TECNO Camon 19) is inside the supported range. Newer Android releases generally maintain backward app compatibility, but this project has not been built or device-tested here against every release. Some platform behaviors (permissions, foreground services, edge-to-edge, storage access, notifications, PiP, and media controls) vary by Android version and OEM.

## Important: target SDK is not the minimum OS version

`minSdk` controls the oldest installable Android release. `compileSdk` controls which platform APIs can be compiled against. `targetSdk` opts the app into platform behavior changes. This pass lowers the minimum and removes the sideload ABI restriction; it does not claim the project already targets the newest Android API. A separate upgrade should move AGP, Gradle, Kotlin/KSP and compile/target SDK together, then run the full build and device test matrix.

## Verification checklist

1. Sync Gradle and build `assembleSideloadDebug` and `assembleSideloadRelease`.
2. Inspect APK ABI contents; confirm `armeabi-v7a` is present only if all native dependencies provide it.
3. Install and launch on Android 6/API 23, Android 8/API 26, Android 13/API 33, and a current Android release.
4. Test media scanning, SAF file access, notification permission, foreground playback service, PiP, biometric vault (with non-biometric fallback), and app update flow.
5. Capture `adb logcat` on each startup failure; changing `minSdk` does not fix an app crash on a device already above the minimum.

## API 23-25 launcher icon fallback

The adaptive icon lives in `mipmap-anydpi-v26`, which does not exist for API 23-25. `res/mipmap-anydpi/ic_launcher*.xml` adds a `layer-list` of the same background/foreground drawables so `@mipmap/ic_launcher` resolves on those versions (API 26+ still uses the adaptive icon).
