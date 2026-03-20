package com.sovereign.shield.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EncryptedPrefs — AES-256-GCM encrypted storage backed by Android Keystore.
 * Stores WireGuard keys, server config, and security metadata.
 */
@Singleton
class EncryptedPrefs @Inject constructor(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_FILENAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // WireGuard keys
    fun storePrivateKey(key: String) = prefs.edit().putString(KEY_PRIVATE, key).apply()
    fun getPrivateKey(): String? = prefs.getString(KEY_PRIVATE, null)
    fun storePublicKey(key: String) = prefs.edit().putString(KEY_PUBLIC, key).apply()
    fun getPublicKey(): String? = prefs.getString(KEY_PUBLIC, null)

    // Peer config from server
    fun storePeerConfig(presharedKey: String, assignedIP: String) {
        prefs.edit()
            .putString(KEY_PRESHARED, presharedKey)
            .putString(KEY_ASSIGNED_IP, assignedIP)
            .apply()
    }

    fun storeServerConfig(serverPublicKey: String, endpoint: String) {
        prefs.edit()
            .putString(KEY_SERVER_PUBLIC_KEY, serverPublicKey)
            .putString(KEY_SERVER_ENDPOINT, endpoint)
            .apply()
    }

    fun getServerPublicKey(): String? = prefs.getString(KEY_SERVER_PUBLIC_KEY, null)
    fun getServerEndpoint(): String? = prefs.getString(KEY_SERVER_ENDPOINT, null)
    fun getPresharedKey(): String? = prefs.getString(KEY_PRESHARED, null)
    fun getAssignedIP(): String? = prefs.getString(KEY_ASSIGNED_IP, null)

    // Key rotation tracking
    fun incrementKeyRotationCount() {
        val current = prefs.getInt(KEY_ROTATION_COUNT, 0)
        prefs.edit().putInt(KEY_ROTATION_COUNT, current + 1).apply()
    }
    fun getKeyRotationCount(): Int = prefs.getInt(KEY_ROTATION_COUNT, 0)
    fun storeLastKeyRotation(timestamp: Long) = prefs.edit().putLong(KEY_LAST_ROTATION, timestamp).apply()
    fun getLastKeyRotation(): Long = prefs.getLong(KEY_LAST_ROTATION, 0L)

    // Connection stats persistence
    fun storeTotalBytesTransferred(rx: Long, tx: Long) {
        prefs.edit()
            .putLong(KEY_TOTAL_RX, prefs.getLong(KEY_TOTAL_RX, 0L) + rx)
            .putLong(KEY_TOTAL_TX, prefs.getLong(KEY_TOTAL_TX, 0L) + tx)
            .apply()
    }
    fun getTotalRx(): Long = prefs.getLong(KEY_TOTAL_RX, 0L)
    fun getTotalTx(): Long = prefs.getLong(KEY_TOTAL_TX, 0L)

    // Connection count
    fun incrementConnectionCount() {
        val current = prefs.getInt(KEY_CONN_COUNT, 0)
        prefs.edit().putInt(KEY_CONN_COUNT, current + 1).apply()
    }
    fun getConnectionCount(): Int = prefs.getInt(KEY_CONN_COUNT, 0)

    fun clearAll() = prefs.edit().clear().apply()

    companion object {
        private const val PREFS_FILENAME = "sovereign_shield_secure_prefs"
        private const val KEY_PRIVATE = "wg_private_key"
        private const val KEY_PUBLIC = "wg_public_key"
        private const val KEY_PRESHARED = "wg_preshared_key"
        private const val KEY_ASSIGNED_IP = "wg_assigned_ip"
        private const val KEY_SERVER_PUBLIC_KEY = "wg_server_public_key"
        private const val KEY_SERVER_ENDPOINT = "wg_server_endpoint"
        private const val KEY_ROTATION_COUNT = "key_rotation_count"
        private const val KEY_LAST_ROTATION = "last_key_rotation"
        private const val KEY_TOTAL_RX = "total_rx_bytes"
        private const val KEY_TOTAL_TX = "total_tx_bytes"
        private const val KEY_CONN_COUNT = "connection_count"
    }
}
