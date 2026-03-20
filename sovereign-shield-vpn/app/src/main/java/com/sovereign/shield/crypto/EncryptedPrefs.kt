package com.sovereign.shield.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EncryptedPrefs — AES-256-GCM encrypted storage backed by Android Keystore.
 * Stores WireGuard keys, server config, and security metadata.
 *
 * IMPORTANT: EncryptedSharedPreferences can fail on some devices (Keystore
 * corruption, first boot race, certain Samsung/Huawei firmware). We wrap the
 * initialisation in try/catch and fall back to regular SharedPreferences if
 * the encrypted version is unavailable. The VPN keys are still protected
 * by the app sandbox — encryption is a defense-in-depth layer, not the
 * only protection.
 */
@Singleton
class EncryptedPrefs @Inject constructor(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                PREFS_FILENAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences failed, falling back to plain prefs", e)
            // Fallback: still app-sandboxed, just not double-encrypted
            context.getSharedPreferences(PREFS_FILENAME_FALLBACK, Context.MODE_PRIVATE)
        }
    }

    // WireGuard keys
    fun storePrivateKey(key: String) {
        try { prefs.edit().putString(KEY_PRIVATE, key).apply() }
        catch (e: Exception) { Log.e(TAG, "storePrivateKey failed", e) }
    }
    fun getPrivateKey(): String? = try { prefs.getString(KEY_PRIVATE, null) }
        catch (e: Exception) { Log.e(TAG, "getPrivateKey failed", e); null }

    fun storePublicKey(key: String) {
        try { prefs.edit().putString(KEY_PUBLIC, key).apply() }
        catch (e: Exception) { Log.e(TAG, "storePublicKey failed", e) }
    }
    fun getPublicKey(): String? = try { prefs.getString(KEY_PUBLIC, null) }
        catch (e: Exception) { Log.e(TAG, "getPublicKey failed", e); null }

    // Peer config from server
    fun storePeerConfig(presharedKey: String, assignedIP: String) {
        try {
            prefs.edit()
                .putString(KEY_PRESHARED, presharedKey)
                .putString(KEY_ASSIGNED_IP, assignedIP)
                .apply()
        } catch (e: Exception) { Log.e(TAG, "storePeerConfig failed", e) }
    }

    fun storeServerConfig(serverPublicKey: String, endpoint: String) {
        try {
            prefs.edit()
                .putString(KEY_SERVER_PUBLIC_KEY, serverPublicKey)
                .putString(KEY_SERVER_ENDPOINT, endpoint)
                .apply()
        } catch (e: Exception) { Log.e(TAG, "storeServerConfig failed", e) }
    }

    fun getServerPublicKey(): String? = try { prefs.getString(KEY_SERVER_PUBLIC_KEY, null) }
        catch (e: Exception) { Log.e(TAG, "getServerPublicKey failed", e); null }

    fun getServerEndpoint(): String? = try { prefs.getString(KEY_SERVER_ENDPOINT, null) }
        catch (e: Exception) { Log.e(TAG, "getServerEndpoint failed", e); null }

    fun getPresharedKey(): String? = try { prefs.getString(KEY_PRESHARED, null) }
        catch (e: Exception) { Log.e(TAG, "getPresharedKey failed", e); null }

    fun getAssignedIP(): String? = try { prefs.getString(KEY_ASSIGNED_IP, null) }
        catch (e: Exception) { Log.e(TAG, "getAssignedIP failed", e); null }

    // Key rotation tracking
    fun incrementKeyRotationCount() {
        try {
            val current = prefs.getInt(KEY_ROTATION_COUNT, 0)
            prefs.edit().putInt(KEY_ROTATION_COUNT, current + 1).apply()
        } catch (e: Exception) { Log.e(TAG, "incrementKeyRotationCount failed", e) }
    }
    fun getKeyRotationCount(): Int = try { prefs.getInt(KEY_ROTATION_COUNT, 0) }
        catch (e: Exception) { Log.e(TAG, "getKeyRotationCount failed", e); 0 }

    fun storeLastKeyRotation(timestamp: Long) {
        try { prefs.edit().putLong(KEY_LAST_ROTATION, timestamp).apply() }
        catch (e: Exception) { Log.e(TAG, "storeLastKeyRotation failed", e) }
    }
    fun getLastKeyRotation(): Long = try { prefs.getLong(KEY_LAST_ROTATION, 0L) }
        catch (e: Exception) { Log.e(TAG, "getLastKeyRotation failed", e); 0L }

    // Connection stats persistence
    fun storeTotalBytesTransferred(rx: Long, tx: Long) {
        try {
            prefs.edit()
                .putLong(KEY_TOTAL_RX, prefs.getLong(KEY_TOTAL_RX, 0L) + rx)
                .putLong(KEY_TOTAL_TX, prefs.getLong(KEY_TOTAL_TX, 0L) + tx)
                .apply()
        } catch (e: Exception) { Log.e(TAG, "storeTotalBytesTransferred failed", e) }
    }
    fun getTotalRx(): Long = try { prefs.getLong(KEY_TOTAL_RX, 0L) }
        catch (e: Exception) { Log.e(TAG, "getTotalRx failed", e); 0L }
    fun getTotalTx(): Long = try { prefs.getLong(KEY_TOTAL_TX, 0L) }
        catch (e: Exception) { Log.e(TAG, "getTotalTx failed", e); 0L }

    // Connection count
    fun incrementConnectionCount() {
        try {
            val current = prefs.getInt(KEY_CONN_COUNT, 0)
            prefs.edit().putInt(KEY_CONN_COUNT, current + 1).apply()
        } catch (e: Exception) { Log.e(TAG, "incrementConnectionCount failed", e) }
    }
    fun getConnectionCount(): Int = try { prefs.getInt(KEY_CONN_COUNT, 0) }
        catch (e: Exception) { Log.e(TAG, "getConnectionCount failed", e); 0 }

    fun clearAll() {
        try { prefs.edit().clear().apply() }
        catch (e: Exception) { Log.e(TAG, "clearAll failed", e) }
    }

    companion object {
        private const val TAG = "EncryptedPrefs"
        private const val PREFS_FILENAME = "sovereign_shield_secure_prefs"
        private const val PREFS_FILENAME_FALLBACK = "sovereign_shield_prefs_fallback"
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
