# LNXV hardening — final report

Status: every change below compiled and unit-tested in GitHub Actions only (PR #1, branch claude/phase1-security). NOTHING is device-tested. No performance or size numbers were measured.

## Changes per phase
- Phase 1 Security: chunked AES-256-GCM vault with Keystore-wrapped key, PBKDF2 PIN + persisted lockout, auto-lock, FLAG_SECURE, manifest/network/backup hardening, updater checksum + signer verification, intent validation, narrower ProGuard, CI checksum + lint.
- Phase 2 Core: Open-with intents, playback error handling, audio focus, background playback connector, network streams, queue/shuffle/repeat/playlists, audio delay. (2.6 mini player/Cast skipped.)
- Phase 3 Graphics: dynamic color, reduce-effects switch, Haze blur on Home top bar, shared-element transition, player polish (play/pause morph, thumb spring, scrub preview).
- Phase 4 Architecture: flavors, HomeScreen + PlayerScreen splits, PlayerController + Koin DI (PlayerViewModel), baseline profile + macrobenchmark module (never run), detekt/ktlint/OWASP CI. NOT done: Actions SHA pinning, other VMs on DI, controls overlay extraction.
- Phase 5 Playback: sleep timer extras, per-video speed, skip silence, equalizer + volume boost, rotate/flip, subtitle styling, gesture sensitivity, vault screenshot block.
- Phase 6 Library: smart collections, NEW badge, progress bar, Storage + Privacy settings, first-run explanation. Rule B: Coil disk cap 50 MB, memory 12%.

## Assumptions
Koin chosen over Hilt (no codegen, no local build to validate KSP/kapt). Volume boost is fixed gain, not normalization.

## New dependencies (and why)
- androidx.core:core-splashscreen: splash without blocking startup.
- LeakCanary (debug): leak detection.
- media3 hls/dash/rtsp: network stream formats.
- Koin 3.5.6 (koin-android, koin-androidx-compose): DI without codegen.
- androidx.profileinstaller 1.3.1: installs the baseline profile on devices. :benchmark module uses androidx.benchmark macro 1.2.4, uiautomator, test-ext (test-only).
- Haze 1.0.2: real backdrop blur on one bar (RenderEffect-based, fallback below Android 12).
- Compose BOM 2024.09.03: required for shared transitions.

## DB migrations
v1->v2 (vaultFile column, vault_thumbs), v2->v3 (playlists, playlist_items). Per-video speed and onboarding use DataStore, no migration.

## Feature flags
ENCRYPTED_VAULT, VAULT_AUTO_LOCK, SECURE_UPDATER_CHECKS, OPEN_WITH_INTENTS, PER_VIDEO_SPEED, SLEEP_TIMER_EXTRAS, SKIP_SILENCE (off), EQUALIZER (off), VIDEO_TRANSFORMS (off), SUBTITLE_STYLING, GESTURE_SENSITIVITY.

## Known limitations
Encrypted MKV lacks the late-Cues seek fix; interrupted encryption restarts; no negative audio delay; no embedded/ASS subtitle shift on Media3 1.4.1; playlist rename/reorder/remove missing; shared-element transition may glitch with SurfaceView; R8 behaviour of reflection-loaded HLS/DASH/RTSP and narrowed ProGuard untested; biometric unlock not built.

## Not done (and why)
- Phase 4: Actions SHA pinning (SHAs not verifiable from the build environment), Home/Settings/Vault ViewModels on DI, PlayerViewModel unit test with a fake controller.
- Phase 5: true loudness normalization, audio-only mode, chapters (no reliable Media3 1.4.1 API), brightness/contrast/saturation (needs GL pipeline), online subtitle search, per-axis gesture customization.
- Phase 6: Paging 3 / windowed queries, multi-select actions, duplicate finder, search filters, hidden-folders UI, SAF picker, shortcuts/widget, adaptive/TV layouts, accessibility audit, Hindi localization and string extraction, thumbnail size slider.
- Rules A/C/D: remove material-icons-extended, locale filters, custom ExtractorsFactory, DefaultLoadControl tuning, WebP thumbnails, R8 full mode, APK size measurement.
See also docs/DEVICE_CHECKLIST.md and PROGRESS.md.
