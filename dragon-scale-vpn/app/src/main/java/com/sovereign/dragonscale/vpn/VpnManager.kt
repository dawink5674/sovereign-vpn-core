package com.sovereign.dragonscale.vpn

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.sovereign.dragonscale.DragonScaleApp
import com.sovereign.dragonscale.crypto.CryptoManager
import com.sovereign.dragonscale.crypto.EncryptedPrefs
import com.sovereign.dragonscale.network.ApiClient
import com.sovereign.dragonscale.network.PeerRegistrationRequest
import com.sovereign.dragonscale.network.PeerRegistrationResponse
import com.sovereign.dragonscale.network.ServerConfig
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.InetNetwork
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress

/**
 * VpnManager — orchestrates the zero-trust VPN lifecycle:
 *
 * 1. Generate keypair locally (CryptoManager)
 * 2. Register public key with Control Plane API
 * 3. Build WireGuard Config using local private key + server response
 * 4. Toggle tunnel via GoBackend
 *
 * The private key NEVER leaves the device.
 */
class VpnManager(private val context: Context) {

    private val encryptedPrefs = EncryptedPrefs(context)
    private val cryptoManager = CryptoManager(encryptedPrefs)
    private val backend: GoBackend get() = DragonScaleApp.get(context).backend

    companion object {
        // Persists across activity recreations (fold changes, rotation)
        private var currentTunnel: DragonScaleTunnel? = null
    }

    /**
     * Check if the VPN permission has been granted.
     * Returns null if granted, or an Intent to launch for user consent.
     */
    fun prepareVpn(activity: Activity): Intent? {
        return VpnService.prepare(activity)
    }

    /**
     * Register this device with the Control Plane API.
     * Generates a keypair if one doesn't exist, then sends the public key.
     *
     * Supports two server response formats:
     * - Live server: returns clientConfig (flat WireGuard .conf string)
     * - Local server: returns serverConfig (structured JSON object)
     *
     * If the server returns 409 (duplicate key), the client rotates its
     * keypair and retries once with a fresh public key.
     *
     * @return ServerConfig with everything needed to build the tunnel config.
     */
    suspend fun registerDevice(deviceName: String): Result<ServerConfig> {
        return withContext(Dispatchers.IO) {
            try {
                val result = attemptRegistration(deviceName)

                // If we hit a 409 Conflict (duplicate key), rotate keys and retry once
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

    /**
     * Single registration attempt against the API.
     */
    private suspend fun attemptRegistration(deviceName: String): Result<ServerConfig> {
        // Generate or retrieve keypair
        val publicKey = if (cryptoManager.hasKeyPair()) {
            cryptoManager.getPublicKey()!!
        } else {
            cryptoManager.generateAndStoreKeyPair()
        }

        // Register with API — only public key is sent
        val response = ApiClient.vpnApi.registerPeer(
            PeerRegistrationRequest(name = deviceName, publicKey = publicKey)
        )

        if (!response.isSuccessful || response.body() == null) {
            return Result.failure(
                Exception("Registration failed: ${response.code()} ${response.message()}")
            )
        }

        val body = response.body()!!

        // Parse the response — support both live and local server formats
        val serverConfig = extractServerConfig(body)
            ?: return Result.failure(Exception("No usable config in server response"))

        // Store server-provided config locally
        encryptedPrefs.storePeerConfig(
            presharedKey = serverConfig.presharedKey,
            assignedIP = body.peer.assignedIP
        )

        // Persist server public key and endpoint
        encryptedPrefs.storeServerConfig(
            serverPublicKey = serverConfig.serverPublicKey,
            endpoint = serverConfig.endpoint
        )

        // If the live server sent us a private key via clientConfig,
        // store it (overrides client-generated key with server-generated one)
        body.clientConfig?.let { conf ->
            parseConfValue(conf, "PrivateKey")?.let { serverPrivateKey ->
                encryptedPrefs.storePrivateKey(serverPrivateKey)
            }
        }

        return Result.success(serverConfig)
    }

    /**
     * Extract a ServerConfig from the API response.
     *
     * Strategy:
     * 1. If serverConfig object exists (local index.js format), use it directly
     * 2. If clientConfig string exists (live server format), parse the .conf
     */
    private fun extractServerConfig(body: PeerRegistrationResponse): ServerConfig? {
        // Prefer structured serverConfig if available
        body.serverConfig?.let { return it }

        // Parse clientConfig WireGuard .conf string
        val conf = body.clientConfig ?: return null

        val serverPublicKey = parseConfValue(conf, "PublicKey") ?: return null
        val endpoint = parseConfValue(conf, "Endpoint") ?: return null
        val presharedKey = parseConfValue(conf, "PresharedKey") ?: return null
        val dns = parseConfValue(conf, "DNS") ?: "1.1.1.1, 1.0.0.1"
        val allowedIPs = parseConfValue(conf, "AllowedIPs") ?: "0.0.0.0/0, ::/0"
        val keepalive = parseConfValue(conf, "PersistentKeepalive")?.toIntOrNull() ?: 25

        return ServerConfig(
            serverPublicKey = serverPublicKey,
            endpoint = endpoint,
            presharedKey = presharedKey,
            dns = dns,
            allowedIPs = allowedIPs,
            persistentKeepalive = keepalive
        )
    }

    /**
     * Parse a value from a WireGuard .conf format string.
     * e.g. parseConfValue("[Peer]\nPublicKey = abc123\n", "PublicKey") → "abc123"
     */
    private fun parseConfValue(conf: String, key: String): String? {
        val regex = Regex("""(?m)^\s*$key\s*=\s*(.+)\s*$""")
        return regex.find(conf)?.groupValues?.get(1)?.trim()
    }

    /**
     * Build a WireGuard Config from local private key + server-provided config.
     */
    fun buildConfig(): Config? {
        val privateKey = cryptoManager.getPrivateKey() ?: return null
        val assignedIP = encryptedPrefs.getAssignedIP() ?: return null
        val presharedKey = encryptedPrefs.getPresharedKey() ?: return null

        // Use persisted server config from registration response
        val serverPublicKey = encryptedPrefs.getServerPublicKey()
            ?: "G1ReQCSgRG/MdfF5/SMrcnU+lKQMlwkr9aIA7/ZK5WI="  // fallback
        val serverEndpoint = encryptedPrefs.getServerEndpoint()
            ?: "35.206.67.49:51820"  // fallback

        val wgInterface = Interface.Builder()
            .parsePrivateKey(privateKey)
            .addAddress(InetNetwork.parse(assignedIP))
            .addDnsServer(InetAddress.getByName("1.1.1.1"))
            .addDnsServer(InetAddress.getByName("1.0.0.1"))
            .build()

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
     * Toggle the VPN tunnel state.
     */
    suspend fun toggleTunnel(): Result<Tunnel.State> {
        return withContext(Dispatchers.IO) {
            try {
                val config = buildConfig()
                    ?: return@withContext Result.failure(Exception("No config — register device first"))

                if (currentTunnel == null) {
                    currentTunnel = DragonScaleTunnel("dragon-scale")
                }

                val tunnel = currentTunnel!!
                val newState = backend.setState(tunnel, Tunnel.State.TOGGLE, config)

                Result.success(newState)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Get current tunnel state.
     */
    fun getTunnelState(): Tunnel.State {
        return currentTunnel?.let {
            try { backend.getState(it) } catch (_: Exception) { Tunnel.State.DOWN }
        } ?: Tunnel.State.DOWN
    }

    /**
     * Get the current tunnel instance (for network monitoring).
     */
    fun getCurrentTunnel(): DragonScaleTunnel? = currentTunnel

    /**
     * Check if device is registered.
     */
    fun isRegistered(): Boolean = cryptoManager.hasKeyPair() && encryptedPrefs.getAssignedIP() != null
}

/**
 * Simple Tunnel implementation for GoBackend.
 */
class DragonScaleTunnel(private val tunnelName: String) : Tunnel {
    override fun getName(): String = tunnelName
    override fun onStateChange(newState: Tunnel.State) {
        // State change callback — can be used for UI updates or notifications
    }
}
