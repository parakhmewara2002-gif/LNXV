package com.lnxv.player.ui.vault

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lnxv.player.LNXVApplication
import com.lnxv.player.core.model.VideoItem
import com.lnxv.player.core.security.PinHasher
import com.lnxv.player.data.repository.VaultJobState
import com.lnxv.player.data.repository.VerifyResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val pinLength: Int = PinHasher.RECOMMENDED_PIN_LENGTH,
    val errorMessage: String? = null,
    val lockoutRemainingSec: Long = 0L,
    val shakeCount: Int = 0,
    val showForgotConfirm: Boolean = false,
    val busy: Boolean = false
) {
    /** While entering an existing PIN its length is known; during setup the user chooses 4-8 and confirms. */
    val needsConfirmButton: Boolean get() = authState != VaultAuthState.ENTER_PIN
}

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LNXVApplication
    private val vaultRepo = app.vaultRepository
    private val transfers = app.vaultTransfers

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    val vaultVideos: StateFlow<List<VideoItem>> = vaultRepo.vaultVideosFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val legacyCount: StateFlow<Int> = vaultRepo.legacyCountFlow.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = 0
    )

    val jobState: StateFlow<VaultJobState> = transfers.state

    private var lockoutJob: Job? = null

    init {
        viewModelScope.launch {
            val isSet = vaultRepo.isPinSet()
            val len = if (isSet) vaultRepo.pinLength() else PinHasher.RECOMMENDED_PIN_LENGTH
            // A session that is still unlocked (e.g. returning from the player) stays authenticated.
            val state = when {
                isSet && vaultRepo.isUnlocked -> VaultAuthState.AUTHENTICATED
                isSet -> VaultAuthState.ENTER_PIN
                else -> VaultAuthState.SETUP_PIN
            }
            _uiState.value = _uiState.value.copy(authState = state, pinLength = len)
            if (state == VaultAuthState.AUTHENTICATED) onAuthenticated()
            else startLockoutCountdownIfNeeded()
        }
    }

    private fun onAuthenticated() {
        viewModelScope.launch { vaultRepo.cleanupOrphans() }
    }

    private fun startLockoutCountdownIfNeeded() {
        lockoutJob?.cancel()
        lockoutJob = viewModelScope.launch {
            var remaining = vaultRepo.lockoutRemainingMs()
            while (remaining > 0) {
                _uiState.value = _uiState.value.copy(lockoutRemainingSec = (remaining + 999) / 1000)
                delay(1000)
                remaining = vaultRepo.lockoutRemainingMs()
            }
            _uiState.value = _uiState.value.copy(lockoutRemainingSec = 0, errorMessage = null)
        }
    }

    fun enterDigit(digit: String) {
        val s = _uiState.value
        if (s.busy || s.lockoutRemainingSec > 0 || s.authState == VaultAuthState.AUTHENTICATED) return
        val max = if (s.authState == VaultAuthState.ENTER_PIN) s.pinLength else PinHasher.MAX_PIN_LENGTH
        if (s.enteredPin.length >= max) return
        val updated = s.enteredPin + digit
        _uiState.value = s.copy(enteredPin = updated, errorMessage = null)
        if (s.authState == VaultAuthState.ENTER_PIN && updated.length == s.pinLength) submit()
    }

    fun deleteDigit() {
        val current = _uiState.value.enteredPin
        if (current.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(enteredPin = current.dropLast(1), errorMessage = null)
        }
    }

    /** OK button (setup / confirm) or auto-submit once the known PIN length is reached. */
    fun submit() {
        val s = _uiState.value
        val pin = s.enteredPin
        if (s.busy) return
        when (s.authState) {
            VaultAuthState.SETUP_PIN -> {
                if (!PinHasher.isValidPin(pin)) {
                    _uiState.value = s.copy(errorMessage = "Use 4-8 digits (6 recommended)")
                    return
                }
                _uiState.value = s.copy(authState = VaultAuthState.CONFIRM_PIN, tempSetupPin = pin, enteredPin = "")
            }
            VaultAuthState.CONFIRM_PIN -> {
                if (pin == s.tempSetupPin) {
                    _uiState.value = s.copy(busy = true)
                    viewModelScope.launch {
                        vaultRepo.setPin(pin)
                        _uiState.value = _uiState.value.copy(
                            authState = VaultAuthState.AUTHENTICATED, enteredPin = "", tempSetupPin = "",
                            pinLength = pin.length, busy = false
                        )
                        onAuthenticated()
                    }
                } else {
                    _uiState.value = s.copy(
                        authState = VaultAuthState.SETUP_PIN, enteredPin = "", tempSetupPin = "",
                        errorMessage = "PINs do not match. Try again.", shakeCount = s.shakeCount + 1
                    )
                }
            }
            VaultAuthState.ENTER_PIN -> {
                _uiState.value = s.copy(busy = true)
                viewModelScope.launch {
                    when (val r = vaultRepo.verifyPin(pin)) {
                        is VerifyResult.Success -> {
                            _uiState.value = _uiState.value.copy(
                                authState = VaultAuthState.AUTHENTICATED, enteredPin = "", busy = false, errorMessage = null
                            )
                            onAuthenticated()
                        }
                        is VerifyResult.Wrong -> _uiState.value = _uiState.value.copy(
                            enteredPin = "", busy = false, shakeCount = _uiState.value.shakeCount + 1,
                            errorMessage = "Incorrect PIN. ${r.attemptsUntilLock} attempt(s) left before lockout."
                        )
                        is VerifyResult.Locked -> {
                            _uiState.value = _uiState.value.copy(
                                enteredPin = "", busy = false, shakeCount = _uiState.value.shakeCount + 1,
                                errorMessage = "Too many attempts."
                            )
                            startLockoutCountdownIfNeeded()
                        }
                    }
                }
            }
            VaultAuthState.AUTHENTICATED -> {}
        }
    }

    fun requestForgotPin() { _uiState.value = _uiState.value.copy(showForgotConfirm = true) }
    fun dismissForgotPin() { _uiState.value = _uiState.value.copy(showForgotConfirm = false) }

    /** Forgot PIN: irreversibly wipes every vault video, then starts PIN setup again. */
    fun confirmWipe() {
        viewModelScope.launch {
            transfers.cancelAll()
            vaultRepo.wipe()
            _uiState.value = VaultUiState(authState = VaultAuthState.SETUP_PIN)
        }
    }

    fun lock() {
        transfers.cancelAll()
        vaultRepo.lock()
        _uiState.value = _uiState.value.copy(authState = VaultAuthState.ENTER_PIN, enteredPin = "")
    }

    fun restoreFromVault(video: VideoItem) = transfers.enqueueRestore(video)

    fun encryptLegacyItems() {
        viewModelScope.launch { vaultRepo.legacyItems().forEach { transfers.enqueueMove(it) } }
    }

    fun cancelTransfers() = transfers.cancelAll()

    suspend fun loadThumbnail(videoId: Long): ByteArray? = vaultRepo.loadThumbnail(videoId)
}
