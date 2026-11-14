package com.lnxv.player.core.model

/** Smart collections shown as chips on the Videos tab (Phase 6). Pure logic, unit tested. */
enum class SmartCollection(val label: String) {
    ALL("All"),
    RECENT("Recently added"),
    UNFINISHED("Unfinished"),
    LARGE("Large files"),
    DUPLICATES("Possible duplicates");

    fun matches(v: VideoItem, nowSec: Long): Boolean =
        matches(v.dateAddedSec, v.sizeBytes, v.durationMs, v.lastPositionMs, nowSec)

    fun matches(dateAddedSec: Long, sizeBytes: Long, durationMs: Long, lastPositionMs: Long, nowSec: Long): Boolean = when (this) {
        ALL -> true
        RECENT -> dateAddedSec > 0 && nowSec - dateAddedSec <= RECENT_WINDOW_SEC
        UNFINISHED -> isUnfinished(durationMs, lastPositionMs)
        LARGE -> sizeBytes >= LARGE_BYTES
        DUPLICATES -> true // computed across the whole list by HomeViewModel via DuplicateFinder
    }

    companion object {
        const val RECENT_WINDOW_SEC = 7L * 24 * 3600
        const val NEW_WINDOW_SEC = 3L * 24 * 3600
        const val LARGE_BYTES = 1L shl 30 // 1 GiB
        private const val MIN_PROGRESS_MS = 5_000L

        fun isUnfinished(durationMs: Long, lastPositionMs: Long): Boolean =
            lastPositionMs > MIN_PROGRESS_MS && (durationMs <= 0L || lastPositionMs < durationMs - MIN_PROGRESS_MS)

        /** 0f..1f watched fraction for the card progress bar; 0 when never played. */
        fun watchedFraction(durationMs: Long, lastPositionMs: Long): Float {
            if (durationMs <= 0L || lastPositionMs <= 0L) return 0f
            return (lastPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        }

        fun watchedFraction(v: VideoItem): Float = watchedFraction(v.durationMs, v.lastPositionMs)

        /** "New" badge: added within 3 days and never played. */
        fun isNew(dateAddedSec: Long, lastPlayedTimestamp: Long, nowSec: Long): Boolean =
            lastPlayedTimestamp == 0L && dateAddedSec > 0 && nowSec - dateAddedSec <= NEW_WINDOW_SEC

        fun isNew(v: VideoItem, nowSec: Long): Boolean = isNew(v.dateAddedSec, v.lastPlayedTimestamp, nowSec)
    }
}
