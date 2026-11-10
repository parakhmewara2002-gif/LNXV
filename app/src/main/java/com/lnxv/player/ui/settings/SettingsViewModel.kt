package com.lnxv.player.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lnxv.player.LNXVApplication
import com.lnxv.player.core.datastore.UserPreferences
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
