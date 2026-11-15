package com.lnxv.player.playback.engine

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Format
import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.extractor.avi.AviExtractor
import androidx.media3.extractor.flac.FlacExtractor
import androidx.media3.extractor.flv.FlvExtractor
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.ogg.OggExtractor
import androidx.media3.extractor.ts.AdtsExtractor
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.PsExtractor
import androidx.media3.extractor.ts.TsExtractor
import androidx.media3.extractor.wav.WavExtractor
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

/**
 * LNXV's own extractor list (Phase 4 / rule A): only the containers a video player needs, in a fixed sniffing order.
 * Compared with Media3's DefaultExtractorsFactory it drops AC-3/AC-4/AMR/MIDI and the image extractors
 * (JPEG, PNG, WebP, BMP, HEIF, AVIF), so R8 can strip them. Matroska gets the Cues workaround when needed.
 * Supported: MKV/WebM, MP4/MOV/3GP (+fragmented), MPEG-TS, AVI, FLV, Ogg, MPEG-PS, WAV, FLAC, AAC (ADTS), MP3.
 */
@UnstableApi
class SeekFixExtractorsFactory(private val context: Context) : ExtractorsFactory {

    private var subtitleParserFactory: SubtitleParser.Factory = DefaultSubtitleParserFactory()
    private var textTrackTranscodingEnabled = true

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun experimentalSetTextTrackTranscodingEnabled(textTrackTranscodingEnabled: Boolean): ExtractorsFactory {
        this.textTrackTranscodingEnabled = textTrackTranscodingEnabled
        return this
    }

    override fun setSubtitleParserFactory(subtitleParserFactory: SubtitleParser.Factory): ExtractorsFactory {
        this.subtitleParserFactory = subtitleParserFactory
        return this
    }

    override fun createExtractors(): Array<Extractor> = build(null)

    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> =
        build(findCuesHint(uri))

    private fun build(cuesRelative: Long?): Array<Extractor> {
        val rawSubs = !textTrackTranscodingEnabled
        val mkvFlags = if (rawSubs) MatroskaExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA else 0
        val matroska: Extractor =
            if (cuesRelative != null) SeekFixMatroskaExtractor(subtitleParserFactory, mkvFlags, cuesRelative)
            else MatroskaExtractor(subtitleParserFactory, mkvFlags)
        val cbr = Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING
        return arrayOf(
            matroska,
            FragmentedMp4Extractor(subtitleParserFactory, if (rawSubs) FragmentedMp4Extractor.FLAG_EMIT_RAW_SUBTITLE_DATA else 0),
            Mp4Extractor(subtitleParserFactory, if (rawSubs) Mp4Extractor.FLAG_EMIT_RAW_SUBTITLE_DATA else 0),
            TsExtractor(
                TsExtractor.MODE_SINGLE_PMT,
                if (rawSubs) TsExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA else 0,
                subtitleParserFactory,
                TimestampAdjuster(0),
                DefaultTsPayloadReaderFactory(0, emptyList<Format>()),
                TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES
            ),
            AviExtractor(if (rawSubs) AviExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA else 0, subtitleParserFactory),
            FlvExtractor(),
            OggExtractor(),
            PsExtractor(),
            WavExtractor(),
            FlacExtractor(0),
            AdtsExtractor(AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING),
            Mp3Extractor(cbr) // last: its sniffing is the most lenient
        )
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
