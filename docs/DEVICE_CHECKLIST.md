# Manual device checklist (nothing below has been run on a device)

Test on at least: one low-RAM device (<=3 GB), one Android 14 device, one Android 8/9 device.

## Playback
- [ ] Open a video from Home; plays, controls hide/show.
- [ ] Seek by slider, double-tap, swipe. Seek an MKV with late Cues (must still land correctly).
- [ ] Rotate device; orientation modes work; no restart of playback.
- [ ] PiP enter/exit; background playback with notification controls.
- [ ] Audio focus: incoming call / other app pauses and resumes.
- [ ] Play/pause morph, thumb scale, scrub frame preview (local file); none of these with Reduced effects.
- [ ] Speed per video: set 1.5x on video A, open video B (default), reopen A (1.5x).
- [ ] Sleep timer: 1 min with fade; "End of video".
- [ ] Equalizer / volume boost (enable flags); skip silence; rotate/flip (enable flag; check SurfaceView rendering).
- [ ] Subtitle styling (background, edge, position); gesture sensitivity.
- [ ] Open with from file manager (content:// and file://) and a network URL.
- [ ] Queue: next/prev, shuffle, repeat; playlists create/add/play.

## Vault / security
- [ ] Set PIN (4 and 8 digits), wrong PIN lockout, forgot PIN wipes vault.
- [ ] Move video to vault: encrypted file appears, original deletion consent shown, playback and thumbnails work, restore works, cancel leaves no orphan.
- [ ] Auto-lock on background / screen off; vault player closes on lock.
- [ ] Screenshot attempt in vault is blocked; screenshot menu item refuses vault videos.
- [ ] Update check: tampered checksum is rejected (sideload flavor). Store flavor shows no Updates section and no install permission.

## UI / library
- [ ] Smart collection chips; NEW badge; progress bar; grid/list toggle; hidden folders.
- [ ] Settings > Storage sizes and Clear cache/thumbnails; Privacy text.
- [ ] First-run explanation dialog before permission prompt.
- [ ] Dynamic color on Android 12+; Reduced effects on a low-RAM device.
- [ ] Release APK (R8) plays HLS/DASH/RTSP and uses reflection-based seek fix; no crash.
- [ ] Measure APK size (APK Analyzer) and cold start before/after; record real numbers.
