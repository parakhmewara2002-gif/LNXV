package com.lnxv.player.core.bookmarks

/** One saved position in a video. [label] is optional user text (empty = show the timestamp only). */
data class Bookmark(val positionMs: Long, val label: String = "")

/**
 * Pure bookmark-list logic and the storage encoding (one line per bookmark: `positionMs<TAB>label`, with tabs and
 * newlines in labels replaced by spaces). Kept free of Android types so it is unit tested.
 */
object BookmarkList {
    const val MAX = 100
    const val MAX_LABEL = 60

    /** Bookmarks closer than this are treated as the same spot. */
    const val MERGE_WINDOW_MS = 1_000L

    fun cleanLabel(raw: String): String = raw.replace(Regex("[\\t\\r\\n]+"), " ").trim().take(MAX_LABEL)

    /** Adds a bookmark, keeps the list sorted by position and de-duplicated within [MERGE_WINDOW_MS], capped at [MAX]. */
    fun add(list: List<Bookmark>, positionMs: Long, label: String = ""): List<Bookmark> {
        val pos = positionMs.coerceAtLeast(0L)
        if (list.any { kotlin.math.abs(it.positionMs - pos) < MERGE_WINDOW_MS }) return list
        if (list.size >= MAX) return list
        return (list + Bookmark(pos, cleanLabel(label))).sortedBy { it.positionMs }
    }

    fun remove(list: List<Bookmark>, positionMs: Long): List<Bookmark> = list.filterNot { it.positionMs == positionMs }

    fun rename(list: List<Bookmark>, positionMs: Long, label: String): List<Bookmark> =
        list.map { if (it.positionMs == positionMs) it.copy(label = cleanLabel(label)) else it }

    /** The first bookmark strictly after [positionMs] (plus a small guard so a just-seeked bookmark is not returned again). */
    fun next(list: List<Bookmark>, positionMs: Long): Bookmark? = list.firstOrNull { it.positionMs > positionMs + 500L }

    fun previous(list: List<Bookmark>, positionMs: Long): Bookmark? = list.lastOrNull { it.positionMs < positionMs - 1_500L }

    fun encode(list: List<Bookmark>): String = list.joinToString("\n") { "${it.positionMs}\t${cleanLabel(it.label)}" }

    fun decode(raw: String?): List<Bookmark> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val idx = line.indexOf('\t')
            val posText = if (idx < 0) line else line.substring(0, idx)
            val pos = posText.trim().toLongOrNull()?.takeIf { it >= 0L } ?: return@mapNotNull null
            Bookmark(pos, if (idx < 0) "" else cleanLabel(line.substring(idx + 1)))
        }.distinctBy { it.positionMs }.sortedBy { it.positionMs }.take(MAX).toList()
    }

    /** "m:ss" or "h:mm:ss". */
    fun formatTime(ms: Long): String {
        val s = (ms.coerceAtLeast(0L) / 1000L)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }
}
