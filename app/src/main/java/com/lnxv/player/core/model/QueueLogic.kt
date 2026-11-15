package com.lnxv.player.core.model

import java.util.Random

enum class RepeatMode { OFF, ONE, ALL }

/**
 * Pure play-queue logic (shuffle / repeat / next / previous), unit tested.
 * [RepeatMode.ONE] is handled by the player itself (REPEAT_MODE_ONE); for manual next/previous it behaves like OFF.
 */
class QueueLogic(
    private val original: List<Long>,
    startId: Long,
    shuffle: Boolean = false,
    repeat: RepeatMode = RepeatMode.OFF,
    private val seed: Long = System.nanoTime()
) {
    var shuffle: Boolean = shuffle
        private set
    var repeat: RepeatMode = repeat
        private set

    private var order: List<Long> = original
    private var index: Int = 0

    init {
        val start = original.indexOf(startId).let { if (it < 0) 0 else it }
        index = start
        if (shuffle) rebuildShuffled()
    }

    val size: Int get() = order.size
    val currentId: Long? get() = order.getOrNull(index)
    val hasNext: Boolean get() = index + 1 < order.size || (repeat == RepeatMode.ALL && order.size > 1)
    val hasPrevious: Boolean get() = index > 0 || (repeat == RepeatMode.ALL && order.size > 1)

    private fun rebuildShuffled() {
        val current = order.getOrNull(index) ?: original.getOrNull(index)
        val rest = original.filter { it != current }.toMutableList()
        rest.shuffle(Random(seed))
        order = if (current != null) listOf(current) + rest else rest
        index = 0
    }

    fun setShuffle(on: Boolean) {
        if (on == shuffle) return
        val current = currentId
        shuffle = on
        if (on) {
            rebuildShuffled()
        } else {
            order = original
            index = original.indexOf(current).let { if (it < 0) 0 else it }
        }
    }

    fun setRepeat(mode: RepeatMode) { repeat = mode }

    fun cycleRepeat(): RepeatMode {
        repeat = when (repeat) { RepeatMode.OFF -> RepeatMode.ALL; RepeatMode.ALL -> RepeatMode.ONE; RepeatMode.ONE -> RepeatMode.OFF }
        return repeat
    }

    /** Advances and returns the id to play, or null at the end of the queue (repeat off). */
    fun next(): Long? {
        if (order.isEmpty()) return null
        if (index + 1 < order.size) { index++; return order[index] }
        if (repeat == RepeatMode.ALL && order.size > 1) { index = 0; return order[0] }
        return null
    }

    fun previous(): Long? {
        if (order.isEmpty()) return null
        if (index > 0) { index--; return order[index] }
        if (repeat == RepeatMode.ALL && order.size > 1) { index = order.size - 1; return order[index] }
        return null
    }
}
