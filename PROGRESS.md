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

## Next
Phase 3 (graphics/transitions/theming). Phase 2.6 (floating mini player, Cast) intentionally skipped: it is optional and SYSTEM_ALERT_WINDOW was removed in Phase 1.4.

## Not started
Phases 3, 4, 5, 6 and the LOW RAM / LOW STORAGE rules (A–D).
