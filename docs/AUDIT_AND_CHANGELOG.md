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

## 4. Hardening roadmap — Phase 1 (Security), increment 1
Status key here: **IMPLEMENTED (CI-compile pending)** = written, not yet built locally (the authoring environment has no Android SDK or Maven access); **NOT DEVICE-TESTED** = never run on a device. Nothing below has been run on a device.

| Item | What changed | Root cause | Files | Status |
|---|---|---|---|---|
| 1.1 Encrypted vault | Move = free-space check → chunked AES-256-GCM copy to `filesDir/lnxv_vault/<uuid>.lnxv.part` → full decrypt-and-SHA-256 verify → atomic rename → Room update → delete original (`createDeleteRequest` on API 30+, `RecoverableSecurityException` on 29, direct on ≤28). Restore decrypts into `Movies/LNXV` via MediaStore (`IS_PENDING`). Encrypted WebP thumbnail in `vault_thumbs`. Room v1→v2 migration; legacy `isVault` rows keep working and can be encrypted via "Encrypt now". Orphan `.part`/unreferenced files cleaned when the vault opens. | `moveToVault()` only flipped `isVault`; file stayed public in MediaStore | `core/security/ChunkedAead.kt`, `VaultKeyManager.kt`, `data/repository/VaultRepository.kt`, `VaultTransferManager.kt`, `core/database/*` | IMPLEMENTED (CI-compile pending), NOT DEVICE-TESTED |
| 1.1 Playback | `EncryptedDataSource` + `VaultRoutingDataSource` (`lnxvvault://v/<file>`): random-access decrypt in memory, nothing written to disk/cache. | — | `playback/engine/*DataSource.kt`, `Media3PlayerManager.kt` | IMPLEMENTED (CI-compile pending), NOT DEVICE-TESTED |
| 1.2 PIN | Random 16-byte per-install salt + PBKDF2WithHmacSHA256 (210 000 iterations), `MessageDigest.isEqual`, 4–8 digits (6 recommended), persisted lockout (5 free tries, then 30 s / 1 m / 5 m / 15 m / 1 h) with countdown UI, change/forgot flow (forgot = irreversible wipe with warning). Legacy SHA-256 hash is verified once and migrated. Master key wrapped by an Android Keystore AES-GCM key (StrongBox if available, TEE fallback). | Static salt `LNXV_SALT_$pin` + SHA-256 (4 digits ⇒ trivially reversible) | `core/security/PinHasher.kt`, `LockoutPolicy.kt`, `VaultKeyManager.kt`, `UserPreferencesDataStore.kt` | IMPLEMENTED (CI-compile pending) |
| 1.3 Session | Auto-lock on background (immediately/30 s/1 m/5 m) and on screen-off; lock cancels transfers, zeroes the key, stops vault playback and pops Vault/Player routes; Player refuses vault items while locked; `FLAG_SECURE` on Vault and on vault playback; "Block screenshots everywhere" setting. | — | `MainActivity.kt`, `LNXVApplication.kt`, `LNXVNavigationHost.kt`, `PlayerViewModel.kt` | IMPLEMENTED (CI-compile pending) |
| 1.4 Manifest | Cleartext removed (+ `network_security_config`, debug-only cleartext override), `allowBackup=false`, backup/extraction rules exclude everything, `SYSTEM_ALERT_WINDOW` removed, FileProvider limited to `cache/updates/`, VIEW intent validator. | — | `AndroidManifest.xml`, `res/xml/*`, `core/util/IncomingIntentValidator.kt` | IMPLEMENTED (CI-compile pending) |
| 1.5 Updater | CI publishes `LNXV.apk.sha256`; updater downloads it first, follows only HTTPS github.com / objects.githubusercontent.com (each redirect hop validated), verifies SHA-256 and the signing certificate against the installed app, deletes on mismatch; release notes, "Skip this version", Wi-Fi-only option. | — | `core/update/*`, `build-apk.yml`, Settings | IMPLEMENTED (CI-compile pending) |
| 1.6 Build | Blanket `-keep class androidx.media3.** {*;}` and `androidx.room.**` rules removed; only the `MatroskaExtractor.cuesContentPosition` rule and entity rules kept. Lint step added (non-blocking until baseline is clean). | — | `proguard-rules.pro`, `build-apk.yml` | IMPLEMENTED; **R8 release behaviour NOT TESTED** |
| G2 flags | `FeatureFlag` enum, DataStore-backed, Settings > Security / Advanced > Experimental. | — | `core/flags/FeatureFlag.kt` | IMPLEMENTED |
| G3 startup | `runBlocking` removed from `MainActivity`; SplashScreen API holds the splash until DataStore emits; unscoped `CoroutineScope(Dispatchers.IO).launch` replaced by `appScope` (SupervisorJob); StrictMode (debug only). | `runBlocking` on main thread | `MainActivity.kt`, `LNXVApplication.kt` | IMPLEMENTED |
| G6 / G8 | `SeekFixReflectionGuardTest` fails if the private Media3 field disappears (runtime already falls back gracefully). LeakCanary added (debug only). | — | `SeekFixReflectionGuardTest.kt`, `app/build.gradle.kts` | IMPLEMENTED |

Tests added: `PinHasherTest`, `LockoutPolicyTest`, `ChunkedAeadTest` (round-trip, random-access seek, tamper, truncation, wrong key), `VaultKeyManagerTest`, `UpdateVerifierTest`, `IncomingIntentValidatorTest`, `FeatureFlagTest`, `SeekFixReflectionGuardTest`.

### Known gaps in this increment (see PROGRESS.md)
Biometric unlock, Change-PIN screen (repository supports re-keying only via forgot/wipe), REQUEST_INSTALL_PACKAGES split into a sideload flavor (Phase 4), true byte-level resume of an interrupted encryption (a cancelled/crashed copy is discarded and restarted; no orphan files remain), "hide vault from PiP/notification metadata" (no media notification exists yet — Phase 2.2), pinning GitHub Actions to commit SHAs (SHAs could not be verified offline), detekt/ktlint/OWASP in CI. MKV late-Cues seek fix does not apply to encrypted MKV files (the inspector reads `content://`/`file://` only); seeking still works through Matroska's normal Cues handling.

## 5. Increment 2 (CI-compiled, NOT DEVICE-TESTED)
| Item | Change | Files |
|---|---|---|
| Change PIN | Verifies old PIN (lockout applies), stores new PBKDF2 credential; key is Keystore-wrapped so no re-encryption | `VaultRepository.changePin`, `VaultScreen.kt` |
| 2.1 Open with | `MainActivity` handles VIEW in onCreate/onNewIntent (`singleTask`), validates scheme/mime, reuses a library video when the URI is a MediaStore id, else plays a transient `VideoItem` (id -1, not in Room); persistable permission taken when offered | `MainActivity.kt`, `LNXVNavigationHost.kt`, `PlayerViewModel.kt`, manifest |
| 2.2 Background play | `BackgroundPlaybackConnector` binds `LNXVPlaybackService` only when the setting is on; ExoPlayer now has audio focus + `handleAudioBecomingNoisy`; media metadata title set (vault = "Private video"); service never releases the shared player | `playback/service/*`, `Media3PlayerManager.kt` |
| 2.3 Network streams | `StreamUrl` validator (HTTP(S)/RTSP, HTTP only in debug), recent URLs (max 10, credentials stripped), Home "Open network stream" dialog, friendly playback errors. New deps: media3-exoplayer-hls/dash/rtsp (same pinned 1.4.1; required for HLS/DASH/RTSP). R8 reflection loading of these factories relies on Media3 consumer rules: NOT TESTED on a release build. | `core/util/StreamUrl.kt`, `HomeScreen.kt`, `Media3PlayerManager.kt` |
Known limits: per-video resume for transient opens is not stored; network-specific larger buffers (rule C) not yet applied.

## 6. Increment 3 (CI-compiled, NOT DEVICE-TESTED)
| Item | Change | Files |
|---|---|---|
| 2.4 Queue | Per-folder queue (title order), auto-play next on end, previous (restarts if >3 s in), shuffle (current first), repeat off/one/all (one = ExoPlayer REPEAT_MODE_ONE). Prev/Next buttons appear when the queue has >1 item; shuffle/repeat in the overflow menu. Vault and transient videos get no queue. | `core/model/QueueLogic.kt`, `PlayerViewModel.kt`, `PlayerScreen.kt` |
| 2.4 Playlists | Room v3 (`playlists`, `playlist_items`), migration 2→3. Home: "Add to playlist" in video menus, Playlists tab (tap = play whole playlist as queue, Delete). Vault rows are excluded by the queries. Not done: rename, remove single item, reorder. | `core/database/PlaylistEntities.kt`, `LNXVDatabase.kt`, `HomeViewModel.kt`, `HomeScreen.kt` |
| 2.5 Audio delay | `AudioDelayProcessor` (16-bit PCM only, inactive otherwise) inserts silence; changing delay while playing inserts/drops samples; re-applied after seek. Range 0..+2000 ms. **Negative delay is not supported** (advancing audio would require delaying the video). | `playback/engine/AudioDelayProcessor.kt`, `Media3PlayerManager.kt` |
| 2.5 Subtitle delay | Existing delay works for external SRT/VTT (cue-shifted copy). **Embedded and ASS/SSA tracks cannot be shifted with Media3 1.4.1** (no public API to offset text-track timing): documented limit. | — |
| 2.6 | Skipped (optional): no floating mini player (SYSTEM_ALERT_WINDOW removed) and no Cast. | — |
