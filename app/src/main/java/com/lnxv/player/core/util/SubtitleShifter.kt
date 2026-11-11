package com.lnxv.player.core.util

/**
 * Shifts every cue time of an SRT / WebVTT file by [offsetMs] (positive = subtitles appear later).
 * Pure Kotlin so it can be unit-tested. Used for "subtitle delay" on external subtitle files.
 */
object SubtitleShifter {

    // [HH:]MM:SS(.|,)mmm
    private val TIME = Regex("""(?:(\d{1,2}):)?(\d{2}):(\d{2})([.,])(\d{3})""")

    fun looksLikeSubtitle(text: String): Boolean = text.contains("-->")

    fun shift(content: String, offsetMs: Long): String {
        if (offsetMs == 0L) return content
        return content.lineSequence().joinToString("\n") { line ->
            if (!line.contains("-->")) {
                line
            } else {
                TIME.replace(line) { m ->
                    val hasHours = m.groupValues[1].isNotEmpty()
                    val hours = if (hasHours) m.groupValues[1].toLong() else 0L
                    val minutes = m.groupValues[2].toLong()
                    val seconds = m.groupValues[3].toLong()
                    val millis = m.groupValues[5].toLong()
                    val total = (((hours * 60 + minutes) * 60 + seconds) * 1000 + millis + offsetMs)
                        .coerceAtLeast(0L)
                    format(total, m.groupValues[4], hasHours)
                }
            }
        }
    }

    fun format(totalMs: Long, separator: String, forceHours: Boolean): String {
        val h = totalMs / 3_600_000
        val m = (totalMs / 60_000) % 60
        val s = (totalMs / 1000) % 60
        val ms = totalMs % 1000
        return if (forceHours || h > 0) {
            "%02d:%02d:%02d%s%03d".format(h, m, s, separator, ms)
        } else {
            "%02d:%02d%s%03d".format(m, s, separator, ms)
        }
    }
}
