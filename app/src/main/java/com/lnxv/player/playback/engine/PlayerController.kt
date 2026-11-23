package com.lnxv.player.playback.engine

import android.net.Uri
import com.lnxv.player.core.model.AspectRatioMode
import com.lnxv.player.core.model.AudioTrackInfo
import com.lnxv.player.core.model.DecoderMode
import com.lnxv.player.core.model.EqPreset
import com.lnxv.player.core.model.PlaybackState
import com.lnxv.player.core.model.PlayerStats
import com.lnxv.player.core.model.RememberedTracks
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
    val videoQualities: StateFlow<List<com.lnxv.player.core.model.VideoQualityInfo>>
    val audioTracks: StateFlow<List<AudioTrackInfo>>
    val subtitleTracks: StateFlow<List<SubtitleTrackInfo>>
    val audioDelayMs: StateFlow<Long>
    val chapters: StateFlow<List<com.lnxv.player.core.model.ChapterItem>>
    val subtitleDelayMs: StateFlow<Long>
    val hasExternalSubtitle: StateFlow<Boolean>
    val eqPreset: StateFlow<EqPreset>
    val bassStrength: StateFlow<Int>
    val virtualizerStrength: StateFlow<Int>
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
    /** User changed the decoder mode while a video is loaded: re-prepares at the same position. */
    fun setDecoderMode(mode: DecoderMode)
    /** Sets the mode for the NEXT [prepareAndPlay] without touching the current media. */
    fun presetDecoderMode(mode: DecoderMode)
    /** Called once when Auto mode fell back to software decoding for the current video. */
    var onDecoderFallback: (() -> Unit)?

    fun setExactSeek(enabled: Boolean)
    fun beginScrub()
    fun scrubTo(positionMs: Long)
    fun endScrub()

    var onAudioTrackChosen: ((String) -> Unit)?
    /** (trackId, subtitlesOff): trackId is null when the user switched subtitles off. */
    var onSubtitleChosen: ((String?, Boolean) -> Unit)?
    fun applyRememberedTracks(remembered: RememberedTracks?)

    fun statsSnapshot(): PlayerStats
    fun toggleScreenLock()
    fun setPointA()
    fun setPointB()
    fun clearABRepeat()
    fun startSleepTimer(minutes: Int, fadeOut: Boolean = false)
    fun cancelSleepTimer()
    fun selectVideoQuality(quality: com.lnxv.player.core.model.VideoQualityInfo)
    fun selectAudioTrack(track: AudioTrackInfo)
    fun selectSubtitleTrack(track: SubtitleTrackInfo?)
    fun setAudioDelayMs(ms: Long)
    fun setEqPreset(p: EqPreset)
    fun setBassStrength(s: Int)
    fun setVirtualizerStrength(s: Int)
    fun setBoostMb(mb: Int)
    fun setAudioOnly(enabled: Boolean)
    val videoColor: StateFlow<com.lnxv.player.core.model.VideoColor>
    fun setVideoColor(color: com.lnxv.player.core.model.VideoColor)
    val picture: StateFlow<com.lnxv.player.core.enhancer.PictureSettings>
    val pictureDisabledReason: StateFlow<com.lnxv.player.core.enhancer.PictureDisabledReason?>
    fun setPicture(settings: com.lnxv.player.core.enhancer.PictureSettings)
    fun setPictureBypass(bypass: Boolean)
    suspend fun loadExternalSubtitle(uri: Uri): Boolean
    val subtitleEncoding: StateFlow<String>
    suspend fun setSubtitleEncoding(name: String)
    suspend fun adjustSubtitleDelay(deltaMs: Long)
    fun describeStream(): List<Pair<String, String>>
    fun diagnosticsSnapshot(video: VideoItem?, containerInfo: String): String
}
