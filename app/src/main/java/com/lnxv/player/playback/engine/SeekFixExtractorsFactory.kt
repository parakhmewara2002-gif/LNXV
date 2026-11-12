package com.lnxv.player.playback.engine

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import com.lnxv.player.core.util.MkvInspector
import com.lnxv.player.playback.diag.ChannelSource
import com.lnxv.player.playback.diag.SeekFixState
import java.io.FileInputStream

/**
 * Media3's MatroskaExtractor only honours a Cues entry found in the FIRST SeekHead. Files written by
 * mkvmerge often list Cues only in a second SeekHead stored after the video data; ExoPlayer then treats
 * the file as unseekable (every seek restarts from 0). This subclass is told where Cues really are.
 */
@UnstableApi
class SeekFixMatroskaExtractor(
    subtitleParserFactory: SubtitleParser.Factory,
    flags: Int,
    private val cuesRelativePosition: Long
) : MatroskaExtractor(subtitleParserFactory, flags) {

    @Throws(androidx.media3.common.ParserException::class)
    override fun startMasterElement(id: Int, contentPosition: Long, contentSize: Long) {
        super.startMasterElement(id, contentPosition, contentSize)
        if (id == SEGMENT_ID) {
            try {
                val field = MatroskaExtractor::class.java.getDeclaredField("cuesContentPosition")
                field.isAccessible = true
                field.setLong(this, contentPosition + cuesRelativePosition)
                SeekFixState.note = "applied (Cues at segment+$cuesRelativePosition)"
            } catch (e: Throwable) {
                SeekFixState.note = "FAILED to apply: ${e.javaClass.simpleName}"
            }
        }
    }

    private companion object {
        const val SEGMENT_ID = 0x18538067
    }
}

/** Same extractors as [DefaultExtractorsFactory], except Matroska gets the Cues workaround when needed. */
@UnstableApi
class SeekFixExtractorsFactory(private val context: Context) : ExtractorsFactory {

    private val defaults = DefaultExtractorsFactory()
    private var subtitleParserFactory: SubtitleParser.Factory = DefaultSubtitleParserFactory()
    private var textTrackTranscodingEnabled = true

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled: Boolean): ExtractorsFactory {
        defaults.experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled)
        this.textTrackTranscodingEnabled = textTrackTranscodingEnabled
        return this
    }

    override fun setSubtitleParserFactory(subtitleParserFactory: SubtitleParser.Factory): ExtractorsFactory {
        defaults.setSubtitleParserFactory(subtitleParserFactory)
        this.subtitleParserFactory = subtitleParserFactory
        return this
    }

    override fun createExtractors(): Array<Extractor> = defaults.createExtractors()

    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> {
        val base = defaults.createExtractors(uri, responseHeaders)
        val cuesRelative = findCuesHint(uri) ?: return base
        val flags = if (textTrackTranscodingEnabled) 0 else MatroskaExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA
        return Array(base.size) { i ->
            if (base[i].underlyingImplementation is MatroskaExtractor) {
                SeekFixMatroskaExtractor(subtitleParserFactory, flags, cuesRelative)
            } else {
                base[i]
            }
        }
    }

    private fun findCuesHint(uri: Uri): Long? {
        val scheme = uri.scheme
        if (scheme != "content" && scheme != "file") return null
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val src = ChannelSource(FileInputStream(pfd.fileDescriptor).channel)
                val head = ByteArray(4)
                src.readAt(0, head, 0, 4)
                if (!MkvInspector.isMatroskaHeader(head)) {
                    null
                } else {
                    val hint = MkvInspector(src).findCuesRelativePosition()
                    SeekFixState.note = if (hint == null) "not needed" else "needed (Cues only in a late SeekHead)"
                    hint
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
