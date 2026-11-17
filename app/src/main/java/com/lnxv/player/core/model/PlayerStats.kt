package com.lnxv.player.core.model

import androidx.compose.runtime.Immutable

/** Live numbers for the "Stats for nerds" panel. Null/unknown values are shown as "n/a". */
@Immutable
data class PlayerStats(
    val videoCodec: String? = null,
    val videoDecoder: String? = null,
    val videoIsSoftware: Boolean? = null,
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 0f,
    val videoBitrateBps: Int = 0,
    val droppedFrames: Long = 0L,
    val audioCodec: String? = null,
    val audioDecoder: String? = null,
    val audioIsSoftware: Boolean? = null,
    val audioBitrateBps: Int = 0,
    val decoderMode: DecoderMode = DecoderMode.AUTO,
    val softwareFallbackUsed: Boolean = false,
    val bufferedAheadMs: Long = 0L
)
