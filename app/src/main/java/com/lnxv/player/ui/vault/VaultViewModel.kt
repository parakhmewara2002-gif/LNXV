package com.lnxv.player.ui.vault

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lnxv.player.LNXVApplication
import com.lnxv.player.core.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class VaultAuthState {
    SETUP_PIN,
    CONFIRM_PIN,
    ENTER_PIN,
    AUTHENTICATED
}

data class VaultUiState(
    val authState: VaultAuthState = VaultAuthState.ENTER_PIN,
    val enteredPin: String = "",
    val tempSetupPin: String = "",
    val errorMessage: String? = null
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val vaultRepo = (application as LNXVApplication).vaultRepository

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    val vaultVideos: StateFlow<List<VideoItem>> = vaultRepo.vaultVideosFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        checkPinStatus()
    }

    private fun checkPinStatus() {
        viewModelScope.launch {
            val isSet = vaultRepo.isPinSet()
            _uiState.value = _uiState.value.copy(
                authState = if (isSet) VaultAuthState.ENTER_PIN else VaultAuthState.SETUP_PIN
            )
        }
    }

    fun enterDigit(digit: String) {
        val current = _uiState.value.enteredPin
        if (current.length < 4) {
            val updated = current + digit
            _uiState.value = _uiState.value.copy(enteredPin = updated, errorMessage = null)
            if (updated.length == 4) {
                handlePinComplete(updated)
            }
        }
    }

    fun deleteDigit() {
        val current = _uiState.value.enteredPin
        if (current.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                enteredPin = current.dropLast(1),
                errorMessage = null
            )
        }
    }

    private fun handlePinComplete(pin: String) {
        when (_uiState.value.authState) {
            VaultAuthState.SETUP_PIN -> {
                _uiState.value = _uiState.value.copy(
                    authState = VaultAuthState.CONFIRM_PIN,
                    tempSetupPin = pin,
                    enteredPin = ""
                )
            }
            VaultAuthState.CONFIRM_PIN -> {
                if (pin == _uiState.value.tempSetupPin) {
                    viewModelScope.launch {
                        vaultRepo.setPin(pin)
                        _uiState.value = _uiState.value.copy(
                            authState = VaultAuthState.AUTHENTICATED,
                            enteredPin = ""
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        authState = VaultAuthState.SETUP_PIN,
                        enteredPin = "",
                        tempSetupPin = "",
                        errorMessage = "PINs do not match. Try again."
                    )
                }
            }
            VaultAuthState.ENTER_PIN -> {
                viewModelScope.launch {
                    val isValid = vaultRepo.verifyPin(pin)
                    if (isValid) {
                        _uiState.value = _uiState.value.copy(
                            authState = VaultAuthState.AUTHENTICATED,
                            enteredPin = ""
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            enteredPin = "",
                            errorMessage = "Incorrect PIN. Try again."
                        )
                    }
                }
            }
            VaultAuthState.AUTHENTICATED -> {}
        }
    }

    fun restoreFromVault(video: VideoItem) {
        viewModelScope.launch {
            vaultRepo.restoreFromVault(video.id)
        }
    }
}
