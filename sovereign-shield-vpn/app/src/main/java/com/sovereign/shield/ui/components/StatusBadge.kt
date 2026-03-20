package com.sovereign.shield.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereign.shield.ui.theme.*

/**
 * Animated connection status badge with pulsing indicator.
 */
@Composable
fun ConnectionStatusBadge(
    isConnected: Boolean,
    isConnecting: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val color by animateColorAsState(
        targetValue = when {
            isConnecting -> StatusConnecting
            isConnected -> StatusConnected
            else -> StatusDisconnected
        },
        animationSpec = tween(500),
        label = "statusColor"
    )

    val text = when {
        isConnecting -> "SECURING CONNECTION"
        isConnected -> "SHIELD ACTIVE"
        else -> "UNPROTECTED"
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Pulsing dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = if (isConnecting) pulseAlpha else 1f))
        )

        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            ),
            color = color
        )
    }
}

/**
 * Encryption level badge.
 */
@Composable
fun EncryptionBadge(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ShieldBlueSubtle)
            .border(1.dp, ShieldBlue.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("🔐", fontSize = 12.sp)
        Text(
            "AES-256 + Curve25519",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            ),
            color = ShieldBlueBright
        )
    }
}

/**
 * Network type indicator.
 */
@Composable
fun NetworkTypeBadge(
    networkType: String,
    modifier: Modifier = Modifier
) {
    val icon = when (networkType) {
        "WiFi" -> "📶"
        "Cellular" -> "📱"
        "Ethernet" -> "🔌"
        "VPN" -> "🛡️"
        else -> "❓"
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SpaceCard)
            .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(icon, fontSize = 12.sp)
        Text(
            networkType.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = TextSecondary
        )
    }
}
