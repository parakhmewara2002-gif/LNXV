package com.lnxv.player.core.model

/** Decides whether videos missing from a MediaStore scan may be removed from the library. */
object LibrarySyncPolicy {
    private const val SMALL_LIBRARY = 10

    /**
     * A scan that returns less than half of a non-trivial library is treated as a transient failure
     * (unmounted SD card, revoked/partial permission) and must not wipe favourites, history and resume points.
     */
    fun shouldPrune(scannedCount: Int, existingCount: Int, hasFullAccess: Boolean = true): Boolean =
        hasFullAccess && (existingCount <= SMALL_LIBRARY || scannedCount * 2 >= existingCount)
}
