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

## 3b. Later phases (all compiled in CI; none device-tested)
**P2 performance:** `@Immutable` on model/state classes (Kotlin 2.0.0 has strong skipping off by default, so `Uri`/`List` made them unstable); player position no longer stored in `PlaybackState` (no whole-screen recomposition each second); request highest refresh-rate display mode; DataStore pre-warm in `Application`. No timing numbers were measured.
**P3 playback:** load external `.srt`/`.vtt` subtitle file; subtitle delay in 0.5 s steps (cue times shifted in a cached copy, playback position kept). 7 extra unit tests (`SubtitleShifterTest`).
**P4 visual polish:** spring press feedback on cards (`bouncyClickable`), animated tab pill, animated empty state, buffering shown as a fading glass disc, bottom sheets with rounded top + drag handle, softer controls fade.

**Diagnostics (Video Properties -> Copy/Share diagnostic report):** plain-text report with device/app version, file facts, a direct read of the container structure (Matroska: SeekHead/Cues check with a verdict; MP4: moov/mdat order), player state, decoders, dropped frames, every track (supported/selected), last error and a rolling event log that records each seek request and the position the player actually jumped to. Pure-Kotlin parsers are covered by unit tests (`ContainerInspectorsTest`).

**MKV seek workaround (found from a real diagnostic report):** the user's 2.67 GB mkvmerge-produced MKV has Cues at the end of the file, but the SeekHead at the start lists only Info/Tracks/*another SeekHead*; Cues are listed only in that second SeekHead after the video data. Media3 1.4.1 `MatroskaExtractor` only accepts a Cues entry directly (source: `endMasterElement(ID_SEEK)`), so at the first Cluster it sends `SeekMap.Unseekable` -> every seek restarts from 0 (the original "forward seek goes backward" symptom). LNXV now reads the nested SeekHeads (cheap: a few small reads) and tells a `MatroskaExtractor` subclass where Cues are (private field `cuesContentPosition`, set via reflection, kept by an R8 rule). Healthy files are untouched. The earlier inspector verdict "seeking should work" for this file was wrong because it merged all SeekHeads; it now reports start vs. late SeekHeads separately. NOT device-tested yet.

**Themes (System / Light / Dark + AMOLED True Black):** new `LNXVColors` palette (`Palette.kt`) provided through a CompositionLocal; Home, Settings and Vault read colours from it instead of hard-coded white/black, so Light theme is now real. Glass cards use a light-tinted fill/border in Light theme; a soft two-colour glow (`ambientBackground`) sits behind the glass in Light and Dark so the translucency is visible (AMOLED stays pure #000000). Settings -> Appearance has a System/Light/Dark chooser (persisted in DataStore); AMOLED applies to Dark. The video player is always dark. NOT device-tested.

**Player panels (from user screenshots):** the Aspect Ratio sheet was cut off at the bottom in landscape (9 options, only 3 visible, no scrolling) and every popup/sheet/dropdown opened in its own window, which made Android bring the navigation buttons back (fullscreen broken). Speed/Aspect/Audio/Subtitle/Properties panels and the ⋮ menu are now drawn inside the player: right-side panel in landscape, bottom panel in portrait, scrollable, spring slide-in, back-press closes them. Frosted-glass look: a tiny `PixelCopy` snapshot of the video is stretched behind the panel (bilinear = soft blur on every Android version) with a tint, specular highlight and gradient border. System bars are also re-hidden on window-focus changes and inside the remaining AlertDialogs. NOT device-tested.

## 4. Not done / not verified (honest list)
- No instrumented/UI tests (fullscreen, lifecycle, theme persistence, navigation): need a device or emulator.
- No real-device testing was possible here.
- Light theme covers Home, Settings and Vault; the video player and its dialogs intentionally stay dark.
- Audio delay not implemented (Media3 has no built-in offset). Subtitle delay only works on loaded external SRT/VTT, not embedded or ASS subtitles.
- True background audio (foreground service + notification) is not wired; with the setting ON playback simply continues while the process lives.
- Only a focused subset of Phase 4 was done (see 3b); no profiling was performed and no performance numbers are claimed.

## 5. Build
`./gradlew testDebugUnitTest assembleRelease` (signed with the project keystore via CI secrets).
