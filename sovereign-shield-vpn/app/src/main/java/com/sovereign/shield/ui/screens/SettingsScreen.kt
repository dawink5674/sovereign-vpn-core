package com.sovereign.shield.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereign.shield.ui.components.GlassCard
import com.sovereign.shield.ui.theme.*
import com.sovereign.shield.vpn.VpnSettings
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val settings = remember { VpnSettings(context) }
    val scope = rememberCoroutineScope()

    val killSwitch by settings.killSwitch.collectAsState(initial = false)
    val autoConnect by settings.autoConnect.collectAsState(initial = false)
    val autoReconnect by settings.autoReconnect.collectAsState(initial = true)
    val biometricLock by settings.biometricLock.collectAsState(initial = false)
    val showThreatMap by settings.showThreatMap.collectAsState(initial = true)
    val notifyStateChange by settings.notifyOnStateChange.collectAsState(initial = true)
    val dnsProvider by settings.dnsProvider.collectAsState(initial = "cloudflare")

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
            "SETTINGS",
            style = MaterialTheme.typography.titleLarge.copy(
                letterSpacing = 4.sp, fontWeight = FontWeight.Bold
            ),
            color = ShieldBlue
        )
        Spacer(Modifier.height(4.dp))

        // Security Section
        SectionHeader("SECURITY")

        SettingsToggle(
            icon = Icons.Default.Shield,
            title = "Kill Switch",
            subtitle = "Block all traffic when VPN disconnects",
            checked = killSwitch,
            onCheckedChange = { scope.launch { settings.setKillSwitch(it) } },
            accentColor = StatusDisconnected
        )

        SettingsToggle(
            icon = Icons.Default.Fingerprint,
            title = "Biometric Lock",
            subtitle = "Require fingerprint to open app",
            checked = biometricLock,
            onCheckedChange = { scope.launch { settings.setBiometricLock(it) } },
            accentColor = ShieldViolet
        )

        // Connection Section
        SectionHeader("CONNECTION")

        SettingsToggle(
            icon = Icons.Default.PlayArrow,
            title = "Auto-Connect",
            subtitle = "Connect VPN when app launches",
            checked = autoConnect,
            onCheckedChange = { scope.launch { settings.setAutoConnect(it) } }
        )

        SettingsToggle(
            icon = Icons.Default.Refresh,
            title = "Auto-Reconnect",
            subtitle = "Automatically reconnect on drop",
            checked = autoReconnect,
            onCheckedChange = { scope.launch { settings.setAutoReconnect(it) } }
        )

        // DNS Section
        SectionHeader("DNS PROVIDER")

        DnsSelector(
            selected = dnsProvider,
            onSelect = { scope.launch { settings.setDnsProvider(it) } }
        )

        // UI Section
        SectionHeader("INTERFACE")

        SettingsToggle(
            icon = Icons.Default.Map,
            title = "Threat Map",
            subtitle = "Show geographic connection visualization",
            checked = showThreatMap,
            onCheckedChange = { scope.launch { settings.setShowThreatMap(it) } }
        )

        SettingsToggle(
            icon = Icons.Default.Notifications,
            title = "State Notifications",
            subtitle = "Notify on connect/disconnect",
            checked = notifyStateChange,
            onCheckedChange = { scope.launch { settings.setNotifyOnStateChange(it) } }
        )

        // About section
        SectionHeader("ABOUT")
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Sovereign Shield VPN", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("Version 1.0.0", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
                Text("🛡️", fontSize = 32.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Zero-trust WireGuard VPN powered by your personal GCP infrastructure. " +
                "Private keys never leave your device.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge.copy(
            letterSpacing = 3.sp,
            fontWeight = FontWeight.Bold
        ),
        color = TextMuted,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: androidx.compose.ui.graphics.Color = ShieldBlue
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon, contentDescription = title,
                tint = if (checked) accentColor else TextMuted,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = accentColor,
                    checkedTrackColor = accentColor.copy(alpha = 0.3f),
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = SpaceCard
                )
            )
        }
    }
}

@Composable
private fun DnsSelector(selected: String, onSelect: (String) -> Unit) {
    val options = listOf(
        Triple("cloudflare", "Cloudflare", "1.1.1.1 — Fast & private"),
        Triple("google", "Google", "8.8.8.8 — Reliable"),
        Triple("quad9", "Quad9", "9.9.9.9 — Security-focused")
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, name, desc) ->
            val isSelected = selected == key
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(key) },
                borderColor = if (isSelected) ShieldBlue.copy(alpha = 0.5f) else GlassBorder,
                backgroundColor = if (isSelected) ShieldBlue.copy(alpha = 0.1f) else SpaceCard.copy(alpha = 0.7f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelect(key) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = ShieldBlue,
                            unselectedColor = TextMuted
                        )
                    )
                    Column {
                        Text(name, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }
        }
    }
}
