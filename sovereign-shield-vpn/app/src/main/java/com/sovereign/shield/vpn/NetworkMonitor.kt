package com.sovereign.shield.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.sovereign.shield.SovereignShieldApp
import com.wireguard.android.backend.Statistics
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced network monitor with speed history tracking and connection quality metrics.
 */
@Singleton
class NetworkMonitor private constructor(private val context: Context) {

    private val backend get() = SovereignShieldApp.get(context).backend
    private var monitorJob: Job? = null
    private var prevRx: Long = 0
    private var prevTx: Long = 0

    // Real-time stats
    private val _rxBytes = MutableStateFlow(0L)
    val rxBytes = _rxBytes.asStateFlow()

    private val _txBytes = MutableStateFlow(0L)
    val txBytes = _txBytes.asStateFlow()

    private val _rxRate = MutableStateFlow("0 B/s")
    val rxRate = _rxRate.asStateFlow()

    private val _txRate = MutableStateFlow("0 B/s")
    val txRate = _txRate.asStateFlow()

    private val _networkType = MutableStateFlow("Unknown")
    val networkType = _networkType.asStateFlow()

    private val _lastHandshake = MutableStateFlow("—")
    val lastHandshake = _lastHandshake.asStateFlow()

    // Speed history for charts (last 60 samples = 2 minutes at 2s intervals)
    private val _rxSpeedHistory = MutableStateFlow<List<Float>>(emptyList())
    val rxSpeedHistory = _rxSpeedHistory.asStateFlow()

    private val _txSpeedHistory = MutableStateFlow<List<Float>>(emptyList())
    val txSpeedHistory = _txSpeedHistory.asStateFlow()

    // Peak speeds
    private val _peakRxRate = MutableStateFlow(0L)
    val peakRxRate = _peakRxRate.asStateFlow()

    private val _peakTxRate = MutableStateFlow(0L)
    val peakTxRate = _peakTxRate.asStateFlow()

    // Log entries
    data class LogEntry(
        val message: String,
        val type: LogType = LogType.INFO,
        val timestamp: String = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
    )

    enum class LogType { INFO, ERROR, TRAFFIC, NETWORK, DNS, SECURITY }

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs = _logs.asStateFlow()

    fun addLog(message: String, type: LogType = LogType.INFO) {
        val updated = _logs.value + LogEntry(message, type = type)
        _logs.value = if (updated.size > 200) updated.takeLast(200) else updated
    }

    fun startMonitoring(tunnel: SovereignTunnel) {
        if (monitorJob?.isActive == true) return
        prevRx = 0
        prevTx = 0
        addLog("Tunnel UP — monitoring started", LogType.INFO)
        addLog("Network: ${detectNetworkType()}", LogType.NETWORK)

        monitorJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            while (isActive) {
                try {
                    val stats = backend.getStatistics(tunnel)
                    updateStats(stats)
                    _networkType.value = detectNetworkType()
                } catch (e: Exception) {
                    addLog("Monitor: ${e.message ?: "error"}", LogType.ERROR)
                }
                delay(2000)
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        addLog("Monitoring stopped", LogType.INFO)
    }

    val isMonitoring: Boolean get() = monitorJob?.isActive == true

    private fun updateStats(stats: Statistics) {
        var totalRx = 0L
        var totalTx = 0L

        stats.peers().forEach { key ->
            totalRx += stats.peer(key)?.txBytes ?: 0
            totalTx += stats.peer(key)?.rxBytes ?: 0

            stats.peer(key)?.latestHandshakeEpochMillis?.let { ms ->
                if (ms > 0) {
                    val secsAgo = (System.currentTimeMillis() - ms) / 1000
                    _lastHandshake.value = when {
                        secsAgo < 5 -> "Just now"
                        secsAgo < 60 -> "${secsAgo}s ago"
                        else -> "${secsAgo / 60}m ${secsAgo % 60}s ago"
                    }
                }
            }
        }

        if (prevRx > 0 || prevTx > 0) {
            val rxDelta = totalRx - prevRx
            val txDelta = totalTx - prevTx
            val rxPerSec = rxDelta / 2
            val txPerSec = txDelta / 2
            _rxRate.value = formatRate(rxPerSec)
            _txRate.value = formatRate(txPerSec)

            // Track peak speeds
            if (rxPerSec > _peakRxRate.value) _peakRxRate.value = rxPerSec
            if (txPerSec > _peakTxRate.value) _peakTxRate.value = txPerSec

            // Update speed history (keep last 60 samples)
            val rxHistory = _rxSpeedHistory.value.toMutableList()
            rxHistory.add(rxPerSec.toFloat())
            if (rxHistory.size > 60) rxHistory.removeAt(0)
            _rxSpeedHistory.value = rxHistory

            val txHistory = _txSpeedHistory.value.toMutableList()
            txHistory.add(txPerSec.toFloat())
            if (txHistory.size > 60) txHistory.removeAt(0)
            _txSpeedHistory.value = txHistory

            if (rxDelta > 50_000) addLog("↓ ${formatBytes(rxDelta)} received", LogType.TRAFFIC)
            if (txDelta > 50_000) addLog("↑ ${formatBytes(txDelta)} sent", LogType.TRAFFIC)
        }

        prevRx = totalRx
        prevTx = totalTx
        _rxBytes.value = totalRx
        _txBytes.value = totalTx
    }

    private fun detectNetworkType(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return "No network"
            val caps = cm.getNetworkCapabilities(network) ?: return "Unknown"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                else -> "Other"
            }
        } catch (_: Exception) { "Unknown" }
    }

    companion object {
        @Volatile private var INSTANCE: NetworkMonitor? = null

        fun getInstance(context: Context): NetworkMonitor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkMonitor(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun formatBytes(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }

        fun formatRate(bytesPerSec: Long): String = when {
            bytesPerSec < 1024 -> "$bytesPerSec B/s"
            bytesPerSec < 1024 * 1024 -> "%.1f KB/s".format(bytesPerSec / 1024.0)
            else -> "%.1f MB/s".format(bytesPerSec / (1024.0 * 1024.0))
        }
    }
}
