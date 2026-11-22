# Device test plan for the phased work (to run after merging; nothing here has been run)

Record device model, Android version, RAM, and result for each line. Mark failures with what you saw.

## Phase 1: decoders
- [ ] Play an AV1 1080p30 file on a device without hardware AV1: plays smoothly or shows the capped/denied message.
- [ ] Play 4K AV1 without hardware: denied with a clear message, no crash.
- [ ] Play AC3/EAC3/DTS audio file: sound present.
- [ ] Settings > Open-source licenses opens and lists FFmpeg, dav1d.
- [ ] Low-RAM device: AV1 cap is 720p.

## Phase 2: subtitles
- [ ] Load .srt in UTF-8, a Windows-1251/1256/1253 file, and a UTF-16 file: text readable; manual encoding chips fix a wrong guess.
- [ ] +/-0.1 s delay moves subtitles; ASS file keeps styling.
- [ ] Sideload only: add your OpenSubtitles API key, search a video, download, it shows. Wrong key shows a clear error.
- [ ] Store build: no online search entry.

## Phase 3: picture enhancer (enable flag)
- [ ] Sharpness 9/13/25 taps visible, no stutter at 1080p30; hold-to-compare works.
- [ ] HDR file: enhancer shows "off" reason. Power saver: off.
- [ ] Colour-blind modes change colours; Reset returns to normal; per-video save and clear work.

## Phase 4: bookmarks (enable flag)
- [ ] Add, jump, previous/next, remove; survive app restart; not offered for vault/network videos.

## Phase 4b: audio effects (enable Equalizer flag)
- [ ] Bass and Virtualizer sliders change sound or are harmlessly ignored; no crash on unsupported devices.
- [ ] Set different settings on speaker vs headphones/Bluetooth; plugging in/out restores each.

## Recycle bin (enable RECYCLE_BIN flag, Android 11+)
- [ ] Select a video, "Move to trash": system consent dialog appears, video leaves the library.
- [ ] Home > Trash lists it with days left; Restore brings it back; Delete forever asks for consent and removes it.
- [ ] Android 10 or lower and flag off: behaviour unchanged (permanent delete dialog).

## WebDAV (enable WEBDAV flag)
- [ ] Network stream dialog > Browse WebDAV: connect to your own https server; wrong password shows a clear error.
- [ ] Browse folders, go up, play a video, seek forward and back.
- [ ] A self-signed certificate is refused (expected). http:// is refused (expected).
- [ ] After Disconnect, the old video URL no longer plays without signing in again.

## Chapters (enable CHAPTERS flag)
- [ ] MKV and M4B with chapters: "Chapters..." appears in the player menu, list is correct, tap jumps, current chapter highlighted.
- [ ] A video without chapters: no menu item.

## Popup window (sideload, enable POPUP_WINDOW)
- [ ] Menu > Popup window asks for "Display over other apps"; after allowing, a floating window plays the same video.
- [ ] Drag works, tap pauses/plays, close button closes, "back to full screen" restores the app and the video still renders.
- [ ] Store build: no menu item and no overlay permission.

## SMB (sideload, Android 8+, enable SMB)
- [ ] Network stream dialog > Browse SMB: smb://server/share, sign in, browse, play, seek. Wrong password shows an error.
- [ ] Store build: no SMB entry.

## Cast (sideload, enable CAST, needs Google Play services)
- [ ] An https video: Menu > Cast to TV opens the device chooser; the Chromecast plays it; local player pauses.
- [ ] Local files and WebDAV/SMB videos: no Cast item.

## Updater
- [ ] On an older build, "Update available" appears and installs the newest release.

## Phase 5/6
- [ ] Light theme: tertiary text and accent readable. TalkBack reads panel buttons. 200% font scale.
- [ ] Settings search: "picture", "av1", Hindi words find the right section.

## General
- [ ] Vault unchanged: PIN, lockout, move/restore.
- [ ] Hindi language: new strings display.
