package com.sovereign.shield.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereign.shield.ui.theme.*

/**
 * Large circular connect/disconnect button with animated glow rings.
 * This is the hero element of the app — massive visual upgrade from Dragon Scale.
 */
@Composable
fun ShieldConnectButton(
    isConnected: Boolean,
    isConnecting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shield_glow")

    // Outer ring pulse
    val outerRingScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnecting) 1.15f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isConnecting) 800 else 2500,
                easing = EaseInOutCubic
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "outerRing"
    )

    // Glow intensity
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = if (isConnecting) 0.8f else 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isConnecting) 600 else 2000,
                easing = EaseInOutCubic
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // Rotation for connecting state
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val buttonColor by animateColorAsState(
        targetValue = when {
            isConnecting -> StatusConnecting
            isConnected -> StatusConnected
            else -> ShieldBlue
        },
        animationSpec = tween(500),
        label = "buttonColor"
    )

    val buttonGradient = when {
        isConnected -> Brush.radialGradient(
            colors = listOf(StatusConnected, StatusConnected.copy(alpha = 0.7f))
        )
        isConnecting -> Brush.sweepGradient(
            colors = listOf(StatusConnecting, StatusConnecting.copy(alpha = 0.3f), StatusConnecting)
        )
        else -> Brush.radialGradient(
            colors = listOf(ShieldBlue, ShieldBlueDim)
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(220.dp)
    ) {
        // Outer glow ring
        Box(
            modifier = Modifier
                .size(220.dp)
                .scale(outerRingScale)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    brush = Brush.radialGradient(
                        colors = listOf(
                            buttonColor.copy(alpha = glowAlpha),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Middle ring
        Box(
            modifier = Modifier
                .size(190.dp)
                .clip(CircleShape)
                .border(
                    width = 1.dp,
                    color = buttonColor.copy(alpha = 0.2f),
                    shape = CircleShape
                )
        )

        // Main button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(buttonGradient)
                .border(
                    width = 2.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.3f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    ),
                    shape = CircleShape
                )
                .clickable(
                    enabled = !isConnecting,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onClick() }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Shield icon
                Text(
                    text = "🛡️",
                    fontSize = 48.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        isConnecting -> "SECURING"
                        isConnected -> "PROTECTED"
                        else -> "CONNECT"
                    },
                    style = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 3.sp,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    ),
                    color = when {
                        isConnected -> SpaceBlack
                        isConnecting -> SpaceBlack
                        else -> Color.White
                    }
                )
            }
        }
    }
}

/**
 * Pill-shaped action button with gradient.
 */
@Composable
fun ShieldActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gradient: Brush = Brush.horizontalGradient(
        colors = listOf(ShieldBlue, ShieldCyan)
    ),
    textColor: Color = Color.White,
    enabled: Boolean = true
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) gradient else Brush.horizontalGradient(
                colors = listOf(TextMuted, TextFaint)
            ))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
            color = if (enabled) textColor else TextSecondary
        )
    }
}
