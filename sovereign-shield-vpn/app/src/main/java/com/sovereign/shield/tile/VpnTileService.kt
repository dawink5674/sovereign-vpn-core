package com.sovereign.shield.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.sovereign.shield.vpn.VpnManager

/**
 * Quick Settings Tile for one-tap VPN toggle.
 * Pixel 10 Pro Fold feature — accessible from notification shade.
 */
class VpnTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        // Toggle VPN state
        val vpnManager = VpnManager(applicationContext)
        val isUp = vpnManager.getTunnelState() == com.wireguard.android.backend.Tunnel.State.UP

        qsTile?.apply {
            state = if (isUp) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = if (isUp) "Disconnecting..." else "Connecting..."
            }
            updateTile()
        }

        // Note: Full toggle requires coroutine scope — in production,
        // this would use a Service or WorkManager to handle the async toggle
    }

    private fun updateTile() {
        val vpnManager = VpnManager(applicationContext)
        val isUp = vpnManager.getTunnelState() == com.wireguard.android.backend.Tunnel.State.UP

        qsTile?.apply {
            state = if (isUp) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = if (isUp) "Protected" else "Tap to connect"
            }
            updateTile()
        }
    }
}
