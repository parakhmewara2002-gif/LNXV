package com.lnxv.player.core.subtitle

import androidx.media3.common.MimeTypes

/** Text subtitle formats LNXV can load from a file. Detection looks at content first, file name second. */
enum class SubtitleFormat(val mimeType: String, val extension: String, val supportsDelay: Boolean) {
    SRT(MimeTypes.APPLICATION_SUBRIP, "srt", true),
    VTT(MimeTypes.TEXT_VTT, "vtt", true),
    ASS(MimeTypes.TEXT_SSA, "ass", true),
    TTML(MimeTypes.APPLICATION_TTML, "ttml", false);

    companion object {
        fun detect(text: String): SubtitleFormat? {
            val head = text.trimStart('﻿', ' ', '\n', '\r', '\t')
            val sample = head.take(4096)
            return when {
                head.startsWith("WEBVTT") -> VTT
                sample.contains("[Script Info]", ignoreCase = true) ||
                    sample.contains("[V4+ Styles]", ignoreCase = true) ||
                    sample.contains("[V4 Styles]", ignoreCase = true) -> ASS
                sample.startsWith("<?xml") && Regex("""<tt[\s>:]""").containsMatchIn(sample) -> TTML
                Regex("""<tt[\s>:]""").containsMatchIn(sample.take(512)) -> TTML
                text.contains("-->") -> SRT
                else -> null
            }
        }

        fun fromFileName(name: String?): SubtitleFormat? {
            val ext = name?.substringAfterLast('.', "")?.lowercase() ?: return null
            return when (ext) {
                "srt" -> SRT
                "vtt" -> VTT
                "ass", "ssa" -> ASS
                "ttml", "xml", "dfxp" -> TTML
                else -> null
            }
        }
    }
}
