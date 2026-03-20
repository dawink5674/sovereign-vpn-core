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
            if (isConnected && userLoc != null) {
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
                Globe3DCanvas(userLoc, serverLoc, isConnected)

                // Connection info overlays
                if (isConnected && userLoc != null && serverLoc != null) {
                    Column(
                        Modifier.align(Alignment.BottomStart).padding(12.dp)
                    ) {
                        LocationBadge("YOU", "${userLoc!!.city}, ${userLoc!!.region}", ShieldCyan)
                        Spacer(Modifier.height(4.dp))
                        LocationBadge("VPN", "${serverLoc!!.city}, ${serverLoc!!.region}", StatusConnected)
                    }

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

                if (!isConnected) {
                    Box(
                        Modifier.fillMaxSize().background(Color(0x60040810)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("\uD83D\uDEE1\uFE0F", fontSize = 48.sp)
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
                        Text("ChaCha20", style = MaterialTheme.typography.titleSmall, color = ShieldViolet)
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

        // --- 4. CONTINENT OUTLINES ---
        // Simplified continent boundary polygons drawn as connected lines
        val continents = getContinentOutlines()
        for (continent in continents) {
            val path = Path()
            var started = false
            var prevPt: Offset? = null
            for (i in continent.indices step 2) {
                if (i + 1 >= continent.size) break
                val lat = continent[i]
                val lon = continent[i + 1]
                val pt = projectGlobe(lat.toDouble(), lon.toDouble())
                if (pt != null) {
                    if (!started) {
                        path.moveTo(pt.x, pt.y)
                        started = true
                    } else {
                        // Only draw if distance isn't too large (avoids wrapping artifacts)
                        if (prevPt != null) {
                            val dist = sqrt((pt.x - prevPt.x).pow(2) + (pt.y - prevPt.y).pow(2))
                            if (dist < radius * 0.8f) {
                                path.lineTo(pt.x, pt.y)
                            } else {
                                path.moveTo(pt.x, pt.y)
                            }
                        } else {
                            path.lineTo(pt.x, pt.y)
                        }
                    }
                    prevPt = pt
                } else {
                    started = false
                    prevPt = null
                }
            }
            drawPath(
                path,
                ShieldBlue.copy(alpha = 0.35f),
                style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
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
// CONTINENT OUTLINES — Simplified polygon data
// Format: [lat1, lon1, lat2, lon2, ...]
// ===========================================================================

private fun getContinentOutlines(): List<FloatArray> {
    return listOf(
        // North America
        floatArrayOf(
            50f, -125f, 55f, -130f, 60f, -140f, 65f, -165f, 70f, -165f,
            72f, -155f, 71f, -140f, 70f, -130f, 68f, -110f, 65f, -90f,
            60f, -80f, 55f, -65f, 50f, -60f, 47f, -65f, 45f, -70f,
            43f, -80f, 40f, -75f, 35f, -80f, 30f, -85f, 28f, -95f,
            25f, -100f, 20f, -105f, 18f, -100f, 15f, -92f, 15f, -88f,
            10f, -85f, 8f, -78f, 10f, -75f, 8f, -73f, 10f, -62f,
            15f, -75f, 20f, -75f, 20f, -88f, 25f, -90f, 30f, -88f,
            30f, -82f, 35f, -75f, 40f, -72f, 45f, -65f, 48f, -55f,
            52f, -55f, 55f, -60f, 60f, -65f, 55f, -80f, 50f, -90f,
            48f, -95f, 50f, -100f, 50f, -110f, 50f, -120f, 50f, -125f
        ),
        // South America
        floatArrayOf(
            12f, -72f, 10f, -68f, 8f, -62f, 5f, -60f, 2f, -50f,
            0f, -50f, -5f, -35f, -10f, -37f, -15f, -40f, -20f, -42f,
            -23f, -45f, -25f, -48f, -30f, -50f, -35f, -57f, -40f, -62f,
            -45f, -65f, -50f, -70f, -55f, -68f, -52f, -72f, -48f, -75f,
            -42f, -73f, -37f, -73f, -30f, -72f, -25f, -70f, -18f, -70f,
            -15f, -75f, -10f, -78f, -5f, -80f, 0f, -78f, 5f, -77f,
            8f, -77f, 12f, -72f
        ),
        // Europe
        floatArrayOf(
            36f, -8f, 38f, -5f, 40f, 0f, 43f, 3f, 45f, 7f,
            44f, 12f, 42f, 15f, 40f, 18f, 38f, 23f, 40f, 26f,
            42f, 28f, 44f, 28f, 46f, 30f, 48f, 25f, 50f, 20f,
            52f, 15f, 54f, 10f, 55f, 8f, 58f, 5f, 60f, 5f,
            62f, 6f, 65f, 12f, 68f, 16f, 70f, 25f, 70f, 30f,
            68f, 35f, 65f, 30f, 60f, 30f, 58f, 28f, 55f, 22f,
            52f, 20f, 50f, 12f, 48f, 8f, 46f, 2f, 44f, -2f,
            42f, -5f, 38f, -8f, 36f, -8f
        ),
        // Africa
        floatArrayOf(
            35f, -5f, 37f, 10f, 33f, 12f, 30f, 10f, 25f, 33f,
            20f, 37f, 15f, 42f, 12f, 44f, 10f, 50f, 5f, 42f,
            0f, 42f, -5f, 40f, -10f, 40f, -15f, 35f, -20f, 35f,
            -25f, 32f, -30f, 30f, -33f, 27f, -35f, 20f, -33f, 18f,
            -30f, 17f, -25f, 15f, -20f, 12f, -15f, 12f, -10f, 8f,
            -5f, 10f, 0f, 10f, 5f, 2f, 5f, -5f, 5f, -10f,
            10f, -15f, 15f, -17f, 20f, -17f, 25f, -15f, 30f, -10f,
            35f, -5f
        ),
        // Asia
        floatArrayOf(
            42f, 30f, 45f, 40f, 42f, 45f, 40f, 50f, 38f, 55f,
            35f, 55f, 30f, 50f, 25f, 55f, 23f, 58f, 20f, 60f,
            15f, 70f, 10f, 78f, 8f, 78f, 15f, 80f, 20f, 88f,
            22f, 90f, 25f, 95f, 20f, 100f, 15f, 100f, 10f, 105f,
            5f, 103f, 0f, 105f, -5f, 110f, -8f, 115f, -5f, 120f,
            5f, 120f, 10f, 118f, 20f, 110f, 22f, 120f, 30f, 122f,
            35f, 130f, 40f, 130f, 42f, 132f, 45f, 140f, 50f, 143f,
            55f, 140f, 58f, 142f, 60f, 150f, 62f, 160f, 65f, 170f,
            68f, 175f, 70f, 180f, 72f, 170f, 70f, 140f, 68f, 130f,
            65f, 120f, 60f, 100f, 55f, 80f, 55f, 70f, 50f, 60f,
            52f, 50f, 50f, 40f, 45f, 35f, 42f, 30f
        ),
        // Australia
        floatArrayOf(
            -12f, 130f, -15f, 125f, -18f, 122f, -22f, 114f, -25f, 114f,
            -30f, 115f, -33f, 115f, -35f, 118f, -35f, 122f, -35f, 135f,
            -37f, 140f, -38f, 145f, -37f, 150f, -34f, 151f, -30f, 153f,
            -25f, 152f, -20f, 148f, -17f, 146f, -15f, 143f, -12f, 142f,
            -12f, 137f, -14f, 135f, -12f, 130f
        )
    )
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
