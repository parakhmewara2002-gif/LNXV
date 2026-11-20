package com.lnxv.player.core.model

/** Where audio is currently going. Equalizer settings are remembered per route (speaker sounds different from headphones). */
enum class OutputRoute { SPEAKER, WIRED, BLUETOOTH, HDMI, OTHER }

/** Picks the active [OutputRoute] from android.media.AudioDeviceInfo type codes (plain ints so it is unit tested). */
object OutputRouteClassifier {
    private val bluetooth = setOf(7, 8, 23, 26, 27, 30) // SCO, A2DP, hearing aid, BLE headset/speaker/broadcast
    private val wired = setOf(3, 4, 5, 11, 22) // headset, headphones, line analog, USB device, USB headset
    private val hdmi = setOf(9, 10, 29) // HDMI, HDMI ARC, HDMI eARC
    private val speaker = setOf(1, 2, 13) // earpiece, built-in speaker, dock

    fun classify(outputTypes: Collection<Int>): OutputRoute = when {
        outputTypes.any { it in bluetooth } -> OutputRoute.BLUETOOTH
        outputTypes.any { it in wired } -> OutputRoute.WIRED
        outputTypes.any { it in hdmi } -> OutputRoute.HDMI
        outputTypes.any { it in speaker } -> OutputRoute.SPEAKER
        else -> OutputRoute.OTHER
    }
}

/** Equalizer preset plus Bass Boost and Virtualizer strengths (0..1000, the Android effect range). */
data class AudioFxSettings(
    val preset: EqPreset = EqPreset.FLAT,
    val bass: Int = 0,
    val virtualizer: Int = 0
) {
    fun sanitized() = copy(bass = bass.coerceIn(0, MAX), virtualizer = virtualizer.coerceIn(0, MAX))

    val isDefault: Boolean get() = this == AudioFxSettings()

    fun encode(): String = with(sanitized()) { "${preset.name}|$bass|$virtualizer" }

    companion object {
        const val MAX = 1000

        fun decode(raw: String?): AudioFxSettings {
            if (raw.isNullOrBlank()) return AudioFxSettings()
            val p = raw.split('|')
            val preset = EqPreset.values().firstOrNull { it.name == p.getOrNull(0) } ?: EqPreset.FLAT
            return AudioFxSettings(preset, p.getOrNull(1)?.toIntOrNull() ?: 0, p.getOrNull(2)?.toIntOrNull() ?: 0).sanitized()
        }
    }
}
