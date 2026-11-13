package com.lnxv.player.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lnxv.player.core.design.NeonCyan
import com.lnxv.player.core.design.Typography
import com.lnxv.player.core.design.bouncyClickable
import com.lnxv.player.core.model.AspectRatioMode
import com.lnxv.player.core.model.AudioTrackInfo
import com.lnxv.player.core.model.SubtitleTrackInfo
import com.lnxv.player.core.model.VideoItem

private val PanelTop = Color(0xF2202A3E)
private val PanelBottom = Color(0xF20D121B)
private val RowIdle = Color(0x0DFFFFFF)
private val RowIdleBorder = Color(0x14FFFFFF)

/** Where a panel sits on screen, so the blurred video behind it lines up like real glass. */
private class BackdropGeometry(val screenW: Dp, val screenH: Dp, val originX: Dp, val originY: Dp)

/**
 * Frosted-glass layers: a heavily blurred snapshot of the video (tiny bitmap stretched with
 * bilinear filtering = soft blur on every Android version), a dark tint, and a specular highlight.
 */
@Composable
private fun BoxScope.GlassLayers(backdrop: ImageBitmap?, geometry: BackdropGeometry?) {
    val backdropAlpha by animateFloatAsState(
        targetValue = if (backdrop != null) 1f else 0f,
        animationSpec = tween(260),
        label = "glass_backdrop"
    )
    if (backdrop != null) {
        val imageModifier = if (geometry != null) {
            Modifier
                .requiredSize(geometry.screenW, geometry.screenH)
                .offset(x = -geometry.originX, y = -geometry.originY)
        } else {
            Modifier.matchParentSize()
        }
        Image(
            bitmap = backdrop,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            filterQuality = FilterQuality.High,
            modifier = imageModifier.graphicsLayer { alpha = backdropAlpha }
        )
    }
    // frost / tint (lighter than before so the movie colours glow through)
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(Brush.verticalGradient(listOf(Color(0x8C161E30), Color(0xB30A0E17))))
    )
    // specular highlight from the top-left corner
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0x2EFFFFFF), Color.Transparent, Color(0x0FFFFFFF))
                )
            )
    )
}

private val GlassBorder = Brush.linearGradient(listOf(Color(0x66FFFFFF), Color(0x14FFFFFF), Color(0x33FFFFFF)))

/**
 * Side panel (landscape) / bottom panel (portrait) drawn INSIDE the player instead of in a separate
 * dialog window: it is scrollable (nothing gets cut off), and it never makes Android bring the
 * system bars back, so fullscreen stays intact.
 */
@Composable
fun BoxScope.PlayerSidePanel(
    visible: Boolean,
    title: String,
    icon: ImageVector,
    onDismiss: () -> Unit,
    backdrop: ImageBitmap? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(enabled = visible) { onDismiss() }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(160)),
        modifier = Modifier.matchParentSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
    }

    BoxWithConstraints(modifier = Modifier.matchParentSize()) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            val panelWidth = 380.dp.coerceAtMost(maxWidth * 0.6f)
            val shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
            AnimatedVisibility(
                visible = visible,
                enter = slideInHorizontally(
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)
                ) { it } + fadeIn(tween(180)),
                exit = slideOutHorizontally(animationSpec = tween(200)) { it } + fadeOut(tween(160)),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                PanelSurface(
                    modifier = Modifier.fillMaxHeight().width(panelWidth),
                    shape = shape,
                    title = title,
                    icon = icon,
                    onDismiss = onDismiss,
                    backdrop = backdrop,
                    geometry = BackdropGeometry(maxWidth, maxHeight, maxWidth - panelWidth, 0.dp),
                    fillHeight = true,
                    content = content
                )
            }
        } else {
            val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)
                ) { it } + fadeIn(tween(180)),
                exit = slideOutVertically(animationSpec = tween(200)) { it } + fadeOut(tween(160)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                PanelSurface(
                    modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight * 0.8f),
                    shape = shape,
                    title = title,
                    icon = icon,
                    onDismiss = onDismiss,
                    backdrop = backdrop,
                    geometry = null,
                    fillHeight = false,
                    content = content
                )
            }
        }
    }
}

@Composable
private fun PanelSurface(
    modifier: Modifier,
    shape: Shape,
    title: String,
    icon: ImageVector,
    onDismiss: () -> Unit,
    backdrop: ImageBitmap?,
    geometry: BackdropGeometry?,
    fillHeight: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .border(width = 1.dp, brush = GlassBorder, shape = shape)
            // swallow taps so touching the panel does not dismiss it through the scrim
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        GlassLayers(backdrop = backdrop, geometry = geometry)
        Column(
            modifier = (if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NeonCyan.copy(alpha = 0.18f))
                        .border(1.dp, NeonCyan.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    color = Color.White,
                    style = Typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color(0xCCFFFFFF))
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x1FFFFFFF)))
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                content = content
            )
        }
    }
}

/** ⋮ menu drawn inside the player (a Popup window would make Android show the system bars again). */
@Composable
fun BoxScope.PlayerOverflowMenu(
    visible: Boolean,
    onDismiss: () -> Unit,
    backdrop: ImageBitmap? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(enabled = visible) { onDismiss() }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(120)),
        modifier = Modifier.matchParentSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
    }

    BoxWithConstraints(modifier = Modifier.matchParentSize()) {
        val menuWidth = 250.dp
        val menuEnd = 12.dp
        val menuTop = 56.dp
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.92f, transformOrigin = TransformOrigin(1f, 0f), animationSpec = tween(160)),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.92f, transformOrigin = TransformOrigin(1f, 0f), animationSpec = tween(120)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = menuTop, end = menuEnd)
        ) {
            val shape = RoundedCornerShape(22.dp)
            Box(
                modifier = Modifier
                    .width(menuWidth)
                    .heightIn(max = maxHeight - menuTop - 16.dp)
                    .clip(shape)
                    .border(width = 1.dp, brush = GlassBorder, shape = shape)
            ) {
                GlassLayers(
                    backdrop = backdrop,
                    geometry = BackdropGeometry(maxWidth, maxHeight, maxWidth - menuEnd - menuWidth, menuTop)
                )
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
fun PlayerMenuItem(text: @Composable () -> Unit, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(0.98f) { onClick() }
            .padding(horizontal = 18.dp, vertical = 13.dp)
    ) {
        CompositionLocalProvider(
            LocalContentColor provides Color.White,
            LocalTextStyle provides LocalTextStyle.current.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium)
        ) {
            text()
        }
    }
}

@Composable
fun PanelOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    subtitle: String? = null
) {
    val bg by animateColorAsState(
        targetValue = if (selected) NeonCyan.copy(alpha = 0.16f) else RowIdle,
        animationSpec = tween(180),
        label = "option_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) NeonCyan.copy(alpha = 0.6f) else RowIdleBorder,
        animationSpec = tween(180),
        label = "option_border"
    )
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .bouncyClickable(0.98f) { onClick() }
            .clip(shape)
            .background(bg)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) NeonCyan else Color(0xB3FFFFFF),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (selected) NeonCyan else Color.White,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(text = subtitle, color = Color(0x80FFFFFF), fontSize = 12.sp)
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Panel contents (titles are drawn by PlayerSidePanel)
// ---------------------------------------------------------------------------------------------

@Composable
fun SpeedBottomSheet(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit
) {
    val presets = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 3.0f, 4.0f)
    Text(
        text = "%.2fx".format(currentSpeed),
        color = NeonCyan,
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 10.dp)
    )
    presets.chunked(4).forEach { rowSpeeds ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rowSpeeds.forEach { speed ->
                SpeedChip(
                    speed = speed,
                    isSelected = currentSpeed == speed,
                    onClick = { onSpeedSelected(speed) },
                    modifier = Modifier.weight(1f)
                )
            }
            repeat(4 - rowSpeeds.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }
}

@Composable
fun SpeedChip(speed: Float, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(
        targetValue = if (isSelected) NeonCyan else RowIdle,
        animationSpec = tween(180),
        label = "speed_chip_bg"
    )
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .bouncyClickable(0.95f) { onClick() }
            .clip(shape)
            .background(bg)
            .border(1.dp, if (isSelected) Color.Transparent else RowIdleBorder, shape)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${speed}x",
            color = if (isSelected) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun AspectRatioBottomSheet(
    currentMode: AspectRatioMode,
    onModeSelected: (AspectRatioMode) -> Unit
) {
    AspectRatioMode.values().forEach { mode ->
        PanelOptionRow(
            label = mode.displayName,
            selected = mode == currentMode,
            icon = Icons.Default.AspectRatio,
            onClick = { onModeSelected(mode) }
        )
    }
}

@Composable
fun AudioTrackBottomSheet(
    tracks: List<AudioTrackInfo>,
    onTrackSelected: (AudioTrackInfo) -> Unit
) {
    if (tracks.isEmpty()) {
        Text("No alternate audio tracks available", color = Color(0x80FFFFFF), fontSize = 14.sp)
    } else {
        tracks.forEach { track ->
            PanelOptionRow(
                label = track.label,
                selected = track.isSelected,
                icon = Icons.Default.Audiotrack,
                onClick = { onTrackSelected(track) }
            )
        }
    }
}

@Composable
fun SubtitleBottomSheet(
    tracks: List<SubtitleTrackInfo>,
    onTrackSelected: (SubtitleTrackInfo?) -> Unit,
    onLoadFile: () -> Unit,
    delayMs: Long,
    canDelay: Boolean,
    onDelayChange: (Long) -> Unit
) {
    PanelOptionRow(
        label = "Off",
        selected = tracks.none { it.isSelected },
        icon = Icons.Default.Subtitles,
        onClick = { onTrackSelected(null) }
    )
    tracks.forEach { track ->
        PanelOptionRow(
            label = track.label,
            selected = track.isSelected,
            icon = Icons.Default.Subtitles,
            onClick = { onTrackSelected(track) }
        )
    }
    PanelOptionRow(
        label = "Load subtitle file…",
        subtitle = ".srt or .vtt",
        selected = false,
        icon = Icons.Default.FolderOpen,
        onClick = onLoadFile
    )
    if (canDelay) {
        Spacer(modifier = Modifier.height(8.dp))
        val shape = RoundedCornerShape(16.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(RowIdle)
                .border(1.dp, RowIdleBorder, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Subtitle delay", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = (if (delayMs >= 0) "+" else "-") + "%.1f".format(kotlin.math.abs(delayMs) / 1000f) + " s",
                    color = NeonCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            DelayStepButton(label = "− 0.5s", onClick = { onDelayChange(-500L) })
            Spacer(modifier = Modifier.width(8.dp))
            DelayStepButton(label = "+ 0.5s", onClick = { onDelayChange(500L) })
        }
        Text(
            text = "Applies to the loaded external file; playback position is kept.",
            color = Color(0x80FFFFFF),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun DelayStepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .bouncyClickable(0.94f) { onClick() }
            .clip(RoundedCornerShape(12.dp))
            .background(NeonCyan.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = NeonCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun PropertiesBottomSheet(
    video: VideoItem,
    streamInfo: List<Pair<String, String>>,
    onCopyReport: () -> Unit,
    onShareReport: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(RowIdle)
            .border(1.dp, RowIdleBorder, shape)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        PropertyRow(label = "Title", value = video.title)
        PropertyRow(label = "Resolution", value = "${video.width} x ${video.height} (${video.resolutionLabel})")
        PropertyRow(label = "Duration", value = video.durationFormatted)
        PropertyRow(label = "File Size", value = video.sizeFormatted)
        PropertyRow(label = "Mime Type", value = video.mimeType)
        PropertyRow(label = "Path", value = video.path)
        streamInfo.forEach { (label, value) -> PropertyRow(label = label, value = value) }
    }
    Spacer(modifier = Modifier.height(10.dp))
    PanelOptionRow(
        label = "Copy diagnostic report",
        subtitle = "Paste it in the chat to get help",
        selected = false,
        icon = Icons.Default.ContentCopy,
        onClick = onCopyReport
    )
    PanelOptionRow(
        label = "Share diagnostic report",
        selected = false,
        icon = Icons.Default.Share,
        onClick = onShareReport
    )
}

@Composable
fun PropertyRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(label, color = Color(0x80FFFFFF), fontSize = 12.sp)
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}
