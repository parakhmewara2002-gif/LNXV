# Changelog

Unreleased changes from the phased work (each is its own pull request; none verified on a device).

## Added
- Native decoders: FFmpeg audio (LGPL, dynamic) and a gated dav1d AV1 software fallback with resolution/fps caps (#14).
- Subtitles: format and encoding detection, ASS shifting, delay buttons, OpenSubtitles search with the user's own key (#17).
- Picture enhancer (flag): sharpness, Vivid, colour-blind assist, tone, per-video settings, auto-off gate (#18).
- Bookmarks per video (flag) (#19).
- Audio effects: Bass Boost, Virtualizer, per-output (speaker/wired/Bluetooth/HDMI) memory (flag) (#27).
- Design tokens, `docs/DESIGN_SYSTEM.md`, WCAG contrast tests (#20).
- Data-driven settings search (#21).

## Changed
- Light theme tertiary text and accent colours darkened to meet 4.5:1 contrast (#20).

## Security
- No Vault, PIN or lockout changes in any phase.
