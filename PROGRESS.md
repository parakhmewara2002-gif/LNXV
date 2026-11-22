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
- Phase 5 increment 3: audio-only mode (AUDIO_ONLY_MODE: video renderer disabled per video).
- Phase 5 NOT done, with reasons: true loudness normalization (needs analysis pass; only a fixed boost exists), chapter markers (no reliable chapter API in Media3 1.4.1), brightness/contrast/saturation (needs GL effects pipeline; Media3 Effects are Transformer-oriented), online subtitle search (needs network + user API key UI), per-axis gesture customization.

## Phase 4 final status (CI green except the last split push; NOT DEVICE-TESTED)
- Flavors; HomeScreen split; PlayerScreen split (PlayerMenu.kt, PlayerOverlays.kt, PlayerSidePanels.kt; ~1015 -> ~780 lines, the controls overlay remains inline).
- PlayerController interface + Koin DI (PlayerViewModel migrated; Home/Settings/Vault VMs still read LNXVApplication directly).
- Baseline profile (hand-written wildcard rules + profileinstaller) and :benchmark module (startup benchmark + profile generator), compiled in CI but NEVER RUN (needs a device). No startup numbers exist.
- CI: non-blocking detekt + ktlint reports (artifact `static-analysis`), weekly OWASP dependency-check workflow (limited: it scans declared files, optional NVD_API_KEY secret).
- Actions pinned to commit SHAs (resolved with git ls-remote from the action repos; tag shown in a comment). NOT done: unit test of PlayerViewModel with a fake controller (needs Robolectric/Application).

## Done (Phase 4, increment 2)
- HomeScreen split: list/card composables moved to HomeComponents.kt. PlayerScreen NOT split. DI (Hilt) intentionally skipped: manual container in LNXVApplication is enough and Hilt adds KSP/build risk with no local build.

## Done (Phase 6 increment 1, CI green, NOT DEVICE-TESTED)
- Smart collections, NEW badge, watched progress bar, Settings > Storage (cache clear), Privacy statement, first-run permission explanation, Coil caps.
- Deliverables written: docs/FINAL_REPORT.md, docs/DEVICE_CHECKLIST.md. Full not-done list is in FINAL_REPORT.md.

## Done (Phase 6 increment 2, CI green, NOT DEVICE-TESTED)
- Multi-select (long-press; bulk Favorite / Add to playlist / Vault / Remove-from-library with confirm / Select all).
- "Possible duplicates" collection (same size + duration; suggestion only, never auto-deletes).
- Search filter tokens: ext:, type:, res:, size>/size<.
- Playlist detail: rename, reorder, remove item, play from item, delete confirm (closes the earlier playlist limitation).
- Hindi localization (values-hi) for the strings listed below; adaptive grid columns (2-6 by width); seek bar + PIN keypad accessibility semantics.
- Hidden-folders UI already existed (folder row toggle).
- Phase 6 NOT built, with reasons: Paging 3 (every filter/sort/collection/duplicate/search path is in-memory; Paging needs SQL equivalents of all of them, a re-architecture; the lists are already lazy), SAF folder picker (needs a non-MediaStore ID scheme and refresh logic so scans do not delete SAF items; MediaStore already indexes all normal video folders), shortcuts/widget (widget needs Glance; shortcuts low value), full string extraction (only Home/tabs/collections/selection/empty states/onboarding/base strings are in Hindi; Settings, Player, Vault screens remain English), TalkBack audit (only the seek bar and keypad were fixed), TV/Chromebook layouts, thumbnail size slider (adaptive columns instead).

## Next
Phase 4 remainder: split PlayerScreen/HomeScreen, DI + PlayerController interface, Baseline Profile, detekt/ktlint/OWASP CI, Actions SHA pinning. Then Phases 5 and 6. Phase 2.6 (floating mini player, Cast) intentionally skipped: it is optional and SYSTEM_ALERT_WINDOW was removed in Phase 1.4.

## Not started
Phases 3, 4, 5, 6 and the LOW RAM / LOW STORAGE rules (A–D).

- Final round: Rules A/B/C done (CI green); brightness/contrast/saturation implemented (VIDEO_TRANSFORMS). Remaining items listed in docs/FINAL_REPORT.md.
- Completion round: see docs/FINAL_REPORT.md (done vs deliberately-not-done lists).

## Done (premium glass + architecture round, PR #2, NOT DEVICE-TESTED)
- Architecture: every ViewModel (Home, Vault, Settings, Player) gets its dependencies through Koin constructors; none reads `LNXVApplication` directly. Cross-screen hand-off state lives in `core/session/SessionState` (Application keeps delegating properties for the remaining non-ViewModel call sites).
- Design: `core/design/GlassKit.kt` (GlassSurface, GlassGroup, GlassTopBar, GlassSearchField, GlassInfoRow); all blur goes through one gate (`LocalReduceEffects`).
- Settings: blurred glass top bar, in-screen search (filters sections), Developer section (system info + copy, reset experimental flags; PIN/vault/theme untouched).
- Player: gesture HUD is now a glass pill with a progress bar (brightness/volume) and spring-in; controls overlay springs in (both skipped when effects are reduced).
- Not done: glass applied to Home cards/Vault screens, PlayerScreen controls overlay still inline, PlayerViewModel unit test with a fake controller, Settings is still one scrolling list (no drill-down pages).


## iOS-inspired visual redesign pass (2026-11-16)

See [`docs/IOS_VISUAL_REDESIGN.md`](docs/IOS_VISUAL_REDESIGN.md) for exact changes, preserved systems, verification limits, and local build steps.

## iOS-style player controls (PR: claude/ios-player-controls, NOT DEVICE-TESTED)
- New `ui/player/PlayerControlsOverlay.kt` (`IosPlayerControls`): glass pill icons top-left (close | PiP, aspect, more), system-volume pill top-right, big centre rewind-10 / play-pause / forward-10 (+ prev/next when queued), bottom-left folder + title + thin progress bar (elapsed / remaining, scrub preview) + text chips (Info, Lock, Rotate, Sleep timer), bottom-right speed / audio / subtitle pill. PlayerScreen only calls it; all actions reuse the existing ViewModel methods.
- Glass here is a translucent fill + border (no real backdrop blur): Haze cannot sample a SurfaceView video, so true blur of the moving video is not possible without a screenshot copy.

## iOS/glass UI remaining work, batch 1 (NOT DEVICE-TESTED, CI not yet run)
- `GlassKit.kt`: `GlassSearchField` now has a search icon and a clear button; new `GlassTextField` (16dp, glassSubtle fill, hairline border, error text, keyboard options, visual transformation); new `GlassDialogButton` (plain / accent capsule / destructive red) and `DestructiveRed`.
- `HomeScreen.kt`: search box -> `GlassSearchField`; all `OutlinedTextField` (playlist name, stream URL, rename, playlist rename) -> `GlassTextField`; all dialogs use `GlassDialogButton` (Cancel plain, confirm accent capsule, delete/remove red); selection bar actions are icon + tiny label (All, Fav, Playlist, Vault, Remove, More, Done), same callbacks.
- `VaultScreen.kt`: change-PIN fields -> `GlassTextField` (PIN transformation kept), dialog buttons restyled, header Change PIN / Lock -> glass pills, legacy-encrypt banner -> `GlassPanel` + `GlassButton`, keypad keys are bouncy glass circles with haptic (`TextHandleMove`).
- Batch 2 (same pass): Settings grouped/segmented/stepper/value rows + update card, cards + empty state, player dialogs -> GlassDialog, iOS navigation transitions, haptics, splash icon, typography. See docs/IOS_VISUAL_REDESIGN.md. Everything NOT DEVICE-TESTED; CI must run before release.

## Liquid Glass engine (ported from the Figma plugin; NOT DEVICE-TESTED)
- `core/design/GlassProfile.kt`: LOW / BALANCED / HIGH chosen from API level, RAM, refresh rate, low-RAM flag and the existing Settings > Effects override (On = LOW, Off = richest tier the OS supports, Auto = detect). Unit-tested (`GlassProfileTest`). No runtime frame-time measurement yet.
- `core/design/LiquidGlass.kt`: `liquidGlassSurface` now follows the Figma layer stack: inner shadow, two-lobe angular-gradient edge (stops 12%/64%), soft wide highlight lobes, top gloss, HIGH adds a static chromatic rim fringe. Drawn once via drawWithCache. LOW keeps the old minimal look.
- `core/design/LiquidLens.kt`: AGSL RuntimeShader (API 33+, HIGH only) on the Home list: SDF edge-band refraction, per-channel chromatic aberration, 12-tap frost, for the top bar and bottom bar. Deviations from Figma: SDF-gradient normal instead of centre direction, 13-tap disc blur instead of gaussian. Shader failure -> falls back to Haze automatically.
- Tecno Camon 19 (Android 13, 60 Hz, ~4-6 GB) resolves to BALANCED/LOW in Auto, so the AGSL lens is off by default there; Settings > Effects "Off" forces HIGH to try it.

## Scroll + video-open performance pass (NOT DEVICE-TESTED, no numbers measured)
- List rows/badges use `liquidGlassSurfaceLite` (solid fill + hairline); the layered glass is only for bars/dialogs.
- Live blur (Haze, AGSL lens) only on the HIGH tier; BALANCED/LOW bars are translucent flat bars (no per-frame backdrop re-render while scrolling). Settings > Effects "Off" forces HIGH.
- Row thumbnails come from MediaStore system thumbnails (`data/thumb/VideoThumbs.kt`, API 29+) instead of decoding a video frame per row; fallback = one MediaMetadataRetriever frame; vault/legacy keep the old path. Thumbnail memory cache 12% -> 15%.
- `videoSharedBounds` (per-row shared element state) only on HIGH.
- Player open: ExoPlayer manager is built on a main-thread idle handler after launch; folder queue is built after playback is started (was before).

## Baseline profile additions
- Added Coil, Media3 decoder/video and LazyList rules to `baseline-prof.txt` (app code was already wildcarded). Kotlin 2.2.10 => Compose strong skipping is already on. No profile was generated/measured on a device.

## Player UI fixes from on-device screenshots (NOT DEVICE-TESTED)
- Volume pill shrinks (weight track) so its icon is never clipped; centre controls spread evenly inside the screen (no edge clipping in portrait); compact bottom chips (Info/Lock/Rotate/Timer) fit beside the speed/audio/subtitle pill.
- Gesture HUD moved below the top pills; controls hide while any panel/menu is open (they showed through the translucent side panel); duplicate subtitle/audio track names are numbered.

## Buffering indicator fix (NOT DEVICE-TESTED)
- The standalone 72dp buffering circle sat behind the 76dp play button (spinner arc peeking out). With controls visible, buffering is now a spinner ring on the play button; the standalone indicator only shows when controls are hidden.

## Seek-bar preview fix (NOT DEVICE-TESTED)
- Scrub preview was one Coil request per drag step, cancelled by the next step before finishing (blank on heavy files) and disabled entirely when effects were reduced. Now `ui/player/ScrubThumbnailer.kt`: one reused MediaMetadataRetriever, keyframe decode at 320x180, newest-position-only worker, last frame kept (never blank), LRU of 48 seconds. Swipe-seek on the video surface still shows only the time HUD (no thumbnail).

## Readable track names (NOT DEVICE-TESTED)
- Audio/subtitle panels now show "English · Stereo · AAC" / "English · SRT · Forced" (`playback/engine/TrackNames.kt`, unit-tested) instead of the container title, which is often only a release tag like "(Movies4u.Foo).". Title is used only when the language is unknown; repeats are still numbered (1), (2).

## Second screen over Wi-Fi (multiscreen)
- Host: player ⋮ menu → "Second screen (Wi-Fi)". Receiver: Home → Join screen (speaker icon).
- One TCP port per host: JSON-lines control + HTTP range server for the current local video. PIN-paired, one follower, vault / SMB / WebDAV videos are never shared. Same Wi-Fi or hotspot (Bluetooth cannot carry video).
- Modes: Speaker (follower plays audio only), Mirror, Span (stacked or side by side, adjustable bezel gap, host top/bottom or left/right).
- Sync: host state every 500 ms, ping/pong clock offset, seek above 250 ms drift, ±3% speed nudge above 40 ms, manual "play earlier by" delay on the follower.
- Follower streams through `LanDataSource` (raw socket) because the app forbids cleartext HTTP.
- Pure maths (`SpanGeometry`, `SyncMath`) unit-tested. NOT tested on two real phones yet.
