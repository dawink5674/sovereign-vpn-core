package com.sovereign.shield.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sovereign.shield.ui.theme.*

/**
 * Glassmorphic card — the signature UI element of Sovereign Shield.
 * Translucent background with subtle border glow.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    borderColor: Color = GlassBorder,
    backgroundColor: Color = SpaceCard.copy(alpha = 0.7f),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .padding(16.dp),
        content = content
    )
}

/**
 * Gradient-bordered glass card with accent glow.
 */
@Composable
fun GlowCard(
    modifier: Modifier = Modifier,
    glowColor: Color = ShieldBlue,
    cornerRadius: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SpaceCard.copy(alpha = 0.9f),
                        SpaceBlack.copy(alpha = 0.95f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.5f),
                        glowColor.copy(alpha = 0.1f)
                    )
                ),
                shape = shape
            )
            .padding(16.dp),
        content = content
    )
}

/**
 * Stat card for displaying a single metric.
 */
@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = 16.dp,
        content = content
    )
}
