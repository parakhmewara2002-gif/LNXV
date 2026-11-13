package com.lnxv.player.core.design

import android.os.Build
import androidx.compose.animation.core.Spring
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
 * Liquid Glass styling modifier that renders a translucent backdrop, subtle border reflection,
 * and high-performance specular shine.
 */
fun Modifier.liquidGlassSurface(
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = Color(0x331E293B),
    borderColor: Color = Color(0x40FFFFFF),
    borderWidth: Dp = 1.dp,
    @Suppress("UNUSED_PARAMETER") blurRadius: Dp = 16.dp // Modifier.blur blurs content, not backdrop; unused
): Modifier = this
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
        drawContent()
        // Top specular reflection highlight
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x26FFFFFF), Color.Transparent),
                center = Offset(size.width * 0.3f, 0f),
                radius = size.width * 0.6f
            )
        )
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
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "glass_button_scale"
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
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "glass_icon_button_scale"
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
                shape = RoundedCornerShape(8.dp),
                backgroundColor = color.copy(alpha = 0.2f),
                borderColor = color.copy(alpha = 0.5f),
                borderWidth = 1.dp
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
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
