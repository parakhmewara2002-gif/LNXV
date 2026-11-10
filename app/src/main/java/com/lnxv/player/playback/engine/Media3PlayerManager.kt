package com.lnxv.player.playback.engine

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.lnxv.player.core.model.ABRepeatState
import com.lnxv.player.core.model.AspectRatioMode
import com.lnxv.player.core.model.AudioTrackInfo
import com.lnxv.player.core.model.DecoderMode
import com.lnxv.player.core.model.PlaybackState
import com.lnxv.player.core.model.SubtitleTrackInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class Media3PlayerManager(private val context: Context) : Player.Listener {

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val trackSelector = DefaultTrackSelector(context).apply {
        setParameters(buildUponParameters().setPreferredTextLanguage("en"))
    }

    private val renderersFactory = DefaultRenderersFactory(context).apply {
        setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        setEnableDecoderFallback(true) // Software decoding fallback when hardware decoding fails
    }

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context, renderersFactory)
        .setTrackSelector(trackSelector)
        .setSeekBackIncrementMs(10000L)
        .setSeekForwardIncrementMs(10000L)
        .build().apply {
            addListener(this@Media3PlayerManager)
        }

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<AudioTrackInfo>>(emptyList())
    val audioTracks: StateFlow<List<AudioTrackInfo>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<SubtitleTrackInfo>>(emptyList())
    val subtitleTracks: StateFlow<List<SubtitleTrackInfo>> = _subtitleTracks.asStateFlow()

    private var progressTrackingJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var savedSpeedBeforePreview: Float? = null

    init {
        startProgressTracking()
    }

    fun prepareAndPlay(
        uri: Uri,
        startPositionMs: Long = 0L,
        externalSubtitleUri: Uri? = null
    ) {
        val mediaItemBuilder = MediaItem.Builder().setUri(uri)

        if (externalSubtitleUri != null) {
            val mimeType = when {
                externalSubtitleUri.toString().endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
                externalSubtitleUri.toString().endsWith(".ass", ignoreCase = true) ||
                externalSubtitleUri.toString().endsWith(".ssa", ignoreCase = true) -> MimeTypes.TEXT_SSA
                else -> MimeTypes.APPLICATION_SUBRIP
            }

            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(externalSubtitleUri)
                .setMimeType(mimeType)
                .setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()

            mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
        }

        exoPlayer.setMediaItem(mediaItemBuilder.build())
        exoPlayer.prepare()
        if (startPositionMs > 0) {
            exoPlayer.seekTo(startPositionMs)
        }
        exoPlayer.playWhenReady = true
    }

    fun play() {
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs.coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L)))
    }

    fun seekRelative(deltaMs: Long) {
        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L))
        exoPlayer.seekTo(target)
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 4.0f)
        exoPlayer.playbackParameters = PlaybackParameters(clamped)
        _playbackState.update { it.copy(playbackSpeed = clamped) }
    }

    fun startSpeedPreview(previewSpeed: Float = 2.0f) {
        if (savedSpeedBeforePreview == null) {
            savedSpeedBeforePreview = exoPlayer.playbackParameters.speed
            exoPlayer.playbackParameters = PlaybackParameters(previewSpeed)
        }
    }

    fun stopSpeedPreview() {
        savedSpeedBeforePreview?.let { speed ->
            exoPlayer.playbackParameters = PlaybackParameters(speed)
            savedSpeedBeforePreview = null
        }
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        _playbackState.update { it.copy(aspectRatio = mode) }
    }

    fun setDecoderMode(mode: DecoderMode) {
        _playbackState.update { it.copy(decoderMode = mode) }
    }

    fun toggleScreenLock() {
        _playbackState.update { it.copy(isScreenLocked = !it.isScreenLocked) }
    }

    // --- AB Repeat Functionality ---
    fun setPointA() {
        val currentPos = exoPlayer.currentPosition
        _playbackState.update {
            it.copy(abRepeat = it.abRepeat.copy(isEnabled = true, pointA = currentPos))
        }
    }

    fun setPointB() {
        val currentPos = exoPlayer.currentPosition
        val pointA = _playbackState.value.abRepeat.pointA ?: 0L
        if (currentPos > pointA) {
            _playbackState.update {
                it.copy(abRepeat = it.abRepeat.copy(isEnabled = true, pointB = currentPos))
            }
        }
    }

    fun clearABRepeat() {
        _playbackState.update {
            it.copy(abRepeat = ABRepeatState(isEnabled = false, pointA = null, pointB = null))
        }
    }

    // --- Sleep Timer Functionality ---
    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _playbackState.update { it.copy(sleepTimerRemainingSeconds = null) }
            return
        }

        var remainingSeconds = minutes * 60L
        _playbackState.update { it.copy(sleepTimerRemainingSeconds = remainingSeconds) }

        sleepTimerJob = scope.launch {
            while (remainingSeconds > 0 && isActive) {
                delay(1000L)
                remainingSeconds--
                _playbackState.update { it.copy(sleepTimerRemainingSeconds = remainingSeconds) }
            }
            pause()
            _playbackState.update { it.copy(sleepTimerRemainingSeconds = null) }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _playbackState.update { it.copy(sleepTimerRemainingSeconds = null) }
    }

    // --- Track Selection (Audio & Subtitle) ---
    fun selectAudioTrack(track: AudioTrackInfo) {
        val currentTracks = exoPlayer.currentTracks
        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    if (format.id == track.id) {
                        trackSelector.setParameters(
                            trackSelector.buildUponParameters()
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(i)))
                        )
                        updateTrackLists(currentTracks)
                        return
                    }
                }
            }
        }
    }

    fun selectSubtitleTrack(track: SubtitleTrackInfo?) {
        if (track == null) {
            // Turn off subtitles
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            )
        } else {
            val currentTracks = exoPlayer.currentTracks
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            )
            for (group in currentTracks.groups) {
                if (group.type == C.TRACK_TYPE_TEXT) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        if (format.id == track.id) {
                            trackSelector.setParameters(
                                trackSelector.buildUponParameters()
                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(i)))
                            )
                            updateTrackLists(currentTracks)
                            return
                        }
                    }
                }
            }
        }
        updateTrackLists(exoPlayer.currentTracks)
    }

    // --- Player.Listener Implementation ---
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _playbackState.update { it.copy(isPlaying = isPlaying) }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        val isBuffering = playbackState == Player.STATE_BUFFERING
        val isEnded = playbackState == Player.STATE_ENDED
        val duration = exoPlayer.duration.coerceAtLeast(0L)

        _playbackState.update {
            it.copy(
                isBuffering = isBuffering,
                isEnded = isEnded,
                durationMs = duration
            )
        }
    }

    override fun onTracksChanged(tracks: Tracks) {
        updateTrackLists(tracks)
    }

    private fun updateTrackLists(tracks: Tracks) {
        val audioList = mutableListOf<AudioTrackInfo>()
        val subtitleList = mutableListOf<SubtitleTrackInfo>()

        for (group in tracks.groups) {
            when (group.type) {
                C.TRACK_TYPE_AUDIO -> {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val isSelected = group.isTrackSelected(i)
                        audioList.add(
                            AudioTrackInfo(
                                id = format.id ?: "audio_$i",
                                language = format.language ?: "und",
                                label = format.label ?: "Track ${i + 1} (${format.language ?: "Unknown"})",
                                channelCount = format.channelCount,
                                sampleRate = format.sampleRate,
                                isSelected = isSelected
                            )
                        )
                    }
                }
                C.TRACK_TYPE_TEXT -> {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val isSelected = group.isTrackSelected(i)
                        subtitleList.add(
                            SubtitleTrackInfo(
                                id = format.id ?: "sub_$i",
                                language = format.language ?: "und",
                                label = format.label ?: "Subtitle ${i + 1} (${format.language ?: "Unknown"})",
                                isExternal = false,
                                isSelected = isSelected
                            )
                        )
                    }
                }
            }
        }

        _audioTracks.value = audioList
        _subtitleTracks.value = subtitleList
    }

    private fun startProgressTracking() {
        progressTrackingJob?.cancel()
        progressTrackingJob = scope.launch {
            while (isActive) {
                val currentPos = exoPlayer.currentPosition
                val duration = exoPlayer.duration.coerceAtLeast(0L)
                val buffered = exoPlayer.bufferedPosition

                // Check AB Repeat boundary
                val abState = _playbackState.value.abRepeat
                if (abState.isEnabled && abState.pointA != null && abState.pointB != null) {
                    if (currentPos >= abState.pointB) {
                        exoPlayer.seekTo(abState.pointA)
                    }
                }

                _playbackState.update {
                    it.copy(
                        currentPositionMs = currentPos,
                        durationMs = duration,
                        bufferedPositionMs = buffered
                    )
                }
                delay(200L)
            }
        }
    }

    fun release() {
        progressTrackingJob?.cancel()
        sleepTimerJob?.cancel()
        exoPlayer.removeListener(this)
        exoPlayer.release()
    }
}
