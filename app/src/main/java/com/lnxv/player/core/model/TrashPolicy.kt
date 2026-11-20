package com.lnxv.player.core.model

/** One video in the system trash (Android 11+ MediaStore). [expiresSec] is DATE_EXPIRES in epoch seconds, 0 when unknown. */
data class TrashItem(val id: Long, val name: String, val sizeBytes: Long, val expiresSec: Long)

object TrashPolicy {
    private const val DAY = 86_400L

    /** Whole days left before the system empties the item (rounded up, never negative); null when the expiry is unknown. */
    fun daysLeft(expiresSec: Long, nowSec: Long): Int? {
        if (expiresSec <= 0L) return null
        val left = expiresSec - nowSec
        if (left <= 0L) return 0
        return ((left + DAY - 1) / DAY).toInt()
    }

    /** Newest-expiring first so the most recently trashed items are on top. */
    fun sorted(items: List<TrashItem>): List<TrashItem> = items.sortedByDescending { it.expiresSec }
}
