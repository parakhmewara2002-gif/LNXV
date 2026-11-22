# Roadmap and deferred work

Items not done, with the reason. "Owner" means it needs a decision before work starts.

| Item | Reason deferred |
|------|-----------------|
| ~~Popup / floating window~~ | Done in #34 (flag `POPUP_WINDOW`, sideload only, `SYSTEM_ALERT_WINDOW` in the sideload manifest); untested on devices, surface hand-over is the main risk. |
| ~~Chromecast / Cast~~ | Done in #36 (flag `CAST`, sideload only) for http(s) videos only; local files need a local HTTP server (not built). Privacy screen not yet updated to mention Google Play services Cast: keep the flag off until reviewed. |
| ~~SMB~~ | Done in #35 (flag `SMB`, sideload only, smbj + BouncyCastle); share must be named in the address, listing all shares is not implemented; untested against real servers. |
| ~~WebDAV browsing~~ | Done in #30 (flag `WEBDAV`, https only, session-only password); untested against real servers. |
| ~~Recycle bin~~ | Done in #29 (flag `RECYCLE_BIN`, system MediaStore trash, Android 11+); needs device testing, especially that trashed videos show in the Trash list. |
| File-info sheet | Small; not prioritised in the first slice of Phase 4. |
| ~~BassBoost / Virtualizer, per-output EQ memory~~ | Done in #27 (flagged); effect quality still needs device testing. |
| Night mode (separate from enhancer) | Picture enhancer has a Night preset; a separate mode would duplicate it. |
| ~~Chapters~~ | Done in #31 (flag `CHAPTERS`) using Media3 1.11 `Chapter` metadata; untested with real MKV/M4B files; next/previous-chapter buttons not added. |
| Screenshot tests | Render/behaviour tests done in #33; PNG capture under Robolectric most likely failed (artifact is tiny), so there are no screenshots and no golden comparison yet. |
| Adaptive tablet/foldable layouts | Needs device or emulator verification. |
| Settings: 11-group redesign, `Settings*Row` components, `SettingsRepository` + migration test, Labs page | Large rewrite of a 759-line screen and the preferences layer without a compiler or device; do one group per PR with device review. |
| Migrate old literal dp values to tokens | Risky sweep; migrate when each screen is touched. |
| Performance numbers (AV1 fps, shader cost) | Require real devices; see `DEVICE_TEST_PLAN.md`. |
