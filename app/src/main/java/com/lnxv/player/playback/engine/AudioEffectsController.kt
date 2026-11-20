package com.lnxv.player.playback.engine

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
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
    private var bassFx: BassBoost? = null
    private var virtFx: Virtualizer? = null
    var bassStrength: Int = 0
        private set
    var virtualizerStrength: Int = 0
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
        if (bassStrength > 0) applyBass()
        if (virtualizerStrength > 0) applyVirtualizer()
    }

    /** Bass Boost strength 0..1000 (0 = off). Best-effort: unsupported devices ignore it. */
    fun setBass(strength: Int) {
        bassStrength = strength.coerceIn(0, 1000)
        if (bassStrength == 0) {
            try { bassFx?.release() } catch (_: Throwable) {}
            bassFx = null
        } else applyBass()
    }

    private fun applyBass() {
        if (sessionId == 0) return
        try {
            val b = bassFx ?: BassBoost(0, sessionId).also { bassFx = it }
            if (b.strengthSupported) b.setStrength(bassStrength.toShort())
            b.enabled = true
        } catch (_: Throwable) {
            try { bassFx?.release() } catch (_: Throwable) {}
            bassFx = null
        }
    }

    /** Virtualizer strength 0..1000 (0 = off). Best-effort. */
    fun setVirtualizer(strength: Int) {
        virtualizerStrength = strength.coerceIn(0, 1000)
        if (virtualizerStrength == 0) {
            try { virtFx?.release() } catch (_: Throwable) {}
            virtFx = null
        } else applyVirtualizer()
    }

    private fun applyVirtualizer() {
        if (sessionId == 0) return
        try {
            val v = virtFx ?: Virtualizer(0, sessionId).also { virtFx = it }
            if (v.strengthSupported) v.setStrength(virtualizerStrength.toShort())
            v.enabled = true
        } catch (_: Throwable) {
            try { virtFx?.release() } catch (_: Throwable) {}
            virtFx = null
        }
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
        try { bassFx?.release() } catch (_: Throwable) {}
        bassFx = null
        try { virtFx?.release() } catch (_: Throwable) {}
        virtFx = null
    }
}
