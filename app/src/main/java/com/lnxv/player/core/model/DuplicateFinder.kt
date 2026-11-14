package com.lnxv.player.core.model

/**
 * Finds *possible* duplicate videos (Phase 6). Heuristic: identical file size AND identical duration (> 0).
 * It does not hash content, so it can (rarely) group different files and must only be used to suggest, never to auto-delete.
 */
object DuplicateFinder {
    fun <T> groups(items: List<T>, sizeOf: (T) -> Long, durationOf: (T) -> Long): List<List<T>> =
        items
            .filter { sizeOf(it) > 0L && durationOf(it) > 0L }
            .groupBy { sizeOf(it) to durationOf(it) }
            .values
            .filter { it.size > 1 }
            .sortedByDescending { sizeOf(it.first()) }

    fun groups(videos: List<VideoItem>): List<List<VideoItem>> =
        groups(videos, { it.sizeBytes }, { it.durationMs })

    /** Flattened, grouped order (largest groups first) for display in a single list. */
    fun flatten(videos: List<VideoItem>): List<VideoItem> = groups(videos).flatten()
}
