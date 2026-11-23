package com.lnxv.player.playback.engine

import com.lnxv.player.core.model.SleepFade
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
import com.lnxv.player.core.model.Av1SoftwarePolicy
import com.lnxv.player.decoders.Av1SoftwareGate
import com.lnxv.player.core.model.AudioTrackInfo
import com.lnxv.player.core.model.BufferProfiles
import com.lnxv.player.core.model.CodecQuirks
import com.lnxv.player.core.model.DecoderFallbackPolicy
import com.lnxv.player.core.model.DecoderMode
import com.lnxv.player.core.model.DecoderNames
import com.lnxv.player.core.model.DecoderSelection
import com.lnxv.player.core.model.PlayerStats
import com.lnxv.player.core.model.RememberedTracks
import com.lnxv.player.core.model.SeekMode
import com.lnxv.player.core.model.SeekPolicy
import com.lnxv.player.core.model.PlaybackState
import com.lnxv.player.core.model.SubtitleTrackInfo
import com.lnxv.player.core.util.SeekMath
import com.lnxv.player.playback.diag.DiagnosticsCollector
import com.lnxv.player.core.util.SubtitleShifter
import com.lnxv.player.core.enhancer.PictureDisabledReason
import com.lnxv.player.core.enhancer.PictureGate
import com.lnxv.player.core.enhancer.PictureSettings
import com.lnxv.player.playback.effects.PictureEffect
import com.lnxv.player.core.subtitle.AssShifter
import com.lnxv.player.core.subtitle.SubtitleEncoding
import com.lnxv.player.core.subtitle.SubtitleFormat
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.InputStream
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
class Media3PlayerManager(private val context: Context) : PlayerController, Player.Listener {

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val trackSelector = DefaultTrackSelector(context).apply {
        setParameters(buildUponParameters().setPreferredTextLanguage("en"))
    }

    private val audioDelayProcessor = AudioDelayProcessor()

    // --- Decoder selection (Settings > Playback > Hardware decoding; per-video override) ---
    @Volatile private var decoderModeValue = DecoderMode.AUTO
    private val fallbackPolicy = DecoderFallbackPolicy()
    /** decoder name -> isSoftware, filled by the selector so the stats panel can say HW / SW reliably. */
    private val decoderIsSoftware = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    private fun isHardwareDecoder(info: androidx.media3.exoplayer.mediacodec.MediaCodecInfo) =
        info.hardwareAccelerated && !info.softwareOnly

    private val codecSelector = androidx.media3.exoplayer.mediacodec.MediaCodecSelector { mimeType, secure, tunneling ->
        val all = androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling)
        all.forEach { decoderIsSoftware[it.name] = !isHardwareDecoder(it) }
        val usable = all.filterNot { CodecQuirks.isBlocked(CodecQuirks.KNOWN, android.os.Build.HARDWARE, it.name, mimeType) }
            .ifEmpty { all } // never block every decoder: a quirk table must not turn "plays badly" into "does not play"
        DecoderSelection.order(decoderModeValue, usable) { isHardwareDecoder(it) }
    }

    private val renderersFactory = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean
        ): androidx.media3.exoplayer.audio.AudioSink =
            androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf<androidx.media3.common.audio.AudioProcessor>(audioDelayProcessor))
                .build()
    }.apply {
        setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        setEnableDecoderFallback(true) // Software decoding fallback when hardware decoding fails
        setMediaCodecSelector(codecSelector)
    }

    init {
        // dav1d software AV1 is only a fallback: it is gated by decoder mode and a resolution / frame-rate cap.
        val lowEnd = lowRamDevice() || android.os.Build.SUPPORTED_64_BIT_ABIS.isEmpty()
        Av1SoftwareGate.setGate { format ->
            when (Av1SoftwarePolicy.decide(decoderModeValue, format.width, format.height, format.frameRate, Av1SoftwarePolicy.limitsFor(lowEnd))) {
                Av1SoftwarePolicy.Verdict.ALLOWED -> Av1SoftwareGate.ALLOWED
                Av1SoftwarePolicy.Verdict.BLOCKED_BY_MODE -> Av1SoftwareGate.BLOCKED_BY_MODE
                Av1SoftwarePolicy.Verdict.EXCEEDS_LIMIT -> Av1SoftwareGate.EXCEEDS_LIMIT
            }
        }
    }

    private fun lowRamDevice() =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)?.isLowRamDevice == true

    private val _audioDelayMs = MutableStateFlow(0L)
    /** Audio delay in ms (0..2000, 50 ms steps). Negative delay is not supported (documented limitation). */
    override val audioDelayMs: StateFlow<Long> = _audioDelayMs.asStateFlow()

    override fun setAudioDelayMs(ms: Long) {
        val v = ms.coerceIn(AudioDelayProcessor.MIN_DELAY_MS, AudioDelayProcessor.MAX_DELAY_MS)
        audioDelayProcessor.setDelayMs(v)
        _audioDelayMs.value = v
    }

    // Rule C: small buffers for local files (fast seek, low RAM), deeper buffers for network streams.
    // ExoPlayer fixes its LoadControl at build time, so one switching proxy picks the profile per source.
    private val lowRam = (context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)?.isLowRamDevice == true
    private fun buildLoadControl(network: Boolean): androidx.media3.exoplayer.DefaultLoadControl {
        val p = BufferProfiles.forSource(network, lowRam)
        return androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(p.minBufferMs, p.maxBufferMs, p.playbackBufferMs, p.rebufferMs)
            .setTargetBufferBytes(p.targetBytes)
            .build()
    }
    private val localLoadControl = buildLoadControl(network = false)
    private val networkLoadControl = buildLoadControl(network = true)
    @Volatile private var networkSource = false
    private val loadControl: androidx.media3.exoplayer.LoadControl =
        SwitchingProxy.create(androidx.media3.exoplayer.LoadControl::class.java) {
            if (networkSource) networkLoadControl else localLoadControl
        }

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context, renderersFactory)
        .setLoadControl(loadControl)
        .setTrackSelector(trackSelector)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                VaultRoutingDataSource.Factory(
                    androidx.media3.datasource.ResolvingDataSource.Factory(
                        androidx.media3.datasource.DataSource.Factory {
                            com.lnxv.player.core.smb.SmbRoutingDataSource(
                                androidx.media3.datasource.DefaultDataSource.Factory(context).createDataSource(),
                                com.lnxv.player.core.smb.SmbBackends.instance?.newDataSource()
                            )
                        },
                        androidx.media3.datasource.ResolvingDataSource.Resolver { spec -> com.lnxv.player.core.webdav.WebDavAuth.resolve(spec) }
                    ),
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

    override fun diagnosticsSnapshot(video: com.lnxv.player.core.model.VideoItem?, containerInfo: String): String =
        diagnostics.snapshot(exoPlayer, video, containerInfo)

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<AudioTrackInfo>>(emptyList())
    override val audioTracks: StateFlow<List<AudioTrackInfo>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<SubtitleTrackInfo>>(emptyList())
    override val subtitleTracks: StateFlow<List<SubtitleTrackInfo>> = _subtitleTracks.asStateFlow()

    private var progressTrackingJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var savedSpeedBeforePreview: Float? = null

    init {
        startProgressTracking()
    }

    // --- External subtitle + delay (SRT / WebVTT only: cue times are shifted in a cached copy) ---
    private var externalSubtitleSource: Uri? = null
    private val _playbackError = MutableStateFlow<String?>(null)
    /** Human-readable error for the current media; null while OK. Collected by the player route. */
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()
    fun clearPlaybackError() { _playbackError.value = null }

    private val audioEffects = AudioEffectsController()
    private val _eqPreset = MutableStateFlow(com.lnxv.player.core.model.EqPreset.FLAT)
    override val eqPreset: StateFlow<com.lnxv.player.core.model.EqPreset> = _eqPreset

    private val _bass = MutableStateFlow(0)
    override val bassStrength: StateFlow<Int> = _bass
    private val _virtualizer = MutableStateFlow(0)
    override val virtualizerStrength: StateFlow<Int> = _virtualizer

    override fun setBassStrength(s: Int) {
        audioEffects.attach(exoPlayer.audioSessionId)
        audioEffects.setBass(s)
        _bass.value = audioEffects.bassStrength
    }

    override fun setVirtualizerStrength(s: Int) {
        audioEffects.attach(exoPlayer.audioSessionId)
        audioEffects.setVirtualizer(s)
        _virtualizer.value = audioEffects.virtualizerStrength
    }

    override fun setEqPreset(p: com.lnxv.player.core.model.EqPreset) {
        audioEffects.attach(exoPlayer.audioSessionId)
        audioEffects.setPreset(p)
        _eqPreset.value = p
    }

    private val _audioOnly = MutableStateFlow(false)
    override val audioOnly: StateFlow<Boolean> = _audioOnly

    /** Disables the video renderer (no decoding, saves battery); audio keeps playing. */
    override fun setAudioOnly(enabled: Boolean) {
        trackSelector.setParameters(
            trackSelector.buildUponParameters().setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, enabled)
        )
        _audioOnly.value = enabled
    }

    private val _videoColor = MutableStateFlow(com.lnxv.player.core.model.VideoColor())
    override val videoColor: StateFlow<com.lnxv.player.core.model.VideoColor> = _videoColor

    private val _picture = MutableStateFlow(PictureSettings())
    override val picture: StateFlow<PictureSettings> = _picture
    private val _pictureDisabledReason = MutableStateFlow<PictureDisabledReason?>(null)
    override val pictureDisabledReason: StateFlow<PictureDisabledReason?> = _pictureDisabledReason
    private var pictureBypass = false

    /** Old brightness/contrast/saturation menu: it now edits the same Picture settings. */
    override fun setVideoColor(color: com.lnxv.player.core.model.VideoColor) {
        setPicture(_picture.value.copy(brightness = color.brightness, contrast = color.contrast, saturation = color.saturation))
    }

    override fun setPicture(settings: PictureSettings) {
        val s = settings.sanitized()
        _picture.value = s
        _videoColor.value = com.lnxv.player.core.model.VideoColor(s.brightness, s.contrast, s.saturation)
        applyPicture()
    }

    /** Hold-to-compare: true shows the untouched picture, false puts the effect back. No player rebuild. */
    override fun setPictureBypass(bypass: Boolean) {
        pictureBypass = bypass
        applyPicture()
    }

    /**
     * One GL effect pass (media3-effect) for the whole Picture pipeline. A neutral picture, a bypass, or a gate reason
     * (HDR, protected content, low-end device, power saver) means no effect chain, so no GPU pass is added.
     */
    private fun applyPicture() {
        val format = exoPlayer.videoFormat
        val transfer = format?.colorInfo?.colorTransfer
        val isHdr = transfer == C.COLOR_TRANSFER_ST2084 || transfer == C.COLOR_TRANSFER_HLG
        val powerSave = (context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager)?.isPowerSaveMode == true
        val reason = PictureGate.decide(
            isHdr = isHdr,
            isProtected = format?.drmInitData != null,
            isLowEnd = lowRamDevice(),
            isPowerSave = powerSave
        )
        _pictureDisabledReason.value = reason
        val p = _picture.value
        val fx = ArrayList<androidx.media3.common.Effect>()
        if (reason == null && !pictureBypass && !p.isNeutral) fx += PictureEffect(p)
        runCatching { exoPlayer.setVideoEffects(fx) }
    }

    private val _boostMb = MutableStateFlow(0)
    override val boostMb: StateFlow<Int> = _boostMb

    override fun setBoostMb(mb: Int) {
        audioEffects.attach(exoPlayer.audioSessionId)
        audioEffects.setBoost(mb)
        _boostMb.value = audioEffects.boostMb
    }

    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        audioEffects.attach(audioSessionId)
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        if (handleDecoderFailure(error)) return
        _playbackError.value = when (error.errorCode) {
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Cannot reach the stream. Check the URL and your connection."
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                "The server rejected the request (bad HTTP status). The link may have expired."
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ->
                "Plain HTTP is blocked for security. Use an https:// URL."
            androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
                "Unsupported or corrupted stream format."
            else -> "Playback error: ${error.errorCodeName}"
        }
    }

    private val _subtitleDelayMs = MutableStateFlow(0L)
    override val subtitleDelayMs: StateFlow<Long> = _subtitleDelayMs.asStateFlow()
    private val _hasExternalSubtitle = MutableStateFlow(false)
    override val hasExternalSubtitle: StateFlow<Boolean> = _hasExternalSubtitle.asStateFlow()

    /** @return false when the file is not a supported SRT/VTT subtitle. */
    override suspend fun loadExternalSubtitle(uri: Uri): Boolean {
        externalSubtitleSource = uri
        _subtitleDelayMs.value = 0L
        val ok = applyExternalSubtitle()
        if (!ok) {
            externalSubtitleSource = null
            _hasExternalSubtitle.value = false
        }
        return ok
    }

    private val _subtitleEncoding = MutableStateFlow("AUTO")
    /** "AUTO" or a charset name from [SubtitleEncoding.CHOICES]. Applies to the loaded external subtitle file. */
    override val subtitleEncoding: StateFlow<String> = _subtitleEncoding.asStateFlow()

    override suspend fun setSubtitleEncoding(name: String) {
        _subtitleEncoding.value = name
        if (externalSubtitleSource != null) applyExternalSubtitle()
    }

    private var externalFormat: SubtitleFormat? = null

    override suspend fun adjustSubtitleDelay(deltaMs: Long) {
        if (externalSubtitleSource == null) return
        if (externalFormat?.supportsDelay == false) return
        _subtitleDelayMs.value = (_subtitleDelayMs.value + deltaMs).coerceIn(-60_000L, 60_000L)
        applyExternalSubtitle()
    }

    private fun readSubtitleBounded(input: InputStream): ByteArray? {
        val output = ByteArrayOutputStream(8 * 1024)
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return output.toByteArray()
            if (count == 0) continue
            if (total > MAX_SUBTITLE_BYTES - count) return null
            output.write(buffer, 0, count)
            total += count
        }
    }

    private suspend fun applyExternalSubtitle(): Boolean {
        val source = externalSubtitleSource ?: return false
        val delay = _subtitleDelayMs.value
        val prepared: Pair<Uri, SubtitleFormat> = withContext(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(source)?.use { readSubtitleBounded(it) }
                    ?: return@withContext null
                val text = SubtitleEncoding.decode(bytes, SubtitleEncoding.charsetFor(_subtitleEncoding.value))
                val format = SubtitleFormat.detect(text) ?: return@withContext null
                val shifted = when (format) {
                    SubtitleFormat.SRT, SubtitleFormat.VTT -> SubtitleShifter.shift(text, delay)
                    SubtitleFormat.ASS -> AssShifter.shift(text, delay)
                    SubtitleFormat.TTML -> text
                }
                val dir = File(context.cacheDir, "subs").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, "sub_${System.nanoTime()}.${format.extension}")
                file.writeText(shifted) // always UTF-8, whatever the source encoding was
                externalFormat = format
                Pair(Uri.fromFile(file), format)
            } catch (e: Exception) {
                null
            }
        } ?: return false

        return withContext(Dispatchers.Main) {
            val current = exoPlayer.currentMediaItem ?: return@withContext false
            val position = exoPlayer.currentPosition
            val playWhenReady = exoPlayer.playWhenReady
            val config = MediaItem.SubtitleConfiguration.Builder(prepared.first)
                .setMimeType(prepared.second.mimeType)
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

    private val _chapters = MutableStateFlow<List<com.lnxv.player.core.model.ChapterItem>>(emptyList())
    override val chapters: StateFlow<List<com.lnxv.player.core.model.ChapterItem>> = _chapters

    /** Collects Media3 1.11 Chapter metadata entries (Matroska, MP4/QuickTime) from [metadata] into the chapter list. */
    private fun collectChapters(metadata: androidx.media3.common.Metadata?, into: MutableList<com.lnxv.player.core.model.ChapterItem>) {
        if (metadata == null) return
        for (i in 0 until metadata.length()) {
            val entry = metadata.get(i)
            if (entry is androidx.media3.extractor.metadata.Chapter && !entry.isHidden && entry.startTimeMs != C.TIME_UNSET) {
                into += com.lnxv.player.core.model.ChapterItem(entry.startTimeMs, entry.title?.value.orEmpty())
            }
        }
    }

    override fun onMetadata(metadata: androidx.media3.common.Metadata) {
        val found = ArrayList<com.lnxv.player.core.model.ChapterItem>(_chapters.value)
        collectChapters(metadata, found)
        if (found.size != _chapters.value.size) _chapters.value = com.lnxv.player.core.model.ChapterList.sanitize(found)
    }

    override fun prepareAndPlay(
        uri: Uri,
        startPositionMs: Long,
        externalSubtitleUri: Uri?,
        title: String?
    ) {
        externalSubtitleSource = null
        _playbackError.value = null
        fallbackPolicy.reset()
        diagnostics.resetPlaybackCounters()
        pendingRemembered = null
        pendingRememberedAttempts = 0
        _chapters.value = emptyList()
        networkSource = BufferProfiles.isNetwork(uri.scheme)
        if (_audioOnly.value) setAudioOnly(false) // audio-only applies per video
        if (!_picture.value.isNeutral) setPicture(PictureSettings()) // picture tweaks apply per video; the ViewModel then loads this video's or the global default
        _subtitleDelayMs.value = 0L
        _hasExternalSubtitle.value = false
        _subtitleEncoding.value = "AUTO"
        externalFormat = null
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

    override fun pause() {
        exoPlayer.pause()
    }

    override fun togglePlayPause() {
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
    override fun describeStream(): List<Pair<String, String>> {
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

    override fun seekTo(positionMs: Long) {
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

    override fun seekRelative(deltaMs: Long) {
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
    override fun stepFrame(forward: Boolean) {
        exoPlayer.pause()
        val fps = exoPlayer.videoFormat?.frameRate?.takeIf { it > 0f } ?: 30f
        val frameMs = (1000f / fps).roundToLong().coerceAtLeast(1L)
        seekRelative(if (forward) frameMs else -frameMs)
    }

    /** Which ViewModel currently owns playback (prevents a late-cleared screen from stopping a newer video). */
    @Volatile override var owner: Any? = null

    /** True while the video player screen is on screen (used for auto-PiP decisions). */
    @Volatile override var isVideoScreenActive: Boolean = false

    /** Fully stops playback and releases the loaded media (the ExoPlayer instance itself is kept). */
    override fun stopPlayback() {
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

    override fun setSkipSilence(enabled: Boolean) {
        exoPlayer.skipSilenceEnabled = enabled
    }

    override fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 4.0f)
        exoPlayer.playbackParameters = PlaybackParameters(clamped)
        _playbackState.update { it.copy(playbackSpeed = clamped) }
    }

    override fun startSpeedPreview(previewSpeed: Float) {
        if (savedSpeedBeforePreview == null) {
            savedSpeedBeforePreview = exoPlayer.playbackParameters.speed
            exoPlayer.playbackParameters = PlaybackParameters(previewSpeed)
        }
    }

    override fun stopSpeedPreview() {
        savedSpeedBeforePreview?.let { speed ->
            exoPlayer.playbackParameters = PlaybackParameters(speed)
            savedSpeedBeforePreview = null
        }
    }

    override fun setAspectRatio(mode: AspectRatioMode) {
        _playbackState.update { it.copy(aspectRatio = mode) }
    }

    override fun presetDecoderMode(mode: DecoderMode) {
        decoderModeValue = mode
        _playbackState.update { it.copy(decoderMode = mode) }
    }

    /** User changed the decoder mode: apply it to the playing video by re-preparing at the same position. */
    override fun setDecoderMode(mode: DecoderMode) {
        val changed = mode != decoderModeValue
        presetDecoderMode(mode)
        fallbackPolicy.reset()
        if (changed && exoPlayer.currentMediaItem != null) reprepareInPlace()
    }

    private fun reprepareInPlace() {
        val item = exoPlayer.currentMediaItem ?: return
        val position = exoPlayer.currentPosition
        val playWhenReady = exoPlayer.playWhenReady
        exoPlayer.setMediaItem(item, position)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = playWhenReady
    }

    override var onDecoderFallback: (() -> Unit)? = null

    /** @return true when the error was a decoder problem that was handled by retrying (no error is shown). */
    private fun handleDecoderFailure(error: androidx.media3.common.PlaybackException): Boolean {
        // The AV1 software gate refused the video: retrying in software would only stutter, so say why instead.
        val denial = Av1SoftwareGate.consumeDenial()
        if (denial != null && denial.reason == Av1SoftwareGate.EXCEEDS_LIMIT) {
            val limits = Av1SoftwarePolicy.limitsFor(lowRamDevice() || android.os.Build.SUPPORTED_64_BIT_ABIS.isEmpty())
            diagnostics.note("av1 software refused: over cap")
            _playbackError.value = context.getString(
                com.lnxv.player.R.string.av1_software_too_demanding,
                Av1SoftwarePolicy.describeVideo(denial.width, denial.height, denial.frameRate),
                limits.describe()
            )
            return true
        }
        val action = when (error.errorCode) {
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
                fallbackPolicy.onDecoderInitFailed(decoderModeValue)
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED ->
                fallbackPolicy.onDecodeError(decoderModeValue, exoPlayer.currentPosition)
            else -> return false
        }
        diagnostics.note("decoder failure ${error.errorCodeName} (mode=$decoderModeValue) -> $action")
        return when (action) {
            DecoderFallbackPolicy.Action.RETRY_SAME -> {
                exoPlayer.prepare()
                true
            }
            DecoderFallbackPolicy.Action.RETRY_SOFTWARE -> {
                presetDecoderMode(DecoderMode.SOFTWARE)
                exoPlayer.prepare()
                android.widget.Toast.makeText(context, context.getString(com.lnxv.player.R.string.decoder_fallback_toast), android.widget.Toast.LENGTH_LONG).show()
                onDecoderFallback?.invoke()
                true
            }
            else -> false
        }
    }

    override fun toggleScreenLock() {
        _playbackState.update { it.copy(isScreenLocked = !it.isScreenLocked) }
    }

    // --- AB Repeat Functionality ---
    override fun setPointA() {
        val currentPos = exoPlayer.currentPosition
        _playbackState.update {
            it.copy(abRepeat = it.abRepeat.copy(isEnabled = true, pointA = currentPos))
        }
    }

    override fun setPointB() {
        val currentPos = exoPlayer.currentPosition
        val pointA = _playbackState.value.abRepeat.pointA ?: 0L
        if (currentPos > pointA) {
            _playbackState.update {
                it.copy(abRepeat = it.abRepeat.copy(isEnabled = true, pointB = currentPos))
            }
        }
    }

    override fun clearABRepeat() {
        _playbackState.update {
            it.copy(abRepeat = ABRepeatState(isEnabled = false, pointA = null, pointB = null))
        }
    }

    // --- Sleep Timer Functionality ---
    override fun startSleepTimer(minutes: Int, fadeOut: Boolean) {
        sleepTimerJob?.cancel()
        exoPlayer.volume = 1f
        if (minutes == 0 || minutes < SleepFade.END_OF_VIDEO) {
            _playbackState.update { it.copy(sleepTimerRemainingSeconds = null) }
            return
        }

        sleepTimerJob = scope.launch {
            if (minutes == SleepFade.END_OF_VIDEO) {
                // Pause when the current video finishes (or at the end, after the optional fade).
                while (isActive) {
                    val dur = exoPlayer.duration
                    val remainingMs = if (dur > 0) (dur - exoPlayer.currentPosition).coerceAtLeast(0L) else Long.MAX_VALUE
                    _playbackState.update {
                        it.copy(sleepTimerRemainingSeconds = if (remainingMs == Long.MAX_VALUE) 0L else remainingMs / 1000L)
                    }
                    if (fadeOut) exoPlayer.volume = SleepFade.factor(remainingMs)
                    if (exoPlayer.playbackState == Player.STATE_ENDED || (dur > 0 && remainingMs <= 300L)) break
                    delay(500L)
                }
            } else {
                val endAt = android.os.SystemClock.elapsedRealtime() + minutes * 60_000L
                while (isActive) {
                    val remainingMs = (endAt - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                    _playbackState.update { it.copy(sleepTimerRemainingSeconds = (remainingMs + 999L) / 1000L) }
                    if (fadeOut) exoPlayer.volume = SleepFade.factor(remainingMs)
                    if (remainingMs <= 0L) break
                    delay(500L)
                }
            }
            pause()
            exoPlayer.volume = 1f
            _playbackState.update { it.copy(sleepTimerRemainingSeconds = null) }
        }
    }

    override var repeatMode: Int
        get() = exoPlayer.repeatMode
        set(value) { exoPlayer.repeatMode = value }

    override fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        exoPlayer.volume = 1f
        _playbackState.update { it.copy(sleepTimerRemainingSeconds = null) }
    }

    // --- Track Selection (Audio & Subtitle) ---
    override fun selectAudioTrack(track: AudioTrackInfo) {
        if (!applyingRemembered) onAudioTrackChosen?.invoke(track.id)
        val currentTracks = exoPlayer.currentTracks
        for (group in currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    if ((format.id ?: "audio_$i") == track.id) {
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

    override fun selectSubtitleTrack(track: SubtitleTrackInfo?) {
        if (!applyingRemembered) onSubtitleChosen?.invoke(track?.id, track == null)
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
                        if ((format.id ?: "sub_$i") == track.id) {
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
        runCatching {
            val found = ArrayList<com.lnxv.player.core.model.ChapterItem>()
            for (g in tracks.groups) for (i in 0 until g.length) collectChapters(g.getTrackFormat(i).metadata, found)
            if (found.isNotEmpty()) _chapters.value = com.lnxv.player.core.model.ChapterList.sanitize(found)
        }
        updateTrackLists(tracks)
        if (!_picture.value.isNeutral) applyPicture() else _pictureDisabledReason.value = null
        tryApplyRemembered()
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
                                label = TrackNames.audioLabel(format.language, format.label, format.channelCount, format.sampleMimeType, i),
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
                                label = TrackNames.subtitleLabel(
                                    language = format.language,
                                    title = format.label,
                                    mime = format.sampleMimeType,
                                    index = i,
                                    forced = (format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0,
                                    sdh = (format.roleFlags and C.ROLE_FLAG_CAPTION) != 0
                                ),
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

    // --- Seek quality: keyframe seeks while the user drags, exact seek on release (toggle in Settings) ---
    @Volatile private var scrubbing = false
    @Volatile private var exactSeekEnabled = true
    private var lastScrubSeekMs = 0L

    private fun applySeekParameters() {
        exoPlayer.setSeekParameters(
            when (SeekPolicy.modeFor(scrubbing, exactSeekEnabled)) {
                SeekMode.CLOSEST_SYNC -> androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC
                SeekMode.EXACT -> androidx.media3.exoplayer.SeekParameters.EXACT
            }
        )
    }

    override fun setExactSeek(enabled: Boolean) {
        exactSeekEnabled = enabled
        applySeekParameters()
    }

    override fun beginScrub() {
        scrubbing = true
        lastScrubSeekMs = 0L
        applySeekParameters()
    }

    /** Live preview seek while dragging (throttled; silently ignored for unseekable files). */
    override fun scrubTo(positionMs: Long) {
        if (!scrubbing || !exoPlayer.isCurrentMediaItemSeekable) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastScrubSeekMs < SCRUB_SEEK_INTERVAL_MS) return
        lastScrubSeekMs = now
        exoPlayer.seekTo(SeekMath.clamp(positionMs, exoPlayer.duration))
    }

    /** Call before the final [seekTo] so that seek uses the exact (or user-chosen) parameters. */
    override fun endScrub() {
        scrubbing = false
        applySeekParameters()
    }

    // --- Remembered audio/subtitle choices (per video, stored by the ViewModel) ---
    override var onAudioTrackChosen: ((String) -> Unit)? = null
    override var onSubtitleChosen: ((String?, Boolean) -> Unit)? = null
    private var pendingRemembered: RememberedTracks? = null
    private var pendingRememberedAttempts = 0
    private var applyingRemembered = false

    override fun applyRememberedTracks(remembered: RememberedTracks?) {
        pendingRemembered = remembered?.takeIf { !it.isEmpty }
        pendingRememberedAttempts = 0
        tryApplyRemembered()
    }

    private fun tryApplyRemembered() {
        val r = pendingRemembered ?: return
        val audio = _audioTracks.value
        val subs = _subtitleTracks.value
        if (audio.isEmpty() && subs.isEmpty()) return // tracks not loaded yet
        applyingRemembered = true
        try {
            r.audioToApply(audio)?.let { selectAudioTrack(it) }
            if (r.subtitlesOff) selectSubtitleTrack(null) else r.subtitleToApply(subs)?.let { selectSubtitleTrack(it) }
        } finally {
            applyingRemembered = false
        }
        if (r.isResolvable(audio, subs) || ++pendingRememberedAttempts >= MAX_REMEMBER_ATTEMPTS) pendingRemembered = null
    }

    // --- Stats for nerds ---
    private fun isSoftwareDecoder(name: String): Boolean = decoderIsSoftware[name] ?: DecoderNames.looksSoftware(name)

    override fun statsSnapshot(): PlayerStats {
        val v = exoPlayer.videoFormat
        val a = exoPlayer.audioFormat
        val videoDecoder = diagnostics.videoDecoderName
        val audioDecoder = diagnostics.audioDecoderName
        return PlayerStats(
            videoCodec = v?.sampleMimeType,
            videoDecoder = videoDecoder,
            videoIsSoftware = videoDecoder?.let { isSoftwareDecoder(it) },
            width = v?.width?.coerceAtLeast(0) ?: 0,
            height = v?.height?.coerceAtLeast(0) ?: 0,
            frameRate = v?.frameRate?.takeIf { it > 0f } ?: 0f,
            videoBitrateBps = v?.let { it.bitrate.takeIf { b -> b > 0 } ?: it.averageBitrate.takeIf { b -> b > 0 } } ?: 0,
            droppedFrames = diagnostics.droppedFrameCount,
            audioCodec = a?.sampleMimeType,
            audioDecoder = audioDecoder,
            audioIsSoftware = audioDecoder?.let { isSoftwareDecoder(it) },
            audioBitrateBps = a?.let { it.bitrate.takeIf { b -> b > 0 } ?: it.averageBitrate.takeIf { b -> b > 0 } } ?: 0,
            decoderMode = decoderModeValue,
            softwareFallbackUsed = fallbackPolicy.softwareFallbackUsed,
            bufferedAheadMs = (exoPlayer.bufferedPosition - exoPlayer.currentPosition).coerceAtLeast(0L)
        )
    }

    private companion object {
        const val MAX_SUBTITLE_BYTES = 4 * 1024 * 1024
        const val SCRUB_SEEK_INTERVAL_MS = 90L
        const val MAX_REMEMBER_ATTEMPTS = 3
    }

    fun release() {
        progressTrackingJob?.cancel()
        sleepTimerJob?.cancel()
        exoPlayer.removeListener(this)
        audioEffects.releaseEffects()
        exoPlayer.release()
    }
}
