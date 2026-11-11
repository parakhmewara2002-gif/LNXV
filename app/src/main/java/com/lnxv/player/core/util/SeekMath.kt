package com.lnxv.player.core.util

/**
 * Pure seek/timeline math (no Android dependencies) so it can be unit-tested.
 *
 * IMPORTANT: ExoPlayer reports an unknown duration as C.TIME_UNSET (a huge negative number).
 * Clamping against `duration.coerceAtLeast(0)` turns that into 0 and makes EVERY seek jump
 * to the start of the video. [clamp] treats an unknown duration as "no upper bound" instead.
 */
object SeekMath {

    fun isDurationKnown(durationMs: Long): Boolean = durationMs > 0L

    fun clamp(positionMs: Long, durationMs: Long): Long {
        val upper = if (isDurationKnown(durationMs)) durationMs else Long.MAX_VALUE
        return positionMs.coerceIn(0L, upper)
    }

    fun relativeTarget(currentMs: Long, deltaMs: Long, durationMs: Long): Long =
        clamp(currentMs.coerceAtLeast(0L) + deltaMs, durationMs)

    fun fractionToPosition(fraction: Float, durationMs: Long): Long {
        if (!isDurationKnown(durationMs)) return 0L
        return (fraction.coerceIn(0f, 1f).toDouble() * durationMs).toLong()
    }

    fun positionToFraction(positionMs: Long, durationMs: Long): Float {
        if (!isDurationKnown(durationMs)) return 0f
        return (positionMs.toDouble() / durationMs).toFloat().coerceIn(0f, 1f)
    }

    /** Horizontal swipe: a drag across the full width seeks [fullWidthSpanMs]. */
    fun dragTarget(
        startMs: Long,
        dragPx: Float,
        widthPx: Float,
        durationMs: Long,
        fullWidthSpanMs: Long = 90_000L
    ): Long {
        val delta = (dragPx / widthPx.coerceAtLeast(1f) * fullWidthSpanMs).toLong()
        return clamp(startMs + delta, durationMs)
    }
}
