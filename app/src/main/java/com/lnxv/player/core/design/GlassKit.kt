package com.lnxv.player.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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

/** iOS-style search field: rounded 18dp glass, search icon on the left, clear button on the right when text is non-empty. */
@Composable
fun GlassSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    GlassPanel(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = appColors.glassSubtle
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = appColors.textTertiary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = appColors.textPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(appColors.accent),
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) Text(placeholder, color = appColors.textTertiary, fontSize = 15.sp, maxLines = 1)
                        inner()
                    }
                }
            )
            if (value.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onValueChange("") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = appColors.textTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

/**
 * Glass text field for dialogs and forms: rounded 16dp, [glassSubtle] fill, hairline border, accent cursor,
 * placeholder, optional error text, optional keyboard options / visual transformation (e.g. PIN).
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    errorText: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None
) {
    val error = errorText != null
    val shape = RoundedCornerShape(16.dp)
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlassSurface(
                    shape = shape,
                    backgroundColor = appColors.glassSubtle,
                    borderColor = if (error) DestructiveRed.copy(alpha = 0.7f) else appColors.glassBorder
                )
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = TextStyle(color = appColors.textPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(appColors.accent),
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(placeholder, color = appColors.textTertiary, fontSize = 15.sp, maxLines = 1)
                        }
                        inner()
                    }
                }
            )
        }
        if (errorText != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(errorText, color = DestructiveRed, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
        }
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
    hazeState: HazeState? = null,
    /** True when something else (the AGSL lens) already frosts what is behind the bar, so the fill can be translucent. */
    backdropHandled: Boolean = false
) {
    val blurred = backdropHandled || (hazeState != null && !LocalReduceEffects.current)
    val tap = rememberTapHaptic()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .glassBlur(hazeState, CircleShape)
            .liquidGlassSurface(
                shape = CircleShape,
                backgroundColor = if (backdropHandled) appColors.barFill.copy(alpha = 0.32f) else if (blurred) appColors.barFill.copy(alpha = 0.6f) else appColors.barFill,
                borderColor = appColors.glassBorder
            )
            .padding(4.dp)
    ) {
        if (labels.isNotEmpty()) {
            val reduceMotion = LocalReduceMotion.current || LocalReduceEffects.current
            val segment = maxWidth / labels.size
            val pillIndex by animateFloatAsState(
                targetValue = selectedIndex.coerceIn(0, labels.lastIndex).toFloat(),
                animationSpec = if (reduceMotion) androidx.compose.animation.core.snap()
                    else spring(dampingRatio = 0.9f, stiffness = 480f),
                label = "tab_pill"
            )
            // One pill slides between segments instead of the fill cross-fading per segment.
            Box(modifier = Modifier.matchParentSize()) {
                Box(
                    modifier = Modifier
                        .offset(x = segment * pillIndex)
                        .fillMaxWidth(1f / labels.size)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(appColors.accent.copy(alpha = 0.22f))
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val reduceMotion = LocalReduceMotion.current || LocalReduceEffects.current
            val fg by animateColorAsState(
                targetValue = if (selected) appColors.accent else appColors.textSecondary,
                animationSpec = if (reduceMotion) androidx.compose.animation.core.snap() else tween(MotionTokens.FAST_MS),
                label = "tab_fg"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .clickable { if (!selected) tap(); onSelect(index) }
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
}

/** iOS system red used for destructive dialog actions. */
val DestructiveRed = Color(0xFFFF453A)

/**
 * Standard dialog action. Plain (default) = text only; [primary] = accent capsule; [destructive] = red
 * (text, or red capsule when also [primary]). Use inside GlassDialog's confirmButton/dismissButton slots.
 */
@Composable
fun GlassDialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    destructive: Boolean = false
) {
    val base = if (destructive) DestructiveRed else appColors.accent
    val fill = when {
        !enabled -> Color.Transparent
        primary -> base.copy(alpha = 0.22f)
        else -> Color.Transparent
    }
    val fg = when {
        !enabled -> appColors.textTertiary
        primary || destructive -> base
        else -> appColors.textSecondary
    }
    Box(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .clip(CircleShape)
            .background(fill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 14.sp,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
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
        val reduceFx = LocalReduceEffects.current || LocalReduceMotion.current
        // A constant targetValue never animated: dialogs used to snap open. Drive a real 0 -> 1 entrance instead.
        val enter = remember { androidx.compose.animation.core.Animatable(if (reduceFx) 1f else 0f) }
        LaunchedEffect(reduceFx) {
            if (reduceFx) enter.snapTo(1f) else enter.animateTo(1f, MotionTokens.SpringFluid)
        }
        Column(
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer {
                    // Draw-phase read: the spring animates without recomposing the dialog content.
                    val p = if (reduceFx) 1f else enter.value
                    val sc = 0.92f + 0.08f * p
                    scaleX = sc
                    scaleY = sc
                    alpha = p.coerceIn(0f, 1f)
                }
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

/** Light tap haptic (no-op friendly). Use for tab changes, switch toggles, keypad and selection. */
@Composable
fun rememberTapHaptic(): () -> Unit {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    return remember(haptic) {
        { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) }
    }
}

/** iOS segmented control: capsule track, selected segment gets an accent fill. */
@Composable
fun GlassSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tap = rememberTapHaptic()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(appColors.glassSubtle)
            .border(1.dp, appColors.glassBorder, CircleShape)
            .padding(3.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val bg by animateColorAsState(
                targetValue = if (selected) appColors.accent.copy(alpha = 0.28f) else Color.Transparent,
                animationSpec = tween(200),
                label = "segment_bg"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable { if (!selected) tap(); onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (selected) appColors.accent else appColors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

/** Small capsule with two buttons ("−" and "+") used for numeric steppers. */
@Composable
fun GlassStepper(onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    val tap = rememberTapHaptic()
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(appColors.glassSubtle)
            .border(1.dp, appColors.glassBorder, CircleShape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(width = 44.dp, height = 34.dp).clickable { tap(); onMinus() },
            contentAlignment = Alignment.Center
        ) { Text("−", color = appColors.accent, fontSize = 20.sp, fontWeight = FontWeight.Medium) }
        Box(modifier = Modifier.width(1.dp).height(18.dp).background(appColors.glassBorder))
        Box(
            modifier = Modifier.size(width = 44.dp, height = 34.dp).clickable { tap(); onPlus() },
            contentAlignment = Alignment.Center
        ) { Text("+", color = appColors.accent, fontSize = 20.sp, fontWeight = FontWeight.Medium) }
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
    val tap = rememberTapHaptic()
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = onCheckedChange?.let { cb -> { v: Boolean -> tap(); cb(v) } },
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
fun GlassProgress(progress: () -> Float, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 6.dp) {
    androidx.compose.material3.LinearProgressIndicator(
        progress = progress,
        modifier = modifier.height(height).clip(CircleShape),
        color = appColors.accent,
        trackColor = appColors.glassBorder,
        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}

/** Glass capsule filter chip (replaces Material FilterChip): animated tint, spring press, selected semantics. */
@Composable
fun GlassFilterChip(selected: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(
        targetValue = if (selected) appColors.accent.copy(alpha = 0.28f) else appColors.glassFill,
        animationSpec = tween(200),
        label = "chip_bg"
    )
    Box(
        modifier = modifier
            .bouncyClickable(pressedScale = 0.94f) { onClick() }
            .liquidGlassSurface(
                shape = CircleShape,
                backgroundColor = bg,
                borderColor = if (selected) appColors.accent else appColors.glassBorder
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = label,
            color = if (selected) appColors.accent else appColors.textPrimary,
            style = Typography.labelLarge
        )
    }
}
