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
                if (info == null) UpdateState.UpToDate else UpdateState.Available(info)
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
                val file = AppUpdater.download(app, info) { _updateState.value = UpdateState.Downloading(it) }
                AppUpdater.install(app, file)
                _updateState.value = UpdateState.Available(info)
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.message ?: "Download failed")
            }
        }
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
}
