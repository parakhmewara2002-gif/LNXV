# Changelog

Release note: v1.0.150-v1.0.157 were created in one burst from a batch of merges, so GitHub marked an older one as latest. This entry triggers a fresh build so the newest release contains everything below.

Unreleased changes from the phased work (each is its own pull request; none verified on a device).

## Added
- Native decoders: FFmpeg audio (LGPL, dynamic) and a gated dav1d AV1 software fallback with resolution/fps caps (#14).
- Subtitles: format and encoding detection, ASS shifting, delay buttons, OpenSubtitles search with the user's own key (#17).
- Picture enhancer (flag): sharpness, Vivid, colour-blind assist, tone, per-video settings, auto-off gate (#18).
- Bookmarks per video (flag) (#19).
- Audio effects: Bass Boost, Virtualizer, per-output (speaker/wired/Bluetooth/HDMI) memory (flag) (#27).
- Recycle bin via system trash (flag) (#29), WebDAV browser, https only (flag) (#30), chapters (flag) (#31).
- Popup window (#34), SMB (#35) and Cast (#36), all flagged and sideload-only; render tests and string parity guard (#33).
- Design tokens, `docs/DESIGN_SYSTEM.md`, WCAG contrast tests (#20).
- Data-driven settings search (#21).

## Changed
- Light theme tertiary text and accent colours darkened to meet 4.5:1 contrast (#20).

## Security
- No Vault, PIN or lockout changes in any phase.
