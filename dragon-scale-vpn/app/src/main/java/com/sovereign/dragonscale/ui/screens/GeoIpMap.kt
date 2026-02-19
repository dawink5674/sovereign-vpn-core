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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereign.dragonscale.network.GeoIpClient
import com.sovereign.dragonscale.network.GeoIpResponse
import com.sovereign.dragonscale.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.*

// ===========================================================================
// SOC Threat Map — US-Only, Canvas-Rendered
// ===========================================================================

// US bounding box for projection
private const val US_LAT_N = 50.5
private const val US_LAT_S = 23.5
private const val US_LON_W = -130.0
private const val US_LON_E = -65.0

@Composable
fun ThreatMapPanel(
    isConnected: Boolean,
    preVpnUserLoc: GeoIpResponse? = null,
    serverIp: String = "35.206.67.49",
    modifier: Modifier = Modifier
) {
    // Use the pre-VPN user location passed from VpnDashboardScreen
    // (fetched before VPN connected, so it's the user's real IP location)
    val userLoc = preVpnUserLoc
    var serverLoc by remember { mutableStateOf<GeoIpResponse?>(null) }
    val scope = rememberCoroutineScope()

    // Fetch server location only when connected — with retry
    var serverError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(isConnected) {
        if (isConnected && serverLoc == null) {
            for (attempt in 1..3) {
                try {
                    serverLoc = GeoIpClient.api.lookup(serverIp)
                    serverError = null
                    break
                } catch (e: Exception) {
                    serverError = "Server GeoIP attempt $attempt: ${e.message}"
                    kotlinx.coroutines.delay(2000L)
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("THREAT MAP — CONUS", style = MaterialTheme.typography.labelLarge.copy(
                letterSpacing = 2.sp), color = TextMuted)
            if (isConnected && userLoc != null && serverLoc != null) {
                Text("${userLoc.ip} ➜ $serverIp", style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace, fontSize = 9.sp
                ), color = DragonCyan)
            } else if (isConnected) {
                // Show loading/error state
                val msg = when {
                    userLoc == null && serverError != null -> "GeoIP: retrying..."
                    userLoc == null -> "Locating user..."
                    serverLoc == null -> "Locating server..."
                    else -> "Loading..."
                }
                Text(msg, style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace, fontSize = 9.sp
                ), color = Color(0xFFFF9800))
            }
        }

        Card(
            Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF010612))
        ) {
            Box(Modifier.fillMaxSize()) {
                USMapCanvas(userLoc, serverLoc, isConnected)

                // Overlays
                if (isConnected && userLoc != null && serverLoc != null) {
                    // Bottom-left: location badges
                    Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                        LocBadge("SRC", "${userLoc!!.city}, ${userLoc!!.region}", DragonCyan)
                        Spacer(Modifier.height(3.dp))
                        LocBadge("DST", "${serverLoc!!.city}, ${serverLoc!!.region}", StatusConnected)
                    }
                    // Top-right: status
                    Column(
                        Modifier.align(Alignment.TopEnd).padding(10.dp)
                            .background(Color(0xDD010612), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("● SECURE TUNNEL", style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp, letterSpacing = 1.sp
                        ), color = StatusConnected)
                        Text("WireGuard · ChaCha20", style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 7.sp
                        ), color = TextMuted)
                    }
                }
                if (!isConnected) {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("AWAITING CONNECTION", style = MaterialTheme.typography.labelMedium.copy(
                            letterSpacing = 3.sp), color = TextMuted)
                        Spacer(Modifier.height(4.dp))
                        Text("Connect VPN to activate threat map", style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.sp), color = TextMuted.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LocBadge(tag: String, info: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.background(Color(0xDD010612), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Box(Modifier.size(6.dp).background(color, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(6.dp))
        Text(tag, style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold, fontSize = 9.sp), color = color)
        Spacer(Modifier.width(6.dp))
        Text(info, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = TextSecondary)
    }
}

// ===========================================================================
// Main Canvas
// ===========================================================================

/** Computed viewport for aspect-ratio-preserving projection */
private data class MapView(
    val offsetX: Float, val offsetY: Float,
    val mapW: Float, val mapH: Float,
    val canvasW: Float, val canvasH: Float,
    val pad: Float
)

private fun computeMapView(canvasW: Float, canvasH: Float, pad: Float): MapView {
    val availW = canvasW - 2 * pad
    val availH = canvasH - 2 * pad
    val geoW = US_LON_E - US_LON_W    // 65°
    val geoH = US_LAT_N - US_LAT_S    // 27°
    // Use 1.85 instead of natural 2.41 for more vertical presence
    // (compensates for latitude compression at mid-latitudes)
    val geoAspect = 1.85

    // Fit within available space while preserving aspect ratio
    val (mapW, mapH) = if (availW / availH > geoAspect) {
        // Canvas is wider than US — constrain by height
        (availH * geoAspect).toFloat() to availH
    } else {
        // Canvas is taller than US — constrain by width
        availW to (availW / geoAspect).toFloat()
    }

    // Center the map in the canvas
    val offsetX = pad + (availW - mapW) / 2f
    val offsetY = pad + (availH - mapH) / 2f

    return MapView(offsetX, offsetY, mapW, mapH, canvasW, canvasH, pad)
}

private fun projectMV(lat: Double, lon: Double, mv: MapView): Offset {
    val x = mv.offsetX + ((lon - US_LON_W) / (US_LON_E - US_LON_W) * mv.mapW).toFloat()
    val y = mv.offsetY + ((US_LAT_N - lat) / (US_LAT_N - US_LAT_S) * mv.mapH).toFloat()
    return Offset(
        x.coerceIn(mv.offsetX, mv.offsetX + mv.mapW),
        y.coerceIn(mv.offsetY, mv.offsetY + mv.mapH)
    )
}

@Composable
private fun USMapCanvas(
    userGeo: GeoIpResponse?, serverGeo: GeoIpResponse?, isConnected: Boolean
) {
    val anim = rememberInfiniteTransition(label = "soc")

    val pulseR by anim.animateFloat(8f, 28f, infiniteRepeatable(
        tween(2200, easing = EaseInOutCubic), RepeatMode.Reverse), label = "pr")
    val pulseA by anim.animateFloat(0.9f, 0.05f, infiniteRepeatable(
        tween(2200, easing = EaseInOutCubic), RepeatMode.Reverse), label = "pa")
    val beamT by anim.animateFloat(0f, 1f, infiniteRepeatable(
        tween(3000, easing = LinearEasing)), label = "bt")
    val gridScroll by anim.animateFloat(0f, 24f, infiniteRepeatable(
        tween(10000, easing = LinearEasing)), label = "gs")
    val radarAngle by anim.animateFloat(0f, 360f, infiniteRepeatable(
        tween(5000, easing = LinearEasing)), label = "ra")
    val scanX by anim.animateFloat(0f, 1f, infiniteRepeatable(
        tween(7000, easing = LinearEasing)), label = "sx")
    val breathe by anim.animateFloat(0.4f, 0.8f, infiniteRepeatable(
        tween(4000, easing = EaseInOutCubic), RepeatMode.Reverse), label = "br")

    val measurer = rememberTextMeasurer()

    Canvas(Modifier.fillMaxSize().padding(4.dp)) {
        val w = size.width
        val h = size.height
        val pad = 14f
        val mv = computeMapView(w, h, pad)

        // Layers
        drawTacticalGrid(w, h, gridScroll)
        drawUSCoastline(mv, breathe)
        drawStateLines(mv)
        drawMajorCities(mv, measurer)
        drawVerticalScanLine(w, h, scanX)

        if (isConnected && userGeo != null && serverGeo != null) {
            val uPt = projectMV(userGeo.latitude, userGeo.longitude, mv)
            val sPt = projectMV(serverGeo.latitude, serverGeo.longitude, mv)

            drawLightBeam(uPt, sPt, beamT)
            drawRadar(sPt, radarAngle, StatusConnected)
            drawUserNode(uPt, DragonCyan, pulseR, pulseA)
            drawServerNode(sPt, StatusConnected, pulseR * 0.7f, pulseA)
            drawCoordLabels(uPt, userGeo, measurer, DragonCyan)
            drawCoordLabels(sPt, serverGeo, measurer, StatusConnected)
        }
    }
}

// ===========================================================================
// Tactical grid
// ===========================================================================

private fun DrawScope.drawTacticalGrid(w: Float, h: Float, scroll: Float) {
    val fine = 16f;  val major = 80f
    val fineC = Color(0xFF081422); val majorC = Color(0xFF0E2438)

    var y = scroll % fine; while (y < h) { drawLine(fineC, Offset(0f,y), Offset(w,y), 0.3f); y += fine }
    var x = 0f; while (x < w) { drawLine(fineC, Offset(x,0f), Offset(x,h), 0.3f); x += fine }
    y = scroll % major; while (y < h) { drawLine(majorC, Offset(0f,y), Offset(w,y), 0.6f); y += major }
    x = 0f; while (x < w) { drawLine(majorC, Offset(x,0f), Offset(x,h), 0.6f); x += major }
}

// ===========================================================================
// US Coastline — detailed polygon
// ===========================================================================

private fun DrawScope.drawUSCoastline(mv: MapView, glow: Float) {
    val fill = Color(0xFF0C1E30)
    val edge = Color(0xFF1E4868)
    val glowC = DragonCyan.copy(alpha = glow * 0.08f)

    // Detailed US coastline (lat, lon) — continental US outline
    val coast = listOf(
        // Pacific NW
        48.4 to -124.7, 48.2 to -123.0, 47.5 to -122.4, 46.3 to -124.0,
        44.6 to -124.1, 42.0 to -124.3, 40.8 to -124.2,
        // California
        39.0 to -123.7, 38.3 to -123.1, 37.8 to -122.5, 37.0 to -122.4,
        36.6 to -121.9, 35.5 to -121.0, 34.5 to -120.5, 34.0 to -119.0,
        33.9 to -118.4, 33.2 to -117.4, 32.5 to -117.1,
        // US-Mexico border (west to east)
        32.5 to -117.1, 32.7 to -114.7, 31.3 to -111.1, 31.3 to -108.2,
        31.8 to -106.6, 29.8 to -104.4, 29.4 to -103.0, 28.0 to -100.5,
        26.0 to -97.5,
        // Texas Gulf coast
        26.1 to -97.2, 27.6 to -97.1, 28.3 to -96.4, 29.0 to -95.0,
        29.3 to -94.7, 29.8 to -93.9, 29.7 to -93.3,
        // Louisiana
        29.5 to -92.3, 29.7 to -91.3, 29.0 to -90.0, 29.2 to -89.4,
        29.6 to -89.0, 30.0 to -89.0, 30.2 to -88.0,
        // Mississippi / Alabama / Florida panhandle
        30.2 to -88.0, 30.3 to -87.5, 30.3 to -86.5, 30.0 to -85.5,
        29.9 to -84.5, 29.0 to -83.0,
        // Florida peninsula
        28.5 to -82.6, 27.5 to -82.7, 26.5 to -82.0, 25.8 to -81.2,
        25.2 to -80.3, 25.8 to -80.1, 26.7 to -80.0, 27.5 to -80.2,
        28.5 to -80.6, 29.5 to -81.0, 30.3 to -81.4, 30.7 to -81.5,
        // Georgia / Carolinas
        31.5 to -81.1, 32.0 to -80.8, 32.8 to -79.9, 33.5 to -79.0,
        34.0 to -77.8, 34.7 to -76.5, 35.2 to -75.5,
        // Outer Banks / Virginia / Chesapeake
        35.9 to -75.6, 36.9 to -76.0, 37.0 to -76.4, 37.5 to -76.0,
        37.8 to -75.5, 38.5 to -75.1, 38.8 to -75.0,
        // Delaware / New Jersey
        39.2 to -75.2, 39.5 to -75.6, 39.7 to -74.1, 40.5 to -74.0,
        40.7 to -74.0,
        // New York / Connecticut / New England
        40.6 to -73.8, 41.0 to -72.0, 41.3 to -71.8, 41.5 to -71.4,
        42.0 to -70.7, 42.3 to -70.0, 43.0 to -70.8, 43.5 to -70.2,
        44.3 to -68.2, 44.8 to -67.0, 45.0 to -67.0,
        // Maine / Canadian border east
        47.0 to -67.8, 47.3 to -68.4, 47.0 to -69.1,
        // Northern border (east to west)
        45.0 to -71.5, 45.0 to -74.0, 44.0 to -76.0, 43.5 to -76.5,
        43.5 to -79.0, 42.0 to -83.0, 42.5 to -82.5,
        // Great Lakes approximate shoreline
        43.0 to -82.5, 43.6 to -82.6, 44.8 to -83.5, 45.5 to -84.0,
        46.0 to -84.5, 46.5 to -84.4, 47.0 to -85.0, 47.5 to -87.5,
        47.0 to -88.5, 46.8 to -89.5, 46.5 to -90.5,
        46.8 to -92.0, 48.0 to -89.5, 48.5 to -88.5, 48.0 to -85.0,
        46.5 to -84.5,
        // Northern border west
        46.5 to -84.5, 46.0 to -86.0, 47.0 to -88.0, 47.5 to -90.0,
        48.0 to -90.5, 49.0 to -95.0, 49.0 to -100.0, 49.0 to -105.0,
        49.0 to -110.0, 49.0 to -115.0, 49.0 to -120.0, 49.0 to -123.0,
        48.4 to -124.7
    )

    val path = Path()
    coast.forEachIndexed { i, (lat, lon) ->
        val pt = projectMV(lat, lon, mv)
        if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
    }
    path.close()

    drawPath(path, fill)
    drawPath(path, glowC, style = Stroke(3f))
    drawPath(path, edge, style = Stroke(1.2f))
}

// ===========================================================================
// State boundary lines
// ===========================================================================

private fun DrawScope.drawStateLines(mv: MapView) {
    val lineColor = Color(0xFF153050)

    // Helper
    fun line(lat1: Double, lon1: Double, lat2: Double, lon2: Double) {
        val a = projectMV(lat1, lon1, mv)
        val b = projectMV(lat2, lon2, mv)
        drawLine(lineColor, a, b, 0.5f)
    }

    // Major state border lines (horizontal)
    // Tennessee / NC border ~ 36.5
    line(36.5, -90.3, 36.5, -75.5)
    // Virginia / NC ~ 36.5 (eastern portion already covered)
    // Oklahoma / Texas ~ 33.6-36.5
    line(36.5, -103.0, 36.5, -94.5)
    // Kansas / Oklahoma ~ 37
    line(37.0, -102.0, 37.0, -94.6)
    // Nebraska / Kansas ~ 40
    line(40.0, -102.0, 40.0, -95.3)
    // South Dakota / Nebraska ~ 43
    line(43.0, -104.0, 43.0, -96.4)
    // North Dakota / South Dakota ~ 46
    line(46.0, -104.0, 46.0, -96.5)
    // Montana / Wyoming ~ 45
    line(45.0, -111.0, 45.0, -104.0)
    // Oregon / California ~ 42
    line(42.0, -124.3, 42.0, -117.0)
    // Arizona / Utah ~ 37 (partial)
    line(37.0, -114.0, 37.0, -109.0)
    // Colorado box
    line(41.0, -109.0, 41.0, -102.0)
    line(37.0, -109.0, 37.0, -102.0)
    line(41.0, -109.0, 37.0, -109.0)
    line(41.0, -102.0, 37.0, -102.0)
    // Idaho / Montana ~ 46.5 -116 to -111
    line(46.5, -116.0, 46.5, -111.4)
    // Washington / Oregon ~ 46
    line(46.2, -124.0, 46.2, -117.0)

    // Major vertical lines
    // Mississippi River approx
    line(47.0, -90.0, 29.0, -90.0)
    // Texas / NM ~ -103
    line(36.5, -103.0, 32.0, -103.0)
    // Arizona / NM ~ -109
    line(37.0, -109.0, 31.3, -109.0)
    // Nevada / Utah ~ -114
    line(42.0, -114.0, 37.0, -114.0)
    // Idaho / Oregon ~ -117
    line(46.2, -117.0, 42.0, -117.0)
}

// ===========================================================================
// Major US cities — small dots with labels
// ===========================================================================

private fun DrawScope.drawMajorCities(mv: MapView, measurer: androidx.compose.ui.text.TextMeasurer) {
    val dotColor = Color(0xFF2A5A7E)
    val style = TextStyle(color = Color(0xFF2A5A7E), fontSize = 7.sp, fontFamily = FontFamily.Monospace)

    data class City(val name: String, val lat: Double, val lon: Double)
    val cities = listOf(
        City("NYC", 40.7, -74.0),
        City("LA", 34.1, -118.2),
        City("CHI", 41.9, -87.6),
        City("HOU", 29.8, -95.4),
        City("PHX", 33.4, -112.1),
        City("SEA", 47.6, -122.3),
        City("MIA", 25.8, -80.2),
        City("DEN", 39.7, -105.0),
        City("ATL", 33.7, -84.4),
        City("DFW", 32.8, -96.8),
        City("SF", 37.8, -122.4),
        City("DC", 38.9, -77.0),
        City("BOS", 42.4, -71.1),
        City("LV", 36.2, -115.1),
        City("MSP", 44.9, -93.3),
        City("STL", 38.6, -90.2),
    )

    cities.forEach { c ->
        val pt = projectMV(c.lat, c.lon, mv)
        drawCircle(dotColor, 2.5f, pt)
        val result = measurer.measure(c.name, style)
        drawText(result, topLeft = Offset(pt.x + 5f, pt.y - 5f))
    }
}

// ===========================================================================
// Vertical scan line (sweeps left to right)
// ===========================================================================

private fun DrawScope.drawVerticalScanLine(w: Float, h: Float, t: Float) {
    val x = t * w
    drawLine(
        brush = Brush.verticalGradient(
            listOf(Color.Transparent, DragonCyan.copy(alpha = 0.12f), DragonCyan.copy(alpha = 0.2f),
                DragonCyan.copy(alpha = 0.12f), Color.Transparent)
        ),
        start = Offset(x, 0f), end = Offset(x, h), strokeWidth = 2f
    )
    // trailing fade
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(DragonCyan.copy(alpha = 0.04f), Color.Transparent),
            startX = x - 30f, endX = x
        ),
        topLeft = Offset(x - 30f, 0f),
        size = androidx.compose.ui.geometry.Size(30f, h)
    )
}

// ===========================================================================
// Light beam — the hero visual effect
// ===========================================================================

private fun DrawScope.drawLightBeam(from: Offset, to: Offset, t: Float) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val dist = sqrt(dx * dx + dy * dy)
    val arcH = min(dist * 0.35f, 100f)
    val mid = Offset((from.x + to.x) / 2, min(from.y, to.y) - arcH)

    val path = Path().apply {
        moveTo(from.x, from.y)
        quadraticTo(mid.x, mid.y, to.x, to.y)
    }

    // Layer 1: Wide ambient glow
    drawPath(path, DragonCyan.copy(alpha = 0.04f), style = Stroke(20f, cap = StrokeCap.Round))
    drawPath(path, DragonCyan.copy(alpha = 0.06f), style = Stroke(12f, cap = StrokeCap.Round))
    drawPath(path, DragonCyan.copy(alpha = 0.10f), style = Stroke(6f, cap = StrokeCap.Round))

    // Layer 2: Core beam
    drawPath(path, DragonCyan.copy(alpha = 0.30f), style = Stroke(2.5f, cap = StrokeCap.Round))

    // Layer 3: Animated bright segment ("energy pulse")
    val pm = PathMeasure().apply { setPath(path, false) }
    val len = pm.length
    if (len > 0) {
        // Primary pulse
        val segLen = len * 0.08f
        val start = (t * len) % len
        val end = min(start + segLen, len)
        val seg = Path()
        pm.getSegment(start, end, seg, true)
        drawPath(seg, DragonCyan, style = Stroke(4f, cap = StrokeCap.Round))
        drawPath(seg, Color.White.copy(alpha = 0.6f), style = Stroke(2f, cap = StrokeCap.Round))

        // Secondary pulse (offset)
        val s2 = ((t + 0.5f) * len) % len
        val e2 = min(s2 + segLen * 0.6f, len)
        val seg2 = Path()
        pm.getSegment(s2, e2, seg2, true)
        drawPath(seg2, DragonCyan.copy(alpha = 0.7f), style = Stroke(3f, cap = StrokeCap.Round))
    }

    // Layer 4: Data packets — 6 traveling dots
    for (i in 0..5) {
        val pt = ((t + i * 0.167f) % 1f)
        val pos = qBez(from, mid, to, pt)
        val trail = qBez(from, mid, to, (pt - 0.015f).coerceIn(0f, 1f))
        drawLine(DragonCyan.copy(alpha = 0.5f), trail, pos, 1.5f)
        drawCircle(DragonCyan, 3f, pos)
        drawCircle(Color.White, 1.5f, pos)
    }

    // Layer 5: Reverse flow (server → client) — dimmer green
    for (i in 0..3) {
        val pt = ((1f - t + i * 0.25f) % 1f)
        val pos = qBez(from, mid, to, pt)
        drawCircle(StatusConnected.copy(alpha = 0.5f), 2f, pos)
    }
}

private fun qBez(a: Offset, c: Offset, b: Offset, t: Float): Offset {
    val u = 1f - t
    return Offset(u*u*a.x + 2*u*t*c.x + t*t*b.x, u*u*a.y + 2*u*t*c.y + t*t*b.y)
}

// ===========================================================================
// User node — pulsing concentric rings + crosshair
// ===========================================================================

private fun DrawScope.drawUserNode(c: Offset, color: Color, pr: Float, pa: Float) {
    // Outer pulse rings (3 concentric)
    drawCircle(color.copy(alpha = pa * 0.2f), pr * 1.5f, c)
    drawCircle(color.copy(alpha = pa * 0.35f), pr, c)
    drawCircle(color.copy(alpha = pa * 0.5f), pr * 0.6f, c)

    // Crosshair
    val ch = 18f
    val chColor = color.copy(alpha = 0.5f)
    drawLine(chColor, Offset(c.x - ch, c.y), Offset(c.x - 7f, c.y), 0.8f)
    drawLine(chColor, Offset(c.x + 7f, c.y), Offset(c.x + ch, c.y), 0.8f)
    drawLine(chColor, Offset(c.x, c.y - ch), Offset(c.x, c.y - 7f), 0.8f)
    drawLine(chColor, Offset(c.x, c.y + 7f), Offset(c.x, c.y + ch), 0.8f)

    // Targeting ring
    drawCircle(color.copy(alpha = 0.6f), 10f, c, style = Stroke(1.5f))

    // Core dot (bright, glowing)
    drawCircle(color, 5f, c)
    drawCircle(Color.White, 3f, c)
}

// ===========================================================================
// Server node — pulsing diamond shape
// ===========================================================================

private fun DrawScope.drawServerNode(c: Offset, color: Color, pr: Float, pa: Float) {
    // Pulse
    drawCircle(color.copy(alpha = pa * 0.3f), pr * 1.3f, c)
    drawCircle(color.copy(alpha = pa * 0.5f), pr, c)

    // Diamond shape
    val d = 10f
    val diamond = Path().apply {
        moveTo(c.x, c.y - d)
        lineTo(c.x + d, c.y)
        lineTo(c.x, c.y + d)
        lineTo(c.x - d, c.y)
        close()
    }
    drawPath(diamond, color.copy(alpha = 0.3f))
    drawPath(diamond, color, style = Stroke(1.5f))

    // Core
    drawCircle(color, 4f, c)
    drawCircle(Color.White, 2f, c)
}

// ===========================================================================
// Radar sweep at server
// ===========================================================================

private fun DrawScope.drawRadar(center: Offset, angle: Float, color: Color) {
    val r = 40f
    val rad = Math.toRadians(angle.toDouble())

    // Fading sweep cone
    for (i in 0..20) {
        val a = angle - i * 1.5f
        val ar = Math.toRadians(a.toDouble())
        val ex = center.x + r * cos(ar).toFloat()
        val ey = center.y + r * sin(ar).toFloat()
        drawLine(color.copy(alpha = (0.12f - i * 0.006f).coerceAtLeast(0f)),
            center, Offset(ex, ey), 0.8f)
    }
    // Leading edge
    val edgeX = center.x + r * cos(rad).toFloat()
    val edgeY = center.y + r * sin(rad).toFloat()
    drawLine(color.copy(alpha = 0.35f), center, Offset(edgeX, edgeY), 1.5f)

    // Range rings
    drawCircle(color.copy(alpha = 0.06f), r, center, style = Stroke(0.5f))
    drawCircle(color.copy(alpha = 0.04f), r * 0.5f, center, style = Stroke(0.5f))
    drawCircle(color.copy(alpha = 0.03f), r * 1.5f, center, style = Stroke(0.5f))
}

// ===========================================================================
// Coordinate labels near nodes
// ===========================================================================

private fun DrawScope.drawCoordLabels(
    pt: Offset, geo: GeoIpResponse,
    measurer: androidx.compose.ui.text.TextMeasurer, color: Color
) {
    val label = "%.2f, %.2f".format(geo.latitude, geo.longitude)
    val style = TextStyle(
        color = color.copy(alpha = 0.6f),
        fontSize = 7.sp,
        fontFamily = FontFamily.Monospace
    )
    val result = measurer.measure(label, style)
    drawText(result, topLeft = Offset(pt.x + 14f, pt.y + 8f))
}
