package com.lnxv.player.core.design

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    @Test fun blackOnWhiteIs21() {
        assertEquals(21.0, Contrast.ratio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.001)
    }

    @Test fun sameColourIs1() {
        assertEquals(1.0, Contrast.ratio(0xFF336699.toInt(), 0xFF336699.toInt()), 0.0001)
    }

    @Test fun translucentWhiteOverBlackBlends() {
        // 60% white over black = #999999 -> about 7.37:1 on black.
        assertEquals(0xFF999999.toInt(), Contrast.over(0x99FFFFFF.toInt(), 0xFF000000.toInt()))
        assertEquals(7.37, Contrast.ratio(0x99FFFFFF.toInt(), 0xFF000000.toInt()), 0.05)
    }

    private fun check(name: String, p: LNXVColors) {
        val bg = p.background.toArgb()
        val text = listOf(
            "textPrimary" to p.textPrimary.toArgb(),
            "textSecondary" to p.textSecondary.toArgb(),
            "textTertiary" to p.textTertiary.toArgb(),
            "accent" to p.accent.toArgb()
        )
        for ((n, c) in text) {
            val r = Contrast.ratio(c, bg)
            assertTrue("$name $n contrast %.2f is below WCAG AA 4.5".format(r), r >= Contrast.AA_NORMAL)
        }
    }

    @Test fun amoledPaletteMeetsAa() = check("AMOLED", AmoledPalette)
    @Test fun darkPaletteMeetsAa() = check("Dark", DarkPalette)
    @Test fun lightPaletteMeetsAa() = check("Light", LightPalette)

    @Test fun reducedMotion() {
        assertEquals(0, MotionTokens.scaled(220, 0f))
        assertEquals(0, MotionTokens.scaled(220, Float.NaN))
        assertEquals(220, MotionTokens.scaled(220, 1f))
        assertEquals(440, MotionTokens.scaled(220, 2f))
        assertEquals(1, MotionTokens.scaled(1, 0.1f))
    }
}
