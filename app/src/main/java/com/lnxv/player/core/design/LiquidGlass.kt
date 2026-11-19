package com.lnxv.player.core.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Liquid Glass surface. Techniques ported from the Figma Liquid Glass plugin's layer stack (glass-layers.ts):
 *  - refraction layer: inner shadow (depth) + angular-gradient edge stroke with two opposite specular lobes
 *    (stops 12% / 64% bright, 28-36% and 78-89% dim),
 *  - highlight layer: a soft, wide version of the same two lobes clipped inside the shape,
 *  - tint layer: the translucent fill.
 * Everything is drawn once per size through drawWithCache (no per-frame work). The tier comes from [LocalGlassProfile]:
 * LOW keeps the minimal fill + hairline + top highlight; BALANCED/HIGH add the layered look; HIGH adds a static chromatic fringe.
 * Real backdrop refraction is NOT done here (a Modifier cannot sample what is behind it): see LiquidLens.kt.
 */
fun Modifier.liquidGlassSurface(
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = Color(0x331E293B),
    borderColor: Color = Color(0x40FFFFFF),
    borderWidth: Dp = 1.dp,
    @Suppress("UNUSED_PARAMETER") blurRadius: Dp = 16.dp // Modifier.blur blurs content, not backdrop; unused
): Modifier = composed {
    val profile = LocalGlassProfile.current
    val isDark = appColors.isDark
    val fill = Modifier
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    backgroundColor.copy(alpha = (backgroundColor.alpha * 1.25f).coerceAtMost(1f)),
                    backgroundColor.copy(alpha = backgroundColor.alpha * 0.75f)
                )
            ),
            shape = shape
        )
    if (profile == GlassProfile.LOW) {
        fill
            .border(
                width = borderWidth,
                brush = Brush.linearGradient(
                    colors = listOf(
                        borderColor,
                        borderColor.copy(alpha = borderColor.alpha * 0.2f),
                        borderColor.copy(alpha = borderColor.alpha * 0.6f)
                    ),
                    start = Offset.Zero,
                    end = Offset.Infinite
                ),
                shape = shape
            )
            .drawWithContent {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.14f),
                        0.25f to Color.White.copy(alpha = 0.05f),
                        0.60f to Color.Transparent,
                        1f to Color.Transparent
                    )
                )
                drawContent()
                drawLine(
                    brush = Brush.horizontalGradient(
                        0f to Color.White.copy(alpha = 0.45f),
                        0.5f to Color.White.copy(alpha = 0.12f),
                        1f to Color.White.copy(alpha = 0.02f)
                    ),
                    start = Offset(10.dp.toPx(), 0.8.dp.toPx()),
                    end = Offset(size.width - 10.dp.toPx(), 0.8.dp.toPx()),
                    strokeWidth = 1.0.dp.toPx()
                )
            }
    } else {
        fill.drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val stroke = borderWidth.toPx().coerceAtLeast(1f)
            val bright = (borderColor.alpha * 2.4f).coerceIn(0.35f, 0.9f)
            val dim = borderColor.alpha * 0.35f
            val base = Color(borderColor.red, borderColor.green, borderColor.blue)
            val center = Offset(size.width / 2f, size.height / 2f)
            // Figma "angular gradient" strokes: lobes at 12% and 64% of the sweep, dim in between.
            fun lobes(peak: Float, floor: Float) = Brush.sweepGradient(
                0f to base.copy(alpha = floor),
                0.12f to base.copy(alpha = peak),
                0.28f to base.copy(alpha = floor),
                0.36f to base.copy(alpha = floor),
                0.64f to base.copy(alpha = peak),
                0.78f to base.copy(alpha = floor),
                0.89f to base.copy(alpha = floor),
                1f to base.copy(alpha = floor),
                center = center
            )
            val edge = lobes(bright, dim)
            // "Highlight reflection": wide soft strokes (approximating the 12px / blur-14 stroke) kept inside the clip.
            val softA = lobes(bright * 0.16f, 0f)
            val softB = lobes(bright * 0.26f, 0f)
            val softC = lobes(bright * 0.40f, 0f)
            val innerShadow = Brush.linearGradient(
                0f to Color.Transparent,
                0.55f to Color.Transparent,
                1f to Color.Black.copy(alpha = if (isDark) 0.22f else 0.07f),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
            val topGloss = Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (isDark) 0.13f else 0.30f),
                0.32f to Color.White.copy(alpha = 0.03f),
                0.7f to Color.Transparent,
                1f to Color.Transparent
            )
            val fringe = profile == GlassProfile.HIGH
            val fringeOffset = 0.7.dp.toPx()
            onDrawWithContent {
                drawOutline(outline, brush = topGloss)
                drawOutline(outline, brush = innerShadow)
                drawOutline(outline, brush = softA, style = Stroke(stroke * 9f))
                drawOutline(outline, brush = softB, style = Stroke(stroke * 5.5f))
                drawOutline(outline, brush = softC, style = Stroke(stroke * 2.8f))
                drawContent()
                if (fringe) {
                    // Static chromatic-aberration hint at the rim (NOT refraction): cool/warm fringes offset opposite ways.
                    translate(-fringeOffset, 0f) { drawOutline(outline, color = Color(0xFF4FC3F7).copy(alpha = 0.10f), style = Stroke(stroke)) }
                    translate(fringeOffset, 0f) { drawOutline(outline, color = Color(0xFFFF7A8A).copy(alpha = 0.10f), style = Stroke(stroke)) }
                }
                drawOutline(outline, brush = edge, style = Stroke(stroke * 1.25f))
            }
        }
    }
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    backgroundColor: Color = appColors.glassFill,
    borderColor: Color = appColors.glassBorder,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.liquidGlassSurface(
            shape = shape,
            backgroundColor = backgroundColor,
            borderColor = borderColor,
            borderWidth = borderWidth
        ),
        content = content
    )
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isPrimary: Boolean = false,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.70f,
            stiffness = 380f
        ),
        label = "glass_button_precision_scale"
    )

    val colors = appColors
    val bgColor = when {
        !enabled -> colors.glassSubtle
        isPrimary -> colors.accent.copy(alpha = 0.35f)
        else -> if (colors.isDark) Color(0x2BFFFFFF) else Color(0x14000000)
    }

    val textColor = when {
        !enabled -> colors.textTertiary
        isPrimary -> colors.accent
        else -> colors.textPrimary
    }

    Box(
        modifier = modifier
            .scale(scale)
            .liquidGlassSurface(
                shape = RoundedCornerShape(16.dp),
                backgroundColor = bgColor,
                borderColor = if (isPrimary) colors.accent.copy(alpha = 0.6f) else colors.glassBorder
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Text(
                text = text,
                color = textColor,
                fontSize = 14.sp,
                style = Typography.labelLarge
            )
        }
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = appColors.textPrimary,
    backgroundColor: Color = appColors.glassFill,
    size: Dp = 44.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.70f,
            stiffness = 400f
        ),
        label = "glass_icon_precision_scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .liquidGlassSurface(
                shape = CircleShape,
                backgroundColor = backgroundColor,
                borderColor = appColors.glassBorder,
                borderWidth = 1.dp
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint
        )
    }
}

@Composable
fun GlassBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = NeonCyan
) {
    Box(
        modifier = modifier
            .liquidGlassSurface(
                shape = CircleShape,
                backgroundColor = color.copy(alpha = 0.2f),
                borderColor = color.copy(alpha = 0.5f),
                borderWidth = 1.dp
            )
            .padding(horizontal = 9.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            style = Typography.labelSmall
        )
    }
}
