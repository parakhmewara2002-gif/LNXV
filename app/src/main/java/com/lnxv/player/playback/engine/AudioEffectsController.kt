package com.lnxv.player.playback.engine

import android.media.audiofx.Equalizer
import com.lnxv.player.core.model.EqPreset

/**
 * Wraps android.media.audiofx.Equalizer for the player's audio session.
 * Every call is best-effort: some devices/ROMs refuse effects, and that must never crash playback.
 */
class AudioEffectsController {
    private var eq: Equalizer? = null
    private var sessionId: Int = 0
    var preset: EqPreset = EqPreset.FLAT
        private set

    fun attach(audioSessionId: Int) {
        if (audioSessionId == sessionId && eq != null) return
        releaseEffects()
        sessionId = audioSessionId
        if (audioSessionId == 0 || preset == EqPreset.FLAT) return
        apply()
    }

    fun setPreset(p: EqPreset) {
        preset = p
        if (p == EqPreset.FLAT) {
            releaseEffects()
            return
        }
        apply()
    }

    private fun apply() {
        if (sessionId == 0) return
        try {
            val e = eq ?: Equalizer(0, sessionId).also { eq = it }
            val range = e.bandLevelRange
            val levels = preset.levels(e.numberOfBands.toInt(), range[0].toInt(), range[1].toInt())
            levels.forEachIndexed { i, mb -> e.setBandLevel(i.toShort(), mb.toShort()) }
            e.enabled = true
        } catch (_: Throwable) {
            releaseEffects()
        }
    }

    fun releaseEffects() {
        try { eq?.release() } catch (_: Throwable) {}
        eq = null
    }
}
