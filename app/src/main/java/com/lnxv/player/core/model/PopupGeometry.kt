package com.lnxv.player.core.model

/** Pure sizing and positioning maths for the floating popup player. */
object PopupGeometry {
    const val MIN_WIDTH_PX = 160

    /** Window size for a video of [videoW] x [videoH] (falls back to 16:9), [widthPx] wide, never taller than [maxHeightPx]. */
    fun size(videoW: Int, videoH: Int, widthPx: Int, maxHeightPx: Int): Pair<Int, Int> {
        val w0 = widthPx.coerceAtLeast(MIN_WIDTH_PX)
        val ratio = if (videoW > 0 && videoH > 0) videoW.toFloat() / videoH else 16f / 9f
        var w = w0
        var h = (w / ratio).toInt().coerceAtLeast(1)
        if (h > maxHeightPx) {
            h = maxHeightPx.coerceAtLeast(1)
            w = (h * ratio).toInt().coerceAtLeast(1)
        }
        return w to h
    }

    /** Keeps the whole window on screen. */
    fun clamp(x: Int, y: Int, w: Int, h: Int, screenW: Int, screenH: Int): Pair<Int, Int> =
        x.coerceIn(0, (screenW - w).coerceAtLeast(0)) to y.coerceIn(0, (screenH - h).coerceAtLeast(0))
}
