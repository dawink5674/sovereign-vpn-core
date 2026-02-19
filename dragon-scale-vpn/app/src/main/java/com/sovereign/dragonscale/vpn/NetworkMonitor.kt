package com.sovereign.dragonscale.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.sovereign.dragonscale.DragonScaleApp
import com.sovereign.dragonscale.ui.screens.LogEntry
import com.sovereign.dragonscale.ui.screens.LogType
import com.wireguard.android.backend.Statistics
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors VPN traffic and network state.
 * SINGLETON — survives fold/unfold, page navigation, and activity recreation.
 * Polls GoBackend for transfer statistics every 2 seconds.
 */
class NetworkMonitor private constructor(private val context: Context) {

    private val backend get() = DragonScaleApp.get(context).backend
    private var monitorJob: Job? = null
    private var prevRx: Long = 0
    private var prevTx: Long = 0

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs = _logs.asStateFlow()

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

    fun addLog(message: String, type: LogType = LogType.INFO) {
        // Keep at most 200 entries to prevent unbounded growth
        val updated = _logs.value + LogEntry(message, type = type)
        _logs.value = if (updated.size > 200) updated.takeLast(200) else updated
    }

    fun startMonitoring(tunnel: DragonScaleTunnel) {
        // Don't restart if already monitoring the same tunnel
        if (monitorJob?.isActive == true) return

        prevRx = 0
        prevTx = 0
        addLog("Tunnel UP — monitoring started", LogType.INFO)
        addLog("Network: ${detectNetworkType()}", LogType.NETWORK)

        // Use SupervisorJob so the coroutine won't die with composable lifecycle
        monitorJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            while (isActive) {
                try {
                    val stats = backend.getStatistics(tunnel)
                    updateStats(stats)
                    _networkType.value = detectNetworkType()
                } catch (e: Exception) {
                    // Log the error but keep polling
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

    /** Whether monitoring is currently active */
    val isMonitoring: Boolean get() = monitorJob?.isActive == true

    private fun updateStats(stats: Statistics) {
        var totalRx = 0L
        var totalTx = 0L

        stats.peers().forEach { key ->
            totalRx += stats.peer(key)?.rxBytes ?: 0
            totalTx += stats.peer(key)?.txBytes ?: 0

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

        // Calculate rate (delta over 2 second interval)
        if (prevRx > 0 || prevTx > 0) {
            val rxDelta = totalRx - prevRx
            val txDelta = totalTx - prevTx
            _rxRate.value = formatRate(rxDelta / 2)
            _txRate.value = formatRate(txDelta / 2)

            // Log significant traffic bursts (>50KB in 2s)
            if (rxDelta > 50_000) {
                addLog("↓ ${formatBytes(rxDelta)} received", LogType.TRAFFIC)
            }
            if (txDelta > 50_000) {
                addLog("↑ ${formatBytes(txDelta)} sent", LogType.TRAFFIC)
            }
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
        } catch (_: Exception) {
            "Unknown"
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: NetworkMonitor? = null

        /**
         * Thread-safe singleton accessor.
         * Always uses applicationContext to avoid activity leaks.
         */
        fun getInstance(context: Context): NetworkMonitor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkMonitor(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun formatBytes(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }

        fun formatRate(bytesPerSec: Long): String = when {
            bytesPerSec < 1024 -> "$bytesPerSec B/s"
            bytesPerSec < 1024 * 1024 -> "%.1f KB/s".format(bytesPerSec / 1024.0)
            else -> "%.1f MB/s".format(bytesPerSec / (1024.0 * 1024.0))
        }
    }
}
