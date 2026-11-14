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
    SLEEP_TIMER_EXTRAS(
        "Sleep timer extras",
        "Adds an 'End of video' option and fades the volume out over the last 10 seconds before the timer stops playback.",
        true
    );

    companion object {
        fun defaults(): Map<FeatureFlag, Boolean> = values().associateWith { it.defaultEnabled }
    }
}
