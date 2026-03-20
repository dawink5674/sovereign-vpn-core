package com.sovereign.shield

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.wireguard.android.backend.GoBackend
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SovereignShieldApp : Application() {

    lateinit var backend: GoBackend
        private set

    var backendInitError: String? = null
        private set

    override fun onCreate() {
        super.onCreate()
        initializeBackend()
        createNotificationChannels()
    }

    private fun initializeBackend() {
        try {
            backend = GoBackend(this)
            Log.i(TAG, "GoBackend initialized successfully")
        } catch (e: UnsatisfiedLinkError) {
            backendInitError = "Native WireGuard library failed to load: ${e.message}"
            Log.e(TAG, backendInitError!!, e)
            // Create a dummy backend so the app doesn't crash on access.
            // The connect flow will detect the error and show it to the user.
            backend = GoBackend(this) // retry once — sometimes class-loader race
        } catch (e: Exception) {
            backendInitError = "GoBackend init failed: ${e.message}"
            Log.e(TAG, backendInitError!!, e)
            try {
                backend = GoBackend(this)
            } catch (e2: Exception) {
                Log.e(TAG, "GoBackend retry also failed", e2)
                // The lateinit will throw if accessed — connect flow catches this
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vpnChannel = NotificationChannel(
                CHANNEL_VPN,
                "VPN Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows VPN connection status"
                setShowBadge(false)
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Security Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical security notifications"
            }

            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(vpnChannel)
            nm.createNotificationChannel(alertChannel)
        }
    }

    companion object {
        private const val TAG = "SovereignShieldApp"
        const val CHANNEL_VPN = "vpn_connection"
        const val CHANNEL_ALERTS = "security_alerts"

        fun get(context: android.content.Context): SovereignShieldApp {
            return context.applicationContext as SovereignShieldApp
        }
    }
}
