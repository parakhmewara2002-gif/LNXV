package com.lnxv.player.core.model

/** Equalizer presets (Phase 5). Shape is given per-band as a fraction of the device's level range (-1..1). */
enum class EqPreset(val label: String, private val shape: FloatArray) {
    FLAT("Flat", floatArrayOf(0f, 0f, 0f, 0f, 0f)),
    BASS("Bass boost", floatArrayOf(0.8f, 0.5f, 0f, 0f, 0f)),
    VOCAL("Vocal", floatArrayOf(-0.3f, 0f, 0.5f, 0.4f, -0.1f)),
    TREBLE("Treble boost", floatArrayOf(0f, 0f, 0f, 0.5f, 0.8f)),
    LOUDNESS("Loud", floatArrayOf(0.6f, 0.2f, 0f, 0.3f, 0.6f));

    /** Millibel level for each of [bands] bands, resampling the 5-point shape and clamping to [minMb, maxMb]. */
    fun levels(bands: Int, minMb: Int, maxMb: Int): IntArray {
        if (bands <= 0) return IntArray(0)
        return IntArray(bands) { i ->
            val pos = if (bands == 1) 0f else i.toFloat() / (bands - 1) * (shape.size - 1)
            val lo = pos.toInt().coerceIn(0, shape.size - 1)
            val hi = (lo + 1).coerceAtMost(shape.size - 1)
            val f = shape[lo] + (shape[hi] - shape[lo]) * (pos - lo)
            val mb = if (f >= 0) (f * maxMb).toInt() else (-f * minMb).toInt()
            mb.coerceIn(minMb, maxMb)
        }
    }

    fun next(): EqPreset = values()[(ordinal + 1) % values().size]
}
