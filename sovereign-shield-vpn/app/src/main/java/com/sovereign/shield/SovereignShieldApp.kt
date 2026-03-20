package com.sovereign.shield

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.wireguard.android.backend.GoBackend
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SovereignShieldApp : Application() {

    lateinit var backend: GoBackend
        private set

    override fun onCreate() {
        super.onCreate()
        backend = GoBackend(this)
        createNotificationChannels()
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
        const val CHANNEL_VPN = "vpn_connection"
        const val CHANNEL_ALERTS = "security_alerts"

        fun get(context: android.content.Context): SovereignShieldApp {
            return context.applicationContext as SovereignShieldApp
        }
    }
}
