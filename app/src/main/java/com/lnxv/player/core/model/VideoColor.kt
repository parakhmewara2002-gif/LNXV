package com.lnxv.player.core.model

/** Video colour adjustment: brightness/contrast in [-1,1], saturation in [-1,1] (0 = unchanged). */
data class VideoColor(val brightness: Float = 0f, val contrast: Float = 0f, val saturation: Float = 0f) {
    val isNeutral: Boolean get() = brightness == 0f && contrast == 0f && saturation == 0f

    companion object {
        const val STEP = 0.1f
        const val LIMIT = 0.5f

        /** Next value when cycling: up by one step, wrapping to the negative limit after the positive limit. */
        fun cycle(v: Float): Float {
            val n = Math.round((v + STEP) * 10f) / 10f
            return if (n > LIMIT + 1e-4f) -LIMIT else n
        }
    }
}
