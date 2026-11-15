package com.lnxv.player.playback

import android.view.KeyEvent
import com.lnxv.player.playback.gestures.KeyAction
import com.lnxv.player.playback.gestures.PlayerKeyMap
import org.junit.Assert.*
import org.junit.Test

class PlayerKeyMapTest {
    @Test fun spaceToggles() = assertEquals(KeyAction.TOGGLE_PLAY, PlayerKeyMap.resolve(KeyEvent.KEYCODE_SPACE))
    @Test fun arrowsSeek() {
        assertEquals(-5_000L, PlayerKeyMap.seekDeltaMs(PlayerKeyMap.resolve(KeyEvent.KEYCODE_DPAD_LEFT)!!))
        assertEquals(10_000L, PlayerKeyMap.seekDeltaMs(PlayerKeyMap.resolve(KeyEvent.KEYCODE_DPAD_RIGHT, shift = true)!!))
    }
    @Test fun unknownIsNull() = assertNull(PlayerKeyMap.resolve(KeyEvent.KEYCODE_Z))
    @Test fun nonSeekHasZeroDelta() = assertEquals(0L, PlayerKeyMap.seekDeltaMs(KeyAction.NEXT))
}
