package com.lnxv.player.core.model

/** Smart collections shown as chips on the Videos tab (Phase 6). Pure logic, unit tested. */
enum class SmartCollection(val label: String) {
    ALL("All"),
    RECENT("Recently added"),
    UNFINISHED("Unfinished"),
    LARGE("Large files");

    fun matches(v: VideoItem, nowSec: Long): Boolean = when (this) {
        ALL -> true
        RECENT -> v.dateAddedSec > 0 && nowSec - v.dateAddedSec <= RECENT_WINDOW_SEC
        UNFINISHED -> isUnfinished(v)
        LARGE -> v.sizeBytes >= LARGE_BYTES
    }

    companion object {
        const val RECENT_WINDOW_SEC = 7L * 24 * 3600
        const val LARGE_BYTES = 1L shl 30 // 1 GiB
        private const val MIN_PROGRESS_MS = 5_000L

        fun isUnfinished(v: VideoItem): Boolean =
            v.lastPositionMs > MIN_PROGRESS_MS && (v.durationMs <= 0L || v.lastPositionMs < v.durationMs - MIN_PROGRESS_MS)

        /** 0f..1f watched fraction for the card progress bar; 0 when never played. */
        fun watchedFraction(v: VideoItem): Float {
            if (v.durationMs <= 0L || v.lastPositionMs <= 0L) return 0f
            return (v.lastPositionMs.toFloat() / v.durationMs.toFloat()).coerceIn(0f, 1f)
        }

        /** "New" badge: added within 3 days and never played. */
        fun isNew(v: VideoItem, nowSec: Long): Boolean =
            v.lastPlayedTimestamp == 0L && v.dateAddedSec > 0 && nowSec - v.dateAddedSec <= 3L * 24 * 3600
    }
}
