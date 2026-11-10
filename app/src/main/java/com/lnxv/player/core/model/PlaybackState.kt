package com.lnxv.player.core.model

import androidx.compose.ui.graphics.Color

enum class AspectRatioMode(val displayName: String) {
    FIT("Fit to Screen"),
    ORIGINAL("Original 100%"),
    FILL("Fill / Crop"),
    STRETCH("Stretch"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3"),
    RATIO_21_9("21:9"),
    RATIO_18_9("18:9"),
    RATIO_235_1("2.35:1")
}

enum class DecoderMode(val displayName: String) {
    HARDWARE("Hardware (HW)"),
    SOFTWARE("Software (SW)")
}

data class SubtitleStyleConfig(
    val textSizeSp: Float = 16f,
    val textColor: Color = Color.White,
    val backgroundColor: Color = Color(0x80000000),
    val outlineColor: Color = Color.Black,
    val outlineWidth: Float = 2f,
    val verticalOffsetDp: Float = 24f,
    val delayMs: Long = 0L
)

data class AudioTrackInfo(
    val id: String,
    val language: String,
    val label: String,
    val channelCount: Int,
    val sampleRate: Int,
    val isSelected: Boolean = false
)

data class SubtitleTrackInfo(
    val id: String,
    val language: String,
    val label: String,
    val isExternal: Boolean = false,
    val isSelected: Boolean = false
)

data class ABRepeatState(
    val isEnabled: Boolean = false,
    val pointA: Long? = null,
    val pointB: Long? = null
)

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val isBuffering: Boolean = false,
    val isEnded: Boolean = false,
    val volume: Float = 1.0f,
    val aspectRatio: AspectRatioMode = AspectRatioMode.FIT,
    val decoderMode: DecoderMode = DecoderMode.HARDWARE,
    val isScreenLocked: Boolean = false,
    val abRepeat: ABRepeatState = ABRepeatState(),
    val sleepTimerRemainingSeconds: Long? = null
) {
    val progress: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}
