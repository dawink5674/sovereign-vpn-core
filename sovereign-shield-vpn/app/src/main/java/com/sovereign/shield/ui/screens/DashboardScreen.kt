package com.sovereign.shield.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.core.layout.WindowWidthSizeClass
import com.sovereign.shield.network.GeoIpClient
import com.sovereign.shield.network.GeoIpResponse
import com.sovereign.shield.ui.components.*
import com.sovereign.shield.ui.theme.*
import com.sovereign.shield.vpn.NetworkMonitor
import com.sovereign.shield.vpn.VpnManager
import com.sovereign.shield.vpn.VpnSettings
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Main Dashboard Screen — the heart of Sovereign Shield.
 * Adaptive layout for Pixel 10 Pro Fold (expanded/folded).
 */
@Composable
fun DashboardScreen(
    onRequestVpnPermission: ((Intent, (Boolean) -> Unit) -> Unit)? = null,
    onNavigateToStats: () -> Unit = {},
    onNavigateToMap: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToLog: () -> Unit = {}
) {
    val context = LocalContext.current
    val vpnManager = remember { VpnManager.getInstance(context) }
    val networkMonitor = remember { NetworkMonitor.getInstance(context) }
    val vpnSettings = remember { VpnSettings(context) }
    val scope = rememberCoroutineScope()

    // Collect user settings
    val killSwitch by vpnSettings.killSwitch.collectAsState(initial = false)
    val autoConnect by vpnSettings.autoConnect.collectAsState(initial = false)
    val autoReconnect by vpnSettings.autoReconnect.collectAsState(initial = true)
    val dnsProvider by vpnSettings.dnsProvider.collectAsState(initial = "cloudflare")

    val initialState = remember { vpnManager.getTunnelState() }
    var vpnState by remember { mutableStateOf(initialState) }
    var statusMessage by remember {
        mutableStateOf(if (initialState == Tunnel.State.UP) "Connected" else "Disconnected")
    }
    var isRegistered by remember { mutableStateOf(vpnManager.isRegistered()) }
    // Mutex prevents multiple simultaneous toggle/register operations
    val toggleMutex = remember { Mutex() }
    var isActionInProgress by remember { mutableStateOf(false) }

    // Collect notification setting
    val notifyOnStateChange by vpnSettings.notifyOnStateChange.collectAsState(initial = true)

    // Helper to send state-change notification
    fun sendStateNotification(connected: Boolean) {
        if (!notifyOnStateChange) return
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "vpn_state", "VPN State", NotificationManager.IMPORTANCE_LOW
                ).apply { description = "VPN connection state changes" }
                nm.createNotificationChannel(channel)
            }
            val title = if (connected) "VPN Connected" else "VPN Disconnected"
            val text = if (connected) "Sovereign Shield is protecting your connection"
                       else "Your connection is no longer tunneled"
            val notification = NotificationCompat.Builder(context, "vpn_state")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .build()
            nm.notify(9001, notification)
        } catch (_: Exception) { /* permission not granted or context issue */ }
    }

    val serverIp = remember(vpnState) { vpnManager.getServerIp() ?: "35.206.67.49" }
    val isConnected = vpnState == Tunnel.State.UP
    val isConnecting = isActionInProgress

    var preVpnUserLoc by remember { mutableStateOf<GeoIpResponse?>(null) }

    // Fetch real user location
    LaunchedEffect(Unit) {
        if (preVpnUserLoc == null) {
            var attempts = 0
            while (preVpnUserLoc == null && attempts < 5) {
                try {
                    val loc = GeoIpClient.fetchAndCacheRealLocation(context, serverIp)
                    if (!loc.error && loc.latitude != 0.0) { preVpnUserLoc = loc; break }
                } catch (_: Exception) {}
                attempts++
                if (attempts < 5) kotlinx.coroutines.delay(2000)
            }
        }
    }

    LaunchedEffect(isConnected) {
        if (!isConnected) {
            GeoIpClient.clearCache(context)
            try {
                kotlinx.coroutines.delay(1000)
                val loc = GeoIpClient.fetchAndCacheRealLocation(context, serverIp)
                if (!loc.error && loc.latitude != 0.0) preVpnUserLoc = loc
            } catch (_: Exception) { preVpnUserLoc = null }
            return@LaunchedEffect
        }
        if (preVpnUserLoc != null) return@LaunchedEffect
        var attempts = 0
        while (preVpnUserLoc == null && attempts < 7) {
            try {
                val loc = GeoIpClient.fetchAndCacheRealLocation(context, serverIp)
                if (!loc.error && loc.latitude != 0.0) { preVpnUserLoc = loc; break }
                val bypass = GeoIpClient.lookupSelfBypassVpn(context, serverIp)
                if (!bypass.error && bypass.latitude != 0.0) { preVpnUserLoc = bypass; break }
            } catch (_: Exception) {}
            attempts++
            if (attempts < 7) kotlinx.coroutines.delay(3000)
        }
    }

    // Network monitor
    val rxBytes by networkMonitor.rxBytes.collectAsState()
    val txBytes by networkMonitor.txBytes.collectAsState()
    val rxRate by networkMonitor.rxRate.collectAsState()
    val txRate by networkMonitor.txRate.collectAsState()
    val networkType by networkMonitor.networkType.collectAsState()
    val lastHandshake by networkMonitor.lastHandshake.collectAsState()
    val rxSpeedHistory by networkMonitor.rxSpeedHistory.collectAsState()
    val txSpeedHistory by networkMonitor.txSpeedHistory.collectAsState()

    // Auto-resume monitoring
    LaunchedEffect(vpnState) {
        if (vpnState == Tunnel.State.UP && !networkMonitor.isMonitoring) {
            vpnManager.getCurrentTunnel()?.let { networkMonitor.startMonitoring(it) }
        }
    }

    fun Context.findActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    // Auto-connect on first composition if enabled and registered
    LaunchedEffect(autoConnect, isRegistered) {
        if (autoConnect && isRegistered && vpnState == Tunnel.State.DOWN && !isActionInProgress) {
            delay(500) // Brief delay to let UI settle
            statusMessage = "Auto-connecting..."
            val result = vpnManager.connect(killSwitch, dnsProvider)
            if (result.isSuccess) {
                val newState = result.getOrNull() ?: Tunnel.State.DOWN
                vpnState = newState
                statusMessage = if (newState == Tunnel.State.UP) "Connected" else "Disconnected"
                if (newState == Tunnel.State.UP) {
                    vpnManager.getCurrentTunnel()?.let { networkMonitor.startMonitoring(it) }
                }
            }
        }
    }

    // Auto-reconnect: monitor connection state and reconnect on unexpected drops
    LaunchedEffect(autoReconnect, vpnState) {
        if (autoReconnect && isRegistered && vpnState == Tunnel.State.UP) {
            // Poll for unexpected drops while connected
            while (true) {
                delay(5000)
                val actualState = vpnManager.getTunnelState()
                if (actualState == Tunnel.State.DOWN && vpnState == Tunnel.State.UP && !isActionInProgress) {
                    // Unexpected drop detected
                    networkMonitor.addLog("Connection dropped — auto-reconnecting", NetworkMonitor.LogType.NETWORK)
                    statusMessage = "Reconnecting..."
                    isActionInProgress = true
                    try {
                        val result = vpnManager.connect(killSwitch, dnsProvider)
                        if (result.isSuccess) {
                            val newState = result.getOrNull() ?: Tunnel.State.DOWN
                            vpnState = newState
                            statusMessage = if (newState == Tunnel.State.UP) "Connected" else "Reconnect failed"
                            if (newState == Tunnel.State.UP) {
                                vpnManager.getCurrentTunnel()?.let { networkMonitor.startMonitoring(it) }
                            }
                        } else {
                            statusMessage = "Reconnect failed"
                            vpnState = Tunnel.State.DOWN
                        }
                    } finally {
                        isActionInProgress = false
                    }
                    break // Exit loop, LaunchedEffect will restart if state changes
                }
            }
        }
    }

    // Explicit connect — always brings tunnel UP
    fun doConnect() {
        scope.launch {
            if (!toggleMutex.tryLock()) return@launch
            try {
                isActionInProgress = true
                statusMessage = "Securing..."
                networkMonitor.addLog("Connecting tunnel...", NetworkMonitor.LogType.INFO)
                val result = vpnManager.connect(killSwitch, dnsProvider)
                if (result.isSuccess) {
                    val newState = result.getOrNull() ?: Tunnel.State.DOWN
                    vpnState = newState
                    statusMessage = if (newState == Tunnel.State.UP) "Connected" else "Connection failed"
                    networkMonitor.addLog("Tunnel state: $newState", NetworkMonitor.LogType.INFO)
                    sendStateNotification(newState == Tunnel.State.UP)
                    if (newState == Tunnel.State.UP) {
                        vpnManager.getCurrentTunnel()?.let { networkMonitor.startMonitoring(it) }
                    }
                } else {
                    vpnState = vpnManager.getTunnelState()
                    statusMessage = "Error: ${result.exceptionOrNull()?.message}"
                    networkMonitor.addLog("ERROR: ${result.exceptionOrNull()?.message}", NetworkMonitor.LogType.ERROR)
                }
            } catch (e: Exception) {
                vpnState = vpnManager.getTunnelState()
                statusMessage = "Error: ${e.message}"
            } finally {
                isActionInProgress = false
                toggleMutex.unlock()
            }
        }
    }

    // Explicit disconnect — always brings tunnel DOWN
    fun doDisconnect() {
        scope.launch {
            if (!toggleMutex.tryLock()) return@launch
            try {
                isActionInProgress = true
                statusMessage = "Disconnecting..."
                networkMonitor.addLog("Disconnecting tunnel...", NetworkMonitor.LogType.INFO)
                val result = vpnManager.disconnect()
                if (result.isSuccess) {
                    val newState = result.getOrNull() ?: Tunnel.State.DOWN
                    vpnState = newState
                    statusMessage = "Disconnected"
                    networkMonitor.addLog("Tunnel state: $newState", NetworkMonitor.LogType.INFO)
                    sendStateNotification(false)
                    networkMonitor.stopMonitoring()
                } else {
                    vpnState = vpnManager.getTunnelState()
                    statusMessage = "Error: ${result.exceptionOrNull()?.message}"
                    networkMonitor.addLog("ERROR: ${result.exceptionOrNull()?.message}", NetworkMonitor.LogType.ERROR)
                }
            } catch (e: Exception) {
                vpnState = vpnManager.getTunnelState()
                statusMessage = "Error: ${e.message}"
            } finally {
                isActionInProgress = false
                toggleMutex.unlock()
            }
        }
    }

    /**
     * Request VPN permission via the OS dialog, then invoke onGranted.
     * This is the ONLY path that shows the VPN popup to the user.
     */
    fun requestPermissionThenDo(onGranted: () -> Unit) {
        try {
            val activity = context.findActivity()
            if (activity != null) {
                val prepareIntent = GoBackend.VpnService.prepare(activity)
                if (prepareIntent != null) {
                    statusMessage = "Requesting VPN permission..."
                    onRequestVpnPermission?.invoke(prepareIntent) { granted ->
                        if (granted) {
                            onGranted()
                        } else {
                            statusMessage = "VPN permission denied"
                            isActionInProgress = false
                        }
                    }
                } else {
                    // Permission already granted — proceed immediately
                    onGranted()
                }
            } else {
                statusMessage = "Error: no activity context"
                isActionInProgress = false
            }
        } catch (e: Exception) {
            statusMessage = "Error: ${e.message}"
            isActionInProgress = false
            android.util.Log.e("DashboardScreen", "VPN permission request failed", e)
        }
    }

    val handleConnect: () -> Unit = {
        if (!isActionInProgress) {
            if (isConnected) {
                // Already connected — disconnect
                doDisconnect()
            } else {
                // Not connected — request VPN permission, then connect
                requestPermissionThenDo { doConnect() }
            }
        }
    }

    /**
     * Single-tap flow for fresh install:
     * 1. Request VPN permission (shows the OS popup)
     * 2. Register the device with the server
     * 3. Immediately connect the tunnel
     *
     * This ensures the user sees the VPN popup on the FIRST tap,
     * not after a confusing two-step process.
     */
    val handleRegisterAndConnect: () -> Unit = {
        if (!isActionInProgress) {
            // Step 1: Request VPN permission FIRST — the user sees the popup immediately
            requestPermissionThenDo {
                // Step 2: Permission granted — now register and connect
                scope.launch {
                    if (!toggleMutex.tryLock()) return@launch
                    try {
                        isActionInProgress = true
                        statusMessage = "Registering device..."
                        android.util.Log.d("DashboardScreen", "Starting device registration...")
                        val result = vpnManager.registerDevice("Android Device")
                        if (result.isSuccess) {
                            isRegistered = true
                            statusMessage = "Registered — Connecting..."
                            android.util.Log.d("DashboardScreen", "Registration successful, connecting tunnel...")
                            // Step 3: Immediately connect the tunnel
                            val connectResult = vpnManager.connect(killSwitch, dnsProvider)
                            if (connectResult.isSuccess) {
                                val newState = connectResult.getOrNull() ?: Tunnel.State.DOWN
                                vpnState = newState
                                statusMessage = if (newState == Tunnel.State.UP) "Connected" else "Connection failed"
                                android.util.Log.d("DashboardScreen", "Tunnel state: $newState")
                                sendStateNotification(newState == Tunnel.State.UP)
                                if (newState == Tunnel.State.UP) {
                                    vpnManager.getCurrentTunnel()?.let { networkMonitor.startMonitoring(it) }
                                }
                            } else {
                                vpnState = vpnManager.getTunnelState()
                                val err = connectResult.exceptionOrNull()?.message ?: "unknown"
                                statusMessage = "Connect failed: $err"
                                android.util.Log.e("DashboardScreen", "Connect failed: $err")
                                networkMonitor.addLog("Connect failed after register: $err", NetworkMonitor.LogType.ERROR)
                            }
                        } else {
                            val err = result.exceptionOrNull()?.message ?: "unknown error"
                            statusMessage = "Registration failed: $err"
                            android.util.Log.e("DashboardScreen", "Registration failed: $err")
                            networkMonitor.addLog("Registration failed: $err", NetworkMonitor.LogType.ERROR)
                        }
                    } catch (e: Exception) {
                        statusMessage = "Error: ${e.message}"
                        android.util.Log.e("DashboardScreen", "Register+connect failed", e)
                    } finally {
                        isActionInProgress = false
                        toggleMutex.unlock()
                    }
                }
            }
        }
    }

    // Detect foldable
    val windowInfo = currentWindowAdaptiveInfo()
    val isExpanded = windowInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED

    // The button now uses a SINGLE handler that does the right thing
    // regardless of registration state:
    // - Not registered: VPN popup → register → connect (all in one tap)
    // - Registered + disconnected: VPN popup → connect
    // - Registered + connected: disconnect
    val unifiedButtonAction = if (!isRegistered) handleRegisterAndConnect else handleConnect

    if (isExpanded) {
        ExpandedDashboard(
            vpnState = vpnState, statusMessage = statusMessage, isRegistered = isRegistered,
            isConnecting = isConnecting, onToggle = unifiedButtonAction,
            rxBytes = rxBytes, txBytes = txBytes, rxRate = rxRate, txRate = txRate,
            networkType = networkType, lastHandshake = lastHandshake,
            rxSpeedHistory = rxSpeedHistory, txSpeedHistory = txSpeedHistory,
            connectionDuration = vpnManager.connectionDuration,
            connectionCount = vpnManager.getConnectionCount()
        )
    } else {
        FoldedDashboard(
            vpnState = vpnState, statusMessage = statusMessage, isRegistered = isRegistered,
            isConnecting = isConnecting, onToggle = unifiedButtonAction,
            rxBytes = rxBytes, txBytes = txBytes, rxRate = rxRate, txRate = txRate,
            networkType = networkType, lastHandshake = lastHandshake,
            rxSpeedHistory = rxSpeedHistory, txSpeedHistory = txSpeedHistory,
            connectionDuration = vpnManager.connectionDuration,
            connectionCount = vpnManager.getConnectionCount()
        )
    }
}

// ---------------------------------------------------------------------------
// EXPANDED (Unfolded) Dashboard Layout
// ---------------------------------------------------------------------------
@Composable
private fun ExpandedDashboard(
    vpnState: Tunnel.State, statusMessage: String, isRegistered: Boolean,
    isConnecting: Boolean, onToggle: () -> Unit,
    rxBytes: Long, txBytes: Long, rxRate: String, txRate: String,
    networkType: String, lastHandshake: String,
    rxSpeedHistory: List<Float>, txSpeedHistory: List<Float>,
    connectionDuration: Long, connectionCount: Int
) {
    val isConnected = vpnState == Tunnel.State.UP

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SpaceBlack, SpaceDark)
                )
            )
            .statusBarsPadding()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left pane — Connection
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ConnectionPanel(
                isConnected = isConnected, isConnecting = isConnecting,
                isRegistered = isRegistered, statusMessage = statusMessage,
                onToggle = onToggle,
                networkType = networkType, lastHandshake = lastHandshake,
                connectionDuration = connectionDuration
            )
        }

        // Right pane — Stats
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatsPanel(
                rxBytes = rxBytes, txBytes = txBytes, rxRate = rxRate, txRate = txRate,
                rxSpeedHistory = rxSpeedHistory, txSpeedHistory = txSpeedHistory,
                connectionCount = connectionCount
            )
        }
    }
}

// ---------------------------------------------------------------------------
// FOLDED Dashboard Layout
// ---------------------------------------------------------------------------
@Composable
private fun FoldedDashboard(
    vpnState: Tunnel.State, statusMessage: String, isRegistered: Boolean,
    isConnecting: Boolean, onToggle: () -> Unit,
    rxBytes: Long, txBytes: Long, rxRate: String, txRate: String,
    networkType: String, lastHandshake: String,
    rxSpeedHistory: List<Float>, txSpeedHistory: List<Float>,
    connectionDuration: Long, connectionCount: Int
) {
    val isConnected = vpnState == Tunnel.State.UP

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SpaceBlack, SpaceDark)
                )
            )
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            "SOVEREIGN SHIELD",
            style = MaterialTheme.typography.titleLarge.copy(
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Bold
            ),
            color = ShieldBlue
        )

        ConnectionPanel(
            isConnected = isConnected, isConnecting = isConnecting,
            isRegistered = isRegistered, statusMessage = statusMessage,
            onToggle = onToggle,
            networkType = networkType, lastHandshake = lastHandshake,
            connectionDuration = connectionDuration
        )

        StatsPanel(
            rxBytes = rxBytes, txBytes = txBytes, rxRate = rxRate, txRate = txRate,
            rxSpeedHistory = rxSpeedHistory, txSpeedHistory = txSpeedHistory,
            connectionCount = connectionCount
        )
    }
}

// ---------------------------------------------------------------------------
// Connection Panel
// ---------------------------------------------------------------------------
@Composable
private fun ConnectionPanel(
    isConnected: Boolean, isConnecting: Boolean, isRegistered: Boolean,
    statusMessage: String, onToggle: () -> Unit,
    networkType: String, lastHandshake: String, connectionDuration: Long
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status badge
        ConnectionStatusBadge(isConnected = isConnected, isConnecting = isConnecting)

        // Main connect button — single unified action for ALL states
        ShieldConnectButton(
            isConnected = isConnected,
            isConnecting = isConnecting,
            onClick = onToggle
        )

        // Status message shown below the button
        Text(
            statusMessage,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
            color = when {
                statusMessage.startsWith("Error") || statusMessage.startsWith("Registration failed") ||
                statusMessage.startsWith("Connect failed") || statusMessage.startsWith("VPN permission denied") -> StatusDisconnected
                isConnected -> StatusConnected
                statusMessage.contains("...") -> StatusConnecting
                else -> TextMuted
            }
        )

        // Encryption badge
        EncryptionBadge()

        // Connection info when connected
        if (isConnected) {
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Duration", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(
                            formatDuration(connectionDuration),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = TextPrimary
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Network", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        NetworkTypeBadge(networkType)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Handshake", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Text(
                            lastHandshake,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = StatusConnected
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Stats Panel
// ---------------------------------------------------------------------------
@Composable
private fun StatsPanel(
    rxBytes: Long, txBytes: Long, rxRate: String, txRate: String,
    rxSpeedHistory: List<Float>, txSpeedHistory: List<Float>,
    connectionCount: Int
) {
    // Speed cards
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlowCard(
            modifier = Modifier.weight(1f),
            glowColor = ChartDownload
        ) {
            Text("DOWNLOAD", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp), color = TextMuted)
            Spacer(Modifier.height(4.dp))
            Text(
                rxRate,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                ),
                color = ChartDownload
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Total: ${NetworkMonitor.formatBytes(rxBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }

        GlowCard(
            modifier = Modifier.weight(1f),
            glowColor = ChartUpload
        ) {
            Text("UPLOAD", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp), color = TextMuted)
            Spacer(Modifier.height(4.dp))
            Text(
                txRate,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                ),
                color = ChartUpload
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Total: ${NetworkMonitor.formatBytes(txBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }

    // Speed chart
    if (rxSpeedHistory.isNotEmpty() || txSpeedHistory.isNotEmpty()) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "THROUGHPUT",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = TextMuted
            )
            Spacer(Modifier.height(8.dp))
            SpeedChart(
                rxHistory = rxSpeedHistory,
                txHistory = txSpeedHistory,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Session info
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Sessions", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text(
                    "$connectionCount",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Protocol", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text(
                    "WireGuard",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ShieldCyan
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Encryption", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text(
                    "ChaCha20",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = ShieldViolet
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}
