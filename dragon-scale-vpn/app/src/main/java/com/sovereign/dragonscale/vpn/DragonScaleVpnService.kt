package com.sovereign.dragonscale.vpn

import android.net.VpnService

/**
 * Android VPN Service for Dragon Scale.
 * Registered in AndroidManifest with BIND_VPN_SERVICE permission.
 * The actual tunnel is managed by WireGuard's GoBackend;
 * this service provides the VPN permission gateway.
 */
class DragonScaleVpnService : VpnService() {

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
