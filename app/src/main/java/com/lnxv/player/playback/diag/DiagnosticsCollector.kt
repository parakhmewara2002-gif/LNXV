package com.lnxv.player.playback.diag

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.lnxv.player.core.model.VideoItem
import com.lnxv.player.core.util.ByteArraySource
import com.lnxv.player.core.util.MkvInspector
import com.lnxv.player.core.util.Mp4Inspector
import com.lnxv.player.core.util.RandomAccessSource
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

internal class ChannelSource(private val channel: FileChannel) : RandomAccessSource {
    override val length: Long = channel.size()
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        return try {
            channel.read(ByteBuffer.wrap(buffer, offset, length), position)
        } catch (e: Exception) {
            -1
        }
    }
}

/** Last note from the MKV seek workaround (shown in the diagnostic report). */
object SeekFixState {
    @Volatile var note: String = "not used yet"
}

/** Reads the file's container structure (seek index etc.) straight from the file. */
object ContainerInspector {
    fun describe(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val src = ChannelSource(FileInputStream(pfd.fileDescriptor).channel)
                val head = ByteArray(12)
                src.readAt(0, head, 0, 12)
                if (MkvInspector.isMatroskaHeader(head)) {
                    MkvInspector(src).inspect().toText()
                } else {
                    Mp4Inspector.inspect(src).toText()
                }
            } ?: "Cannot open file for inspection"
        } catch (e: Exception) {
            "Container inspection failed: ${e.javaClass.simpleName}: ${e.message}"
        }
    }
}

/** Keeps a short event log + decoder info and builds a plain-text report for troubleshooting. */
class DiagnosticsCollector(private val context: Context) {

    private val log = ArrayDeque<String>()
    private val startMs = SystemClock.elapsedRealtime()

    @Volatile private var videoDecoder: String? = null
    @Volatile private var audioDecoder: String? = null
    @Volatile private var droppedFrames = 0L
    @Volatile private var lastError: String? = null

    fun note(message: String) {
        val t = (SystemClock.elapsedRealtime() - startMs) / 1000.0
        synchronized(log) {
            log.addLast("[+" + "%.1f".format(t) + "s] " + message)
            while (log.size > 80) log.removeFirst()
        }
    }

    fun fmt(ms: Long): String {
        if (ms < 0) return "unknown"
        val h = ms / 3_600_000
        val m = (ms / 60_000) % 60
        val s = (ms / 1000) % 60
        return "%d:%02d:%02d.%03d".format(h, m, s, ms % 1000)
    }

    private fun reasonName(reason: Int): String = when (reason) {
        Player.DISCONTINUITY_REASON_SEEK -> "SEEK"
        Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> "SEEK_ADJUSTMENT"
        Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> "AUTO_TRANSITION"
        Player.DISCONTINUITY_REASON_SKIP -> "SKIP"
        Player.DISCONTINUITY_REASON_REMOVE -> "REMOVE"
        Player.DISCONTINUITY_REASON_INTERNAL -> "INTERNAL"
        else -> "reason=$reason"
    }

    private fun stateName(state: Int): String = when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "state=$state"
    }

    fun attach(player: ExoPlayer) {
        player.addListener(object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                note("jump ${reasonName(reason)}: ${fmt(oldPosition.positionMs)} -> ${fmt(newPosition.positionMs)}")
            }

            override fun onPlayerError(error: PlaybackException) {
                lastError = error.errorCodeName + ": " + error.message
                note("ERROR " + error.errorCodeName + ": " + error.message)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                note("state " + stateName(playbackState))
            }
        })
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                videoDecoder = decoderName
                note("video decoder: $decoderName")
            }

            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                audioDecoder = decoderName
                note("audio decoder: $decoderName")
            }

            override fun onDroppedVideoFrames(
                eventTime: AnalyticsListener.EventTime,
                droppedFrames: Int,
                elapsedMs: Long
            ) {
                this@DiagnosticsCollector.droppedFrames += droppedFrames
            }
        })
    }

    private fun trackLines(player: ExoPlayer): List<String> {
        val lines = mutableListOf<String>()
        player.currentTracks.groups.forEachIndexed { gi, group ->
            val type = when (group.type) {
                C.TRACK_TYPE_VIDEO -> "VIDEO"
                C.TRACK_TYPE_AUDIO -> "AUDIO"
                C.TRACK_TYPE_TEXT -> "TEXT"
                else -> "OTHER(${group.type})"
            }
            for (i in 0 until group.length) {
                val f = group.getTrackFormat(i)
                val extra = when (type) {
                    "VIDEO" -> " ${f.width}x${f.height} fps=${f.frameRate}"
                    "AUDIO" -> " ${f.channelCount}ch ${f.sampleRate}Hz"
                    else -> ""
                }
                lines += "$type #$gi.$i supported=${group.isTrackSupported(i)} selected=${group.isTrackSelected(i)} " +
                    "mime=${f.sampleMimeType} codecs=${f.codecs} lang=${f.language} bitrate=${f.bitrate}$extra"
            }
        }
        if (lines.isEmpty()) lines += "(no tracks loaded)"
        return lines
    }

    /** Must be called on the main thread (reads ExoPlayer state). */
    fun snapshot(player: ExoPlayer, video: VideoItem?, containerInfo: String): String = buildString {
        appendLine("=== LNXV diagnostic report ===")
        appendLine("App: " + try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            "?"
        })
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ABI ${Build.SUPPORTED_ABIS.firstOrNull()}")
        appendLine()
        appendLine("--- File ---")
        if (video != null) {
            appendLine("Title: ${video.title}")
            appendLine("Path: ${video.path}")
            appendLine("Size: ${video.sizeBytes} bytes (${video.sizeFormatted})")
            appendLine("MediaStore: mime=${video.mimeType} duration=${fmt(video.durationMs)} ${video.width}x${video.height}")
        } else {
            appendLine("(no video)")
        }
        append(containerInfo)
        appendLine()
        appendLine("--- Player ---")
        appendLine("State: ${stateName(player.playbackState)}, playWhenReady=${player.playWhenReady}, isPlaying=${player.isPlaying}")
        appendLine("Position: ${fmt(player.currentPosition)} / ${fmt(player.duration)} (buffered to ${fmt(player.bufferedPosition)})")
        appendLine("Seekable (player): ${player.isCurrentMediaItemSeekable}")
        appendLine("Speed: ${player.playbackParameters.speed}")
        appendLine("LNXV MKV seek workaround: ${SeekFixState.note}")
        appendLine("Video decoder: ${videoDecoder ?: "n/a"}")
        appendLine("Audio decoder: ${audioDecoder ?: "n/a"}")
        appendLine("Dropped frames: $droppedFrames")
        appendLine("Last error: ${lastError ?: "none"}")
        appendLine()
        appendLine("--- Tracks ---")
        trackLines(player).forEach { appendLine(it) }
        appendLine()
        appendLine("--- Recent events (oldest first) ---")
        synchronized(log) { log.forEach { appendLine(it) } }
    }
}
