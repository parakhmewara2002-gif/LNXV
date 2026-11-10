package com.lnxv.player.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lnxv_user_preferences")

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
    val isBackgroundPlayEnabled: Boolean = true,
    val isAutoPiPEnabled: Boolean = true
)

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
            isBackgroundPlayEnabled = prefs[PreferencesKeys.BACKGROUND_PLAY] ?: true,
            isAutoPiPEnabled = prefs[PreferencesKeys.AUTO_PIP] ?: true
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

    suspend fun setVaultPin(pin: String) {
        val hash = hashPin(pin)
        context.dataStore.edit { it[PreferencesKeys.VAULT_PIN_HASH] = hash }
    }

    suspend fun verifyVaultPin(enteredPin: String, currentHash: String): Boolean {
        if (currentHash.isEmpty()) return false
        val enteredHash = hashPin(enteredPin)
        return enteredHash == currentHash
    }

    private fun hashPin(pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest("LNXV_SALT_$pin".toByteArray())
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
