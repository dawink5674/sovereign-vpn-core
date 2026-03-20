package com.sovereign.shield.vpn

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vpn_settings")

/**
 * App settings backed by DataStore.
 * Manages user preferences for VPN behavior and UI.
 */
class VpnSettings(private val context: Context) {

    // Kill Switch — block all traffic when VPN disconnects
    private val KILL_SWITCH = booleanPreferencesKey("kill_switch")
    val killSwitch: Flow<Boolean> = context.dataStore.data.map { it[KILL_SWITCH] ?: false }
    suspend fun setKillSwitch(enabled: Boolean) {
        context.dataStore.edit { it[KILL_SWITCH] = enabled }
    }

    // Auto-connect on app launch
    private val AUTO_CONNECT = booleanPreferencesKey("auto_connect")
    val autoConnect: Flow<Boolean> = context.dataStore.data.map { it[AUTO_CONNECT] ?: false }
    suspend fun setAutoConnect(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_CONNECT] = enabled }
    }

    // Auto-reconnect on drop
    private val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
    val autoReconnect: Flow<Boolean> = context.dataStore.data.map { it[AUTO_RECONNECT] ?: true }
    suspend fun setAutoReconnect(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_RECONNECT] = enabled }
    }

    // Biometric lock
    private val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
    val biometricLock: Flow<Boolean> = context.dataStore.data.map { it[BIOMETRIC_LOCK] ?: false }
    suspend fun setBiometricLock(enabled: Boolean) {
        context.dataStore.edit { it[BIOMETRIC_LOCK] = enabled }
    }

    // Dark theme (always dark for this app, but exposed for consistency)
    private val DARK_THEME = booleanPreferencesKey("dark_theme")
    val darkTheme: Flow<Boolean> = context.dataStore.data.map { it[DARK_THEME] ?: true }

    // Show threat map
    private val SHOW_THREAT_MAP = booleanPreferencesKey("show_threat_map")
    val showThreatMap: Flow<Boolean> = context.dataStore.data.map { it[SHOW_THREAT_MAP] ?: true }
    suspend fun setShowThreatMap(enabled: Boolean) {
        context.dataStore.edit { it[SHOW_THREAT_MAP] = enabled }
    }

    // DNS provider preference
    private val DNS_PROVIDER = stringPreferencesKey("dns_provider")
    val dnsProvider: Flow<String> = context.dataStore.data.map { it[DNS_PROVIDER] ?: "cloudflare" }
    suspend fun setDnsProvider(provider: String) {
        context.dataStore.edit { it[DNS_PROVIDER] = provider }
    }

    // Notification on connect/disconnect
    private val NOTIFY_ON_STATE_CHANGE = booleanPreferencesKey("notify_state_change")
    val notifyOnStateChange: Flow<Boolean> = context.dataStore.data.map { it[NOTIFY_ON_STATE_CHANGE] ?: true }
    suspend fun setNotifyOnStateChange(enabled: Boolean) {
        context.dataStore.edit { it[NOTIFY_ON_STATE_CHANGE] = enabled }
    }
}
