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
    VIDEO_TRANSFORMS(
        "Rotate / flip video",
        "Adds Rotate and Flip to the player menu. Applied to the video surface; may look wrong on some devices (SurfaceView).",
        false
    ),
    EQUALIZER(
        "Equalizer presets",
        "Adds an Equalizer preset switcher (Flat, Bass, Vocal, Treble, Loud) to the player menu. Uses the system audio effect; may be unsupported on some devices.",
        false
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
