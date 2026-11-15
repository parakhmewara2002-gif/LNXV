package com.lnxv.player.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild

/**
 * Shared "glass" building blocks. Every blur goes through [glassBlur] so the app has ONE place that honours
 * Settings > Appearance & performance (LocalReduceEffects): when effects are reduced (or no backdrop source
 * was provided) the surface falls back to a flat translucent fill, never to a blur.
 */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/** Blurs whatever the given [state] source (Modifier.haze) draws behind this element. No-op when effects are reduced. */
@Composable
fun Modifier.glassBlur(state: HazeState?, shape: Shape): Modifier =
    if (state == null || LocalReduceEffects.current) this else this.clip(shape).hazeChild(state = state)

/** Translucent glass container. Blurs the backdrop when a [hazeState] is available and effects are not reduced. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    hazeState: HazeState? = LocalHazeState.current,
    tint: Color = appColors.glassFill,
    content: @Composable BoxScope.() -> Unit
) {
    val reduce = LocalReduceEffects.current
    val blurred = !reduce && hazeState != null
    Box(
        modifier = modifier
            .glassBlur(hazeState, shape)
            .liquidGlassSurface(
                shape = shape,
                backgroundColor = if (blurred) tint.copy(alpha = (tint.alpha * 0.9f).coerceIn(0.12f, 0.6f)) else appColors.barFill,
                borderColor = appColors.glassBorder
            ),
        content = content
    )
}

/** iOS-style grouped list container: one rounded glass card holding several rows separated by hairlines. */
@Composable
fun GlassGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassPanel(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        backgroundColor = appColors.glassFill
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), content = content)
    }
}

/** Thin separator between rows inside a [GlassGroup]. */
@Composable
fun GlassDivider() {
    Spacer(modifier = Modifier.height(12.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(appColors.glassBorder.copy(alpha = 0.5f))
    )
    Spacer(modifier = Modifier.height(12.dp))
}

/** Large-title top bar that blurs the list scrolling underneath it (pass the same [hazeState] given to Modifier.haze). */
@Composable
fun GlassTopBar(
    title: String,
    onBack: (() -> Unit)?,
    hazeState: HazeState?,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    val reduce = LocalReduceEffects.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassBlur(hazeState, shape)
            .liquidGlassSurface(
                shape = shape,
                backgroundColor = if (reduce || hazeState == null) appColors.barFill else appColors.barFill.copy(alpha = 0.45f),
                borderColor = appColors.glassBorder
            )
            .padding(top = 40.dp, bottom = 14.dp, start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            GlassIconButton(
                icon = Icons.Default.ArrowBack,
                contentDescription = "Back",
                onClick = onBack
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Text(
            text = title,
            color = appColors.textPrimary,
            style = Typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Single-line glass text field with a placeholder (used for in-screen search). */
@Composable
fun GlassSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    GlassPanel(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = appColors.glassSubtle
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = appColors.textPrimary, fontSize = 15.sp),
            cursorBrush = SolidColor(appColors.accent),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, color = appColors.textTertiary, fontSize = 15.sp)
                    inner()
                }
            }
        )
    }
}

/** Read-only "label … value" row for info lists. */
@Composable
fun GlassInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = appColors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(end = 12.dp))
        Text(value, color = appColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
