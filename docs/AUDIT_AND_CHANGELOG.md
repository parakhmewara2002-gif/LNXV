# LNXV – Audit, Root Causes, Changelog & Test Report

Status key: **FIXED** = code changed and compiled in CI. **NOT DEVICE-TESTED** = no real-device run was performed in this environment.

## 1. Audit (P0/P1 findings)

| # | File | Problem | Root cause | Severity | Status |
|---|------|---------|-----------|----------|--------|
| 1 | `Media3PlayerManager.seekTo/seekRelative` | Seeking could jump to 0 (looks like "forward seek goes backward") | Clamp used `exoPlayer.duration.coerceAtLeast(0)`. When duration is `C.TIME_UNSET` (buffering/unknown) the upper bound became 0, so every seek went to the start | P0 | FIXED (`SeekMath.clamp` treats unknown duration as unbounded) |
| 2 | `PlayerScreen` seek bar | Thumb jumped back while dragging; a seek was issued on every drag event | `onValueChange` called `seekTo` immediately while the slider value was bound to a position that is only refreshed every 250 ms (stale value overwrote the drag) | P0 | FIXED (local drag state; one seek on release) |
| 3 | `PlayerScreen` | No real fullscreen | Only `enableEdgeToEdge()`; no `WindowInsetsController` hide, no cutout mode, no keep-screen-on | P0 | FIXED (immersive hide, transient bars on swipe, re-applied on every resume, restored on exit) |
| 4 | `PlayerScreen` / `PlayerViewModel` | Video kept playing after closing the player | Player is an app-wide singleton and nothing stopped it on exit | P0 | FIXED (`closePlayer()` on back/system back/ViewModel cleared; ownership token prevents a late-cleared screen from stopping a newer video) |
| 5 | `MainActivity.onUserLeaveHint` | PiP could start from any screen and ignored the "Auto PiP" setting | No screen/pref check | P0 | FIXED |
| 6 | `MainActivity` | PiP window dismissed / activity finishing left audio playing | No `onStop`/`onDestroy` handling | P0 | FIXED |
| 7 | `LNXVPlaybackService.onDestroy` | Would release the app-wide shared ExoPlayer | Service called `player.release()` | P1 | FIXED (service never started today; hazard removed) |
| 8 | `UserPreferences.isBackgroundPlayEnabled` | Default `true` although no foreground service is started | Misleading default | P1 | FIXED (default false) |
| 9 | `LNXVTheme` / screens | True Black toggle had no effect | `isAmoled` was only read by the Settings switch; theme never received it; screens hard-coded `Color.Black` | P1 | FIXED (theme reads DataStore; Home/Settings/Vault use theme background) |
| 10 | `PlayerGestureDetector` | Swipe brightness persisted after leaving the player | Window brightness override never reset | P1 | FIXED |

## 2. Added
- Frame-by-frame stepping (overflow menu, uses stream frame rate, 30 fps fallback).
- `SeekMath` (pure Kotlin) used by the seek bar, gestures and the player manager.

## 3. Automated tests
`app/src/test/.../SeekMathTest.kt` (JUnit): unknown-duration regression, begin/end clamping, forward/backward, rapid repeated seeks, fraction conversion, swipe seek.
Run: `./gradlew testDebugUnitTest`. CI prints the result as a `Unit tests` annotation on the workflow run.

## 4. Not done / not verified (honest list)
- No instrumented/UI tests (fullscreen, lifecycle, theme persistence, navigation): need a device or emulator.
- No real-device testing was possible here.
- Light theme is not implemented (screens use hard-coded white text); "Dark mode" is forced dark.
- Subtitle/audio **delay** not implemented (Media3 has no built-in offset).
- True background audio (foreground service + notification) is not wired; with the setting ON playback simply continues while the process lives.
- Phase 4 (full visual redesign) and Phase 5 profiling were not performed; no performance numbers are claimed.

## 5. Build
`./gradlew testDebugUnitTest assembleRelease` (signed with the project keystore via CI secrets).
