package com.lnxv.player.playback.engine

import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import com.lnxv.player.core.model.EqPreset

/**
 * Wraps android.media.audiofx.Equalizer for the player's audio session.
 * Every call is best-effort: some devices/ROMs refuse effects, and that must never crash playback.
 */
class AudioEffectsController {
    private var eq: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null
    var boostMb: Int = 0
        private set
    private var sessionId: Int = 0
    var preset: EqPreset = EqPreset.FLAT
        private set

    fun attach(audioSessionId: Int) {
        if (audioSessionId == sessionId && eq != null) return
        releaseEffects()
        sessionId = audioSessionId
        if (audioSessionId == 0) return
        if (preset != EqPreset.FLAT) apply()
        if (boostMb > 0) applyBoost()
    }

    /** Volume boost in millibels (0 = off). Not true loudness normalization; it is a fixed gain. */
    fun setBoost(mb: Int) {
        boostMb = mb.coerceIn(0, 1500)
        if (boostMb == 0) {
            try { loudness?.release() } catch (_: Throwable) {}
            loudness = null
        } else applyBoost()
    }

    private fun applyBoost() {
        if (sessionId == 0) return
        try {
            val l = loudness ?: LoudnessEnhancer(sessionId).also { loudness = it }
            l.setTargetGain(boostMb)
            l.enabled = true
        } catch (_: Throwable) {
            try { loudness?.release() } catch (_: Throwable) {}
            loudness = null
        }
    }

    fun setPreset(p: EqPreset) {
        preset = p
        if (p == EqPreset.FLAT) {
            releaseEq()
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
            releaseEq()
        }
    }

    private fun releaseEq() {
        try { eq?.release() } catch (_: Throwable) {}
        eq = null
    }

    fun releaseEffects() {
        try { eq?.release() } catch (_: Throwable) {}
        eq = null
        try { loudness?.release() } catch (_: Throwable) {}
        loudness = null
    }
}
