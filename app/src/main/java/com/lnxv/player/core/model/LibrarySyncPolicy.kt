package com.lnxv.player.core.model

/** Decides whether videos missing from a MediaStore scan may be removed from the library. */
object LibrarySyncPolicy {
    /**
     * Pruning requires full access, a non-empty successful scan, and at least 80% of the existing
     * library to be visible. Empty or suspiciously small scans preserve metadata rather than wiping
     * favourites, history, playlists, resume points, and remembered subtitles.
     */
    fun shouldPrune(scannedCount: Int, existingCount: Int, hasFullAccess: Boolean = true): Boolean {
        if (!hasFullAccess || scannedCount <= 0 || existingCount < 0) return false
        if (existingCount == 0) return true
        return scannedCount.toLong() * 5L >= existingCount.toLong() * 4L
    }
}
