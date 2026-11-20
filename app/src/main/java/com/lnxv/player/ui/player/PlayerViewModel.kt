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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import com.lnxv.player.core.model.QueueLogic
import com.lnxv.player.core.model.RepeatMode
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class QueueUi(
    val size: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF
)

@Immutable
data class PlayerUiState(
    val currentVideo: VideoItem? = null,
    val isControlsVisible: Boolean = true,
    val showSpeedDialog: Boolean = false,
    val showAspectRatioDialog: Boolean = false,
    val showAudioDialog: Boolean = false,
    val showSubtitleDialog: Boolean = false,
    val showOnlineSubtitleDialog: Boolean = false,
    val showPropertiesDialog: Boolean = false,
    val subtitleStyle: SubtitleStyleConfig = SubtitleStyleConfig(),
    val resumePromptPositionMs: Long? = null,
    /** Vault video requested while the vault session is locked: the route pops back instead of playing. */
    val accessDenied: Boolean = false
)

class PlayerViewModel(
    application: Application,
    private val playerManager: com.lnxv.player.playback.engine.PlayerController,
    private val videoRepository: com.lnxv.player.data.repository.VideoRepository,
    private val dataStore: com.lnxv.player.core.datastore.UserPreferencesDataStore,
    private val vaultRepository: com.lnxv.player.data.repository.VaultRepository,
    private val session: com.lnxv.player.core.session.SessionState,
    private val videoPrefs: com.lnxv.player.data.repository.VideoPrefsRepository
) : AndroidViewModel(application) {
    private var pendingSpeed = 1.0f
    private var pendingDecoder = DecoderMode.AUTO
    private var pendingTracks: com.lnxv.player.core.model.RememberedTracks? = null
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
    val subtitleEncoding: StateFlow<String> = playerManager.subtitleEncoding
    fun setSubtitleEncoding(name: String) { viewModelScope.launch { playerManager.setSubtitleEncoding(name) } }

    // Online subtitles (OpenSubtitles): user-initiated only; see OnlineSubtitlesController.
    private val onlineSettings = com.lnxv.player.core.subtitle.online.OnlineSubtitleSettings(application)
    val onlineSubtitles = com.lnxv.player.core.subtitle.online.OnlineSubtitlesController(
        viewModelScope,
        application,
        onlineSettings,
        com.lnxv.player.core.subtitle.online.OpenSubtitlesClient(
            onlineSettings,
            "LNXV v" + (runCatching { application.packageManager.getPackageInfo(application.packageName, 0).versionName }.getOrNull() ?: "1.0")
        ),
        com.lnxv.player.core.subtitle.online.OnlineSubtitleStore(application)
    )

    fun showOnlineSubtitleDialog(show: Boolean) {
        if (!show) onlineSubtitles.cancel()
        _uiState.value = _uiState.value.copy(showOnlineSubtitleDialog = show)
    }

    fun downloadOnlineSubtitle(sub: com.lnxv.player.core.subtitle.OsSubtitle) {
        onlineSubtitles.download(_uiState.value.currentVideo, sub) { file ->
            viewModelScope.launch {
                val ok = playerManager.loadExternalSubtitle(Uri.fromFile(file))
                val app = getApplication<Application>()
                android.widget.Toast.makeText(
                    app,
                    if (ok) app.getString(com.lnxv.player.R.string.online_subs_loaded) else app.getString(com.lnxv.player.R.string.os_err_bad_response),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                if (ok) showOnlineSubtitleDialog(false)
            }
        }
    }
    val audioDelayMs: StateFlow<Long> = playerManager.audioDelayMs

    fun adjustAudioDelay(deltaMs: Long) = playerManager.setAudioDelayMs(playerManager.audioDelayMs.value + deltaMs)
    val eqPreset: StateFlow<com.lnxv.player.core.model.EqPreset> = playerManager.eqPreset
    val audioOnly: StateFlow<Boolean> = playerManager.audioOnly
    fun toggleAudioOnly() = playerManager.setAudioOnly(!playerManager.audioOnly.value)
    val videoColor: StateFlow<com.lnxv.player.core.model.VideoColor> = playerManager.videoColor
    fun setVideoColor(c: com.lnxv.player.core.model.VideoColor) = playerManager.setVideoColor(c)
    val boostMb: StateFlow<Int> = playerManager.boostMb
    /** Boost in percent (100 = off) for the menu label. */
    fun boostPercent(mb: Int): Int = com.lnxv.player.core.model.AudioBoost.mbToPercent(mb)
    fun cycleBoost() {
        val next = com.lnxv.player.core.model.AudioBoost.next(boostPercent(playerManager.boostMb.value))
        playerManager.setBoostMb(com.lnxv.player.core.model.AudioBoost.percentToMb(next))
        if (com.lnxv.player.core.model.AudioBoost.needsWarning(next)) {
            android.widget.Toast.makeText(
                getApplication<Application>(),
                getApplication<Application>().getString(com.lnxv.player.R.string.audio_boost_warning),
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    fun cycleEqPreset() = playerManager.setEqPreset(playerManager.eqPreset.value.next())

    fun resetAudioDelay() = playerManager.setAudioDelayMs(0L)

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var queue: QueueLogic? = null
    private val _queueUi = MutableStateFlow(QueueUi())
    val queueUi: StateFlow<QueueUi> = _queueUi.asStateFlow()

    /** Vault, transient ("Open with") and network-stream videos are never remembered. */
    private fun rememberableId(video: VideoItem?): Long? =
        video?.takeIf { !it.isVault && it.id > 0L && it.folderPath != "Network Streams" }?.id

    init {
        playerManager.onDecoderFallback = {
            if (playerManager.owner === this) rememberableId(_uiState.value.currentVideo)?.let { id ->
                viewModelScope.launch { videoPrefs.setDecoder(id, DecoderMode.SOFTWARE) }
            }
        }
        playerManager.onAudioTrackChosen = { trackId ->
            if (playerManager.owner === this) rememberableId(_uiState.value.currentVideo)?.let { id ->
                viewModelScope.launch { videoPrefs.setAudioTrack(id, trackId) }
            }
        }
        playerManager.onSubtitleChosen = { trackId, off ->
            if (playerManager.owner === this) rememberableId(_uiState.value.currentVideo)?.let { id ->
                viewModelScope.launch { videoPrefs.setSubtitle(id, trackId, off) }
            }
        }
        // Auto-play next: when the current item ends, advance the queue (repeat-one is handled by ExoPlayer itself).
        viewModelScope.launch {
            playerManager.playbackState.map { it.isEnded }.distinctUntilChanged().collect { ended ->
                if (ended && !closed && playerManager.owner === this@PlayerViewModel) playNext()
            }
        }
    }

    private fun publishQueue() {
        val q = queue
        _queueUi.value = if (q == null) QueueUi() else QueueUi(q.size, q.hasNext, q.hasPrevious, q.shuffle, q.repeat)
    }

    private suspend fun buildQueue(video: VideoItem) {
        val override = session.queueOverride
        session.queueOverride = null
        val ids = when {
            override != null -> override
            video.isVault || video.id == LNXVApplication.TRANSIENT_VIDEO_ID -> emptyList()
            else -> videoRepository.getFolderVideoIds(video.folderPath)
        }
        val keep = queue
        queue = if (ids.size > 1) QueueLogic(
            ids, video.id,
            shuffle = keep?.shuffle ?: false,
            repeat = keep?.repeat ?: RepeatMode.OFF
        ) else null
        publishQueue()
    }

    fun playNext() {
        val id = queue?.next() ?: return
        publishQueue()
        switchTo(id)
    }

    fun playPrevious() {
        // Like most players: restart the current video when more than 3 s in, otherwise go to the previous one.
        if (positionMs.value > 3_000L) { playerManager.seekTo(0L); return }
        val id = queue?.previous() ?: return
        publishQueue()
        switchTo(id)
    }

    fun toggleShuffle() {
        queue?.let { it.setShuffle(!it.shuffle); publishQueue() }
    }

    fun cycleRepeat() {
        val q = queue ?: return
        val mode = q.cycleRepeat()
        playerManager.repeatMode =
            if (mode == RepeatMode.ONE) androidx.media3.common.Player.REPEAT_MODE_ONE else androidx.media3.common.Player.REPEAT_MODE_OFF
        publishQueue()
    }

    private fun switchTo(videoId: Long) {
        if (closed) return
        saveProgress()
        viewModelScope.launch {
            val video = videoRepository.getVideoById(videoId) ?: return@launch
            begin(video, rebuildQueue = false)
        }
    }

    fun loadVideo(videoId: Long) {
        if (_uiState.value.currentVideo?.id == videoId) return
        viewModelScope.launch {
            val video = (if (videoId == LNXVApplication.TRANSIENT_VIDEO_ID) session.transientVideo
                else videoRepository.getVideoById(videoId)) ?: return@launch
            begin(video, rebuildQueue = true)
        }
    }

    private suspend fun begin(video: VideoItem, rebuildQueue: Boolean) {
        run {
            if (video.isVault && !vaultRepository.isUnlocked) {
                _uiState.value = _uiState.value.copy(accessDenied = true)
                return
            }
            session.playingVaultItem = video.isVault
            playerManager.owner = this@PlayerViewModel
            val prefs = dataStore.userPreferencesFlow.first()
            val remembered = rememberableId(video)?.let { videoPrefs.get(it) }
            pendingDecoder = remembered?.decoderOverride ?: DecoderMode.fromName(prefs.decoderMode)
            pendingTracks = remembered?.tracks
            playerManager.setExactSeek(prefs.exactSeek)
            val last = video.lastPositionMs
            val canResume = last > 5_000L && (video.durationMs <= 0L || last < video.durationMs - 5_000L)
            val perVideo = if (prefs.isEnabled(com.lnxv.player.core.flags.FeatureFlag.PER_VIDEO_SPEED) && !video.isVault)
                com.lnxv.player.core.model.PerVideoSpeed.get(prefs.perVideoSpeeds, video.id) else null
            val speed = perVideo ?: if (prefs.rememberSpeed) prefs.lastSpeed else prefs.defaultSpeed
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
            // The folder queue only feeds next/previous, so build it AFTER playback has been handed to ExoPlayer
            // instead of making the first frame wait for another database read.
            if (rebuildQueue) buildQueue(video)
        }
    }

    private fun startPlayback(video: VideoItem, positionMs: Long, speed: Float) {
        // Vault videos never expose their real title to notification / lock-screen / Bluetooth metadata.
        playerManager.presetDecoderMode(pendingDecoder)
        playerManager.prepareAndPlay(
            uri = video.uri,
            startPositionMs = positionMs,
            externalSubtitleUri = onlineSubtitles.rememberedFor(video)?.let { Uri.fromFile(it) },
            title = if (video.isVault) "Private video" else video.title
        )
        playerManager.applyRememberedTracks(pendingTracks)
        playerManager.setPlaybackSpeed(speed)
        playerManager.setSkipSilence(userPrefs.value.isEnabled(com.lnxv.player.core.flags.FeatureFlag.SKIP_SILENCE))
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
        playerManager.setExactSeek(userPrefs.value.exactSeek)
        playerManager.presetDecoderMode(DecoderMode.fromName(userPrefs.value.decoderMode))
        playerManager.prepareAndPlay(uri, title = title)
    }

    fun togglePlayPause() = playerManager.togglePlayPause()

    fun seekTo(positionMs: Long) = playerManager.seekTo(positionMs)

    fun seekRelative(deltaMs: Long) = playerManager.seekRelative(deltaMs)

    fun setPlaybackSpeed(speed: Float) {
        playerManager.setPlaybackSpeed(speed)
        if (userPrefs.value.rememberSpeed) {
            viewModelScope.launch { dataStore.updateLastSpeed(speed) }
        }
        val cur = _uiState.value.currentVideo
        if (cur != null && !cur.isVault && userPrefs.value.isEnabled(com.lnxv.player.core.flags.FeatureFlag.PER_VIDEO_SPEED)) {
            viewModelScope.launch { dataStore.setVideoSpeed(cur.id, speed) }
        }
    }

    fun startSpeedPreview() = playerManager.startSpeedPreview(2.0f)

    fun stopSpeedPreview() = playerManager.stopSpeedPreview()

    fun setAspectRatio(mode: AspectRatioMode) = playerManager.setAspectRatio(mode)

    fun setDecoderMode(mode: DecoderMode) {
        playerManager.setDecoderMode(mode)
        rememberableId(_uiState.value.currentVideo)?.let { id -> viewModelScope.launch { videoPrefs.setDecoder(id, mode) } }
    }

    /** Auto -> Hardware only -> Software preferred -> Auto, for this video only. */
    fun cycleDecoderMode() {
        val next = when (playerManager.playbackState.value.decoderMode) {
            DecoderMode.AUTO -> DecoderMode.HARDWARE
            DecoderMode.HARDWARE -> DecoderMode.SOFTWARE
            DecoderMode.SOFTWARE -> DecoderMode.AUTO
        }
        setDecoderMode(next)
    }

    fun stats(): com.lnxv.player.core.model.PlayerStats = playerManager.statsSnapshot()

    fun beginScrub() = playerManager.beginScrub()
    fun scrubTo(positionMs: Long) = playerManager.scrubTo(positionMs)
    fun endScrub() = playerManager.endScrub()

    fun toggleScreenLock() = playerManager.toggleScreenLock()

    fun setPointA() = playerManager.setPointA()
    fun setPointB() = playerManager.setPointB()
    fun clearABRepeat() = playerManager.clearABRepeat()

    fun startSleepTimer(minutes: Int) = playerManager.startSleepTimer(
        minutes,
        fadeOut = userPrefs.value.isEnabled(com.lnxv.player.core.flags.FeatureFlag.SLEEP_TIMER_EXTRAS)
    )
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
                    "Unsupported subtitle file. Use .srt, .vtt, .ass, .ssa or .ttml",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun adjustSubtitleDelay(deltaMs: Long) {
        viewModelScope.launch { playerManager.adjustSubtitleDelay(deltaMs) }
    }

    fun describeStream(): List<Pair<String, String>> = playerManager.describeStream()

    /** Full plain-text report (container index check + player state + tracks + event log). */
    suspend fun buildDiagnosticsReport(): String {
        val video = _uiState.value.currentVideo
        val container = if (video != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.lnxv.player.playback.diag.ContainerInspector.describe(getApplication<Application>(), video.uri)
            }
        } else {
            "No video loaded"
        }
        return playerManager.diagnosticsSnapshot(video, container)
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
        session.playingVaultItem = false
        if (playerManager.owner === this) {
            playerManager.owner = null
            playerManager.isVideoScreenActive = false
            playerManager.repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
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
        if (playerManager.owner === this) {
            playerManager.onDecoderFallback = null
            playerManager.onAudioTrackChosen = null
            playerManager.onSubtitleChosen = null
        }
        closePlayer()
    }
}
