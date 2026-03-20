package com.sovereign.shield.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereign.shield.crypto.EncryptedPrefs
import com.sovereign.shield.ui.components.*
import com.sovereign.shield.ui.theme.*
import com.sovereign.shield.vpn.NetworkMonitor
import com.sovereign.shield.vpn.VpnManager

@Composable
fun StatsScreen() {
    val context = LocalContext.current
    val networkMonitor = remember { NetworkMonitor.getInstance(context) }
    val vpnManager = remember { VpnManager(context) }
    val prefs = remember { EncryptedPrefs(context) }

    val rxBytes by networkMonitor.rxBytes.collectAsState()
    val txBytes by networkMonitor.txBytes.collectAsState()
    val rxRate by networkMonitor.rxRate.collectAsState()
    val txRate by networkMonitor.txRate.collectAsState()
    val peakRx by networkMonitor.peakRxRate.collectAsState()
    val peakTx by networkMonitor.peakTxRate.collectAsState()
    val rxHistory by networkMonitor.rxSpeedHistory.collectAsState()
    val txHistory by networkMonitor.txSpeedHistory.collectAsState()
    val lastHandshake by networkMonitor.lastHandshake.collectAsState()
    val networkType by networkMonitor.networkType.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(SpaceBlack, SpaceDark)))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "STATISTICS",
            style = MaterialTheme.typography.titleLarge.copy(
                letterSpacing = 4.sp, fontWeight = FontWeight.Bold
            ),
            color = ShieldBlue
        )

        // Current speed
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlowCard(modifier = Modifier.weight(1f), glowColor = ChartDownload) {
                Text("DOWNLOAD", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp), color = TextMuted)
                Spacer(Modifier.height(4.dp))
                Text(
                    rxRate,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace
                    ),
                    color = ChartDownload
                )
            }
            GlowCard(modifier = Modifier.weight(1f), glowColor = ChartUpload) {
                Text("UPLOAD", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp), color = TextMuted)
                Spacer(Modifier.height(4.dp))
                Text(
                    txRate,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace
                    ),
                    color = ChartUpload
                )
            }
        }

        // Speed chart
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("THROUGHPUT HISTORY", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp), color = TextMuted)
            Spacer(Modifier.height(8.dp))
            SpeedChart(rxHistory = rxHistory, txHistory = txHistory, modifier = Modifier.fillMaxWidth().height(160.dp))
        }

        // Session stats
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("SESSION", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp), color = TextMuted)
            Spacer(Modifier.height(12.dp))
            StatRow("Data Received", NetworkMonitor.formatBytes(rxBytes), ChartDownload)
            StatRow("Data Sent", NetworkMonitor.formatBytes(txBytes), ChartUpload)
            StatRow("Peak Download", NetworkMonitor.formatRate(peakRx), ShieldCyan)
            StatRow("Peak Upload", NetworkMonitor.formatRate(peakTx), ShieldViolet)
            StatRow("Last Handshake", lastHandshake, StatusConnected)
            StatRow("Network Type", networkType, ShieldBlue)
        }

        // Lifetime stats
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("LIFETIME", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp), color = TextMuted)
            Spacer(Modifier.height(12.dp))
            StatRow("Total Sessions", "${prefs.getConnectionCount()}", ShieldBlue)
            StatRow("Total Downloaded", NetworkMonitor.formatBytes(prefs.getTotalRx() + rxBytes), ChartDownload)
            StatRow("Total Uploaded", NetworkMonitor.formatBytes(prefs.getTotalTx() + txBytes), ChartUpload)
            StatRow("Key Rotations", "${prefs.getKeyRotationCount()}", ShieldViolet)
        }

        // Security info
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text("SECURITY", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp), color = TextMuted)
            Spacer(Modifier.height(12.dp))
            StatRow("Protocol", "WireGuard", ShieldBlue)
            StatRow("Cipher", "ChaCha20-Poly1305", ShieldCyan)
            StatRow("Key Exchange", "Curve25519 (ECDH)", ShieldViolet)
            StatRow("PFS", "Per-session preshared key", StatusConnected)
            StatRow("Key Length", "256-bit", StatusConnected)
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
            ),
            color = valueColor
        )
    }
}
