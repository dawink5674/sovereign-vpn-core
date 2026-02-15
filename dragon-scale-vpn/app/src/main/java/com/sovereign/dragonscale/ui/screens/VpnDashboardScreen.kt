package com.sovereign.dragonscale.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowWidthSizeClass
import com.sovereign.dragonscale.ui.theme.*
import com.sovereign.dragonscale.vpn.VpnManager
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()

    var vpnState by remember { mutableStateOf(Tunnel.State.DOWN) }
    var statusMessage by remember { mutableStateOf("Disconnected") }
    var isRegistered by remember { mutableStateOf(vpnManager.isRegistered()) }
    var logEntries by remember { mutableStateOf(listOf<LogEntry>()) }

    // Shared connect logic — used by both expanded and folded layouts
    val handleConnect: () -> Unit = {
        val prepareIntent = vpnManager.prepareVpn(context as Activity)
        if (prepareIntent != null) {
            // VPN permission not yet granted — launch the system consent dialog
            logEntries = logEntries + LogEntry("Requesting VPN permission...")
            statusMessage = "Requesting permission..."
            if (onRequestVpnPermission != null) {
                onRequestVpnPermission(prepareIntent) { granted ->
                    if (granted) {
                        logEntries = logEntries + LogEntry("VPN permission granted")
                        // Now proceed with tunnel toggle
                        scope.launch {
                            statusMessage = "Connecting..."
                            logEntries = logEntries + LogEntry("Initiating tunnel...")
                            val result = vpnManager.toggleTunnel()
                            if (result.isSuccess) {
                                vpnState = result.getOrNull()!!
                                statusMessage = if (vpnState == Tunnel.State.UP) "Connected" else "Disconnected"
                                logEntries = logEntries + LogEntry("Tunnel state: $vpnState")
                            } else {
                                statusMessage = "Error: ${result.exceptionOrNull()?.message}"
                                logEntries = logEntries + LogEntry("ERROR: ${result.exceptionOrNull()?.message}")
                            }
                        }
                    } else {
                        statusMessage = "VPN permission denied"
                        logEntries = logEntries + LogEntry("ERROR: VPN permission denied by user")
                    }
                }
            }
        } else {
            // Permission already granted — toggle tunnel directly
            scope.launch {
                statusMessage = "Connecting..."
                logEntries = logEntries + LogEntry("Initiating tunnel...")
                val result = vpnManager.toggleTunnel()
                if (result.isSuccess) {
                    vpnState = result.getOrNull()!!
                    statusMessage = if (vpnState == Tunnel.State.UP) "Connected" else "Disconnected"
                    logEntries = logEntries + LogEntry("Tunnel state: $vpnState")
                } else {
                    statusMessage = "Error: ${result.exceptionOrNull()?.message}"
                    logEntries = logEntries + LogEntry("ERROR: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    // Shared register logic
    val handleRegister: () -> Unit = {
        scope.launch {
            statusMessage = "Registering..."
            logEntries = logEntries + LogEntry("Registering device...")
            val result = vpnManager.registerDevice("Pixel 10 Pro Fold")
            if (result.isSuccess) {
                isRegistered = true
                statusMessage = "Registered — Ready"
                logEntries = logEntries + LogEntry("Device registered with server")
            } else {
                statusMessage = "Registration failed"
                logEntries = logEntries + LogEntry("ERROR: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    // Detect foldable posture
    val windowInfo = currentWindowAdaptiveInfo()
    val isExpanded = windowInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED

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
        if (isExpanded) {
            // ---- UNFOLDED: Two-pane layout ----
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
                        onRegister = handleRegister,
                        onToggle = handleConnect
                    )
                }

                // Right pane — Logs & Stats
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    StatsPanel(vpnState, logEntries)
                }
            }
        } else {
            // ---- FOLDED: Single column layout ----
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
                    onRegister = handleRegister,
                    onToggle = handleConnect
                )
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

    // Animate the glow ring
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
            // Outer glow ring
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

            // Inner circle
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                SurfaceCardElevated,
                                SurfaceCard
                            )
                        )
                    )
                    .border(1.dp, statusColor.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🐉",
                    fontSize = 64.sp
                )
            }
        }

        // Connect / Register button
        if (!isRegistered) {
            Button(
                onClick = onRegister,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DragonViolet
                )
            ) {
                Text(
                    "REGISTER DEVICE",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        } else {
            Button(
                onClick = onToggle,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(56.dp),
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
                Text(
                    "ENCRYPTION",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
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
// Stats Panel — logs and peer info (shown in unfolded right pane)
// ---------------------------------------------------------------------------

@Composable
fun StatsPanel(vpnState: Tunnel.State, logEntries: List<LogEntry>) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Tunnel stats card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "TUNNEL STATUS",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextMuted
                )
                Spacer(Modifier.height(12.dp))

                StatRow("Protocol", "WireGuard")
                StatRow("Cipher", "ChaCha20-Poly1305")
                StatRow("Key Exchange", "Curve25519 (ECDH)")
                StatRow("Hash", "BLAKE2s")
                StatRow("Port", "51820/UDP")
                StatRow("State", if (vpnState == Tunnel.State.UP) "🟢 UP" else "🔴 DOWN")
            }
        }

        // Live log
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "ROUTING LOG",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextMuted
                )
                Spacer(Modifier.height(8.dp))

                if (logEntries.isEmpty()) {
                    Text(
                        "No activity yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                } else {
                    LazyColumn {
                        items(logEntries.reversed()) { entry ->
                            Text(
                                text = "${entry.timestamp} ${entry.message}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 12.sp
                                ),
                                color = if (entry.message.startsWith("ERROR"))
                                    DragonRed else TextSecondary,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

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

data class LogEntry(
    val message: String,
    val timestamp: String = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
        .format(java.util.Date())
)
