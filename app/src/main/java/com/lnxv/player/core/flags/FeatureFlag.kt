package com.lnxv.player.core.flags

/**
 * DataStore-backed feature flags (Settings > Advanced > Experimental).
 * Low-risk features default ON, experimental ones default OFF, so a crash-prone feature
 * can be disabled without shipping a new build.
 */
enum class FeatureFlag(val title: String, val description: String, val defaultEnabled: Boolean) {
    ENCRYPTED_VAULT(
        "Encrypted vault",
        "Encrypt videos moved to the vault (AES-256-GCM). When off, new vault moves only hide the video inside the app.",
        true
    ),
    VAULT_AUTO_LOCK(
        "Vault auto-lock",
        "Lock the vault when the app goes to background or the screen turns off.",
        true
    ),
    SECURE_UPDATER_CHECKS(
        "Verify updates",
        "Verify the SHA-256 checksum and signing certificate of downloaded updates before install.",
        true
    ),
    OPEN_WITH_INTENTS(
        "Open with",
        "Play videos opened from other apps (VIEW intents).",
        true
    ),
    PER_VIDEO_SPEED(
        "Per-video speed",
        "Remember the playback speed separately for each video (not stored for vault videos).",
        true
    ),
    GESTURE_SENSITIVITY(
        "Gesture sensitivity",
        "Adjust how strongly swipe gestures change brightness, volume and seek position (Settings).",
        true
    ),
    SUBTITLE_STYLING(
        "Subtitle styling",
        "Subtitle background opacity, edge style and vertical position in Settings. When off, the default style is used.",
        true
    ),
    AUDIO_ONLY_MODE(
        "Audio-only mode",
        "Adds 'Audio only' to the player menu: video decoding is switched off (saves battery) while audio keeps playing. Applies to the current video only.",
        true
    ),
    BIOMETRIC_UNLOCK(
        "Biometric vault unlock",
        "Lets you unlock the vault with fingerprint/face (Android BiometricPrompt, strong class) instead of the PIN. Enable it in Settings > Privacy. The PIN always still works; biometrics are refused while the PIN lockout is active.",
        false
    ),
    VIDEO_TRANSFORMS(
        "Rotate / flip / colour",
        "Adds Rotate, Flip and Brightness/Contrast/Saturation to the player menu. Rotate/flip is applied to the video surface and may look wrong on some devices (SurfaceView); colour uses a GL effect pass.",
        false
    ),
    PICTURE_ENHANCER(
        "Picture enhancer",
        "Adds a Picture sheet to the player menu: sharpness, Vivid low-light lift, colour-blind assist, tone, brightness/contrast/saturation/gamma and presets, applied with one GPU pass. Turns itself off for HDR, protected and low-end cases and says why. Experimental: not measured on real devices.",
        false
    ),
    CHAPTERS(
        "Chapters",
        "Adds 'Chapters' to the player menu when the video has chapter markers (MKV, MP4/M4B): list, jump, current chapter. Reads Media3 1.11 chapter metadata; not tested with real files.",
        false
    ),
    RECYCLE_BIN(
        "Recycle bin",
        "On Android 11+, 'Delete from device' moves videos to the system trash (kept about 30 days) instead of deleting them for good, and Home gets a Trash button to restore or delete them. Below Android 11 nothing changes. Not tested on real devices.",
        false
    ),
    WEBDAV(
        "WebDAV browser",
        "Adds 'Browse WebDAV' to the network stream dialog: sign in to your own WebDAV server (https only), browse folders and play videos. The password is kept in memory for this session only. Not tested against real servers.",
        false
    ),
    POPUP_WINDOW(
        "Popup window",
        "Adds 'Popup window' to the player menu: plays the video in a small floating window over other apps (sideload build only). Asks you to allow 'Display over other apps'. Not tested on real devices.",
        false
    ),
    SMB(
        "SMB shares",
        "Adds 'Browse SMB' to the network stream dialog (sideload build, Android 8+): sign in to a Windows/NAS share, browse folders and play videos. The password is kept in memory for this session only. Not tested against real servers.",
        false
    ),
    CAST(
        "Cast (Chromecast & TV)",
        "Adds 'Cast' to the player menu for videos: send the video to a Chromecast or Smart TV. Needs Google Play services.",
        true
    ),
    BOOKMARKS(
        "Bookmarks",
        "Adds 'Bookmarks' to the player menu: save positions in a video, jump between them. Stored on this device only, per library video (never for vault or network videos).",
        true
    ),
    EQUALIZER(
        "Equalizer presets & Clear Voice",
        "Adds an Audio effects sheet to the player menu: Clear Voice / Dialogue enhancer, equalizer presets, Bass Boost and Virtualizer, remembered separately for speaker, headphones, Bluetooth and HDMI. Uses system audio effects; may be unsupported on some devices.",
        true
    ),
    SKIP_SILENCE(
        "Skip silence",
        "Skip silent parts of the audio during playback (takes effect on the next video you open). Experimental: can make audio sound clipped.",
        false
    ),
    SLEEP_TIMER_EXTRAS(
        "Sleep timer extras",
        "Adds an 'End of video' option and fades the volume out over the last 10 seconds before the timer stops playback.",
        true
    );

    companion object {
        fun defaults(): Map<FeatureFlag, Boolean> = values().associateWith { it.defaultEnabled }
    }
}
