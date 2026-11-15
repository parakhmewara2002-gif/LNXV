package com.lnxv.player.core.design

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.compositionLocalOf

/** True when blur, shared-element transitions and heavy animations should be skipped. One switch for all of them. */
val LocalReduceEffects = compositionLocalOf { false }

enum class EffectsMode { AUTO, ON, OFF;
    fun next(): EffectsMode = when (this) { AUTO -> ON; ON -> OFF; OFF -> AUTO }
    companion object { fun parse(s: String?): EffectsMode = values().firstOrNull { it.name == s } ?: AUTO }
}

/** Low-end detection (2-3 GB RAM class, isLowRamDevice, or a 60 Hz-only panel combined with <= 4 GB). */
object DeviceTier {
    /** `totalRamBytes` from ActivityManager is lower than the nominal size, so 3.2e9 ~ "3 GB device". */
    const val LOW_RAM_BYTES = 3_200_000_000L
    const val MID_RAM_BYTES = 4_200_000_000L

    fun isLowEnd(isLowRamDevice: Boolean, totalRamBytes: Long, maxRefreshHz: Float): Boolean =
        isLowRamDevice || totalRamBytes <= LOW_RAM_BYTES || (maxRefreshHz <= 60.5f && totalRamBytes <= MID_RAM_BYTES)

    fun resolveReduceEffects(mode: EffectsMode, lowEnd: Boolean): Boolean = when (mode) {
        EffectsMode.ON -> true
        EffectsMode.OFF -> false
        EffectsMode.AUTO -> lowEnd
    }

    fun detect(context: Context, maxRefreshHz: Float): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return isLowEnd(am.isLowRamDevice, info.totalMem, maxRefreshHz)
    }
}
