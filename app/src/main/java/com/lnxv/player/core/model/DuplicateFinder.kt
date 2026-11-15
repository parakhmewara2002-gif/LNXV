package com.lnxv.player.core.model

import kotlinx.coroutines.ensureActive

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

    /**
     * Confirms candidate groups by content fingerprint. Suspends cooperatively ([ensureActive] per item) so the
     * caller can cancel it; items whose hash is unavailable are dropped. Groups left with < 2 items disappear.
     */
    suspend fun <T> confirm(groups: List<List<T>>, hashOf: suspend (T) -> String?): List<List<T>> {
        val out = ArrayList<List<T>>()
        for (g in groups) {
            val byHash = LinkedHashMap<String, MutableList<T>>()
            for (item in g) {
                kotlin.coroutines.coroutineContext.ensureActive()
                val h = hashOf(item) ?: continue
                byHash.getOrPut(h) { mutableListOf() }.add(item)
            }
            byHash.values.filter { it.size > 1 }.forEach { out += it }
        }
        return out
    }

    /** Flattened, grouped order (largest groups first) for display in a single list. */
    fun flatten(videos: List<VideoItem>): List<VideoItem> = groups(videos).flatten()
}
