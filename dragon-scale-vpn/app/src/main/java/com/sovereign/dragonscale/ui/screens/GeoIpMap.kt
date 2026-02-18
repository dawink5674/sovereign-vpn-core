package com.sovereign.dragonscale.ui.screens

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereign.dragonscale.network.GeoIpClient
import com.sovereign.dragonscale.network.GeoIpResponse
import com.sovereign.dragonscale.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.*

// ---------------------------------------------------------------------------
// SOC-Style Geo-IP Threat Map — Premium Edition
// ---------------------------------------------------------------------------

@Composable
fun ThreatMapPanel(
    isConnected: Boolean,
    serverIp: String = "35.206.67.49",
    modifier: Modifier = Modifier
) {
    var userLocation by remember { mutableStateOf<GeoIpResponse?>(null) }
    var serverLocation by remember { mutableStateOf<GeoIpResponse?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isConnected) {
        if (isConnected) {
            scope.launch {
                try {
                    userLocation = GeoIpClient.api.lookupSelf()
                    serverLocation = GeoIpClient.api.lookup(serverIp)
                } catch (_: Exception) { }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "THREAT MAP",
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                color = TextMuted
            )
            if (isConnected && userLocation != null) {
                Text(
                    "${userLocation?.ip} → $serverIp",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace, fontSize = 10.sp
                    ),
                    color = DragonCyan
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF020810))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                SOCMapCanvas(
                    userGeo = userLocation,
                    serverGeo = serverLocation,
                    isConnected = isConnected
                )
                // Info overlay
                if (isConnected && userLocation != null && serverLocation != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp)
                    ) {
                        LocationChip("YOU", userLocation!!.city, userLocation!!.country_name, DragonCyan)
                        Spacer(Modifier.height(3.dp))
                        LocationChip("EXIT", serverLocation!!.city, serverLocation!!.country_name, StatusConnected)
                    }
                    // Latency / status badge
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .background(Color(0xCC020810), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("ENCRYPTED", style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp, letterSpacing = 1.5.sp
                        ), color = StatusConnected)
                        Text("WireGuard/UDP", style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp
                        ), color = TextMuted)
                    }
                }
                if (!isConnected) {
                    Text(
                        "CONNECT TO ACTIVATE",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.sp),
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationChip(tag: String, city: String, country: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color(0xCC020810), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(6.dp))
        Text(tag, style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold, fontSize = 9.sp
        ), color = color)
        Spacer(Modifier.width(6.dp))
        Text("$city, $country", style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 10.sp
        ), color = TextSecondary)
    }
}

// ---------------------------------------------------------------------------
// Canvas: full SOC experience
// ---------------------------------------------------------------------------

@Composable
private fun SOCMapCanvas(
    userGeo: GeoIpResponse?,
    serverGeo: GeoIpResponse?,
    isConnected: Boolean
) {
    val anim = rememberInfiniteTransition(label = "soc")

    val pulseR by anim.animateFloat(6f, 22f, infiniteRepeatable(
        tween(2000, easing = EaseInOutCubic), RepeatMode.Reverse
    ), label = "pr")
    val pulseA by anim.animateFloat(0.8f, 0.1f, infiniteRepeatable(
        tween(2000, easing = EaseInOutCubic), RepeatMode.Reverse
    ), label = "pa")
    val arcT by anim.animateFloat(0f, 1f, infiniteRepeatable(
        tween(4000, easing = LinearEasing)
    ), label = "at")
    val gridScroll by anim.animateFloat(0f, 30f, infiniteRepeatable(
        tween(8000, easing = LinearEasing)
    ), label = "gs")
    val scanAngle by anim.animateFloat(0f, 360f, infiniteRepeatable(
        tween(6000, easing = LinearEasing)
    ), label = "scan")
    val glowPulse by anim.animateFloat(0.3f, 0.7f, infiniteRepeatable(
        tween(3000, easing = EaseInOutCubic), RepeatMode.Reverse
    ), label = "gp")

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Layer 1: Grid
        drawSOCGrid(w, h, gridScroll)

        // Layer 2: World coastlines (real coordinates)
        drawRealWorldMap(w, h, glowPulse)

        // Layer 3: Horizontal scan line
        val scanY = (gridScroll / 30f * h) % h
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, DragonCyan.copy(alpha = 0.15f), Color.Transparent)
            ),
            start = Offset(0f, scanY), end = Offset(w, scanY), strokeWidth = 2f
        )

        // Layer 4: Connection
        if (isConnected && userGeo != null && serverGeo != null) {
            val userPt = mercator(userGeo.latitude, userGeo.longitude, w, h)
            val serverPt = mercator(serverGeo.latitude, serverGeo.longitude, w, h)

            // Great-circle arc with glow
            drawGreatCircleArc(userPt, serverPt, arcT, w, h)

            // Radar sweep at server
            drawRadarSweep(serverPt, scanAngle, StatusConnected)

            // Pulsing nodes
            drawSOCNode(userPt, DragonCyan, pulseR, pulseA, "SRC")
            drawSOCNode(serverPt, StatusConnected, pulseR, pulseA, "DST")

            // Distance line info
            val midPt = Offset((userPt.x + serverPt.x) / 2, min(userPt.y, serverPt.y) - 40f)
            val distKm = haversineKm(
                userGeo.latitude, userGeo.longitude,
                serverGeo.latitude, serverGeo.longitude
            )
            // Distance is just for visual effect — drawn as part of the arc
        }
    }
}

// ---------------------------------------------------------------------------
// Mercator projection
// ---------------------------------------------------------------------------

private fun mercator(lat: Double, lon: Double, w: Float, h: Float): Offset {
    val x = ((lon + 180.0) / 360.0 * w).toFloat()
    val latRad = lat * PI / 180.0
    val mercN = ln(tan(PI / 4.0 + latRad / 2.0))
    val y = (h / 2.0 - mercN / PI * h / 2.0).toFloat()
    return Offset(x.coerceIn(12f, w - 12f), y.coerceIn(12f, h - 12f))
}

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
    val R = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return (R * 2 * atan2(sqrt(a), sqrt(1 - a))).toInt()
}

// ---------------------------------------------------------------------------
// SOC Grid — parallax scrolling with latitude/longitude lines
// ---------------------------------------------------------------------------

private fun DrawScope.drawSOCGrid(w: Float, h: Float, scroll: Float) {
    val fine = 20f
    val major = 80f
    val fineColor = Color(0xFF071420)
    val majorColor = Color(0xFF0C2236)

    // Fine grid
    var y = scroll % fine
    while (y < h) {
        drawLine(fineColor, Offset(0f, y), Offset(w, y), 0.3f)
        y += fine
    }
    var x = 0f
    while (x < w) {
        drawLine(fineColor, Offset(x, 0f), Offset(x, h), 0.3f)
        x += fine
    }

    // Major grid (meridians/parallels)
    y = scroll % major
    while (y < h) {
        drawLine(majorColor, Offset(0f, y), Offset(w, y), 0.6f)
        y += major
    }
    x = 0f
    while (x < w) {
        drawLine(majorColor, Offset(x, 0f), Offset(x, h), 0.6f)
        x += major
    }
}

// ---------------------------------------------------------------------------
// Real World Map — actual coastline polygons (simplified)
// ---------------------------------------------------------------------------

private fun DrawScope.drawRealWorldMap(w: Float, h: Float, glow: Float) {
    val coastColor = Color(0xFF1A3D52)
    val fillColor = Color(0xFF0A1E2D)
    val borderGlow = DragonCyan.copy(alpha = glow * 0.12f)

    // Coastline polygons as (lat, lon) pairs — simplified but recognizable
    val continents = listOf(
        // North America
        listOf(
            50.0 to -128.0, 54.0 to -130.0, 60.0 to -140.0, 64.0 to -142.0,
            70.0 to -141.0, 72.0 to -128.0, 70.0 to -100.0, 64.0 to -90.0,
            60.0 to -80.0, 55.0 to -60.0, 50.0 to -56.0, 47.0 to -53.0,
            44.0 to -63.0, 42.0 to -70.0, 40.0 to -74.0, 35.0 to -75.0,
            30.0 to -81.0, 25.0 to -80.0, 25.0 to -82.0, 30.0 to -85.0,
            30.0 to -90.0, 29.0 to -95.0, 26.0 to -97.0, 22.0 to -98.0,
            18.0 to -105.0, 20.0 to -106.0, 24.0 to -110.0, 30.0 to -114.0,
            32.0 to -117.0, 34.0 to -120.0, 38.0 to -123.0, 42.0 to -124.0,
            46.0 to -124.0, 48.0 to -125.0, 50.0 to -128.0
        ),
        // South America
        listOf(
            12.0 to -72.0, 10.0 to -62.0, 7.0 to -52.0, 2.0 to -50.0,
            -3.0 to -41.0, -8.0 to -35.0, -13.0 to -38.0, -18.0 to -39.0,
            -23.0 to -41.0, -28.0 to -48.0, -33.0 to -52.0, -38.0 to -57.0,
            -41.0 to -63.0, -46.0 to -66.0, -50.0 to -68.0, -53.0 to -70.0,
            -55.0 to -69.0, -52.0 to -75.0, -46.0 to -76.0, -40.0 to -73.0,
            -33.0 to -72.0, -27.0 to -71.0, -18.0 to -70.0, -12.0 to -77.0,
            -5.0 to -81.0, 0.0 to -80.0, 5.0 to -77.0, 8.0 to -77.0,
            10.0 to -75.0, 12.0 to -72.0
        ),
        // Europe
        listOf(
            36.0 to -6.0, 37.0 to -2.0, 38.0 to 0.0, 41.0 to 2.0,
            43.0 to 3.0, 44.0 to 8.0, 45.0 to 13.0, 42.0 to 18.0,
            40.0 to 20.0, 38.0 to 24.0, 41.0 to 29.0, 42.0 to 28.0,
            44.0 to 29.0, 46.0 to 30.0, 48.0 to 24.0, 52.0 to 21.0,
            54.0 to 18.0, 55.0 to 10.0, 57.0 to 8.0, 58.0 to 6.0,
            62.0 to 5.0, 64.0 to 10.0, 68.0 to 15.0, 71.0 to 26.0,
            70.0 to 30.0, 65.0 to 30.0, 60.0 to 28.0, 60.0 to 20.0,
            56.0 to 10.0, 54.0 to 9.0, 53.0 to 6.0, 51.0 to 4.0,
            50.0 to 1.0, 49.0 to -1.0, 48.0 to -5.0, 44.0 to -1.0,
            43.0 to -3.0, 43.0 to -9.0, 38.0 to -9.0, 36.0 to -6.0
        ),
        // Africa
        listOf(
            35.0 to -6.0, 37.0 to 10.0, 33.0 to 12.0, 32.0 to 25.0,
            31.0 to 32.0, 22.0 to 37.0, 15.0 to 40.0, 12.0 to 43.0,
            12.0 to 51.0, 2.0 to 42.0, -1.0 to 42.0, -5.0 to 40.0,
            -10.0 to 40.0, -15.0 to 35.0, -25.0 to 35.0, -30.0 to 32.0,
            -34.0 to 27.0, -34.0 to 18.0, -28.0 to 16.0, -22.0 to 14.0,
            -17.0 to 12.0, -12.0 to 14.0, -5.0 to 12.0, 0.0 to 10.0,
            5.0 to 2.0, 4.0 to 7.0, 6.0 to 3.0, 5.0 to -5.0,
            8.0 to -13.0, 12.0 to -16.0, 15.0 to -17.0, 20.0 to -17.0,
            22.0 to -16.0, 28.0 to -13.0, 32.0 to -8.0, 35.0 to -6.0
        ),
        // Asia (mainland)
        listOf(
            42.0 to 28.0, 41.0 to 40.0, 40.0 to 44.0, 38.0 to 48.0,
            30.0 to 48.0, 25.0 to 56.0, 22.0 to 60.0, 25.0 to 62.0,
            25.0 to 66.0, 24.0 to 68.0, 20.0 to 73.0, 22.0 to 79.0,
            18.0 to 83.0, 8.0 to 77.0, 6.0 to 80.0, 15.0 to 100.0,
            10.0 to 104.0, 1.0 to 104.0, 1.0 to 110.0, 7.0 to 117.0,
            20.0 to 110.0, 22.0 to 114.0, 25.0 to 120.0, 30.0 to 122.0,
            35.0 to 120.0, 38.0 to 122.0, 40.0 to 124.0, 42.0 to 130.0,
            45.0 to 133.0, 50.0 to 135.0, 53.0 to 140.0, 55.0 to 137.0,
            58.0 to 140.0, 60.0 to 143.0, 62.0 to 150.0, 64.0 to 160.0,
            66.0 to 170.0, 66.0 to 180.0, 70.0 to 180.0, 72.0 to 140.0,
            72.0 to 100.0, 70.0 to 70.0, 68.0 to 55.0, 60.0 to 30.0,
            55.0 to 28.0, 50.0 to 30.0, 46.0 to 30.0, 44.0 to 29.0,
            42.0 to 28.0
        ),
        // Australia
        listOf(
            -12.0 to 136.0, -12.0 to 142.0, -16.0 to 146.0, -19.0 to 147.0,
            -24.0 to 152.0, -28.0 to 153.0, -33.0 to 152.0, -37.0 to 150.0,
            -39.0 to 146.0, -38.0 to 141.0, -35.0 to 137.0, -35.0 to 136.0,
            -33.0 to 134.0, -32.0 to 132.0, -34.0 to 129.0, -34.0 to 119.0,
            -31.0 to 115.0, -25.0 to 113.0, -22.0 to 114.0, -18.0 to 122.0,
            -15.0 to 129.0, -14.0 to 132.0, -12.0 to 136.0
        ),
        // Japan
        listOf(
            31.0 to 131.0, 33.0 to 130.0, 34.0 to 132.0, 35.0 to 135.0,
            36.0 to 137.0, 37.0 to 137.0, 39.0 to 140.0, 41.0 to 140.0,
            42.0 to 141.0, 44.0 to 145.0, 43.0 to 146.0, 42.0 to 143.0,
            40.0 to 142.0, 37.0 to 141.0, 35.0 to 140.0, 34.0 to 137.0,
            33.0 to 136.0, 31.0 to 131.0
        ),
        // UK + Ireland
        listOf(
            50.0 to -5.0, 51.0 to 1.0, 53.0 to 0.0, 55.0 to -1.0,
            57.0 to -2.0, 58.0 to -5.0, 57.0 to -7.0, 55.0 to -7.0,
            53.0 to -10.0, 52.0 to -10.0, 51.0 to -9.0, 50.0 to -5.0
        ),
        // Greenland
        listOf(
            60.0 to -43.0, 62.0 to -42.0, 66.0 to -35.0, 70.0 to -22.0,
            76.0 to -18.0, 80.0 to -20.0, 82.0 to -30.0, 80.0 to -55.0,
            76.0 to -60.0, 72.0 to -55.0, 66.0 to -52.0, 62.0 to -50.0,
            60.0 to -43.0
        ),
        // Alaska
        listOf(
            52.0 to -172.0, 54.0 to -166.0, 56.0 to -160.0, 58.0 to -152.0,
            60.0 to -148.0, 61.0 to -147.0, 63.0 to -146.0, 65.0 to -143.0,
            70.0 to -141.0, 71.0 to -155.0, 70.0 to -163.0, 66.0 to -168.0,
            60.0 to -170.0, 55.0 to -163.0, 52.0 to -172.0
        )
    )

    continents.forEach { coords ->
        val path = Path()
        coords.forEachIndexed { i, (lat, lon) ->
            val pt = mercator(lat, lon, w, h)
            if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
        }
        path.close()

        // Fill
        drawPath(path, fillColor)
        // Border glow
        drawPath(path, borderGlow, style = Stroke(1.5f))
        // Sharp edge
        drawPath(path, coastColor, style = Stroke(0.8f))
    }
}

// ---------------------------------------------------------------------------
// Great-circle arc with animated data packets
// ---------------------------------------------------------------------------

private fun DrawScope.drawGreatCircleArc(from: Offset, to: Offset, t: Float, w: Float, h: Float) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val dist = sqrt(dx * dx + dy * dy)

    // Arc height scales with distance
    val arcH = min(dist * 0.3f, 80f)
    val midX = (from.x + to.x) / 2
    val midY = min(from.y, to.y) - arcH
    val ctrl = Offset(midX, midY)

    // Full path
    val path = Path().apply {
        moveTo(from.x, from.y)
        quadraticTo(ctrl.x, ctrl.y, to.x, to.y)
    }

    // Ambient glow (wide, faint)
    drawPath(path, DragonCyan.copy(alpha = 0.06f), style = Stroke(12f, cap = StrokeCap.Round))
    drawPath(path, DragonCyan.copy(alpha = 0.10f), style = Stroke(6f, cap = StrokeCap.Round))

    // Base arc
    drawPath(path, DragonCyan.copy(alpha = 0.25f), style = Stroke(2f, cap = StrokeCap.Round))

    // Animated bright segment
    val pm = PathMeasure().apply { setPath(path, false) }
    val len = pm.length
    if (len > 0) {
        val segLen = len * 0.12f
        val start = (t * len) % len
        val end = min(start + segLen, len)
        val seg = Path()
        pm.getSegment(start, end, seg, true)
        drawPath(seg, DragonCyan, style = Stroke(3f, cap = StrokeCap.Round))
    }

    // Data packets (5 traveling dots with trails)
    for (i in 0..4) {
        val pt = ((t + i * 0.2f) % 1f)
        val pos = bezierPoint(from, ctrl, to, pt)
        val trail = bezierPoint(from, ctrl, to, (pt - 0.02f).coerceIn(0f, 1f))
        // Trail
        drawLine(DragonCyan.copy(alpha = 0.4f), trail, pos, strokeWidth = 1.5f)
        // Dot
        drawCircle(DragonCyan, 3f, pos)
        drawCircle(Color.White, 1.5f, pos)
    }

    // Reverse packets (server → client, dimmer)
    for (i in 0..2) {
        val pt = ((1f - t + i * 0.33f) % 1f)
        val pos = bezierPoint(from, ctrl, to, pt)
        drawCircle(StatusConnected.copy(alpha = 0.6f), 2f, pos)
    }
}

private fun bezierPoint(from: Offset, ctrl: Offset, to: Offset, t: Float): Offset {
    val u = 1f - t
    return Offset(
        u * u * from.x + 2 * u * t * ctrl.x + t * t * to.x,
        u * u * from.y + 2 * u * t * ctrl.y + t * t * to.y
    )
}

// ---------------------------------------------------------------------------
// Radar sweep at node
// ---------------------------------------------------------------------------

private fun DrawScope.drawRadarSweep(center: Offset, angle: Float, color: Color) {
    val radius = 35f
    val sweepRadians = Math.toRadians(angle.toDouble())
    val endX = center.x + radius * cos(sweepRadians).toFloat()
    val endY = center.y + radius * sin(sweepRadians).toFloat()

    // Fading radar cone (30° sweep)
    for (i in 0..15) {
        val a = angle - i * 2f
        val r = Math.toRadians(a.toDouble())
        val ex = center.x + radius * cos(r).toFloat()
        val ey = center.y + radius * sin(r).toFloat()
        drawLine(
            color.copy(alpha = (0.15f - i * 0.01f).coerceAtLeast(0f)),
            center, Offset(ex, ey), strokeWidth = 1f
        )
    }
    // Leading edge
    drawLine(color.copy(alpha = 0.4f), center, Offset(endX, endY), strokeWidth = 1.5f)

    // Range rings
    drawCircle(color.copy(alpha = 0.08f), radius, center, style = Stroke(0.5f))
    drawCircle(color.copy(alpha = 0.05f), radius * 0.6f, center, style = Stroke(0.5f))
    drawCircle(color.copy(alpha = 0.03f), radius * 1.4f, center, style = Stroke(0.5f))
}

// ---------------------------------------------------------------------------
// SOC Node with crosshair
// ---------------------------------------------------------------------------

private fun DrawScope.drawSOCNode(
    center: Offset, color: Color, pulseR: Float, pulseA: Float, label: String
) {
    // Outer pulse
    drawCircle(color.copy(alpha = pulseA * 0.3f), pulseR, center)
    // Crosshair
    val ch = 14f
    drawLine(color.copy(alpha = 0.4f), Offset(center.x - ch, center.y), Offset(center.x + ch, center.y), 0.8f)
    drawLine(color.copy(alpha = 0.4f), Offset(center.x, center.y - ch), Offset(center.x, center.y + ch), 0.8f)
    // Inner ring
    drawCircle(color.copy(alpha = 0.5f), 9f, center, style = Stroke(1.2f))
    // Core
    drawCircle(color, 4.5f, center)
    drawCircle(Color.White, 2f, center)
}
