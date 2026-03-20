package com.sovereign.shield.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sovereign.shield.MainActivity
import com.sovereign.shield.R
import com.sovereign.shield.SovereignShieldApp

/**
 * Android VPN Service for Sovereign Shield.
 * Enhanced over Dragon Scale with persistent notification and foreground service.
 */
class SovereignVpnService : VpnService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> startForeground(NOTIFICATION_ID, buildNotification("Connected"))
            ACTION_DISCONNECT -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun buildNotification(status: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, SovereignShieldApp.CHANNEL_VPN)
            .setContentTitle("Sovereign Shield VPN")
            .setContentText("Status: $status")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        const val ACTION_CONNECT = "com.sovereign.shield.CONNECT"
        const val ACTION_DISCONNECT = "com.sovereign.shield.DISCONNECT"
        const val NOTIFICATION_ID = 1001
    }
}
