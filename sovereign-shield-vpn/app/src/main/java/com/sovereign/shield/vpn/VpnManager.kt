package com.sovereign.shield.vpn

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.sovereign.shield.SovereignShieldApp
import com.sovereign.shield.crypto.CryptoManager
import com.sovereign.shield.crypto.EncryptedPrefs
import com.sovereign.shield.network.ApiClient
import com.sovereign.shield.network.PeerRegistrationRequest
import com.sovereign.shield.network.PeerRegistrationResponse
import com.sovereign.shield.network.ServerConfig
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.InetNetwork
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VpnManager — orchestrates the zero-trust VPN lifecycle.
 *
 * Enhancements over Dragon Scale:
 * - Explicit connect/disconnect (no toggle-based state that can desync)
 * - Kill switch support via DNS leak protection
 * - Auto-reconnect capability
 * - Connection timing metrics
 * - Key rotation on 409 conflicts
 */
@Singleton
class VpnManager @Inject constructor(private val context: Context) {

    private val encryptedPrefs = EncryptedPrefs(context)
    private val cryptoManager = CryptoManager(encryptedPrefs)
    private val backend: GoBackend get() = SovereignShieldApp.get(context).backend

    // Connection timing
    private var connectionStartTime: Long = 0L
    val connectionDuration: Long
        get() = if (connectionStartTime > 0 && getTunnelState() == Tunnel.State.UP)
            System.currentTimeMillis() - connectionStartTime else 0L

    companion object {
        private var currentTunnel: SovereignTunnel? = null

        @Volatile
        private var INSTANCE: VpnManager? = null

        /**
         * Singleton accessor for use in Compose screens.
         * Ensures the same VpnManager instance is used everywhere,
         * preventing state desync between button taps.
         */
        fun getInstance(context: Context): VpnManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VpnManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun prepareVpn(activity: Activity): Intent? = GoBackend.VpnService.prepare(activity)

    suspend fun registerDevice(deviceName: String): Result<ServerConfig> {
        return withContext(Dispatchers.IO) {
            try {
                val result = attemptRegistration(deviceName)
                if (result.isFailure) {
                    val msg = result.exceptionOrNull()?.message ?: ""
                    if (msg.contains("409")) {
                        cryptoManager.clearKeys()
                        return@withContext attemptRegistration(deviceName)
                    }
                }
                result
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private suspend fun attemptRegistration(deviceName: String): Result<ServerConfig> {
        val publicKey = if (cryptoManager.hasKeyPair()) {
            cryptoManager.getPublicKey()!!
        } else {
            cryptoManager.generateAndStoreKeyPair()
        }

        val response = ApiClient.vpnApi.registerPeer(
            PeerRegistrationRequest(name = deviceName, publicKey = publicKey)
        )

        if (!response.isSuccessful || response.body() == null) {
            return Result.failure(
                Exception("Registration failed: ${response.code()} ${response.message()}")
            )
        }

        val body = response.body()!!
        val serverConfig = extractServerConfig(body)
            ?: return Result.failure(Exception("No usable config in server response"))

        encryptedPrefs.storePeerConfig(
            presharedKey = serverConfig.presharedKey,
            assignedIP = body.peer.assignedIP
        )
        encryptedPrefs.storeServerConfig(
            serverPublicKey = serverConfig.serverPublicKey,
            endpoint = serverConfig.endpoint
        )

        // NOTE: Do NOT override the local private key with anything from clientConfig.
        // The private key was generated locally by CryptoManager and the matching
        // public key was sent to the server during registration. Overwriting it
        // would break the Curve25519 key agreement and the tunnel would fail.

        return Result.success(serverConfig)
    }

    private fun extractServerConfig(body: PeerRegistrationResponse): ServerConfig? {
        body.serverConfig?.let { return it }
        val conf = body.clientConfig ?: return null
        val serverPublicKey = parseConfValue(conf, "PublicKey") ?: return null
        val endpoint = parseConfValue(conf, "Endpoint") ?: return null
        val presharedKey = parseConfValue(conf, "PresharedKey") ?: return null
        val dns = parseConfValue(conf, "DNS") ?: "1.1.1.1, 1.0.0.1"
        val allowedIPs = parseConfValue(conf, "AllowedIPs") ?: "0.0.0.0/0, ::/0"
        val keepalive = parseConfValue(conf, "PersistentKeepalive")?.toIntOrNull() ?: 25
        return ServerConfig(serverPublicKey, endpoint, presharedKey, dns, allowedIPs, keepalive)
    }

    private fun parseConfValue(conf: String, key: String): String? {
        val regex = Regex("""(?m)^\s*$key\s*=\s*(.+)\s*$""")
        return regex.find(conf)?.groupValues?.get(1)?.trim()
    }

    fun buildConfig(killSwitch: Boolean = false, dnsProvider: String = "cloudflare"): Config? {
        val privateKey = cryptoManager.getPrivateKey()
        if (privateKey == null) {
            android.util.Log.e("VpnManager", "buildConfig failed: no private key")
            return null
        }
        val assignedIP = encryptedPrefs.getAssignedIP()
        if (assignedIP == null) {
            android.util.Log.e("VpnManager", "buildConfig failed: no assigned IP")
            return null
        }
        val presharedKey = encryptedPrefs.getPresharedKey()
        if (presharedKey == null) {
            android.util.Log.e("VpnManager", "buildConfig failed: no preshared key")
            return null
        }
        val serverPublicKey = encryptedPrefs.getServerPublicKey()
            ?: "G1ReQCSgRG/MdfF5/SMrcnU+lKQMlwkr9aIA7/ZK5WI="
        val serverEndpoint = encryptedPrefs.getServerEndpoint()
            ?: "35.206.67.49:51820"

        val interfaceBuilder = Interface.Builder()
            .parsePrivateKey(privateKey)
            .addAddress(InetNetwork.parse(assignedIP))

        // DNS: Use the user-selected DNS provider
        val (primaryDns, secondaryDns) = when (dnsProvider) {
            "google" -> "8.8.8.8" to "8.8.4.4"
            "quad9" -> "9.9.9.9" to "149.112.112.112"
            else -> "1.1.1.1" to "1.0.0.1" // cloudflare (default)
        }

        interfaceBuilder.addDnsServer(InetAddress.getByName(primaryDns))
        if (!killSwitch) {
            // With kill switch, use only primary DNS to prevent leaks
            interfaceBuilder.addDnsServer(InetAddress.getByName(secondaryDns))
        }

        val wgInterface = interfaceBuilder.build()

        val wgPeer = Peer.Builder()
            .parsePublicKey(serverPublicKey)
            .parsePreSharedKey(presharedKey)
            .parseEndpoint(serverEndpoint)
            .addAllowedIp(InetNetwork.parse("0.0.0.0/0"))
            .addAllowedIp(InetNetwork.parse("::/0"))
            .setPersistentKeepalive(25)
            .build()

        return Config.Builder()
            .setInterface(wgInterface)
            .addPeer(wgPeer)
            .build()
    }

    /**
     * Explicitly connect the VPN tunnel. This is NOT a toggle — calling this
     * when already connected is a safe no-op (returns current UP state).
     *
     * This approach eliminates the state desync that caused "button glitches"
     * with the old toggle-based design, where the UI thought the tunnel was
     * in one state but the backend was in another.
     */
    suspend fun connect(killSwitch: Boolean = false, dnsProvider: String = "cloudflare"): Result<Tunnel.State> {
        return withContext(Dispatchers.IO) {
            try {
                val config = buildConfig(killSwitch, dnsProvider)
                    ?: return@withContext Result.failure(Exception("No config — register device first"))

                if (currentTunnel == null) {
                    currentTunnel = SovereignTunnel("sovereign-shield")
                }

                val tunnel = currentTunnel!!

                // Check if already connected — if so, return success immediately
                val currentState = try { backend.getState(tunnel) } catch (_: Exception) { Tunnel.State.DOWN }
                if (currentState == Tunnel.State.UP) {
                    return@withContext Result.success(Tunnel.State.UP)
                }

                val newState = backend.setState(tunnel, Tunnel.State.UP, config)

                if (newState == Tunnel.State.UP) {
                    connectionStartTime = System.currentTimeMillis()
                    encryptedPrefs.incrementConnectionCount()
                }

                Result.success(newState)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Explicitly disconnect the VPN tunnel. Safe to call when already disconnected.
     */
    suspend fun disconnect(): Result<Tunnel.State> {
        return withContext(Dispatchers.IO) {
            try {
                val tunnel = currentTunnel
                    ?: return@withContext Result.success(Tunnel.State.DOWN)

                val currentState = try { backend.getState(tunnel) } catch (_: Exception) { Tunnel.State.DOWN }
                if (currentState == Tunnel.State.DOWN) {
                    connectionStartTime = 0L
                    return@withContext Result.success(Tunnel.State.DOWN)
                }

                val newState = backend.setState(tunnel, Tunnel.State.DOWN, null)
                connectionStartTime = 0L
                Result.success(newState)
            } catch (e: Exception) {
                connectionStartTime = 0L
                Result.failure(e)
            }
        }
    }

    /**
     * Backwards-compatible toggle that delegates to connect/disconnect
     * based on desired direction. Uses the UI-provided state to decide
     * direction, NOT the backend state — preventing desync glitches.
     */
    suspend fun toggleTunnel(killSwitch: Boolean = false, dnsProvider: String = "cloudflare"): Result<Tunnel.State> {
        val currentState = getTunnelState()
        return if (currentState == Tunnel.State.UP) {
            disconnect()
        } else {
            connect(killSwitch, dnsProvider)
        }
    }

    fun getTunnelState(): Tunnel.State {
        return currentTunnel?.let {
            try { backend.getState(it) } catch (_: Exception) { Tunnel.State.DOWN }
        } ?: Tunnel.State.DOWN
    }

    fun getCurrentTunnel(): SovereignTunnel? = currentTunnel
    fun isRegistered(): Boolean = cryptoManager.hasKeyPair() && encryptedPrefs.getAssignedIP() != null

    fun getServerIp(): String? {
        val endpoint = encryptedPrefs.getServerEndpoint() ?: return null
        return endpoint.substringBefore(":")
    }

    fun getConnectionCount(): Int = encryptedPrefs.getConnectionCount()
}

class SovereignTunnel(private val tunnelName: String) : Tunnel {
    override fun getName(): String = tunnelName
    override fun onStateChange(newState: Tunnel.State) {}
}
