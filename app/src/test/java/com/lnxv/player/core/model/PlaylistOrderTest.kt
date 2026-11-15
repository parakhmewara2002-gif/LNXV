package com.lnxv.player.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistOrderTest {
    @Test fun moveDown() = assertEquals(listOf(2, 1, 3), PlaylistOrder.move(listOf(1, 2, 3), 1, 1))
    @Test fun moveUp() = assertEquals(listOf(3, 1, 2), PlaylistOrder.move(listOf(1, 2, 3), 3, -2))
    @Test fun clampsAtEnds() {
        assertEquals(listOf(1, 2, 3), PlaylistOrder.move(listOf(1, 2, 3), 1, -5))
        assertEquals(listOf(2, 3, 1), PlaylistOrder.move(listOf(1, 2, 3), 1, 99))
    }
    @Test fun missingItemUnchanged() = assertEquals(listOf(1, 2), PlaylistOrder.move(listOf(1, 2), 9, 1))
}
