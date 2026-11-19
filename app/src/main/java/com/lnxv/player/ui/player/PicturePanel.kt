package com.lnxv.player.ui.player

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lnxv.player.R
import com.lnxv.player.core.design.GlassFilterChip
import com.lnxv.player.core.design.NeonCyan
import com.lnxv.player.core.enhancer.ColorBlindMode
import com.lnxv.player.core.enhancer.ColorBlindType
import com.lnxv.player.core.enhancer.PictureDisabledReason
import com.lnxv.player.core.enhancer.PicturePreset
import com.lnxv.player.core.enhancer.PictureSettings
import com.lnxv.player.core.enhancer.SharpTaps
import com.lnxv.player.core.enhancer.Tone

@Composable
private fun presetLabel(p: PicturePreset): String = when (p) {
    PicturePreset.OFF -> stringResource(R.string.picture_preset_off)
    PicturePreset.CLEAR -> stringResource(R.string.picture_preset_clear)
    PicturePreset.CINEMA -> stringResource(R.string.picture_preset_cinema)
    PicturePreset.VIVID -> stringResource(R.string.picture_preset_vivid)
    PicturePreset.NIGHT -> stringResource(R.string.picture_preset_night)
}

@Composable
private fun reasonText(r: PictureDisabledReason): String = when (r) {
    PictureDisabledReason.HDR_SOURCE -> stringResource(R.string.picture_off_hdr)
    PictureDisabledReason.PROTECTED_CONTENT -> stringResource(R.string.picture_off_protected)
    PictureDisabledReason.LOW_END_DEVICE -> stringResource(R.string.picture_off_low_end)
    PictureDisabledReason.POWER_SAVE -> stringResource(R.string.picture_off_power_save)
}

@Composable
private fun PictureSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, valueText: String, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(valueText, color = NeonCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
    Slider(
        value = value.coerceIn(range.start, range.endInclusive),
        onValueChange = onChange,
        valueRange = range,
        colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan),
        modifier = Modifier.semantics { contentDescription = label }
    )
}

@Composable
private fun <T> ChipRow(items: List<T>, isSelected: (T) -> Boolean, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        items.forEach { item ->
            GlassFilterChip(selected = isSelected(item), label = label(item), onClick = { onSelect(item) })
            Spacer(Modifier.width(6.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Color(0xB3FFFFFF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
}

/** Body of the "Picture" side panel. Every change is applied immediately; hold "Compare" to see the original. */
@Composable
fun ColumnScope.PictureContent(
    settings: PictureSettings,
    disabledReason: PictureDisabledReason?,
    highTier: Boolean,
    canRememberForVideo: Boolean,
    onChange: (PictureSettings) -> Unit,
    onCompare: (Boolean) -> Unit,
    onSaveDefault: () -> Unit,
    onSaveVideo: () -> Unit,
    onClearVideo: () -> Unit
) {
    if (disabledReason != null) {
        Text(
            stringResource(R.string.picture_off_prefix) + " " + reasonText(disabledReason),
            color = Color(0xFFFFB74D), fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)
        )
    }

    SectionLabel(stringResource(R.string.picture_presets))
    ChipRow(
        items = PicturePreset.values().toList(),
        isSelected = { PicturePreset.matching(settings) == it },
        label = { presetLabel(it) },
        onSelect = { onChange(it.settings.copy(sharpTaps = settings.sharpTaps)) }
    )

    // Hold to compare: while the finger is down the effect is bypassed (no player rebuild, just the effect chain).
    val compareLabel = stringResource(R.string.picture_compare)
    Box(
        modifier = Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NeonCyan.copy(alpha = 0.18f))
            .semantics { contentDescription = compareLabel }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    onCompare(true)
                    try { tryAwaitRelease() } finally { onCompare(false) }
                })
            }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(compareLabel, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }

    SectionLabel(stringResource(R.string.picture_detail))
    PictureSlider(stringResource(R.string.picture_sharpness), settings.sharpness, 0f..PictureSettings.MAX_SHARPNESS, "%.0f%%".format(settings.sharpness / PictureSettings.MAX_SHARPNESS * 100)) {
        onChange(settings.copy(sharpness = it))
    }
    PictureSlider(stringResource(R.string.picture_noise_threshold), settings.sharpNoiseThreshold, 0f..PictureSettings.MAX_NOISE_THRESHOLD, "%.3f".format(settings.sharpNoiseThreshold)) {
        onChange(settings.copy(sharpNoiseThreshold = it))
    }
    if (highTier) {
        ChipRow(
            items = SharpTaps.values().toList(),
            isSelected = { it == settings.sharpTaps },
            label = { stringResource(R.string.picture_taps, it.taps) },
            onSelect = { onChange(settings.copy(sharpTaps = it)) }
        )
    }
    PictureSlider(stringResource(R.string.picture_vivid), settings.vivid, 0f..1f, "%.0f%%".format(settings.vivid * 100)) {
        onChange(settings.copy(vivid = it))
    }
    Text(stringResource(R.string.picture_vivid_note), color = Color(0x80FFFFFF), fontSize = 11.sp)

    SectionLabel(stringResource(R.string.picture_light_colour))
    val lim = PictureSettings.BRIGHTNESS_LIMIT
    PictureSlider(stringResource(R.string.picture_brightness), settings.brightness, -lim..lim, "%+.0f%%".format(settings.brightness * 100)) {
        onChange(settings.copy(brightness = it))
    }
    PictureSlider(stringResource(R.string.picture_contrast), settings.contrast, -PictureSettings.CONTRAST_LIMIT..PictureSettings.CONTRAST_LIMIT, "%+.0f%%".format(settings.contrast * 100)) {
        onChange(settings.copy(contrast = it))
    }
    PictureSlider(stringResource(R.string.picture_saturation), settings.saturation, -1f..1f, "%+.0f%%".format(settings.saturation * 100)) {
        onChange(settings.copy(saturation = it))
    }
    PictureSlider(stringResource(R.string.picture_gamma), settings.gamma, PictureSettings.MIN_GAMMA..PictureSettings.MAX_GAMMA, "%.2f".format(settings.gamma)) {
        onChange(settings.copy(gamma = it))
    }

    SectionLabel(stringResource(R.string.picture_tone))
    ChipRow(
        items = Tone.values().toList(),
        isSelected = { it == settings.tone },
        label = {
            when (it) {
                Tone.NEUTRAL -> stringResource(R.string.picture_tone_neutral)
                Tone.WARM -> stringResource(R.string.picture_tone_warm)
                Tone.COOL -> stringResource(R.string.picture_tone_cool)
            }
        },
        onSelect = { onChange(settings.copy(tone = it)) }
    )
    if (settings.tone != Tone.NEUTRAL) {
        PictureSlider(stringResource(R.string.picture_tone_strength), settings.toneAmount, 0f..1f, "%.0f%%".format(settings.toneAmount * 100)) {
            onChange(settings.copy(toneAmount = it))
        }
    }

    SectionLabel(stringResource(R.string.picture_colour_blind))
    ChipRow(
        items = ColorBlindType.values().toList(),
        isSelected = { it == settings.colorBlind },
        label = {
            when (it) {
                ColorBlindType.OFF -> stringResource(R.string.picture_cb_off)
                ColorBlindType.PROTANOPIA -> stringResource(R.string.picture_cb_protan)
                ColorBlindType.DEUTERANOPIA -> stringResource(R.string.picture_cb_deutan)
                ColorBlindType.TRITANOPIA -> stringResource(R.string.picture_cb_tritan)
            }
        },
        onSelect = { onChange(settings.copy(colorBlind = it)) }
    )
    if (settings.colorBlind != ColorBlindType.OFF) {
        Spacer(Modifier.height(6.dp))
        ChipRow(
            items = ColorBlindMode.values().toList(),
            isSelected = { it == settings.colorBlindMode },
            label = { if (it == ColorBlindMode.CORRECT) stringResource(R.string.picture_cb_correct) else stringResource(R.string.picture_cb_simulate) },
            onSelect = { onChange(settings.copy(colorBlindMode = it)) }
        )
    }

    Spacer(Modifier.height(12.dp))
    PanelOptionRow(label = stringResource(R.string.picture_reset), selected = false, onClick = { onChange(PictureSettings(sharpTaps = settings.sharpTaps)) })
    PanelOptionRow(label = stringResource(R.string.picture_save_default), selected = false, onClick = onSaveDefault)
    if (canRememberForVideo) {
        PanelOptionRow(label = stringResource(R.string.picture_save_video), selected = false, onClick = onSaveVideo)
        PanelOptionRow(label = stringResource(R.string.picture_clear_video), selected = false, onClick = onClearVideo)
    }
}
