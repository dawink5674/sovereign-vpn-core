package com.sovereign.dragonscale.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.core.layout.WindowWidthSizeClass
import com.sovereign.dragonscale.network.GeoIpClient
import com.sovereign.dragonscale.network.GeoIpResponse
import com.sovereign.dragonscale.ui.theme.*
import com.sovereign.dragonscale.vpn.NetworkMonitor
import com.sovereign.dragonscale.vpn.VpnManager
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Log entry types — color-coded
// ---------------------------------------------------------------------------

enum class LogType { INFO, ERROR, TRAFFIC, NETWORK, DNS }

data class LogEntry(
    val message: String,
    val type: LogType = LogType.INFO,
    val timestamp: String = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
        .format(java.util.Date())
)

// Folded navigation destinations
enum class FoldedPage { CONNECTION, TUNNEL_STATUS, ROUTING_LOG, THREAT_MAP }

// ---------------------------------------------------------------------------
// Dashboard Screen — adaptive for Pixel 10 Pro Fold
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnDashboardScreen(
    onRequestVpnPermission: ((Intent, (Boolean) -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val vpnManager = remember { VpnManager(context) }
    val networkMonitor = remember { NetworkMonitor.getInstance(context) }
    val scope = rememberCoroutineScope()

    // Query actual tunnel state — survives fold/unfold and activity recreation
    val initialState = remember { vpnManager.getTunnelState() }
    var vpnState by remember { mutableStateOf(initialState) }
    var statusMessage by remember {
        mutableStateOf(if (initialState == Tunnel.State.UP) "Connected" else "Disconnected")
    }
    var isRegistered by remember { mutableStateOf(vpnManager.isRegistered()) }

    // --- Fetch real user location BEFORE VPN connects ---
    // This runs once at dashboard creation, capturing the user's real public IP
    var preVpnUserLoc by remember { mutableStateOf<GeoIpResponse?>(null) }
    LaunchedEffect(Unit) {
        if (preVpnUserLoc == null) {
            try { preVpnUserLoc = GeoIpClient.api.lookupSelf() } catch (_: Exception) {}
        }
    }

    // Collect network monitor flows
    val monitorLogs by networkMonitor.logs.collectAsState()
    val rxBytes by networkMonitor.rxBytes.collectAsState()
    val txBytes by networkMonitor.txBytes.collectAsState()
    val rxRate by networkMonitor.rxRate.collectAsState()
    val txRate by networkMonitor.txRate.collectAsState()
    val networkType by networkMonitor.networkType.collectAsState()
    val lastHandshake by networkMonitor.lastHandshake.collectAsState()

    // Merged log: monitor logs + manual log entries
    var manualLogs by remember { mutableStateOf(listOf<LogEntry>()) }
    val allLogs = remember(monitorLogs, manualLogs) { (manualLogs + monitorLogs).sortedByDescending { it.timestamp } }

    fun addLog(msg: String, type: LogType = LogType.INFO) {
        manualLogs = manualLogs + LogEntry(msg, type)
    }

    // Auto-resume monitoring if tunnel is UP but monitor died (fold/unfold recovery)
    LaunchedEffect(vpnState) {
        if (vpnState == Tunnel.State.UP && !networkMonitor.isMonitoring) {
            vpnManager.getCurrentTunnel()?.let { networkMonitor.startMonitoring(it) }
        }
    }

    // Helper to safely find the Activity from any Context wrapper
    fun Context.findActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    // Shared tunnel toggle with error handling
    fun doToggle() {
        scope.launch {
            try {
                statusMessage = "Connecting..."
                addLog("Initiating tunnel...", LogType.INFO)
                val result = vpnManager.toggleTunnel()
                if (result.isSuccess) {
                    val newState = result.getOrNull() ?: Tunnel.State.DOWN
                    vpnState = newState
                    statusMessage = if (newState == Tunnel.State.UP) "Connected" else "Disconnected"
                    addLog("Tunnel state: $newState", LogType.INFO)
                    if (newState == Tunnel.State.UP) {
                        vpnManager.getCurrentTunnel()?.let { networkMonitor.startMonitoring(it) }
                    } else {
                        networkMonitor.stopMonitoring()
                        addLog("Tunnel DOWN — monitoring stopped", LogType.INFO)
                    }
                } else {
                    statusMessage = "Error: ${result.exceptionOrNull()?.message}"
                    addLog("ERROR: ${result.exceptionOrNull()?.message}", LogType.ERROR)
                }
            } catch (e: Exception) {
                statusMessage = "Error: ${e.message}"
                addLog("CRASH prevented: ${e.message}", LogType.ERROR)
            }
        }
    }

    // Shared connect logic
    val handleConnect: () -> Unit = {
        try {
            val activity = context.findActivity()
            if (activity == null) {
                statusMessage = "Error: no activity context"
                addLog("ERROR: Could not find Activity", LogType.ERROR)
            } else {
                val prepareIntent = vpnManager.prepareVpn(activity)
                if (prepareIntent != null) {
                    addLog("Requesting VPN permission...", LogType.INFO)
                    statusMessage = "Requesting permission..."
                    if (onRequestVpnPermission != null) {
                        onRequestVpnPermission(prepareIntent) { granted ->
                            if (granted) {
                                addLog("VPN permission granted", LogType.INFO)
                                doToggle()
                            } else {
                                statusMessage = "VPN permission denied"
                                addLog("VPN permission denied by user", LogType.ERROR)
                            }
                        }
                    }
                } else {
                    // Permission already granted
                    doToggle()
                }
            }
        } catch (e: Exception) {
            statusMessage = "Error: ${e.message}"
            addLog("CRASH prevented: ${e.message}", LogType.ERROR)
        }
    }

    // Shared register logic
    val handleRegister: () -> Unit = {
        scope.launch {
            statusMessage = "Registering..."
            addLog("Registering device...", LogType.INFO)
            val result = vpnManager.registerDevice("Pixel 10 Pro Fold")
            if (result.isSuccess) {
                isRegistered = true
                statusMessage = "Registered — Ready"
                addLog("Device registered with server", LogType.INFO)
            } else {
                statusMessage = "Registration failed"
                addLog("ERROR: ${result.exceptionOrNull()?.message}", LogType.ERROR)
            }
        }
    }

    // Detect foldable
    val windowInfo = currentWindowAdaptiveInfo()
    val isExpanded = windowInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED

    if (isExpanded) {
        ExpandedLayout(
            vpnState = vpnState,
            statusMessage = statusMessage,
            isRegistered = isRegistered,
            onRegister = handleRegister,
            onToggle = handleConnect,
            logEntries = allLogs,
            rxBytes = rxBytes,
            txBytes = txBytes,
            rxRate = rxRate,
            txRate = txRate,
            networkType = networkType,
            lastHandshake = lastHandshake,
            preVpnUserLoc = preVpnUserLoc
        )
    } else {
        FoldedLayout(
            vpnState = vpnState,
            statusMessage = statusMessage,
            isRegistered = isRegistered,
            onRegister = handleRegister,
            onToggle = handleConnect,
            logEntries = allLogs,
            rxBytes = rxBytes,
            txBytes = txBytes,
            rxRate = rxRate,
            txRate = txRate,
            networkType = networkType,
            lastHandshake = lastHandshake,
            preVpnUserLoc = preVpnUserLoc
        )
    }
}

// ---------------------------------------------------------------------------
// EXPANDED (Unfolded) Layout — Two pane with swipeable right pane
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpandedLayout(
    vpnState: Tunnel.State,
    statusMessage: String,
    isRegistered: Boolean,
    onRegister: () -> Unit,
    onToggle: () -> Unit,
    logEntries: List<LogEntry>,
    rxBytes: Long,
    txBytes: Long,
    rxRate: String,
    txRate: String,
    networkType: String,
    lastHandshake: String,
    preVpnUserLoc: GeoIpResponse? = null
) {
    val isConnected = vpnState == Tunnel.State.UP

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "DRAGON SCALE",
                        style = MaterialTheme.typography.titleLarge.copy(
                            letterSpacing = 4.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = DragonCyan
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = SurfaceDeep
                )
            )
        },
        containerColor = SurfaceDeep
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left pane — Controls
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ConnectionPanel(
                    vpnState = vpnState,
                    statusMessage = statusMessage,
                    isRegistered = isRegistered,
                    onRegister = onRegister,
                    onToggle = onToggle
                )
            }

            // Right pane — Swipeable: Tunnel Status | Routing Log | Threat Map
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                SwipeableStatsPanel(
                    vpnState = vpnState,
                    logEntries = logEntries,
                    rxBytes = rxBytes,
                    txBytes = txBytes,
                    rxRate = rxRate,
                    txRate = txRate,
                    networkType = networkType,
                    lastHandshake = lastHandshake,
                    isConnected = isConnected,
                    preVpnUserLoc = preVpnUserLoc
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// FOLDED Layout — Hamburger drawer + sub-pages
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoldedLayout(
    vpnState: Tunnel.State,
    statusMessage: String,
    isRegistered: Boolean,
    onRegister: () -> Unit,
    onToggle: () -> Unit,
    logEntries: List<LogEntry>,
    rxBytes: Long,
    txBytes: Long,
    rxRate: String,
    txRate: String,
    networkType: String,
    lastHandshake: String,
    preVpnUserLoc: GeoIpResponse? = null
) {
    val isConnected = vpnState == Tunnel.State.UP
    var currentPage by remember { mutableStateOf(FoldedPage.CONNECTION) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = SurfaceDark,
                modifier = Modifier.width(260.dp)
            ) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "DRAGON SCALE",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium.copy(
                        letterSpacing = 3.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = DragonCyan
                )
                HorizontalDivider(color = SurfaceCard, modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))

                DrawerMenuItem("🐉", "Connection", currentPage == FoldedPage.CONNECTION) {
                    currentPage = FoldedPage.CONNECTION
                    scope.launch { drawerState.close() }
                }
                DrawerMenuItem("🔒", "Tunnel Status", currentPage == FoldedPage.TUNNEL_STATUS) {
                    currentPage = FoldedPage.TUNNEL_STATUS
                    scope.launch { drawerState.close() }
                }
                DrawerMenuItem("📊", "Routing Log", currentPage == FoldedPage.ROUTING_LOG) {
                    currentPage = FoldedPage.ROUTING_LOG
                    scope.launch { drawerState.close() }
                }
                DrawerMenuItem("🗺️", "Threat Map", currentPage == FoldedPage.THREAT_MAP) {
                    currentPage = FoldedPage.THREAT_MAP
                    scope.launch { drawerState.close() }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        if (currentPage == FoldedPage.CONNECTION) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, "Menu", tint = TextSecondary)
                            }
                        } else {
                            IconButton(onClick = { currentPage = FoldedPage.CONNECTION }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary)
                            }
                        }
                    },
                    title = {
                        Text(
                            when (currentPage) {
                                FoldedPage.CONNECTION -> "DRAGON SCALE"
                                FoldedPage.TUNNEL_STATUS -> "TUNNEL STATUS"
                                FoldedPage.ROUTING_LOG -> "ROUTING LOG"
                                FoldedPage.THREAT_MAP -> "THREAT MAP"
                            },
                            style = MaterialTheme.typography.titleLarge.copy(
                                letterSpacing = 4.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = DragonCyan
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = SurfaceDeep
                    )
                )
            },
            containerColor = SurfaceDeep
        ) { padding ->
            when (currentPage) {
                FoldedPage.CONNECTION -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ConnectionPanel(
                            vpnState = vpnState,
                            statusMessage = statusMessage,
                            isRegistered = isRegistered,
                            onRegister = onRegister,
                            onToggle = onToggle
                        )
                    }
                }
                FoldedPage.TUNNEL_STATUS -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp)
                    ) {
                        TunnelStatusCard(
                            vpnState = vpnState,
                            rxBytes = rxBytes,
                            txBytes = txBytes,
                            rxRate = rxRate,
                            txRate = txRate,
                            networkType = networkType,
                            lastHandshake = lastHandshake
                        )
                    }
                }
                FoldedPage.ROUTING_LOG -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp)
                    ) {
                        EnhancedRoutingLog(logEntries)
                    }
                }
                FoldedPage.THREAT_MAP -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp)
                    ) {
                        ThreatMapPanel(isConnected = isConnected, preVpnUserLoc = preVpnUserLoc)
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerMenuItem(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { Text(icon, fontSize = 20.sp) },
        label = {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) DragonCyan else TextSecondary
            )
        },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = DragonCyan.copy(alpha = 0.1f),
            unselectedContainerColor = Color.Transparent
        )
    )
}

// ---------------------------------------------------------------------------
// Swipeable Stats Panel (Unfolded right pane)
// ---------------------------------------------------------------------------

@Composable
private fun SwipeableStatsPanel(
    vpnState: Tunnel.State,
    logEntries: List<LogEntry>,
    rxBytes: Long,
    txBytes: Long,
    rxRate: String,
    txRate: String,
    networkType: String,
    lastHandshake: String,
    isConnected: Boolean,
    preVpnUserLoc: GeoIpResponse? = null
) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val pageLabels = listOf("STATUS", "LOG", "MAP")

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            pageLabels.forEachIndexed { index, label ->
                val isSelected = pagerState.currentPage == index
                TextButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 2.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isSelected) DragonCyan else TextMuted
                    )
                }
                if (index < pageLabels.lastIndex) {
                    Text("·", color = TextMuted, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }

        // Dot indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (pagerState.currentPage == index) DragonCyan else TextMuted
                        )
                )
            }
        }

        // Pages
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> TunnelStatusCard(vpnState, rxBytes, txBytes, rxRate, txRate, networkType, lastHandshake)
                1 -> EnhancedRoutingLog(logEntries)
                2 -> ThreatMapPanel(isConnected = isConnected, preVpnUserLoc = preVpnUserLoc)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Connection Panel — large connect button + status
// ---------------------------------------------------------------------------

@Composable
fun ConnectionPanel(
    vpnState: Tunnel.State,
    statusMessage: String,
    isRegistered: Boolean,
    onRegister: () -> Unit,
    onToggle: () -> Unit
) {
    val isConnected = vpnState == Tunnel.State.UP

    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val statusColor by animateColorAsState(
        targetValue = when {
            isConnected -> StatusConnected
            statusMessage.contains("Connecting") || statusMessage.contains("Registering") -> StatusConnecting
            else -> StatusDisconnected
        },
        animationSpec = tween(500),
        label = "statusColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Status indicator
        Text(
            text = statusMessage.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
            color = statusColor
        )

        // Shield icon area with glow
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(200.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        brush = Brush.radialGradient(
                            colors = listOf(
                                statusColor.copy(alpha = glowAlpha),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(SurfaceCardElevated, SurfaceCard)
                        )
                    )
                    .border(1.dp, statusColor.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🐉", fontSize = 64.sp)
            }
        }

        // Connect / Register button
        if (!isRegistered) {
            Button(
                onClick = onRegister,
                modifier = Modifier.fillMaxWidth(0.7f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DragonViolet)
            ) {
                Text("REGISTER DEVICE", style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth(0.7f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) DragonRed else DragonCyan,
                    contentColor = SurfaceDeep
                )
            ) {
                Text(
                    text = if (isConnected) "DISCONNECT" else "CONNECT",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        // Encryption badge
        Card(
            modifier = Modifier.fillMaxWidth(0.8f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("ENCRYPTION", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Curve25519 • ChaCha20-Poly1305 • BLAKE2s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DragonCyan,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tunnel Status Card — enhanced with live stats
// ---------------------------------------------------------------------------

@Composable
fun TunnelStatusCard(
    vpnState: Tunnel.State,
    rxBytes: Long,
    txBytes: Long,
    rxRate: String,
    txRate: String,
    networkType: String,
    lastHandshake: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("TUNNEL STATUS", style = MaterialTheme.typography.labelLarge, color = TextMuted)
            Spacer(Modifier.height(12.dp))

            StatRow("Protocol", "WireGuard")
            StatRow("Cipher", "ChaCha20-Poly1305")
            StatRow("Key Exchange", "Curve25519 (ECDH)")
            StatRow("Hash", "BLAKE2s")
            StatRow("Port", "51820/UDP")
            StatRow("State", if (vpnState == Tunnel.State.UP) "🟢 UP" else "🔴 DOWN")

            if (vpnState == Tunnel.State.UP) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = SurfaceCardElevated
                )
                Text("LIVE METRICS", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Spacer(Modifier.height(8.dp))
                StatRow("Network", networkType)
                StatRow("Last Handshake", lastHandshake)
                StatRow("↓ Download", "${NetworkMonitor.formatBytes(rxBytes)} ($rxRate)")
                StatRow("↑ Upload", "${NetworkMonitor.formatBytes(txBytes)} ($txRate)")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Enhanced Routing Log — color-coded with type icons
// ---------------------------------------------------------------------------

@Composable
fun EnhancedRoutingLog(logEntries: List<LogEntry>) {
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("ROUTING LOG", style = MaterialTheme.typography.labelLarge, color = TextMuted)
            Spacer(Modifier.height(8.dp))

            if (logEntries.isEmpty()) {
                Text(
                    "No activity yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            } else {
                LazyColumn {
                    items(logEntries) { entry ->
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Type icon
                            Text(
                                text = when (entry.type) {
                                    LogType.INFO -> "ℹ️"
                                    LogType.ERROR -> "🔴"
                                    LogType.TRAFFIC -> "📶"
                                    LogType.NETWORK -> "🌐"
                                    LogType.DNS -> "🔍"
                                },
                                fontSize = 10.sp,
                                modifier = Modifier.width(20.dp)
                            )
                            // Timestamp
                            Text(
                                text = entry.timestamp,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                color = TextMuted,
                                modifier = Modifier.width(65.dp)
                            )
                            // Message
                            Text(
                                text = entry.message,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                color = when (entry.type) {
                                    LogType.ERROR -> DragonRed
                                    LogType.TRAFFIC -> StatusConnected
                                    LogType.NETWORK -> DragonViolet
                                    LogType.DNS -> DragonCyan
                                    LogType.INFO -> TextSecondary
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}
