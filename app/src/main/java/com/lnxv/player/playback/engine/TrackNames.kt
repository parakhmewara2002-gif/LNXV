package com.lnxv.player.playback.engine

import java.util.Locale

/**
 * Human track names for the audio/subtitle panels. Container titles are often just a release tag
 * ("(Movies4u.Foo).") so the name is built from language + channels/format instead, and the raw title is only used
 * when no language is known.
 */
object TrackNames {
    fun languageName(tag: String?): String? {
        val t = tag?.trim().orEmpty()
        if (t.isEmpty() || t.equals("und", true) || t.equals("zxx", true) || t.equals("mis", true)) return null
        val name = Locale.forLanguageTag(t).displayLanguage
        return if (name.isNullOrBlank() || name.equals(t, true)) t.uppercase(Locale.ROOT) else name.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

    fun channelLayout(count: Int): String? = when {
        count <= 0 -> null
        count == 1 -> "Mono"
        count == 2 -> "Stereo"
        count == 6 -> "5.1"
        count == 8 -> "7.1"
        else -> "${count}ch"
    }

    fun audioCodec(mime: String?): String? = when (mime?.lowercase(Locale.ROOT)) {
        "audio/ac3" -> "AC3"
        "audio/eac3", "audio/eac3-joc" -> "E-AC3"
        "audio/mp4a-latm" -> "AAC"
        "audio/opus" -> "Opus"
        "audio/vorbis" -> "Vorbis"
        "audio/true-hd" -> "TrueHD"
        "audio/vnd.dts" -> "DTS"
        "audio/vnd.dts.hd" -> "DTS-HD"
        "audio/mpeg", "audio/mpeg-l2" -> "MP3"
        "audio/flac" -> "FLAC"
        else -> null
    }

    fun subtitleFormat(mime: String?): String? = when (mime?.lowercase(Locale.ROOT)) {
        "application/x-subrip" -> "SRT"
        "text/x-ssa" -> "ASS"
        "text/vtt" -> "VTT"
        "application/pgs" -> "PGS"
        "application/dvbsubs" -> "DVB"
        "application/vobsub" -> "VobSub"
        "application/ttml+xml", "application/x-quicktime-tx3g", "application/x-mp4-vtt" -> "TTML"
        "application/cea-608", "application/cea-708" -> "CC"
        else -> null
    }

    fun audioLabel(language: String?, title: String?, channels: Int, mime: String?, index: Int): String {
        val lang = languageName(language)
        val head = lang ?: title?.takeIf { it.isNotBlank() } ?: "Track ${index + 1}"
        return listOfNotNull(head, channelLayout(channels), audioCodec(mime)).joinToString(" · ")
    }

    fun subtitleLabel(language: String?, title: String?, mime: String?, index: Int, forced: Boolean, sdh: Boolean): String {
        val lang = languageName(language)
        val head = lang ?: title?.takeIf { it.isNotBlank() } ?: "Subtitle ${index + 1}"
        return listOfNotNull(head, subtitleFormat(mime), if (forced) "Forced" else null, if (sdh) "SDH" else null).joinToString(" · ")
    }
}
