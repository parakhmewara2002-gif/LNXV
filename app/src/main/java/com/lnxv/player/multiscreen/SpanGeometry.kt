package com.lnxv.player.multiscreen

/** Rectangle in dp. */
data class DpRect(val left: Float, val top: Float, val width: Float, val height: Float)

/** Screen size in dp (landscape: width >= height). */
data class DpSize2(val w: Float, val h: Float)

/**
 * Pure geometry for Mirror and Span. Everything is in dp so that two phones with different densities
 * still agree on physical size (1 dp is about 1/160 inch).
 */
object SpanGeometry {
    /** Largest rect of [aspect] (w/h) that fits a [w] x [h] canvas, centred. */
    fun fit(w: Float, h: Float, aspect: Float): DpRect {
        if (w <= 0f || h <= 0f || aspect <= 0f) return DpRect(0f, 0f, w.coerceAtLeast(0f), h.coerceAtLeast(0f))
        return if (aspect >= w / h) {
            val vh = w / aspect
            DpRect(0f, (h - vh) / 2f, w, vh)
        } else {
            val vw = h * aspect
            DpRect((w - vw) / 2f, 0f, vw, h)
        }
    }

    /**
     * Where the WHOLE video must be drawn, in this device's own coordinates, so that this device shows its share
     * of a video fitted over both screens plus the bezel gap. The result may start at negative coordinates
     * (the part that belongs to the other phone); the host view clips to the screen.
     *
     * [first] = this device is the top phone (stacked) or the left phone (side by side).
     */
    fun span(self: DpSize2, other: DpSize2, stacked: Boolean, first: Boolean, bezelDp: Float, aspect: Float): DpRect {
        val a = if (first) self else other
        val b = if (first) other else self
        val gap = bezelDp.coerceAtLeast(0f)
        val canvasW: Float
        val canvasH: Float
        val ox: Float
        val oy: Float
        if (stacked) {
            canvasW = maxOf(a.w, b.w)
            canvasH = a.h + gap + b.h
            ox = (canvasW - self.w) / 2f
            oy = if (first) 0f else a.h + gap
        } else {
            canvasW = a.w + gap + b.w
            canvasH = maxOf(a.h, b.h)
            ox = if (first) 0f else a.w + gap
            oy = (canvasH - self.h) / 2f
        }
        val v = fit(canvasW, canvasH, aspect)
        return DpRect(v.left - ox, v.top - oy, v.width, v.height)
    }

    /** Mirror: the whole video fitted into this screen. */
    fun mirror(self: DpSize2, aspect: Float): DpRect = fit(self.w, self.h, aspect)
}
