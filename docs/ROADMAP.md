# Roadmap and deferred work

Items not done, with the reason. "Owner" means it needs a decision before work starts.

| Item | Reason deferred |
|------|-----------------|
| Popup / floating window | Needs `SYSTEM_ALERT_WINDOW`; an earlier decision removed it. Owner decision. |
| Chromecast / Cast | Needs Google Play services Cast dependency (size, privacy review); sideload-only, flagged. Owner. |
| SMB / WebDAV browsing | New network stack and credentials storage; needs approval and a security review. |
| Recycle bin | Touches delete/MediaStore flows; needs device testing for scoped storage. |
| File-info sheet | Small; not prioritised in the first slice of Phase 4. |
| BassBoost / Virtualizer, per-output EQ memory | Audio effects are device-dependent; needs device testing. |
| Night mode (separate from enhancer) | Picture enhancer has a Night preset; a separate mode would duplicate it. |
| Chapters | No reliable Media3 chapter API was found in the survey; investigate on 1.11 with sample files. |
| Screenshot tests | Needs a screenshot library dependency (owner). |
| Adaptive tablet/foldable layouts | Needs device or emulator verification. |
| Settings: 11-group redesign, `Settings*Row` components, `SettingsRepository` + migration test, Labs page | Large rewrite of a 759-line screen and the preferences layer without a compiler or device; do one group per PR with device review. |
| Migrate old literal dp values to tokens | Risky sweep; migrate when each screen is touched. |
| Performance numbers (AV1 fps, shader cost) | Require real devices; see `DEVICE_TEST_PLAN.md`. |
