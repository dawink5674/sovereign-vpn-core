package com.sovereign.dragonscale.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * EncryptedPrefs — secure storage for WireGuard private keys.
 *
 * Uses AndroidX EncryptedSharedPreferences backed by Android Keystore
 * (AES-256-GCM encryption). The private key is encrypted at rest and
 * never accessible outside this process.
 */
class EncryptedPrefs(context: Context) {

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

    fun storePrivateKey(privateKey: String) {
        prefs.edit().putString(KEY_PRIVATE, privateKey).apply()
    }

    fun getPrivateKey(): String? {
        return prefs.getString(KEY_PRIVATE, null)
    }

    fun storePublicKey(publicKey: String) {
        prefs.edit().putString(KEY_PUBLIC, publicKey).apply()
    }

    fun getPublicKey(): String? {
        return prefs.getString(KEY_PUBLIC, null)
    }

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

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_FILENAME = "dragon_scale_secure_prefs"
        private const val KEY_PRIVATE = "wg_private_key"
        private const val KEY_PUBLIC = "wg_public_key"
        private const val KEY_PRESHARED = "wg_preshared_key"
        private const val KEY_ASSIGNED_IP = "wg_assigned_ip"
        private const val KEY_SERVER_PUBLIC_KEY = "wg_server_public_key"
        private const val KEY_SERVER_ENDPOINT = "wg_server_endpoint"
    }
}
