package com.lnxv.player.core.model

/**
 * Decides whether the dav1d software AV1 decoder may be used. Pure Kotlin so it is unit tested on the JVM.
 *
 * The software decoder is only ever reached when MediaCodec cannot handle the format (it is an extension renderer
 * that sits after the MediaCodec renderers). Beyond that it is gated by:
 *  - the decoder mode: "Hardware only" never uses software;
 *  - a resolution and frame-rate cap, so heavy streams fail with a clear message instead of stuttering.
 *
 * The cap values are conservative defaults chosen without device measurements. They are NOT benchmarked.
 */
object Av1SoftwarePolicy {
    enum class Verdict { ALLOWED, BLOCKED_BY_MODE, EXCEEDS_LIMIT }

    data class Limits(val maxLongSide: Int, val maxShortSide: Int, val maxFps: Float) {
        fun describe(): String = "up to ${maxLongSide}x$maxShortSide at ${maxFps.toInt()} fps"
    }

    /** 1080p30 for ordinary devices. */
    val NORMAL = Limits(maxLongSide = 1920, maxShortSide = 1088, maxFps = 30f)

    /** 720p30 for low-RAM devices and devices without a 64-bit ABI. */
    val LOW_END = Limits(maxLongSide = 1280, maxShortSide = 736, maxFps = 30f)

    /** NTSC-style rates (29.97, 30.03) and rounding must not trip a 30 fps cap. */
    private const val FPS_TOLERANCE = 1.04f

    fun limitsFor(lowEnd: Boolean): Limits = if (lowEnd) LOW_END else NORMAL

    /** Unknown dimensions or frame rate (<= 0, Media3 uses -1) are not treated as exceeding the cap. */
    fun decide(mode: DecoderMode, width: Int, height: Int, frameRate: Float, limits: Limits): Verdict {
        if (mode == DecoderMode.HARDWARE) return Verdict.BLOCKED_BY_MODE
        if (width > 0 && height > 0) {
            val longSide = maxOf(width, height)
            val shortSide = minOf(width, height)
            if (longSide > limits.maxLongSide || shortSide > limits.maxShortSide) return Verdict.EXCEEDS_LIMIT
        }
        if (frameRate > 0f && frameRate > limits.maxFps * FPS_TOLERANCE) return Verdict.EXCEEDS_LIMIT
        return Verdict.ALLOWED
    }

    fun describeVideo(width: Int, height: Int, frameRate: Float): String {
        val size = if (width > 0 && height > 0) "${width}x$height" else "unknown size"
        return if (frameRate > 0f) "$size @ ${"%.0f".format(frameRate)} fps" else size
    }
}
