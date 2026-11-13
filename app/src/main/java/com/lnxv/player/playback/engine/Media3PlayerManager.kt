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
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.lnxv.player.LNXVApplication
import com.lnxv.player.data.repository.VaultFiles
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.lnxv.player.core.model.ABRepeatState
import com.lnxv.player.core.model.AspectRatioMode
import com.lnxv.player.core.model.AudioTrackInfo
import com.lnxv.player.core.model.DecoderMode
import com.lnxv.player.core.model.PlaybackState
import com.lnxv.player.core.model.SubtitleTrackInfo
import com.lnxv.player.core.util.SeekMath
import com.lnxv.player.playback.diag.DiagnosticsCollector
import com.lnxv.player.core.util.SubtitleShifter
import java.io.File
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong
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
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                VaultRoutingDataSource.Factory(
                    androidx.media3.datasource.DefaultDataSource.Factory(context),
                    EncryptedDataSource.Factory(
                        keyProvider = { (context.applicationContext as LNXVApplication).vaultKeyManager.requireKey() },
                        fileResolver = { uri -> java.io.File(VaultFiles.dir(context), uri.lastPathSegment ?: "") }
                    )
                ),
                SeekFixExtractorsFactory(context)
            )
        )
        .setAudioAttributes(
            androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true
        )
        .setHandleAudioBecomingNoisy(true)
        .setSeekBackIncrementMs(10000L)
        .setSeekForwardIncrementMs(10000L)
        .build().apply {
            addListener(this@Media3PlayerManager)
        }

    /** Event log + decoder info + report builder (used by Video Properties -> diagnostic report). */
    val diagnostics = DiagnosticsCollector(context).also { it.attach(exoPlayer) }

    fun diagnosticsSnapshot(video: com.lnxv.player.core.model.VideoItem?, containerInfo: String): String =
        diagnostics.snapshot(exoPlayer, video, containerInfo)

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

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

    // --- External subtitle + delay (SRT / WebVTT only: cue times are shifted in a cached copy) ---
    private var externalSubtitleSource: Uri? = null
    private val _subtitleDelayMs = MutableStateFlow(0L)
    val subtitleDelayMs: StateFlow<Long> = _subtitleDelayMs.asStateFlow()
    private val _hasExternalSubtitle = MutableStateFlow(false)
    val hasExternalSubtitle: StateFlow<Boolean> = _hasExternalSubtitle.asStateFlow()

    /** @return false when the file is not a supported SRT/VTT subtitle. */
    suspend fun loadExternalSubtitle(uri: Uri): Boolean {
        externalSubtitleSource = uri
        _subtitleDelayMs.value = 0L
        val ok = applyExternalSubtitle()
        if (!ok) {
            externalSubtitleSource = null
            _hasExternalSubtitle.value = false
        }
        return ok
    }

    suspend fun adjustSubtitleDelay(deltaMs: Long) {
        if (externalSubtitleSource == null) return
        _subtitleDelayMs.value = (_subtitleDelayMs.value + deltaMs).coerceIn(-60_000L, 60_000L)
        applyExternalSubtitle()
    }

    private suspend fun applyExternalSubtitle(): Boolean {
        val source = externalSubtitleSource ?: return false
        val delay = _subtitleDelayMs.value
        val prepared: Pair<Uri, Boolean> = withContext(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
                    ?: return@withContext null
                var text = bytes.toString(Charsets.UTF_8)
                if (text.contains('\uFFFD')) text = bytes.toString(Charsets.ISO_8859_1)
                if (!SubtitleShifter.looksLikeSubtitle(text)) return@withContext null
                val isVtt = text.trimStart('\uFEFF', ' ', '\n', '\r').startsWith("WEBVTT")
                val dir = File(context.cacheDir, "subs").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, "sub_${System.nanoTime()}." + if (isVtt) "vtt" else "srt")
                file.writeText(SubtitleShifter.shift(text, delay))
                Pair(Uri.fromFile(file), isVtt)
            } catch (e: Exception) {
                null
            }
        } ?: return false

        return withContext(Dispatchers.Main) {
            val current = exoPlayer.currentMediaItem ?: return@withContext false
            val position = exoPlayer.currentPosition
            val playWhenReady = exoPlayer.playWhenReady
            val config = MediaItem.SubtitleConfiguration.Builder(prepared.first)
                .setMimeType(if (prepared.second) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
                .setLanguage("en")
                .setLabel("External subtitle")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            exoPlayer.setMediaItem(
                current.buildUpon().setSubtitleConfigurations(listOf(config)).build(),
                position
            )
            exoPlayer.prepare()
            exoPlayer.playWhenReady = playWhenReady
            _hasExternalSubtitle.value = true
            true
        }
    }

    fun prepareAndPlay(
        uri: Uri,
        startPositionMs: Long = 0L,
        externalSubtitleUri: Uri? = null,
        title: String? = null
    ) {
        externalSubtitleSource = null
        _subtitleDelayMs.value = 0L
        _hasExternalSubtitle.value = false
        val mediaItemBuilder = MediaItem.Builder().setUri(uri)
        if (title != null) {
            mediaItemBuilder.setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title).build())
        }

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

    private var lastUnseekableToastMs = 0L

    /**
     * Some files (e.g. MKV without a usable Cues index) are UNSEEKABLE: ExoPlayer would silently restart
     * them from 0 on every seek. Refuse the seek and tell the user instead.
     */
    private fun isSeekBlocked(): Boolean {
        if (exoPlayer.playbackState != Player.STATE_READY || exoPlayer.isCurrentMediaItemSeekable) return false
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastUnseekableToastMs > 4000L) {
            lastUnseekableToastMs = now
            android.widget.Toast.makeText(
                context,
                "This file has no seek index, so seeking is not possible",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
        return true
    }

    /** Facts about the loaded stream (shown in Video Properties) for diagnosing playback problems. */
    fun describeStream(): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val v = exoPlayer.videoFormat
        val a = exoPlayer.audioFormat
        out += "Seekable" to (if (exoPlayer.isCurrentMediaItemSeekable) "Yes" else "No (file has no seek index)")
        out += "Container" to (v?.containerMimeType ?: a?.containerMimeType ?: "unknown")
        if (v != null) {
            out += "Video" to ((v.sampleMimeType ?: "?") + " " + v.width + "x" + v.height +
                (if (v.frameRate > 0f) " @ " + "%.2f".format(v.frameRate) + " fps" else "") +
                (v.codecs?.let { " ($it)" } ?: ""))
        }
        if (a != null) {
            out += "Audio" to ((a.sampleMimeType ?: "?") + " " + a.channelCount + "ch " + a.sampleRate + "Hz" +
                (a.codecs?.let { " ($it)" } ?: ""))
        }
        val d = exoPlayer.duration
        out += "Duration (player)" to (if (d > 0) com.lnxv.player.core.model.VideoItem.formatDuration(d) else "unknown")
        return out
    }

    fun seekTo(positionMs: Long) {
        diagnostics.note("seekTo requested=${diagnostics.fmt(positionMs)} duration=${diagnostics.fmt(exoPlayer.duration)} seekable=${exoPlayer.isCurrentMediaItemSeekable}")
        if (isSeekBlocked()) {
            diagnostics.note("seek BLOCKED: file is unseekable")
            return
        }
        // SeekMath treats C.TIME_UNSET as "unknown" (the old clamp turned it into 0 => seek jumped to start)
        val target = SeekMath.clamp(positionMs, exoPlayer.duration)
        exoPlayer.seekTo(target)
        _positionMs.value = target // publish immediately so repeated/rapid seeks never use a stale position
    }

    fun seekRelative(deltaMs: Long) {
        diagnostics.note("seekRelative delta=${deltaMs}ms from=${diagnostics.fmt(exoPlayer.currentPosition)}")
        if (isSeekBlocked()) {
            diagnostics.note("seek BLOCKED: file is unseekable")
            return
        }
        // currentPosition already reflects a pending seek, so rapid repeated seeks accumulate correctly
        val target = SeekMath.relativeTarget(exoPlayer.currentPosition, deltaMs, exoPlayer.duration)
        exoPlayer.seekTo(target)
        _positionMs.value = target
    }

    /** Pauses and steps one video frame (frame duration from the stream's frame rate, 30 fps fallback). */
    fun stepFrame(forward: Boolean) {
        exoPlayer.pause()
        val fps = exoPlayer.videoFormat?.frameRate?.takeIf { it > 0f } ?: 30f
        val frameMs = (1000f / fps).roundToLong().coerceAtLeast(1L)
        seekRelative(if (forward) frameMs else -frameMs)
    }

    /** Which ViewModel currently owns playback (prevents a late-cleared screen from stopping a newer video). */
    @Volatile var owner: Any? = null

    /** True while the video player screen is on screen (used for auto-PiP decisions). */
    @Volatile var isVideoScreenActive: Boolean = false

    /** Fully stops playback and releases the loaded media (the ExoPlayer instance itself is kept). */
    fun stopPlayback() {
        exoPlayer.pause()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        cancelSleepTimer()
        _positionMs.value = 0L
        _playbackState.update {
            it.copy(
                isPlaying = false,
                currentPositionMs = 0L,
                durationMs = 0L,
                bufferedPositionMs = 0L,
                isBuffering = false,
                isEnded = false,
                abRepeat = ABRepeatState()
            )
        }
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

                // Position lives ONLY in positionMs (collected by the seek bar alone).
                // PlaybackState is updated only when something screen-wide changes (e.g. duration).
                _positionMs.value = currentPos
                // Assume seekable until the player is READY; then trust the container's seek map
                val seekable = exoPlayer.playbackState != Player.STATE_READY || exoPlayer.isCurrentMediaItemSeekable
                if (_playbackState.value.durationMs != duration || _playbackState.value.isSeekable != seekable) {
                    _playbackState.update { it.copy(durationMs = duration, isSeekable = seekable) }
                }
                delay(if (exoPlayer.isPlaying) 250L else 1000L)
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
