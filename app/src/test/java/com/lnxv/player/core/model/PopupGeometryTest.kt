package com.lnxv.player.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PopupGeometryTest {
    @Test fun keepsAspectRatio() {
        assertEquals(640 to 360, PopupGeometry.size(1920, 1080, 640, 2000))
        assertEquals(400 to 400, PopupGeometry.size(1000, 1000, 400, 2000))
    }

    @Test fun unknownVideoSizeIsSixteenNine() {
        assertEquals(320 to 180, PopupGeometry.size(0, 0, 320, 2000))
    }

    @Test fun tallVideoIsCappedByMaxHeight() {
        val (w, h) = PopupGeometry.size(1080, 1920, 600, 500)
        assertEquals(500, h)
        assertEquals(281, w)
    }

    @Test fun minimumWidth() {
        assertEquals(160 to 90, PopupGeometry.size(1920, 1080, 10, 2000))
    }

    @Test fun clampKeepsWindowOnScreen() {
        assertEquals(0 to 0, PopupGeometry.clamp(-50, -9, 200, 100, 1080, 1920))
        assertEquals(880 to 1820, PopupGeometry.clamp(5000, 5000, 200, 100, 1080, 1920))
        assertEquals(100 to 200, PopupGeometry.clamp(100, 200, 200, 100, 1080, 1920))
        assertEquals(0 to 0, PopupGeometry.clamp(10, 10, 2000, 3000, 1080, 1920))
    }
}
