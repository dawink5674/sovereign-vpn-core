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
import androidx.compose.ui.geometry.Size
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
// SOC-Style Geo-IP Threat Map
// ---------------------------------------------------------------------------

data class GeoPoint(val lat: Double, val lon: Double, val label: String, val ip: String)

@Composable
fun ThreatMapPanel(
    isConnected: Boolean,
    serverIp: String = "35.206.67.49",
    modifier: Modifier = Modifier
) {
    var userLocation by remember { mutableStateOf<GeoIpResponse?>(null) }
    var serverLocation by remember { mutableStateOf<GeoIpResponse?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Fetch geo data when connection state changes
    LaunchedEffect(isConnected) {
        if (isConnected) {
            scope.launch {
                try {
                    userLocation = GeoIpClient.api.lookupSelf()
                    serverLocation = GeoIpClient.api.lookup(serverIp)
                    errorMsg = null
                } catch (e: Exception) {
                    errorMsg = "Geo lookup failed"
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "THREAT MAP",
                style = MaterialTheme.typography.labelLarge,
                color = TextMuted
            )
            if (isConnected && userLocation != null) {
                Text(
                    "${userLocation?.query} → $serverIp",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    color = DragonCyan
                )
            }
        }

        // Map canvas
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF050A12))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                ThreatMapCanvas(
                    userGeo = userLocation,
                    serverGeo = serverLocation,
                    isConnected = isConnected
                )

                // Location labels overlay
                if (isConnected && userLocation != null && serverLocation != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    ) {
                        LocationChip("YOU", userLocation!!.city, userLocation!!.country, DragonCyan)
                        Spacer(Modifier.height(4.dp))
                        LocationChip("EXIT", serverLocation!!.city, serverLocation!!.country, StatusConnected)
                    }
                }

                if (!isConnected) {
                    Text(
                        "CONNECT TO ACTIVATE",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.labelMedium,
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
            .background(SurfaceDeep.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            tag,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            ),
            color = color
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "$city, $country",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = TextSecondary
        )
    }
}

// ---------------------------------------------------------------------------
// Canvas-rendered world map with animated connection arc
// ---------------------------------------------------------------------------

@Composable
private fun ThreatMapCanvas(
    userGeo: GeoIpResponse?,
    serverGeo: GeoIpResponse?,
    isConnected: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "map")

    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 6f, targetValue = 18f,
        animationSpec = infiniteRepeatable(tween(1500, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "pulse"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(1500, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "pulseA"
    )
    val arcProgress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "arc"
    )
    val gridScroll by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 20f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)),
        label = "grid"
    )


    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // --- Scrolling grid background ---
        drawGrid(w, h, gridScroll)

        // --- Simplified world coastline ---
        drawWorldOutline(w, h)

        // --- Connection arc + nodes ---
        if (isConnected && userGeo != null && serverGeo != null) {
            val userPt = geoToScreen(userGeo.lat, userGeo.lon, w, h)
            val serverPt = geoToScreen(serverGeo.lat, serverGeo.lon, w, h)

            drawConnectionArc(userPt, serverPt, arcProgress)
            drawPulsingNode(userPt, DragonCyan, pulseRadius, pulseAlpha)
            drawPulsingNode(serverPt, StatusConnected, pulseRadius, pulseAlpha)
        }
    }
}

// --- Mercator projection ---
private fun geoToScreen(lat: Double, lon: Double, w: Float, h: Float): Offset {
    val x = ((lon + 180.0) / 360.0 * w).toFloat()
    val latRad = lat * PI / 180.0
    val mercN = ln(tan(PI / 4.0 + latRad / 2.0))
    val y = (h / 2.0 - mercN / PI * h / 2.0).toFloat()
    return Offset(x.coerceIn(0f, w), y.coerceIn(0f, h))
}

// --- Grid background ---
private fun DrawScope.drawGrid(w: Float, h: Float, scroll: Float) {
    val spacing = 40f
    val gridColor = Color(0xFF0D1B2A)

    // Horizontal lines
    var y = scroll % spacing
    while (y < h) {
        drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 0.5f)
        y += spacing
    }
    // Vertical lines
    var x = 0f
    while (x < w) {
        drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 0.5f)
        x += spacing
    }
}

// --- Simplified continent outlines (major landmasses via rectangles + shapes) ---
private fun DrawScope.drawWorldOutline(w: Float, h: Float) {
    val coastColor = Color(0xFF1B3A4B)
    val coastFill = Color(0xFF0F2233)

    // Helper to draw a continent region
    fun drawRegion(lonW: Double, latN: Double, lonE: Double, latS: Double) {
        val tl = geoToScreen(latN, lonW, w, h)
        val br = geoToScreen(latS, lonE, w, h)
        drawRect(
            color = coastFill,
            topLeft = tl,
            size = Size(br.x - tl.x, br.y - tl.y)
        )
        drawRect(
            color = coastColor,
            topLeft = tl,
            size = Size(br.x - tl.x, br.y - tl.y),
            style = Stroke(0.8f)
        )
    }

    // North America
    drawRegion(-130.0, 55.0, -70.0, 25.0)
    // Central America
    drawRegion(-110.0, 25.0, -80.0, 10.0)
    // South America
    drawRegion(-80.0, 10.0, -35.0, -55.0)
    // Europe
    drawRegion(-10.0, 60.0, 40.0, 35.0)
    // Africa
    drawRegion(-20.0, 35.0, 50.0, -35.0)
    // Asia (main)
    drawRegion(40.0, 60.0, 140.0, 10.0)
    // Southeast Asia / Indonesia
    drawRegion(95.0, 10.0, 140.0, -10.0)
    // Australia
    drawRegion(115.0, -10.0, 155.0, -40.0)
    // Russia/Siberia (upper)
    drawRegion(40.0, 72.0, 180.0, 55.0)
    // Alaska
    drawRegion(-170.0, 72.0, -140.0, 55.0)
    // Greenland
    drawRegion(-55.0, 78.0, -20.0, 60.0)
}

// --- Animated connection arc ---
private fun DrawScope.drawConnectionArc(from: Offset, to: Offset, progress: Float) {
    val midX = (from.x + to.x) / 2
    val midY = min(from.y, to.y) - 60f // arc upward
    val control = Offset(midX, midY)

    val path = Path().apply {
        moveTo(from.x, from.y)
        quadraticTo(control.x, control.y, to.x, to.y)
    }

    // Glow arc (full)
    drawPath(
        path,
        color = DragonCyan.copy(alpha = 0.15f),
        style = Stroke(width = 4f, cap = StrokeCap.Round)
    )

    // Animated bright segment
    val pathMeasure = PathMeasure().apply { setPath(path, false) }
    val length = pathMeasure.length
    val segStart = (progress * length).coerceIn(0f, length)
    val segEnd = ((progress + 0.15f) * length).coerceIn(0f, length)

    if (segEnd > segStart) {
        val segment = Path()
        pathMeasure.getSegment(segStart, segEnd, segment, true)
        drawPath(
            segment,
            color = DragonCyan,
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
    }

    // Data packet dots along arc
    for (i in 0..2) {
        val t = ((progress + i * 0.33f) % 1f)
        val pos = Offset(
            (1 - t) * (1 - t) * from.x + 2 * (1 - t) * t * control.x + t * t * to.x,
            (1 - t) * (1 - t) * from.y + 2 * (1 - t) * t * control.y + t * t * to.y
        )
        drawCircle(DragonCyan, radius = 2.5f, center = pos)
    }
}

// --- Pulsing node indicator ---
private fun DrawScope.drawPulsingNode(center: Offset, color: Color, pulseR: Float, pulseA: Float) {
    // Outer pulse ring
    drawCircle(
        color = color.copy(alpha = pulseA * 0.5f),
        radius = pulseR,
        center = center
    )
    // Middle ring
    drawCircle(
        color = color.copy(alpha = 0.4f),
        radius = 8f,
        center = center,
        style = Stroke(1.5f)
    )
    // Core dot
    drawCircle(
        color = color,
        radius = 4f,
        center = center
    )
}
