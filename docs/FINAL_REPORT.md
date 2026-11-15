# LNXV hardening — final report

Status: every change below compiled and unit-tested in GitHub Actions only (PR #1, branch claude/phase1-security). NOTHING is device-tested. No performance or size numbers were measured.

## Changes per phase
- Phase 1 Security: chunked AES-256-GCM vault with Keystore-wrapped key, PBKDF2 PIN + persisted lockout, auto-lock, FLAG_SECURE, manifest/network/backup hardening, updater checksum + signer verification, intent validation, narrower ProGuard, CI checksum + lint.
- Phase 2 Core: Open-with intents, playback error handling, audio focus, background playback connector, network streams, queue/shuffle/repeat/playlists, audio delay. (2.6 mini player/Cast skipped.)
- Phase 3 Graphics: dynamic color, reduce-effects switch, Haze blur on Home top bar, shared-element transition, player polish (play/pause morph, thumb spring, scrub preview).
- Phase 4 Architecture: flavors, HomeScreen + PlayerScreen splits, PlayerController + Koin DI (PlayerViewModel), baseline profile + macrobenchmark module (never run), detekt/ktlint/OWASP CI. NOT done: other VMs on DI, controls overlay extraction.
- Phase 5 Playback: sleep timer extras, per-video speed, skip silence, equalizer + volume boost, rotate/flip, audio-only mode, subtitle styling, gesture sensitivity, vault screenshot block.
- Phase 6 Library: smart collections, duplicates, NEW badge, progress bar, multi-select bulk actions, search filter tokens, playlist editing, adaptive grid, Hindi (partial), seek/keypad accessibility, Storage + Privacy settings, first-run explanation. Rule B: Coil disk cap 50 MB, memory 12%.

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
ENCRYPTED_VAULT, VAULT_AUTO_LOCK, SECURE_UPDATER_CHECKS, OPEN_WITH_INTENTS, PER_VIDEO_SPEED, SLEEP_TIMER_EXTRAS, AUDIO_ONLY_MODE, SKIP_SILENCE (off), EQUALIZER (off), VIDEO_TRANSFORMS (off), SUBTITLE_STYLING, GESTURE_SENSITIVITY.

## Known limitations
Encrypted MKV lacks the late-Cues seek fix; interrupted encryption restarts; no negative audio delay; no embedded/ASS subtitle shift on Media3 1.4.1; playlist rename/reorder/remove missing; shared-element transition may glitch with SurfaceView; R8 behaviour of reflection-loaded HLS/DASH/RTSP and narrowed ProGuard untested; biometric unlock not built.

## Not done (and why)
- Phase 4: Home/Settings/Vault ViewModels on DI, PlayerViewModel unit test with a fake controller.
- Phase 5 (infeasible on this stack, see changelog section 13): true loudness normalization, chapters, online subtitle search; per-axis gesture customization not built.
- Phase 6: Paging 3, SAF picker, shortcuts/widget, full Hindi string extraction (Settings/Player/Vault), TalkBack audit, TV layouts (reasons in PROGRESS.md).
- Rules A/C/D: remove material-icons-extended, locale filters, custom ExtractorsFactory, DefaultLoadControl tuning, WebP thumbnails, R8 full mode, APK size measurement.
See also docs/DEVICE_CHECKLIST.md and PROGRESS.md.

## Update: final round
Done since the last report: Rules A (icons, extractors, locales, R8 full, AAB), B (cache cleanup), C (load control, no largeHeap), and video brightness/contrast/saturation (my earlier "infeasible" claim was wrong). Sizes are printed by CI in the run's step summary; none measured on device.

Also done in the last round (all CI green except benchmarks, see below): Favorites smart collection; search filters `dur>30m`, `dur<90s`, `folder:name`; partial-hash duplicate confirmation (background, cancellable, <=192 KiB read per file); thumbnail size setting; multi-select share / rename / delete-from-device (MediaStore createDeleteRequest/createWriteRequest on API 30+, direct delete on 26-29); keyboard / D-pad / media-key player controls; subtitle font choice; independent brightness / volume / seek / long-press gesture switches (volume was wrongly tied to brightness, fixed); "Continue watching" app shortcut and home-screen widget (vault titles never shown); optional biometric vault unlock (flag BIOMETRIC_UNLOCK, default off, BiometricPrompt STRONG, PIN stays; app-level gate, not CryptoObject-bound); audio-only keeps playing when the screen turns off; ~40 more strings extracted (en + hi, Hindi not native-reviewed); Home-scroll and Player-open macrobenchmarks (compile-only in CI, never run).

Deliberately NOT done, with reasons:
- Online subtitle search: OpenSubtitles downloads require a logged-in account, which would mean storing user credentials; not worth the security cost.
- Chapter markers: Media3 1.4.1 does not expose Matroska/MP4 chapters through the player API; would need a custom parser.
- True loudness normalization: needs an analysis pass (EBU R128); only the existing volume boost (LoudnessEnhancer) is offered.
- Orientation: existing auto / landscape / portrait lock and cutout handling kept; no extra modes added.
- SAF folder picker, Paging 3, WebP thumbnail re-encoding, Koin for Home/Settings/Vault ViewModels, instrumented UI tests (vault lock/unlock, open-with), PlayerViewModel fake-controller test (PlayerViewModel needs an Application, so not JVM-constructible), a full TalkBack / 48dp audit, dedicated tablet/foldable layouts (grid already adapts to width and thumbnail size), remaining string extraction in less-visited screens. Each is a scoped follow-up.

Everything shipped remains NOT DEVICE-TESTED; see docs/DEVICE_CHECKLIST.md.
