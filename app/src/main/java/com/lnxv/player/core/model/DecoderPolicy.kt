package com.lnxv.player.core.model

import kotlin.math.abs

/**
 * Pure decoder-selection logic. Kept free of Media3 types so it can be unit tested on the JVM;
 * the player adapts `MediaCodecInfo` to these functions.
 */
object DecoderSelection {
    /**
     * Orders/filters decoder [candidates] for [mode].
     *  - AUTO: the platform order is kept (hardware first on virtually all devices).
     *  - HARDWARE: software decoders are removed. If nothing is left, playback fails honestly instead of stuttering.
     *  - SOFTWARE: software decoders first, hardware kept as a last resort so the video still plays.
     */
    fun <T> order(mode: DecoderMode, candidates: List<T>, isHardware: (T) -> Boolean): List<T> = when (mode) {
        DecoderMode.AUTO -> candidates
        DecoderMode.HARDWARE -> candidates.filter(isHardware)
        DecoderMode.SOFTWARE -> candidates.filterNot(isHardware) + candidates.filter(isHardware)
    }
}

/** One known-bad decoder. [hardware] is matched against `Build.HARDWARE` (case-insensitive) when not null. */
data class CodecQuirk(
    val decoderName: String,
    val mimeType: String? = null,
    val hardware: String? = null,
    val note: String = ""
)

/**
 * Table of decoders that must never be used. Entries are only added for combinations that were
 * reproduced on a real device (with the device and Android version in [CodecQuirk.note]).
 * The table intentionally starts empty: no entry is invented without a verified report.
 */
object CodecQuirks {
    val KNOWN: List<CodecQuirk> = emptyList()

    fun isBlocked(table: List<CodecQuirk>, buildHardware: String, decoderName: String, mimeType: String): Boolean =
        table.any { q ->
            decoderName.equals(q.decoderName, ignoreCase = true) &&
                (q.mimeType == null || q.mimeType.equals(mimeType, ignoreCase = true)) &&
                (q.hardware == null || q.hardware.equals(buildHardware, ignoreCase = true))
        }
}

object DecoderNames {
    private val SOFTWARE_PREFIXES = listOf("omx.google.", "c2.android.", "c2.google.", "omx.ffmpeg", "ffmpeg", "libdav1d", "dav1d")

    /** Name-based guess used only when the selector did not report a decoder's type. */
    fun looksSoftware(name: String): Boolean {
        val n = name.lowercase()
        return SOFTWARE_PREFIXES.any { n.startsWith(it) }
    }
}

/**
 * Decides what to do after a decoder error. Only in [DecoderMode.AUTO]: a decoder that fails to initialise, or
 * keeps failing, is retried once in software; after that the error is surfaced (no endless retry loops).
 * An explicit "Hardware only" / "Software preferred" choice is never overridden.
 */
class DecoderFallbackPolicy(
    private val decodeErrorThreshold: Int = 2,
    private val progressResetMs: Long = 5_000L
) {
    enum class Action { NONE, RETRY_SAME, RETRY_SOFTWARE, GIVE_UP }

    private var consecutiveDecodeErrors = 0
    private var lastErrorPositionMs = Long.MIN_VALUE
    private var softwareRetried = false

    val softwareFallbackUsed: Boolean get() = softwareRetried

    fun reset() {
        consecutiveDecodeErrors = 0
        lastErrorPositionMs = Long.MIN_VALUE
        softwareRetried = false
    }

    /** Decoder could not be created, or the format exceeds the decoder's capabilities. */
    fun onDecoderInitFailed(mode: DecoderMode): Action {
        if (softwareRetried || mode != DecoderMode.AUTO) return Action.GIVE_UP
        softwareRetried = true
        consecutiveDecodeErrors = 0
        return Action.RETRY_SOFTWARE
    }

    /** Decoder failed while decoding at [positionMs]. Errors far apart in playback time do not count as consecutive. */
    fun onDecodeError(mode: DecoderMode, positionMs: Long): Action {
        if (lastErrorPositionMs != Long.MIN_VALUE && abs(positionMs - lastErrorPositionMs) >= progressResetMs) {
            consecutiveDecodeErrors = 0
        }
        lastErrorPositionMs = positionMs
        consecutiveDecodeErrors++
        if (softwareRetried || mode != DecoderMode.AUTO) return Action.GIVE_UP
        if (consecutiveDecodeErrors < decodeErrorThreshold) return Action.RETRY_SAME
        softwareRetried = true
        consecutiveDecodeErrors = 0
        return Action.RETRY_SOFTWARE
    }
}
