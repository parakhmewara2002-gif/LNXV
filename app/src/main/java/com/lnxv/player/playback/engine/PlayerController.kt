package com.lnxv.player.playback.engine

import android.net.Uri
import com.lnxv.player.core.model.AspectRatioMode
import com.lnxv.player.core.model.AudioTrackInfo
import com.lnxv.player.core.model.DecoderMode
import com.lnxv.player.core.model.EqPreset
import com.lnxv.player.core.model.PlaybackState
import com.lnxv.player.core.model.SubtitleTrackInfo
import com.lnxv.player.core.model.VideoItem
import kotlinx.coroutines.flow.StateFlow

/**
 * What the UI layer needs from the player engine. [Media3PlayerManager] is the real implementation;
 * tests can supply a fake so ViewModels are testable without ExoPlayer (Phase 4).
 */
interface PlayerController {
    val playbackState: StateFlow<PlaybackState>
    val positionMs: StateFlow<Long>
    val audioTracks: StateFlow<List<AudioTrackInfo>>
    val subtitleTracks: StateFlow<List<SubtitleTrackInfo>>
    val audioDelayMs: StateFlow<Long>
    val subtitleDelayMs: StateFlow<Long>
    val hasExternalSubtitle: StateFlow<Boolean>
    val eqPreset: StateFlow<EqPreset>
    val boostMb: StateFlow<Int>
    val audioOnly: StateFlow<Boolean>

    var owner: Any?
    var isVideoScreenActive: Boolean
    var repeatMode: Int

    fun prepareAndPlay(uri: Uri, startPositionMs: Long = 0L, externalSubtitleUri: Uri? = null, title: String? = null)
    fun togglePlayPause()
    fun pause()
    fun stopPlayback()
    fun seekTo(positionMs: Long)
    fun seekRelative(deltaMs: Long)
    fun stepFrame(forward: Boolean)
    fun setPlaybackSpeed(speed: Float)
    fun startSpeedPreview(previewSpeed: Float = 2.0f)
    fun stopSpeedPreview()
    fun setSkipSilence(enabled: Boolean)
    fun setAspectRatio(mode: AspectRatioMode)
    fun setDecoderMode(mode: DecoderMode)
    fun toggleScreenLock()
    fun setPointA()
    fun setPointB()
    fun clearABRepeat()
    fun startSleepTimer(minutes: Int, fadeOut: Boolean = false)
    fun cancelSleepTimer()
    fun selectAudioTrack(track: AudioTrackInfo)
    fun selectSubtitleTrack(track: SubtitleTrackInfo?)
    fun setAudioDelayMs(ms: Long)
    fun setEqPreset(p: EqPreset)
    fun setBoostMb(mb: Int)
    fun setAudioOnly(enabled: Boolean)
    suspend fun loadExternalSubtitle(uri: Uri): Boolean
    suspend fun adjustSubtitleDelay(deltaMs: Long)
    fun describeStream(): List<Pair<String, String>>
    fun diagnosticsSnapshot(video: VideoItem?, containerInfo: String): String
}
