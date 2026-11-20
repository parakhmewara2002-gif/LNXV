package com.lnxv.player.ui.player

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lnxv.player.R
import com.lnxv.player.core.design.GlassFilterChip
import com.lnxv.player.core.design.NeonCyan
import com.lnxv.player.core.model.EqPreset
import com.lnxv.player.core.model.OutputRoute

@Composable
private fun routeLabel(r: OutputRoute): String = when (r) {
    OutputRoute.SPEAKER -> stringResource(R.string.route_speaker)
    OutputRoute.WIRED -> stringResource(R.string.route_wired)
    OutputRoute.BLUETOOTH -> stringResource(R.string.route_bluetooth)
    OutputRoute.HDMI -> stringResource(R.string.route_hdmi)
    OutputRoute.OTHER -> stringResource(R.string.route_other)
}

@Composable
private fun FxSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text("${value / 10}%", color = NeonCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.toInt()) },
        valueRange = 0f..1000f,
        colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan),
        modifier = Modifier.semantics { contentDescription = label }
    )
}

/** Body of the "Audio effects" side panel. Settings are saved for the active output (speaker, headphones, Bluetooth, HDMI). */
@Composable
fun ColumnScope.AudioFxContent(
    preset: EqPreset,
    bass: Int,
    virtualizer: Int,
    route: OutputRoute,
    onPreset: (EqPreset) -> Unit,
    onBass: (Int) -> Unit,
    onVirtualizer: (Int) -> Unit
) {
    Text(
        stringResource(R.string.audio_fx_route, routeLabel(route)),
        color = Color(0xB3FFFFFF), fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp)
    )
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        EqPreset.values().forEach { p ->
            GlassFilterChip(selected = p == preset, label = p.label, onClick = { onPreset(p) })
            Spacer(Modifier.width(6.dp))
        }
    }
    Spacer(Modifier.padding(top = 8.dp))
    FxSlider(stringResource(R.string.audio_fx_bass), bass, onBass)
    FxSlider(stringResource(R.string.audio_fx_virtualizer), virtualizer, onVirtualizer)
    Text(
        stringResource(R.string.audio_fx_note),
        color = Color(0x99FFFFFF), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)
    )
}
