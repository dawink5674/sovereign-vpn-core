package com.sovereign.shield.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereign.shield.ui.theme.*
import com.sovereign.shield.vpn.NetworkMonitor

@Composable
fun LogScreen() {
    val context = LocalContext.current
    val networkMonitor = remember { NetworkMonitor.getInstance(context) }
    val logs by networkMonitor.logs.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to latest
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.lastIndex)
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
                "SECURITY LOG",
                style = MaterialTheme.typography.titleLarge.copy(
                    letterSpacing = 4.sp, fontWeight = FontWeight.Bold
                ),
                color = ShieldBlue
            )
            Text(
                "${logs.size} entries",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📋", fontSize = 48.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No log entries yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextMuted
                    )
                    Text(
                        "Connect to start logging activity",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextFaint
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { entry ->
                    LogEntryRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: NetworkMonitor.LogEntry) {
    val color = when (entry.type) {
        NetworkMonitor.LogType.INFO -> ShieldBlue
        NetworkMonitor.LogType.ERROR -> StatusDisconnected
        NetworkMonitor.LogType.TRAFFIC -> ShieldCyan
        NetworkMonitor.LogType.NETWORK -> ShieldViolet
        NetworkMonitor.LogType.DNS -> StatusConnecting
        NetworkMonitor.LogType.SECURITY -> StatusConnected
    }

    val icon = when (entry.type) {
        NetworkMonitor.LogType.INFO -> "ℹ️"
        NetworkMonitor.LogType.ERROR -> "⚠️"
        NetworkMonitor.LogType.TRAFFIC -> "📊"
        NetworkMonitor.LogType.NETWORK -> "🌐"
        NetworkMonitor.LogType.DNS -> "🔍"
        NetworkMonitor.LogType.SECURITY -> "🔒"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SpaceCard.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 14.sp)
        Text(
            entry.timestamp,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = TextMuted
        )
        Text(
            entry.message,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = color,
            modifier = Modifier.weight(1f)
        )
    }
}
