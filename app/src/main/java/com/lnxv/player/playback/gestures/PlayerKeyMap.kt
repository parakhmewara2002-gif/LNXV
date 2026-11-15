package com.lnxv.player.playback.gestures

import android.view.KeyEvent

/** Hardware keyboard / D-pad / media-key mapping for the player (Phase 6, TV and keyboard support). Pure, unit tested. */
enum class KeyAction { TOGGLE_PLAY, SEEK_BACK, SEEK_FORWARD, SEEK_BACK_LONG, SEEK_FORWARD_LONG, NEXT, PREVIOUS, SHOW_CONTROLS }

object PlayerKeyMap {
    fun resolve(keyCode: Int, shift: Boolean = false): KeyAction? = when (keyCode) {
        KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_K -> KeyAction.TOGGLE_PLAY
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> if (shift) KeyAction.SEEK_BACK_LONG else KeyAction.SEEK_BACK
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> if (shift) KeyAction.SEEK_FORWARD_LONG else KeyAction.SEEK_FORWARD
        KeyEvent.KEYCODE_J -> KeyAction.SEEK_BACK_LONG
        KeyEvent.KEYCODE_L -> KeyAction.SEEK_FORWARD_LONG
        KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_N -> KeyAction.NEXT
        KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_P -> KeyAction.PREVIOUS
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> KeyAction.SHOW_CONTROLS
        else -> null
    }

    fun seekDeltaMs(a: KeyAction): Long = when (a) {
        KeyAction.SEEK_BACK -> -5_000L
        KeyAction.SEEK_FORWARD -> 5_000L
        KeyAction.SEEK_BACK_LONG -> -10_000L
        KeyAction.SEEK_FORWARD_LONG -> 10_000L
        else -> 0L
    }
}
