package com.lnxv.player.core.datastore

import androidx.compose.runtime.Immutable
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.lnxv.player.core.security.LockoutState
import com.lnxv.player.core.flags.FeatureFlag

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lnxv_user_preferences")

@Immutable
data class UserPreferences(
    val isDarkMode: Boolean = true,
    val isAmoled: Boolean = true,
    val glassBlurIntensity: Float = 16f,
    val defaultSpeed: Float = 1.0f,
    val defaultAspectRatio: String = "FIT",
    val doubleTapSeekSeconds: Int = 10,
    val isBrightnessGestureEnabled: Boolean = true,
    val isVolumeGestureEnabled: Boolean = true,
    val isSeekGestureEnabled: Boolean = true,
    val isLongPressSpeedEnabled: Boolean = true,
    val resumePlaybackMode: String = "ALWAYS",
    val subtitleTextSize: Float = 16f,
    val subtitleTextColorHex: String = "#FFFFFFFF",
    val librarySortMode: String = "DATE_DESC",
    val vaultPinHash: String = "",
    val isBackgroundPlayEnabled: Boolean = false,
    val isAutoPiPEnabled: Boolean = true,
    val rememberSpeed: Boolean = false,
    val lastSpeed: Float = 1.0f,
    val hiddenFolders: Set<String> = emptySet(),
    val libraryViewMode: String = "LIST",
    val themeMode: String = "DARK",
    /** Base64 PBKDF2 credential (replaces the legacy static-salt SHA-256 in [vaultPinHash]). */
    val vaultPinSalt: String = "",
    val vaultPinDerived: String = "",
    val vaultPinIterations: Int = 0,
    val vaultPinLength: Int = 6,
    val vaultFailedAttempts: Int = 0,
    val vaultLockedUntilMs: Long = 0L,
    /** 0 = immediately when the app leaves the foreground, otherwise grace period in ms. */
    val vaultAutoLockMs: Long = 0L,
    val blockScreenshotsEverywhere: Boolean = false,
    val updateWifiOnly: Boolean = false,
    val dynamicColor: Boolean = false,
    /** AUTO / ON / OFF: reduce effects (blur, shared elements, heavy animations). AUTO follows device-tier detection. */
    val effectsMode: String = "AUTO",
    /** Newline-separated recent stream URLs (credentials stripped). */
    val recentStreams: String = "",
    val skippedUpdateBuild: Int = 0,
    val featureFlags: Map<FeatureFlag, Boolean> = FeatureFlag.defaults()
) {
    val hasVaultCredential: Boolean get() = vaultPinDerived.isNotEmpty() || vaultPinHash.isNotEmpty()
    val lockoutState: LockoutState get() = LockoutState(vaultFailedAttempts, vaultLockedUntilMs)
    fun isEnabled(flag: FeatureFlag): Boolean = featureFlags[flag] ?: flag.defaultEnabled
}

class UserPreferencesDataStore(private val context: Context) {

    private object PreferencesKeys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val AMOLED = booleanPreferencesKey("amoled")
        val GLASS_BLUR = floatPreferencesKey("glass_blur")
        val DEFAULT_SPEED = floatPreferencesKey("default_speed")
        val DEFAULT_ASPECT_RATIO = stringPreferencesKey("default_aspect_ratio")
        val DOUBLE_TAP_SEEK = intPreferencesKey("double_tap_seek")
        val GESTURE_BRIGHTNESS = booleanPreferencesKey("gesture_brightness")
        val GESTURE_VOLUME = booleanPreferencesKey("gesture_volume")
        val GESTURE_SEEK = booleanPreferencesKey("gesture_seek")
        val GESTURE_LONG_PRESS = booleanPreferencesKey("gesture_long_press")
        val RESUME_MODE = stringPreferencesKey("resume_mode")
        val SUBTITLE_SIZE = floatPreferencesKey("subtitle_size")
        val SUBTITLE_COLOR = stringPreferencesKey("subtitle_color")
        val LIBRARY_SORT = stringPreferencesKey("library_sort")
        val VAULT_PIN_HASH = stringPreferencesKey("vault_pin_hash")
        val BACKGROUND_PLAY = booleanPreferencesKey("background_play")
        val AUTO_PIP = booleanPreferencesKey("auto_pip")
        val REMEMBER_SPEED = booleanPreferencesKey("remember_speed")
        val LAST_SPEED = floatPreferencesKey("last_speed")
        val HIDDEN_FOLDERS = stringSetPreferencesKey("hidden_folders")
        val VIEW_MODE = stringPreferencesKey("library_view_mode")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val VAULT_PIN_SALT = stringPreferencesKey("vault_pin_salt")
        val VAULT_PIN_DERIVED = stringPreferencesKey("vault_pin_derived")
        val VAULT_PIN_ITER = intPreferencesKey("vault_pin_iter")
        val VAULT_PIN_LEN = intPreferencesKey("vault_pin_len")
        val VAULT_FAILED = intPreferencesKey("vault_failed_attempts")
        val VAULT_LOCKED_UNTIL = longPreferencesKey("vault_locked_until")
        val VAULT_AUTOLOCK = longPreferencesKey("vault_autolock_ms")
        val BLOCK_SCREENSHOTS = booleanPreferencesKey("block_screenshots")
        val RECENT_STREAMS = stringPreferencesKey("recent_streams")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val EFFECTS_MODE = stringPreferencesKey("effects_mode")
        val UPDATE_WIFI_ONLY = booleanPreferencesKey("update_wifi_only")
        val UPDATE_SKIPPED = intPreferencesKey("update_skipped_build")
        fun flagKey(flag: FeatureFlag) = booleanPreferencesKey("ff_" + flag.name.lowercase())
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            isDarkMode = prefs[PreferencesKeys.DARK_MODE] ?: true,
            isAmoled = prefs[PreferencesKeys.AMOLED] ?: true,
            glassBlurIntensity = prefs[PreferencesKeys.GLASS_BLUR] ?: 16f,
            defaultSpeed = prefs[PreferencesKeys.DEFAULT_SPEED] ?: 1.0f,
            defaultAspectRatio = prefs[PreferencesKeys.DEFAULT_ASPECT_RATIO] ?: "FIT",
            doubleTapSeekSeconds = prefs[PreferencesKeys.DOUBLE_TAP_SEEK] ?: 10,
            isBrightnessGestureEnabled = prefs[PreferencesKeys.GESTURE_BRIGHTNESS] ?: true,
            isVolumeGestureEnabled = prefs[PreferencesKeys.GESTURE_VOLUME] ?: true,
            isSeekGestureEnabled = prefs[PreferencesKeys.GESTURE_SEEK] ?: true,
            isLongPressSpeedEnabled = prefs[PreferencesKeys.GESTURE_LONG_PRESS] ?: true,
            resumePlaybackMode = prefs[PreferencesKeys.RESUME_MODE] ?: "ALWAYS",
            subtitleTextSize = prefs[PreferencesKeys.SUBTITLE_SIZE] ?: 16f,
            subtitleTextColorHex = prefs[PreferencesKeys.SUBTITLE_COLOR] ?: "#FFFFFFFF",
            librarySortMode = prefs[PreferencesKeys.LIBRARY_SORT] ?: "DATE_DESC",
            vaultPinHash = prefs[PreferencesKeys.VAULT_PIN_HASH] ?: "",
            isBackgroundPlayEnabled = prefs[PreferencesKeys.BACKGROUND_PLAY] ?: false,
            isAutoPiPEnabled = prefs[PreferencesKeys.AUTO_PIP] ?: true,
            rememberSpeed = prefs[PreferencesKeys.REMEMBER_SPEED] ?: false,
            lastSpeed = prefs[PreferencesKeys.LAST_SPEED] ?: 1.0f,
            hiddenFolders = prefs[PreferencesKeys.HIDDEN_FOLDERS] ?: emptySet(),
            libraryViewMode = prefs[PreferencesKeys.VIEW_MODE] ?: "LIST",
            themeMode = prefs[PreferencesKeys.THEME_MODE] ?: "DARK",
            vaultPinSalt = prefs[PreferencesKeys.VAULT_PIN_SALT] ?: "",
            vaultPinDerived = prefs[PreferencesKeys.VAULT_PIN_DERIVED] ?: "",
            vaultPinIterations = prefs[PreferencesKeys.VAULT_PIN_ITER] ?: 0,
            vaultPinLength = prefs[PreferencesKeys.VAULT_PIN_LEN] ?: 6,
            vaultFailedAttempts = prefs[PreferencesKeys.VAULT_FAILED] ?: 0,
            vaultLockedUntilMs = prefs[PreferencesKeys.VAULT_LOCKED_UNTIL] ?: 0L,
            vaultAutoLockMs = prefs[PreferencesKeys.VAULT_AUTOLOCK] ?: 0L,
            blockScreenshotsEverywhere = prefs[PreferencesKeys.BLOCK_SCREENSHOTS] ?: false,
            updateWifiOnly = prefs[PreferencesKeys.UPDATE_WIFI_ONLY] ?: false,
            dynamicColor = prefs[PreferencesKeys.DYNAMIC_COLOR] ?: false,
            effectsMode = prefs[PreferencesKeys.EFFECTS_MODE] ?: "AUTO",
            recentStreams = prefs[PreferencesKeys.RECENT_STREAMS] ?: "",
            skippedUpdateBuild = prefs[PreferencesKeys.UPDATE_SKIPPED] ?: 0,
            featureFlags = FeatureFlag.values().associateWith { f ->
                prefs[PreferencesKeys.flagKey(f)] ?: f.defaultEnabled
            }
        )
    }

    suspend fun updateDarkMode(isDark: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.DARK_MODE] = isDark }
    }

    suspend fun updateAmoled(isAmoled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.AMOLED] = isAmoled }
    }

    suspend fun updateGlassBlur(blur: Float) {
        context.dataStore.edit { it[PreferencesKeys.GLASS_BLUR] = blur }
    }

    suspend fun updateDefaultSpeed(speed: Float) {
        context.dataStore.edit { it[PreferencesKeys.DEFAULT_SPEED] = speed }
    }

    suspend fun updateDefaultAspectRatio(ratio: String) {
        context.dataStore.edit { it[PreferencesKeys.DEFAULT_ASPECT_RATIO] = ratio }
    }

    suspend fun updateDoubleTapSeek(seconds: Int) {
        context.dataStore.edit { it[PreferencesKeys.DOUBLE_TAP_SEEK] = seconds }
    }

    suspend fun updateLibrarySort(sort: String) {
        context.dataStore.edit { it[PreferencesKeys.LIBRARY_SORT] = sort }
    }

    suspend fun updateBackgroundPlay(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.BACKGROUND_PLAY] = enabled }
    }

    suspend fun updateAutoPiP(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.AUTO_PIP] = enabled }
    }

    suspend fun updateBrightnessGesture(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.GESTURE_BRIGHTNESS] = enabled }
    }

    suspend fun updateResumeMode(mode: String) {
        context.dataStore.edit { it[PreferencesKeys.RESUME_MODE] = mode }
    }

    suspend fun updateRememberSpeed(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.REMEMBER_SPEED] = enabled }
    }

    suspend fun updateLastSpeed(speed: Float) {
        context.dataStore.edit { it[PreferencesKeys.LAST_SPEED] = speed }
    }

    suspend fun updateSubtitleSize(size: Float) {
        context.dataStore.edit { it[PreferencesKeys.SUBTITLE_SIZE] = size }
    }

    suspend fun updateSubtitleColor(hex: String) {
        context.dataStore.edit { it[PreferencesKeys.SUBTITLE_COLOR] = hex }
    }

    suspend fun updateThemeMode(mode: String) {
        context.dataStore.edit { it[PreferencesKeys.THEME_MODE] = mode }
    }

    suspend fun updateViewMode(mode: String) {
        context.dataStore.edit { it[PreferencesKeys.VIEW_MODE] = mode }
    }

    suspend fun setFolderHidden(path: String, hidden: Boolean) {
        context.dataStore.edit { p ->
            val cur = p[PreferencesKeys.HIDDEN_FOLDERS] ?: emptySet()
            p[PreferencesKeys.HIDDEN_FOLDERS] = if (hidden) cur + path else cur - path
        }
    }

    suspend fun saveVaultCredential(saltB64: String, derivedB64: String, iterations: Int, pinLength: Int) {
        context.dataStore.edit {
            it[PreferencesKeys.VAULT_PIN_SALT] = saltB64
            it[PreferencesKeys.VAULT_PIN_DERIVED] = derivedB64
            it[PreferencesKeys.VAULT_PIN_ITER] = iterations
            it[PreferencesKeys.VAULT_PIN_LEN] = pinLength
            it.remove(PreferencesKeys.VAULT_PIN_HASH) // drop legacy static-salt hash
            it[PreferencesKeys.VAULT_FAILED] = 0
            it[PreferencesKeys.VAULT_LOCKED_UNTIL] = 0L
        }
    }

    suspend fun clearVaultCredential() {
        context.dataStore.edit {
            it.remove(PreferencesKeys.VAULT_PIN_HASH)
            it.remove(PreferencesKeys.VAULT_PIN_SALT)
            it.remove(PreferencesKeys.VAULT_PIN_DERIVED)
            it.remove(PreferencesKeys.VAULT_PIN_ITER)
            it.remove(PreferencesKeys.VAULT_PIN_LEN)
            it[PreferencesKeys.VAULT_FAILED] = 0
            it[PreferencesKeys.VAULT_LOCKED_UNTIL] = 0L
        }
    }

    suspend fun saveLockout(state: LockoutState) {
        context.dataStore.edit {
            it[PreferencesKeys.VAULT_FAILED] = state.failedAttempts
            it[PreferencesKeys.VAULT_LOCKED_UNTIL] = state.lockedUntilMs
        }
    }

    suspend fun updateVaultAutoLock(ms: Long) {
        context.dataStore.edit { it[PreferencesKeys.VAULT_AUTOLOCK] = ms }
    }

    suspend fun saveRecentStreams(encoded: String) {
        context.dataStore.edit { it[PreferencesKeys.RECENT_STREAMS] = encoded }
    }

    suspend fun updateDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun updateEffectsMode(mode: String) {
        context.dataStore.edit { it[PreferencesKeys.EFFECTS_MODE] = mode }
    }

    suspend fun updateUpdateWifiOnly(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.UPDATE_WIFI_ONLY] = enabled }
    }

    suspend fun skipUpdateBuild(build: Int) {
        context.dataStore.edit { it[PreferencesKeys.UPDATE_SKIPPED] = build }
    }

    suspend fun updateBlockScreenshots(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.BLOCK_SCREENSHOTS] = enabled }
    }

    suspend fun setFeatureFlag(flag: FeatureFlag, enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.flagKey(flag)] = enabled }
    }
}
