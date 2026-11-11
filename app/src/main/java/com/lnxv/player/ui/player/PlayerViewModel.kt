package com.lnxv.player.ui.player

import androidx.compose.runtime.Immutable
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lnxv.player.LNXVApplication
import com.lnxv.player.core.datastore.UserPreferences
import com.lnxv.player.core.model.AspectRatioMode
import com.lnxv.player.core.model.AudioTrackInfo
import com.lnxv.player.core.model.DecoderMode
import com.lnxv.player.core.model.PlaybackState
import com.lnxv.player.core.model.SubtitleStyleConfig
import com.lnxv.player.core.model.SubtitleTrackInfo
import com.lnxv.player.core.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class PlayerUiState(
    val currentVideo: VideoItem? = null,
    val isControlsVisible: Boolean = true,
    val showSpeedDialog: Boolean = false,
    val showAspectRatioDialog: Boolean = false,
    val showAudioDialog: Boolean = false,
    val showSubtitleDialog: Boolean = false,
    val showPropertiesDialog: Boolean = false,
    val subtitleStyle: SubtitleStyleConfig = SubtitleStyleConfig(),
    val resumePromptPositionMs: Long? = null
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val playerManager = (application as LNXVApplication).playerManager
    private val videoRepository = (application as LNXVApplication).videoRepository
    private val dataStore = (application as LNXVApplication).dataStore
    private var pendingSpeed = 1.0f
    private var closed = false

    val userPrefs: StateFlow<UserPreferences> = dataStore.userPreferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = UserPreferences()
    )

    val playbackState: StateFlow<PlaybackState> = playerManager.playbackState
    val positionMs: StateFlow<Long> = playerManager.positionMs
    val audioTracks: StateFlow<List<AudioTrackInfo>> = playerManager.audioTracks
    val subtitleTracks: StateFlow<List<SubtitleTrackInfo>> = playerManager.subtitleTracks
    val subtitleDelayMs: StateFlow<Long> = playerManager.subtitleDelayMs
    val hasExternalSubtitle: StateFlow<Boolean> = playerManager.hasExternalSubtitle

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    fun loadVideo(videoId: Long) {
        if (_uiState.value.currentVideo?.id == videoId) return
        viewModelScope.launch {
            val video = videoRepository.getVideoById(videoId) ?: return@launch
            playerManager.owner = this@PlayerViewModel
            val prefs = dataStore.userPreferencesFlow.first()
            val last = video.lastPositionMs
            val canResume = last > 5_000L && (video.durationMs <= 0L || last < video.durationMs - 5_000L)
            val speed = if (prefs.rememberSpeed) prefs.lastSpeed else prefs.defaultSpeed
            _uiState.value = _uiState.value.copy(currentVideo = video, resumePromptPositionMs = null)

            when {
                !canResume || prefs.resumePlaybackMode == "NEVER" -> startPlayback(video, 0L, speed)
                prefs.resumePlaybackMode == "ASK" -> {
                    pendingSpeed = speed
                    playerManager.pause()
                    _uiState.value = _uiState.value.copy(resumePromptPositionMs = last)
                }
                else -> startPlayback(video, last, speed)
            }
        }
    }

    private fun startPlayback(video: VideoItem, positionMs: Long, speed: Float) {
        playerManager.prepareAndPlay(uri = video.uri, startPositionMs = positionMs)
        playerManager.setPlaybackSpeed(speed)
    }

    fun resumeFromPrompt() {
        val video = _uiState.value.currentVideo ?: return
        val pos = _uiState.value.resumePromptPositionMs ?: 0L
        _uiState.value = _uiState.value.copy(resumePromptPositionMs = null)
        startPlayback(video, pos, pendingSpeed)
    }

    fun startOverFromPrompt() {
        val video = _uiState.value.currentVideo ?: return
        _uiState.value = _uiState.value.copy(resumePromptPositionMs = null)
        startPlayback(video, 0L, pendingSpeed)
    }

    fun playNetworkStream(url: String, title: String) {
        val uri = Uri.parse(url)
        val dummyVideo = VideoItem(
            id = System.currentTimeMillis(),
            title = title,
            uri = uri,
            path = url,
            durationMs = 0L,
            sizeBytes = 0L,
            width = 1920,
            height = 1080,
            mimeType = "video/*",
            dateAddedSec = System.currentTimeMillis() / 1000,
            folderPath = "Network Streams",
            folderName = "Network"
        )
        _uiState.value = _uiState.value.copy(currentVideo = dummyVideo)
        playerManager.prepareAndPlay(uri)
    }

    fun togglePlayPause() = playerManager.togglePlayPause()

    fun seekTo(positionMs: Long) = playerManager.seekTo(positionMs)

    fun seekRelative(deltaMs: Long) = playerManager.seekRelative(deltaMs)

    fun setPlaybackSpeed(speed: Float) {
        playerManager.setPlaybackSpeed(speed)
        if (userPrefs.value.rememberSpeed) {
            viewModelScope.launch { dataStore.updateLastSpeed(speed) }
        }
    }

    fun startSpeedPreview() = playerManager.startSpeedPreview(2.0f)

    fun stopSpeedPreview() = playerManager.stopSpeedPreview()

    fun setAspectRatio(mode: AspectRatioMode) = playerManager.setAspectRatio(mode)

    fun setDecoderMode(mode: DecoderMode) = playerManager.setDecoderMode(mode)

    fun toggleScreenLock() = playerManager.toggleScreenLock()

    fun setPointA() = playerManager.setPointA()
    fun setPointB() = playerManager.setPointB()
    fun clearABRepeat() = playerManager.clearABRepeat()

    fun startSleepTimer(minutes: Int) = playerManager.startSleepTimer(minutes)
    fun cancelSleepTimer() = playerManager.cancelSleepTimer()

    fun selectAudioTrack(track: AudioTrackInfo) = playerManager.selectAudioTrack(track)
    fun selectSubtitleTrack(track: SubtitleTrackInfo?) = playerManager.selectSubtitleTrack(track)

    fun toggleControlsVisibility() {
        _uiState.value = _uiState.value.copy(isControlsVisible = !_uiState.value.isControlsVisible)
    }

    fun setControlsVisibility(visible: Boolean) {
        _uiState.value = _uiState.value.copy(isControlsVisible = visible)
    }

    fun showSpeedDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSpeedDialog = show)
    }

    fun showAspectRatioDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showAspectRatioDialog = show)
    }

    fun showAudioDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showAudioDialog = show)
    }

    fun showSubtitleDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showSubtitleDialog = show)
    }

    fun showPropertiesDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showPropertiesDialog = show)
    }

    fun loadExternalSubtitle(uri: Uri) {
        viewModelScope.launch {
            val ok = playerManager.loadExternalSubtitle(uri)
            if (!ok) {
                android.widget.Toast.makeText(
                    getApplication<Application>(),
                    "Unsupported subtitle file. Use .srt or .vtt",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun adjustSubtitleDelay(deltaMs: Long) {
        viewModelScope.launch { playerManager.adjustSubtitleDelay(deltaMs) }
    }

    fun setVideoScreenActive(active: Boolean) {
        playerManager.isVideoScreenActive = active
    }

    fun stepFrame(forward: Boolean) = playerManager.stepFrame(forward)

    /** Saves progress, then stops playback if this screen still owns the player. */
    fun closePlayer() {
        if (closed) return
        saveProgress()
        closed = true
        if (playerManager.owner === this) {
            playerManager.owner = null
            playerManager.isVideoScreenActive = false
            playerManager.stopPlayback()
        }
    }

    fun saveProgress() {
        if (closed) return
        if (playerManager.owner !== this) return
        val video = _uiState.value.currentVideo ?: return
        val currentPosition = positionMs.value
        viewModelScope.launch {
            videoRepository.updatePlaybackPosition(video.id, currentPosition)
        }
    }

    override fun onCleared() {
        super.onCleared()
        closePlayer()
    }
}
