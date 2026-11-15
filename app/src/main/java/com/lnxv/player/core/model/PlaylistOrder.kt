package com.lnxv.player.core.model

/** Pure list helpers for playlist editing (Phase 6). */
object PlaylistOrder {
    /** Moves the element equal to [item] by [delta] places (clamped). Returns the same list if not found or no change. */
    fun <T> move(list: List<T>, item: T, delta: Int): List<T> {
        val from = list.indexOf(item)
        if (from < 0) return list
        val to = (from + delta).coerceIn(0, list.size - 1)
        if (to == from) return list
        val out = list.toMutableList()
        out.removeAt(from)
        out.add(to, item)
        return out
    }
}
