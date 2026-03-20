package com.sovereign.shield.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereign.shield.network.GeoIpClient
import com.sovereign.shield.network.GeoIpResponse
import com.sovereign.shield.ui.components.GlassCard
import com.sovereign.shield.ui.theme.*
import com.sovereign.shield.vpn.VpnManager
import com.wireguard.android.backend.Tunnel
import kotlin.math.*

/**
 * Full-screen threat map with global projection.
 * Shows user location, server location, and connection arc.
 * Significant upgrade from Dragon Scale's US-only map.
 */
@Composable
fun ThreatMapScreen() {
    val context = LocalContext.current
    val vpnManager = remember { VpnManager(context) }
    val isConnected = vpnManager.getTunnelState() == Tunnel.State.UP
    val serverIp = vpnManager.getServerIp() ?: "35.206.67.49"

    var userLoc by remember { mutableStateOf<GeoIpResponse?>(null) }
    var serverLoc by remember { mutableStateOf<GeoIpResponse?>(null) }

    LaunchedEffect(Unit) {
        val loc = GeoIpClient.fetchAndCacheRealLocation(context, serverIp)
        if (!loc.error) userLoc = loc
    }

    LaunchedEffect(isConnected, serverIp) {
        if (isConnected) {
            kotlinx.coroutines.delay(2000)
            val loc = GeoIpClient.lookupWithFallback(serverIp)
            if (loc.latitude != 0.0) serverLoc = loc
        } else serverLoc = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(SpaceBlack, SpaceDark)))
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "THREAT MAP",
                style = MaterialTheme.typography.titleLarge.copy(
                    letterSpacing = 4.sp, fontWeight = FontWeight.Bold
                ),
                color = ShieldBlue
            )
            if (isConnected && userLoc != null) {
                Text(
                    "${userLoc?.ip} ➜ $serverIp",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace, fontSize = 10.sp
                    ),
                    color = ShieldCyan
                )
            }
        }

        // Map card
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF040810))
        ) {
            Box(Modifier.fillMaxSize()) {
                GlobalMapCanvas(userLoc, serverLoc, isConnected)

                if (isConnected && userLoc != null && serverLoc != null) {
                    Column(
                        Modifier.align(Alignment.BottomStart).padding(12.dp)
                    ) {
                        LocationBadge("SRC", "${userLoc!!.city}, ${userLoc!!.region}", ShieldCyan)
                        Spacer(Modifier.height(4.dp))
                        LocationBadge("DST", "${serverLoc!!.city}, ${serverLoc!!.region}", StatusConnected)
                    }

                    Column(
                        Modifier.align(Alignment.TopEnd).padding(12.dp)
                            .background(Color(0xDD040810), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "● ENCRYPTED TUNNEL ACTIVE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.sp, fontWeight = FontWeight.Bold
                            ),
                            color = StatusConnected
                        )
                    }
                }

                if (!isConnected) {
                    Box(
                        Modifier.fillMaxSize().background(Color(0x80040810)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🛡️", fontSize = 48.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "CONNECT TO ACTIVATE MAP",
                                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }

        // Connection details
        if (isConnected && userLoc != null && serverLoc != null) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Protocol", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text("WireGuard", style = MaterialTheme.typography.titleSmall, color = ShieldBlue)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Encryption", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text("ChaCha20-Poly1305", style = MaterialTheme.typography.titleSmall, color = ShieldViolet)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Key Exchange", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text("Curve25519", style = MaterialTheme.typography.titleSmall, color = ShieldCyan)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlobalMapCanvas(
    userLoc: GeoIpResponse?,
    serverLoc: GeoIpResponse?,
    isConnected: Boolean
) {
    val textMeasurer = rememberTextMeasurer()
    val infiniteTransition = rememberInfiniteTransition(label = "map")
    val arcProgress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "arc"
    )
    val pulseSize by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pad = 20f

        // Grid
        val gridColor = Color(0xFF0A1628)
        for (i in 0..8) {
            val y = pad + (h - 2 * pad) * i / 8
            drawLine(gridColor, Offset(pad, y), Offset(w - pad, y), 0.5f)
        }
        for (i in 0..12) {
            val x = pad + (w - 2 * pad) * i / 12
            drawLine(gridColor, Offset(x, pad), Offset(x, h - pad), 0.5f)
        }

        fun project(lat: Double, lon: Double): Offset {
            val x = pad + ((lon + 180) / 360) * (w - 2 * pad)
            val y = pad + ((90 - lat) / 180) * (h - 2 * pad)
            return Offset(x.toFloat(), y.toFloat())
        }

        // User location
        if (userLoc != null && !userLoc.error && userLoc.latitude != 0.0) {
            val pos = project(userLoc.latitude, userLoc.longitude)
            drawCircle(ShieldCyan.copy(alpha = 0.15f), pulseSize, pos)
            drawCircle(ShieldCyan.copy(alpha = 0.4f), 8f, pos)
            drawCircle(ShieldCyan, 4f, pos)

            val label = textMeasurer.measure(
                userLoc.city.ifEmpty { "You" },
                TextStyle(color = ShieldCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            )
            drawText(label, topLeft = Offset(pos.x + 10, pos.y - label.size.height / 2))
        }

        // Server location and connection arc
        if (isConnected && serverLoc != null && !serverLoc.error && userLoc != null) {
            val srcPos = project(userLoc.latitude, userLoc.longitude)
            val dstPos = project(serverLoc.latitude, serverLoc.longitude)

            // Animated arc
            val midX = (srcPos.x + dstPos.x) / 2
            val midY = minOf(srcPos.y, dstPos.y) - 60f
            val arcPath = Path()
            arcPath.moveTo(srcPos.x, srcPos.y)
            arcPath.quadraticBezierTo(midX, midY, dstPos.x, dstPos.y)

            drawPath(
                arcPath, ShieldBlue.copy(alpha = 0.3f),
                style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f)))
            )

            // Animated pulse along arc
            val pathMeasure = PathMeasure()
            pathMeasure.setPath(arcPath, false)
            val length = pathMeasure.length
            if (length > 0) {
                val pos = FloatArray(2)
                pathMeasure.getPosition(length * arcProgress, pos)
                drawCircle(ShieldBlueBright, 4f, Offset(pos[0], pos[1]))
                drawCircle(ShieldBlue.copy(alpha = 0.3f), 10f, Offset(pos[0], pos[1]))
            }

            // Server pin
            drawCircle(StatusConnected.copy(alpha = 0.15f), pulseSize, dstPos)
            drawCircle(StatusConnected.copy(alpha = 0.4f), 8f, dstPos)
            drawCircle(StatusConnected, 4f, dstPos)

            val serverLabel = textMeasurer.measure(
                serverLoc.city.ifEmpty { "Server" },
                TextStyle(color = StatusConnected, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            )
            drawText(serverLabel, topLeft = Offset(dstPos.x + 10, dstPos.y - serverLabel.size.height / 2))
        }
    }
}

@Composable
private fun LocationBadge(prefix: String, location: String, color: Color) {
    Row(
        modifier = Modifier
            .background(Color(0xDD040810), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            prefix,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = color
        )
        Text(
            location,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
            color = TextSecondary
        )
    }
}
