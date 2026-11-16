package com.lnxv.player.core.design

import androidx.compose.foundation.background
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.getValue
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
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
        shape = RoundedCornerShape(22.dp),
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
                contentDescription = androidx.compose.ui.res.stringResource(com.lnxv.player.R.string.cd_back),
                onClick = onBack
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Text(
            text = title,
            color = appColors.textPrimary,
            style = Typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.35).sp
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

/** Fully rounded translucent pill that groups icon buttons (iOS grouped-control look). Pass [hazeState] to blur what is behind it. */
@Composable
fun IosPill(
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    content: @Composable RowScope.() -> Unit
) {
    val blurred = hazeState != null && !LocalReduceEffects.current
    Row(
        modifier = modifier
            .glassBlur(hazeState, CircleShape)
            .liquidGlassSurface(
                shape = CircleShape,
                backgroundColor = if (blurred) appColors.barFill.copy(alpha = 0.55f) else appColors.glassFill,
                borderColor = appColors.glassBorder
            ),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** Icon button meant to sit inside an [IosPill]. */
@Composable
fun IosPillIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp = 42.dp,
    tint: Color = appColors.textPrimary
) {
    Box(
        modifier = Modifier
            .size(size)
            .bouncyClickable(pressedScale = 0.88f) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Floating glass tab bar (iOS style): equal-width labels, selected one gets an accent capsule. */
@Composable
fun IosTabBar(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null
) {
    val blurred = hazeState != null && !LocalReduceEffects.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassBlur(hazeState, CircleShape)
            .liquidGlassSurface(
                shape = CircleShape,
                backgroundColor = if (blurred) appColors.barFill.copy(alpha = 0.6f) else appColors.barFill,
                borderColor = appColors.glassBorder
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val bg by animateColorAsState(
                targetValue = if (selected) appColors.accent.copy(alpha = 0.22f) else Color.Transparent,
                animationSpec = tween(200),
                label = "tab_bg"
            )
            val fg by animateColorAsState(
                targetValue = if (selected) appColors.accent else appColors.textSecondary,
                animationSpec = tween(200),
                label = "tab_fg"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = fg,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

/** iOS-style alert: centered frosted card with hairline border, title, body and a trailing action row. Drop-in for AlertDialog. */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest) {
        val shape = RoundedCornerShape(28.dp)
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .liquidGlassSurface(
                    shape = shape,
                    backgroundColor = appColors.barFill.copy(alpha = 0.96f),
                    borderColor = appColors.glassBorder
                )
                .padding(start = 22.dp, end = 14.dp, top = 22.dp, bottom = 10.dp)
        ) {
            if (title != null) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides appColors.textPrimary
                ) {
                    androidx.compose.material3.ProvideTextStyle(
                        Typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    ) { Box(Modifier.padding(end = 8.dp)) { title() } }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (text != null) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides appColors.textSecondary
                ) {
                    androidx.compose.material3.ProvideTextStyle(Typography.bodyMedium) {
                        Box(Modifier.padding(end = 8.dp)) { text() }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}

/** Rounded frosted dropdown menu with a hairline border. */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        containerColor = appColors.barFill.copy(alpha = 0.97f),
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, appColors.glassBorder),
        content = content
    )
}

/** iOS-style switch: white thumb, accent track when on. */
@Composable
fun GlassSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = androidx.compose.material3.SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = appColors.accent,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = appColors.glassBorder,
            uncheckedBorderColor = Color.Transparent
        )
    )
}

/** Thin rounded progress bar. */
@Composable
fun GlassProgress(progress: () -> Float, modifier: Modifier = Modifier) {
    androidx.compose.material3.LinearProgressIndicator(
        progress = progress,
        modifier = modifier.height(6.dp).clip(CircleShape),
        color = appColors.accent,
        trackColor = appColors.glassBorder,
        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}
