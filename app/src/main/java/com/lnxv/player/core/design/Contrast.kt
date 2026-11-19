package com.lnxv.player.core.design

import kotlin.math.pow

/** WCAG 2.x contrast maths on ARGB ints. Pure so it can be unit tested. */
object Contrast {
    const val AA_NORMAL = 4.5
    const val AA_LARGE_OR_GRAPHIC = 3.0

    private fun channel(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(argb: Int): Double {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    /** Alpha-composites [fg] (using its alpha) over an opaque [bg]; the result is opaque. */
    fun over(fg: Int, bg: Int): Int {
        val a = ((fg ushr 24) and 0xFF) / 255.0
        fun mix(shift: Int): Int {
            val f = (fg shr shift) and 0xFF
            val b = (bg shr shift) and 0xFF
            return Math.round(f * a + b * (1 - a)).toInt()
        }
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    fun ratio(fg: Int, bg: Int): Double {
        val l1 = luminance(over(fg, bg))
        val l2 = luminance(bg)
        val hi = maxOf(l1, l2)
        val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }
}
