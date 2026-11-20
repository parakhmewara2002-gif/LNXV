package com.lnxv.player.core.enhancer

enum class Tone { NEUTRAL, WARM, COOL }
enum class ColorBlindType { OFF, PROTANOPIA, DEUTERANOPIA, TRITANOPIA }
enum class ColorBlindMode { CORRECT, SIMULATE }

/** Number of samples of the unsharp-mask blur. 13 and 25 are only offered on high-tier devices. */
enum class SharpTaps(val taps: Int) { T9(9), T13(13), T25(25) }

/**
 * Everything the "Picture" sheet controls. Neutral values mean "no effect"; a neutral [PictureSettings] adds no GPU pass.
 * Gamma follows `out = in^(1/gamma)`, so gamma above 1 brightens mid-tones.
 */
data class PictureSettings(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val gamma: Float = 1f,
    val sharpness: Float = 0f,
    val sharpNoiseThreshold: Float = DEFAULT_NOISE_THRESHOLD,
    val sharpTaps: SharpTaps = SharpTaps.T9,
    val vivid: Float = 0f,
    val tone: Tone = Tone.NEUTRAL,
    val toneAmount: Float = 0.5f,
    val colorBlind: ColorBlindType = ColorBlindType.OFF,
    val colorBlindMode: ColorBlindMode = ColorBlindMode.CORRECT
) {
    val isNeutral: Boolean
        get() = brightness == 0f && contrast == 0f && saturation == 0f && gamma == 1f && sharpness == 0f && vivid == 0f &&
            tone == Tone.NEUTRAL && colorBlind == ColorBlindType.OFF

    /** Clamps every value into its supported range (also used on values read back from storage). */
    fun sanitized(): PictureSettings = copy(
        brightness = brightness.coerceIn(-BRIGHTNESS_LIMIT, BRIGHTNESS_LIMIT),
        contrast = contrast.coerceIn(-CONTRAST_LIMIT, CONTRAST_LIMIT),
        saturation = saturation.coerceIn(-1f, 1f),
        gamma = gamma.coerceIn(MIN_GAMMA, MAX_GAMMA),
        sharpness = sharpness.coerceIn(0f, MAX_SHARPNESS),
        sharpNoiseThreshold = sharpNoiseThreshold.coerceIn(0f, MAX_NOISE_THRESHOLD),
        vivid = vivid.coerceIn(0f, 1f),
        toneAmount = toneAmount.coerceIn(0f, 1f)
    )

    /** Compact text form for DataStore. Unknown or broken input decodes to defaults. */
    fun encode(): String = listOf(
        "v1", brightness, contrast, saturation, gamma, sharpness, sharpNoiseThreshold, sharpTaps.name, vivid, tone.name,
        toneAmount, colorBlind.name, colorBlindMode.name
    ).joinToString(";")

    companion object {
        const val DEFAULT_NOISE_THRESHOLD = 0.02f
        const val BRIGHTNESS_LIMIT = 0.5f
        const val CONTRAST_LIMIT = 0.5f
        const val MIN_GAMMA = 0.5f
        const val MAX_GAMMA = 2.0f
        const val MAX_SHARPNESS = 1.2f
        const val MAX_NOISE_THRESHOLD = 0.1f

        fun decode(text: String?): PictureSettings {
            val p = text?.split(';') ?: return PictureSettings()
            if (p.size != 13 || p[0] != "v1") return PictureSettings()
            return try {
                PictureSettings(
                    brightness = p[1].toFloat(), contrast = p[2].toFloat(), saturation = p[3].toFloat(), gamma = p[4].toFloat(),
                    sharpness = p[5].toFloat(), sharpNoiseThreshold = p[6].toFloat(), sharpTaps = SharpTaps.valueOf(p[7]),
                    vivid = p[8].toFloat(), tone = Tone.valueOf(p[9]), toneAmount = p[10].toFloat(),
                    colorBlind = ColorBlindType.valueOf(p[11]), colorBlindMode = ColorBlindMode.valueOf(p[12])
                ).sanitized()
            } catch (e: IllegalArgumentException) {
                PictureSettings()
            }
        }
    }
}

/** Built-in looks. Values are starting points chosen by reasoning, not tuned on real footage. */
enum class PicturePreset(val settings: PictureSettings) {
    OFF(PictureSettings()),
    CLEAR(PictureSettings(contrast = 0.05f, saturation = 0.05f, sharpness = 0.5f)),
    CINEMA(PictureSettings(contrast = 0.10f, saturation = -0.05f, gamma = 1.05f, sharpness = 0.2f, tone = Tone.WARM, toneAmount = 0.25f)),
    VIVID(PictureSettings(contrast = 0.08f, saturation = 0.15f, sharpness = 0.3f, vivid = 0.5f)),
    NIGHT(PictureSettings(brightness = 0.04f, gamma = 1.25f, vivid = 0.7f, tone = Tone.WARM, toneAmount = 0.3f));

    companion object {
        /** The preset whose settings equal [s] ignoring the tap count, or null for a custom mix. */
        fun matching(s: PictureSettings): PicturePreset? = values().firstOrNull { it.settings == s.copy(sharpTaps = SharpTaps.T9) }
    }
}
