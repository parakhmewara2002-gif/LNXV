package com.lnxv.player.core.subtitle

/** Turns a video file name into a search text plus optional season / episode. Pure logic. */
object SubtitleQueryBuilder {
    data class Parsed(val query: String, val season: Int?, val episode: Int?)

    private val EPISODE = Regex("""(?i)\bs(\d{1,2})[ ._-]?e(\d{1,3})\b""")
    private val TAGS = setOf(
        "480p", "576p", "720p", "1080p", "1440p", "2160p", "4k", "uhd", "bluray", "blu-ray", "brrip", "bdrip", "webrip", "web-dl", "webdl",
        "web", "hdtv", "hdrip", "dvdrip", "dvdscr", "x264", "x265", "h264", "h265", "hevc", "av1", "xvid", "divx", "10bit", "hdr", "remux", "proper", "repack"
    )

    fun parse(fileName: String): Parsed {
        var base = fileName.substringBeforeLast('.', fileName)
        base = base.replace('.', ' ').replace('_', ' ')
        base = base.replace(Regex("""[\[(][^\])]*[\])]"""), " ")
        var season: Int? = null
        var episode: Int? = null
        EPISODE.find(base)?.let {
            season = it.groupValues[1].toInt()
            episode = it.groupValues[2].toInt()
            base = base.substring(0, it.range.first)
        }
        val words = base.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        val kept = ArrayList<String>()
        for (w in words) {
            if (w.lowercase() in TAGS) break
            kept += w
        }
        val query = kept.joinToString(" ").trim().ifEmpty { words.joinToString(" ") }
        return Parsed(query, season, episode)
    }
}
