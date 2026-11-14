package com.lnxv.player.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lnxv.player.LNXVApplication
import com.lnxv.player.core.datastore.UserPreferences
import com.lnxv.player.core.update.AppUpdater
import com.lnxv.player.core.update.UpdateInfo
import com.lnxv.player.core.update.UpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore = (application as LNXVApplication).dataStore

    val userPreferences: StateFlow<UserPreferences> = dataStore.userPreferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserPreferences()
    )

    private val app: Application = application
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()
    val currentVersion: String = AppUpdater.currentVersionName(application)

    fun checkForUpdate() {
        val s = _updateState.value
        if (s is UpdateState.Checking || s is UpdateState.Downloading) return
        viewModelScope.launch {
            _updateState.value = UpdateState.Checking
            _updateState.value = try {
                val info = AppUpdater.check(app)
                val skipped = userPreferences.value.skippedUpdateBuild
                if (info == null || !com.lnxv.player.core.update.UpdateVerifier.shouldOffer(info.build, AppUpdater.currentBuild(app), skipped)) UpdateState.UpToDate
                else UpdateState.Available(info)
            } catch (e: Exception) {
                UpdateState.Error(e.message ?: "Update check failed")
            }
        }
    }

    fun downloadAndInstall(info: UpdateInfo) {
        if (_updateState.value is UpdateState.Downloading) return
        viewModelScope.launch {
            if (!AppUpdater.canInstall(app)) {
                AppUpdater.openInstallPermissionSettings(app)
                _updateState.value = UpdateState.Error("Allow 'Install unknown apps' for LNXV, then tap Update again")
                return@launch
            }
            _updateState.value = UpdateState.Downloading(0f)
            try {
                val prefs = userPreferences.value
                val file = AppUpdater.download(
                    app, info,
                    wifiOnly = prefs.updateWifiOnly,
                    verify = prefs.isEnabled(com.lnxv.player.core.flags.FeatureFlag.SECURE_UPDATER_CHECKS)
                ) { _updateState.value = UpdateState.Downloading(it) }
                AppUpdater.install(app, file)
                _updateState.value = UpdateState.Available(info)
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.message ?: "Download failed")
            }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { dataStore.updateThemeMode(mode) }
    }

    fun setResumeMode(mode: String) {
        viewModelScope.launch { dataStore.updateResumeMode(mode) }
    }

    fun setRememberSpeed(enabled: Boolean) {
        viewModelScope.launch { dataStore.updateRememberSpeed(enabled) }
    }

    fun setSubtitleSize(size: Float) {
        viewModelScope.launch { dataStore.updateSubtitleSize(size.coerceIn(10f, 32f)) }
    }

    fun setGestureSensitivity(pct: Int) { viewModelScope.launch { dataStore.updateGestureSensitivity(pct) } }
    fun setSubtitleBg(pct: Int) { viewModelScope.launch { dataStore.updateSubtitleBg(pct) } }
    fun setSubtitleEdge(edge: String) { viewModelScope.launch { dataStore.updateSubtitleEdge(edge) } }
    fun setSubtitleBottom(pct: Int) { viewModelScope.launch { dataStore.updateSubtitleBottom(pct) } }

    fun setSubtitleColor(hex: String) {
        viewModelScope.launch { dataStore.updateSubtitleColor(hex) }
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.updateDarkMode(enabled)
        }
    }

    fun toggleAmoled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.updateAmoled(enabled)
        }
    }

    fun updateGlassBlur(blur: Float) {
        viewModelScope.launch {
            dataStore.updateGlassBlur(blur)
        }
    }

    fun updateDefaultSpeed(speed: Float) {
        viewModelScope.launch {
            dataStore.updateDefaultSpeed(speed)
        }
    }

    fun updateDoubleTapSeek(seconds: Int) {
        viewModelScope.launch {
            dataStore.updateDoubleTapSeek(seconds)
        }
    }

    fun toggleBackgroundPlay(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.updateBackgroundPlay(enabled)
        }
    }

    fun toggleAutoPiP(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.updateAutoPiP(enabled)
        }
    }

    fun toggleBrightnessGesture(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.updateBrightnessGesture(enabled)
        }
    }

    fun setBlockScreenshots(enabled: Boolean) {
        viewModelScope.launch { dataStore.updateBlockScreenshots(enabled) }
    }

    fun cycleAutoLock(currentMs: Long) {
        val next = when (currentMs) { 0L -> 30_000L; 30_000L -> 60_000L; 60_000L -> 300_000L; else -> 0L }
        viewModelScope.launch { dataStore.updateVaultAutoLock(next) }
    }

    fun setFlag(flag: com.lnxv.player.core.flags.FeatureFlag, enabled: Boolean) {
        viewModelScope.launch { dataStore.setFeatureFlag(flag, enabled) }
    }

    fun setUpdateWifiOnly(enabled: Boolean) {
        viewModelScope.launch { dataStore.updateUpdateWifiOnly(enabled) }
    }

    fun skipThisVersion(info: UpdateInfo) {
        viewModelScope.launch {
            dataStore.skipUpdateBuild(info.build)
            _updateState.value = UpdateState.UpToDate
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { dataStore.updateDynamicColor(enabled) }
    }

    fun cycleEffectsMode(current: String) {
        val next = com.lnxv.player.core.design.EffectsMode.parse(current).next().name
        viewModelScope.launch { dataStore.updateEffectsMode(next) }
    }
}
