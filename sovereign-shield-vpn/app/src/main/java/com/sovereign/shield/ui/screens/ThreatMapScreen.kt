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
 * 3D interactive globe with orthographic projection.
 * Shows user location, server location, and encrypted connection arc.
 * The globe slowly rotates to center on the user's position.
 */
@Composable
fun ThreatMapScreen() {
    val context = LocalContext.current
    val vpnManager = remember { VpnManager.getInstance(context) }
    val isConnected = vpnManager.getTunnelState() == Tunnel.State.UP
    val serverIp = vpnManager.getServerIp() ?: "35.206.67.49"

    var userLoc by remember { mutableStateOf<GeoIpResponse?>(null) }
    var serverLoc by remember { mutableStateOf<GeoIpResponse?>(null) }

    // Re-poll connection state every 2 seconds so globe reacts to connect/disconnect
    var liveConnected by remember { mutableStateOf(isConnected) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000)
            val newState = vpnManager.getTunnelState() == Tunnel.State.UP
            if (newState != liveConnected) liveConnected = newState
        }
    }

    // Fetch user location — try bypass-VPN method first, then cache, then direct
    LaunchedEffect(Unit) {
        // Try the VPN-bypass method which uses the physical network
        val bypassLoc = try {
            GeoIpClient.lookupSelfBypassVpn(context, serverIp)
        } catch (_: Exception) { null }
        if (bypassLoc != null && !bypassLoc.error && bypassLoc.latitude != 0.0) {
            userLoc = bypassLoc
            return@LaunchedEffect
        }
        // Fallback: try normal fetch+cache (works when VPN is off)
        val cached = try {
            GeoIpClient.fetchAndCacheRealLocation(context, serverIp)
        } catch (_: Exception) { null }
        if (cached != null && !cached.error && cached.latitude != 0.0) {
            userLoc = cached
        }
    }

    // Fetch server location — known IP, no VPN bypass needed
    LaunchedEffect(liveConnected, serverIp) {
        // Always resolve the server IP so pin shows when connected
        if (liveConnected) {
            // Small delay to let connection stabilize
            kotlinx.coroutines.delay(500)
            val loc = try {
                GeoIpClient.lookupWithFallback(serverIp)
            } catch (_: Exception) { null }
            if (loc != null && loc.latitude != 0.0) serverLoc = loc
        } else {
            serverLoc = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(SpaceBlack, SpaceDark)))
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
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
            if (liveConnected && userLoc != null) {
                Text(
                    "${userLoc?.ip} \u27A4 $serverIp",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace, fontSize = 10.sp
                    ),
                    color = ShieldCyan
                )
            }
        }

        // Globe Card
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF040810))
        ) {
            Box(Modifier.fillMaxSize()) {
                Globe3DCanvas(userLoc, serverLoc, liveConnected)

                // Location badges — always show user location if available
                val uLoc = userLoc
                val sLoc = serverLoc
                if (uLoc != null && !uLoc.error) {
                    Column(
                        Modifier.align(Alignment.BottomStart).padding(12.dp)
                    ) {
                        LocationBadge("YOU", "${uLoc.city}, ${uLoc.region}", ShieldCyan)
                        if (liveConnected && sLoc != null) {
                            Spacer(Modifier.height(4.dp))
                            LocationBadge("VPN", "${sLoc.city}, ${sLoc.region}", StatusConnected)
                        }
                    }
                }

                // Encrypted tunnel badge — only when connected
                if (liveConnected && serverLoc != null) {
                    Column(
                        Modifier.align(Alignment.TopEnd).padding(12.dp)
                            .background(Color(0xDD040810), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "\u25CF ENCRYPTED TUNNEL ACTIVE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.sp, fontWeight = FontWeight.Bold
                            ),
                            color = StatusConnected
                        )
                    }
                }

                if (!liveConnected) {
                    // Light overlay at bottom only — keep globe visible
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .background(Color(0xCC040810), RoundedCornerShape(12.dp))
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "CONNECT TO ACTIVATE TUNNEL",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                            color = TextMuted
                        )
                    }
                }
            }
        }

        // Connection details — show encryption info when connected
        if (liveConnected && userLoc != null && serverLoc != null) {
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
                        Text("ChaCha20", style = MaterialTheme.typography.titleSmall, color = ShieldViolet)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Key Exchange", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text("Curve25519", style = MaterialTheme.typography.titleSmall, color = ShieldCyan)
                    }
                }
            }
        } else if (!liveConnected) {
            // Show user location even when disconnected
            val uLocDisc = userLoc
            if (uLocDisc != null && !uLocDisc.error) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Your Location", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text(
                                "${uLocDisc.city}, ${uLocDisc.region}",
                                style = MaterialTheme.typography.titleSmall, color = ShieldCyan
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Your IP", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text(
                                uLocDisc.ip.ifEmpty { "—" },
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                                color = ShieldBlue
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Status", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text("Exposed", style = MaterialTheme.typography.titleSmall, color = Color(0xFFF59E0B))
                        }
                    }
                }
            }
        }
    }
}

// ===========================================================================
// 3D GLOBE — Orthographic Projection with Continent Outlines
// ===========================================================================

@Composable
private fun Globe3DCanvas(
    userLoc: GeoIpResponse?,
    serverLoc: GeoIpResponse?,
    isConnected: Boolean
) {
    val textMeasurer = rememberTextMeasurer()

    // Target longitude: center on user if known, otherwise 0
    val targetLon = userLoc?.longitude?.toFloat() ?: 0f

    // Animated rotation — slow spin that settles on user location
    val infiniteTransition = rememberInfiniteTransition(label = "globe")

    // Slow continuous base rotation (full revolution in 120 seconds)
    val baseRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(120_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )

    // Animate centering on user location
    val centerLon by animateFloatAsState(
        targetValue = -targetLon,
        animationSpec = tween(3000, easing = EaseInOutCubic),
        label = "centerLon"
    )

    // Combined rotation: slow spin + user centering
    val rotationLon = centerLon + baseRotation * 0.15f // Slow drift after centering

    // Pulse animation for location pins
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 8f, targetValue = 22f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseR"
    )

    // Data packet animation along the arc
    val packetProgress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "packet"
    )

    // Atmosphere glow pulse
    val atmosAlpha by infiniteTransition.animateFloat(
        initialValue = 0.06f, targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "atmos"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val radius = minOf(w, h) * 0.40f
        val tiltDeg = 23.5f // Earth's axial tilt

        val rotLonRad = Math.toRadians(rotationLon.toDouble())
        val tiltRad = Math.toRadians(tiltDeg.toDouble())

        /**
         * Project lat/lon to screen x,y using orthographic projection.
         * Returns null if the point is on the far side of the globe.
         */
        fun projectGlobe(lat: Double, lon: Double): Offset? {
            val latRad = Math.toRadians(lat)
            val lonRad = Math.toRadians(lon) + rotLonRad

            // Orthographic projection with tilt
            val x = cos(latRad) * sin(lonRad)
            val y = -(sin(latRad) * cos(tiltRad) - cos(latRad) * sin(tiltRad) * cos(lonRad))
            val z = sin(latRad) * sin(tiltRad) + cos(latRad) * cos(tiltRad) * cos(lonRad)

            if (z < 0) return null // Behind the globe

            return Offset(
                (cx + x * radius).toFloat(),
                (cy + y * radius).toFloat()
            )
        }

        // --- 1. ATMOSPHERE GLOW ---
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ShieldBlue.copy(alpha = atmosAlpha),
                    ShieldCyan.copy(alpha = atmosAlpha * 0.5f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = radius * 1.25f
            ),
            radius = radius * 1.25f,
            center = Offset(cx, cy)
        )

        // --- 2. GLOBE BODY ---
        // Dark ocean fill
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF0A1628), Color(0xFF050A14)),
                center = Offset(cx - radius * 0.2f, cy - radius * 0.2f),
                radius = radius * 1.2f
            ),
            radius = radius,
            center = Offset(cx, cy)
        )

        // Globe edge highlight
        drawCircle(
            color = ShieldBlue.copy(alpha = 0.15f),
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(2f)
        )

        // --- 3. GRID LINES (latitude/longitude) ---
        // Latitude lines every 30°
        for (lat in -60..60 step 30) {
            val path = Path()
            var started = false
            for (lonStep in 0..360) {
                val lon = lonStep.toDouble() - 180.0
                val pt = projectGlobe(lat.toDouble(), lon) ?: continue
                if (!started) { path.moveTo(pt.x, pt.y); started = true }
                else path.lineTo(pt.x, pt.y)
            }
            drawPath(path, Color(0xFF0F2340).copy(alpha = 0.6f), style = Stroke(0.5f))
        }

        // Longitude lines every 30°
        for (lon in -180..150 step 30) {
            val path = Path()
            var started = false
            for (latStep in -90..90) {
                val pt = projectGlobe(latStep.toDouble(), lon.toDouble()) ?: continue
                if (!started) { path.moveTo(pt.x, pt.y); started = true }
                else path.lineTo(pt.x, pt.y)
            }
            drawPath(path, Color(0xFF0F2340).copy(alpha = 0.6f), style = Stroke(0.5f))
        }

        // --- 4. CONTINENT OUTLINES (Natural Earth data) ---
        val continents = getContinentOutlines()
        for (continent in continents) {
            val fillPath = Path()
            val strokePath = Path()
            var started = false
            var prevPt: Offset? = null
            var visibleCount = 0
            for (i in continent.indices step 2) {
                if (i + 1 >= continent.size) break
                val lat = continent[i]
                val lon = continent[i + 1]
                val pt = projectGlobe(lat.toDouble(), lon.toDouble())
                if (pt != null) {
                    if (!started) {
                        fillPath.moveTo(pt.x, pt.y)
                        strokePath.moveTo(pt.x, pt.y)
                        started = true
                        visibleCount = 1
                    } else {
                        if (prevPt != null) {
                            val dist = sqrt((pt.x - prevPt.x).pow(2) + (pt.y - prevPt.y).pow(2))
                            if (dist < radius * 0.8f) {
                                fillPath.lineTo(pt.x, pt.y)
                                strokePath.lineTo(pt.x, pt.y)
                                visibleCount++
                            } else {
                                fillPath.moveTo(pt.x, pt.y)
                                strokePath.moveTo(pt.x, pt.y)
                            }
                        } else {
                            fillPath.lineTo(pt.x, pt.y)
                            strokePath.lineTo(pt.x, pt.y)
                            visibleCount++
                        }
                    }
                    prevPt = pt
                } else {
                    started = false
                    prevPt = null
                }
            }
            // Fill land masses with subtle color
            if (visibleCount >= 3) {
                fillPath.close()
                drawPath(fillPath, Color(0xFF0A1E3D).copy(alpha = 0.6f))
            }
            // Outline stroke
            drawPath(
                strokePath,
                ShieldBlue.copy(alpha = 0.45f),
                style = Stroke(1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // --- 5. USER LOCATION PIN ---
        if (userLoc != null && !userLoc.error && userLoc.latitude != 0.0) {
            val userPt = projectGlobe(userLoc.latitude, userLoc.longitude)
            if (userPt != null) {
                // Pulse ring
                drawCircle(ShieldCyan.copy(alpha = pulseAlpha * 0.4f), pulseRadius * 1.2f, userPt)
                drawCircle(ShieldCyan.copy(alpha = pulseAlpha), pulseRadius * 0.7f, userPt)
                // Solid dot
                drawCircle(ShieldCyan, 5f, userPt)
                drawCircle(Color.White, 2f, userPt)

                // Label
                val label = textMeasurer.measure(
                    userLoc.city.ifEmpty { "You" },
                    TextStyle(
                        color = ShieldCyan,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                )
                drawText(label, topLeft = Offset(userPt.x + 14, userPt.y - label.size.height / 2))
            }
        }

        // --- 6. SERVER LOCATION PIN + CONNECTION ARC ---
        if (isConnected && serverLoc != null && !serverLoc.error && userLoc != null) {
            val serverPt = projectGlobe(serverLoc.latitude, serverLoc.longitude)
            val userPt = projectGlobe(userLoc.latitude, userLoc.longitude)

            // Draw connection arc (great circle approximation)
            if (userPt != null || serverPt != null) {
                val arcPath = Path()
                var arcStarted = false
                val steps = 60
                val arcPoints = mutableListOf<Offset>()

                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    // Interpolate along great circle
                    val lat = userLoc.latitude + (serverLoc.latitude - userLoc.latitude) * t
                    val lon = userLoc.longitude + (serverLoc.longitude - userLoc.longitude) * t
                    // Add altitude for the arc (higher in the middle)
                    val arcHeight = sin(t * PI).toFloat() * 0.08f

                    val latRad = Math.toRadians(lat)
                    val lonRad = Math.toRadians(lon) + rotLonRad

                    val xBase = cos(latRad) * sin(lonRad)
                    val yBase = -(sin(latRad) * cos(tiltRad) - cos(latRad) * sin(tiltRad) * cos(lonRad))
                    val z = sin(latRad) * sin(tiltRad) + cos(latRad) * cos(tiltRad) * cos(lonRad)

                    if (z < -0.1) { arcStarted = false; continue }

                    val scale = 1f + arcHeight
                    val pt = Offset(
                        (cx + xBase * radius * scale).toFloat(),
                        (cy + yBase * radius * scale).toFloat()
                    )
                    arcPoints.add(pt)

                    if (!arcStarted) {
                        arcPath.moveTo(pt.x, pt.y)
                        arcStarted = true
                    } else {
                        arcPath.lineTo(pt.x, pt.y)
                    }
                }

                // Arc glow (wider, dimmer)
                drawPath(
                    arcPath,
                    Brush.linearGradient(
                        colors = listOf(ShieldCyan.copy(alpha = 0.2f), ShieldBlue.copy(alpha = 0.2f))
                    ),
                    style = Stroke(6f, cap = StrokeCap.Round)
                )

                // Arc line
                drawPath(
                    arcPath,
                    Brush.linearGradient(
                        colors = listOf(ShieldCyan, ShieldBlue, StatusConnected)
                    ),
                    style = Stroke(
                        2f,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
                    )
                )

                // Animated data packet traveling along the arc
                if (arcPoints.size > 2) {
                    val idx = (packetProgress * (arcPoints.size - 1)).toInt()
                        .coerceIn(0, arcPoints.size - 1)
                    val packetPt = arcPoints[idx]
                    drawCircle(Color.White, 4f, packetPt)
                    drawCircle(ShieldCyan.copy(alpha = 0.5f), 10f, packetPt)
                    drawCircle(ShieldBlue.copy(alpha = 0.2f), 18f, packetPt)
                }
            }

            // Server pin (if visible)
            if (serverPt != null) {
                drawCircle(StatusConnected.copy(alpha = pulseAlpha * 0.4f), pulseRadius * 1.2f, serverPt)
                drawCircle(StatusConnected.copy(alpha = pulseAlpha), pulseRadius * 0.7f, serverPt)
                drawCircle(StatusConnected, 5f, serverPt)
                drawCircle(Color.White, 2f, serverPt)

                val serverLabel = textMeasurer.measure(
                    serverLoc.city.ifEmpty { "Server" },
                    TextStyle(
                        color = StatusConnected,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                )
                drawText(serverLabel, topLeft = Offset(serverPt.x + 14, serverPt.y - serverLabel.size.height / 2))
            }
        }

        // --- 7. SPECULAR HIGHLIGHT (glass effect) ---
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.04f),
                    Color.Transparent
                ),
                center = Offset(cx - radius * 0.25f, cy - radius * 0.3f),
                radius = radius * 0.6f
            ),
            radius = radius,
            center = Offset(cx, cy)
        )
    }
}

// ===========================================================================
// LOCATION BADGE
// ===========================================================================

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
