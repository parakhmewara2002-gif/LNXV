# PROGRESS

Build status: authoring environment had no Android SDK/Maven access; every change is verified only by GitHub Actions on the PR. Nothing is device-tested.

## Done (increment 1)
- Phase 1.1 encrypted vault (move/restore/playback/thumbnails/migration/orphan cleanup)
- Phase 1.2 PBKDF2 PIN, lockout, Keystore-wrapped key, 4–8 digits, forgot-PIN wipe
- Phase 1.3 auto-lock, player auth gate, FLAG_SECURE, block-screenshots setting
- Phase 1.4 manifest/network/backup/FileProvider hardening, intent validator
- Phase 1.5 updater integrity (checksum, signer, trusted hosts, Wi-Fi-only, skip version, notes)
- Phase 1.6 narrower ProGuard, CI checksum + lint
- G2 flags, G3 startup (no runBlocking, splash, StrictMode), G5 app scope, G6 reflection guard, G8 LeakCanary

## Done (increment 2, CI green)
- Phase 1 gap: Change-PIN; README security model rewritten
- Phase 2.1 Open-with (VIEW intents validated, library video reused for resume, else transient, flag OPEN_WITH_INTENTS)
- Phase 2.2 background playback: MediaSession service bound via controller only while background play is on, audio focus + becoming-noisy, vault titles hidden ("Private video"), POST_NOTIFICATIONS asked lazily
- Phase 2.3 network streams: HLS/DASH/RTSP modules added, URL validator, recent list without credentials, friendly errors (Plain HTTP blocked in release)

## Done (increment 3, CI green)
- Phase 2.4 queue per folder, auto-play next, shuffle, repeat off/one/all (`QueueLogic`, unit tested); Room playlists (DB v3, migration 2→3), "Add to playlist", Playlists tab (play / delete); vault videos never appear in playlists
- Phase 2.5 audio delay 0..+2000 ms in 50 ms steps via a custom `AudioProcessor` (unit tested)

## In progress / remaining in Phase 1
- BiometricPrompt unlock (needs FragmentActivity + androidx.biometric)
- detekt/ktlint/OWASP dependency-check in CI; pin Actions to SHAs
- REQUEST_INSTALL_PACKAGES only in sideload flavor (with Phase 4 flavors)

## Done (Phase 3, CI green, NOT DEVICE-TESTED)
- Theming: dynamic color (Android 12+), Appearance & performance settings (effects mode Auto/Full/Reduced), `LocalReduceEffects` gates blur, shared elements and transitions.
- Haze 1.0.2 backdrop blur on the Home top bar; Compose BOM bumped to 2024.09.03.
- Shared-element container transform card -> player (skipped when reduce effects).
- Player polish: animated play/pause morph, spring thumb scale while scrubbing, frame preview while scrubbing (local content/file only, never vault/network).
- Not done in 3: vault unlock success animation, spring HUD for brightness/volume/seek.

## Done (Phase 4, increment 1, CI green, NOT DEVICE-TESTED)
- Product flavors: `sideload` (arm64-only, updater + install permission) and `store` (REQUEST_INSTALL_PACKAGES removed via manifest merge, updater UI hidden). CI builds both; release artifact is the sideload APK.

## Done (Phase 5 increment 1, CI green, NOT DEVICE-TESTED)
- Sleep timer: "End of video" + 10 s volume fade-out, wall-clock based (flag SLEEP_TIMER_EXTRAS).
- Per-video playback speed in DataStore, capped at 200 entries, not stored for vault videos (flag PER_VIDEO_SPEED).
- Skip silence (flag SKIP_SILENCE, off by default). Equalizer presets via android.media.audiofx (flag EQUALIZER, off by default).
- Screenshots are blocked for vault videos.
- Already existed before Phase 5: resume modes, touch lock, sleep timer, screenshot.
- Phase 5 increment 2: rotate/flip video (VIDEO_TRANSFORMS, off), volume boost via LoudnessEnhancer (under EQUALIZER flag), subtitle styling (SUBTITLE_STYLING), gesture sensitivity (GESTURE_SENSITIVITY).
- Phase 5 NOT done, with reasons: true loudness normalization (needs analysis pass; only a fixed boost exists), audio-only/screen-off mode (background playback exists, dedicated mode not built), chapter markers (no reliable chapter API in Media3 1.4.1), brightness/contrast/saturation (needs GL effects pipeline; Media3 Effects are Transformer-oriented), online subtitle search (needs network + user API key UI), per-axis gesture customization.

## Done (Phase 4, increment 2)
- HomeScreen split: list/card composables moved to HomeComponents.kt. PlayerScreen NOT split. DI (Hilt) intentionally skipped: manual container in LNXVApplication is enough and Hilt adds KSP/build risk with no local build.

## Next
Phase 4 remainder: split PlayerScreen/HomeScreen, DI + PlayerController interface, Baseline Profile, detekt/ktlint/OWASP CI, Actions SHA pinning. Then Phases 5 and 6. Phase 2.6 (floating mini player, Cast) intentionally skipped: it is optional and SYSTEM_ALERT_WINDOW was removed in Phase 1.4.

## Not started
Phases 3, 4, 5, 6 and the LOW RAM / LOW STORAGE rules (A–D).
