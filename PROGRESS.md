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

## In progress / remaining in Phase 1
- BiometricPrompt unlock (needs FragmentActivity + androidx.biometric)
- Change-PIN screen
- detekt/ktlint/OWASP dependency-check in CI; pin Actions to SHAs
- REQUEST_INSTALL_PACKAGES only in sideload flavor (with Phase 4 flavors)

## Not started
Phases 2, 3, 4, 5, 6 and the LOW RAM / LOW STORAGE rules (A–D).
