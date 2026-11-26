package com.lnxv.player.core.design

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * Single owner of [WindowManager.LayoutParams.FLAG_SECURE].
 *
 * Before this, each screen added and cleared the flag itself. Vault list -> vault player -> back cleared the flag on
 * the player's dispose while the vault list was still on screen, so vault content briefly became screenshot-able (and
 * the reverse leak happened on the way in). Reference counting keeps the flag on as long as ANY screen needs it.
 */
object ScreenshotGuard {
    private var activity: Activity? = null
    private var holders = 0
    private var global = false

    fun attach(target: Activity) {
        activity = target
        apply()
    }

    fun detach(target: Activity) {
        if (activity === target) activity = null
    }

    fun setGlobal(enabled: Boolean) {
        global = enabled
        apply()
    }

    fun acquire() {
        holders++
        apply()
    }

    fun release() {
        holders = (holders - 1).coerceAtLeast(0)
        apply()
    }

    private fun apply() {
        val window = activity?.window ?: return
        if (global || holders > 0) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

/** Keeps FLAG_SECURE on while this screen is composed. Safe to nest with other secure screens. */
@Composable
fun SecureScreen(enabled: Boolean = true) {
    DisposableEffect(enabled) {
        if (enabled) ScreenshotGuard.acquire()
        onDispose { if (enabled) ScreenshotGuard.release() }
    }
}
