package com.lnxv.player.core.model

data class ChapterItem(val startMs: Long, val title: String)

/** Pure chapter-list helpers: cleaning what the container gave us and finding the chapter for a position. */
object ChapterList {
    const val MAX = 500

    /** Drops unset/negative starts, sorts, merges duplicate starts (first wins) and names untitled chapters "Chapter N". */
    fun sanitize(raw: List<ChapterItem>): List<ChapterItem> {
        val sorted = raw.filter { it.startMs >= 0L }.sortedBy { it.startMs }.distinctBy { it.startMs }.take(MAX)
        return sorted.mapIndexed { i, c -> c.copy(title = c.title.trim().ifEmpty { "Chapter ${i + 1}" }) }
    }

    /** Index of the chapter containing [positionMs], or -1 when before the first chapter or the list is empty. */
    fun currentIndex(list: List<ChapterItem>, positionMs: Long): Int {
        var idx = -1
        for (i in list.indices) if (list[i].startMs <= positionMs) idx = i else break
        return idx
    }

    fun next(list: List<ChapterItem>, positionMs: Long): ChapterItem? = list.firstOrNull { it.startMs > positionMs }

    /** Previous chapter start; within the first 3 s of a chapter this goes to the one before, else restarts the current one. */
    fun previous(list: List<ChapterItem>, positionMs: Long): ChapterItem? {
        val cur = currentIndex(list, positionMs)
        if (cur < 0) return null
        return if (positionMs - list[cur].startMs > 3_000L || cur == 0) list[cur] else list[cur - 1]
    }
}
