package com.sovereign.dragonscale.crypto

import com.wireguard.crypto.KeyPair

/**
 * CryptoManager — handles local WireGuard key generation.
 *
 * Keys are generated client-side using WireGuard's Curve25519 implementation.
 * The private key NEVER leaves the device. Only the public key is sent to
 * the Control Plane API for peer registration.
 */
class CryptoManager(private val encryptedPrefs: EncryptedPrefs) {

    /**
     * Generate a new WireGuard keypair and persist the private key
     * in EncryptedSharedPreferences.
     *
     * @return The public key (base64) to send to the server.
     */
    fun generateAndStoreKeyPair(): String {
        val keyPair = KeyPair()
        val privateKeyBase64 = keyPair.privateKey.toBase64()
        val publicKeyBase64 = keyPair.publicKey.toBase64()

        encryptedPrefs.storePrivateKey(privateKeyBase64)
        encryptedPrefs.storePublicKey(publicKeyBase64)

        return publicKeyBase64
    }

    /**
     * Retrieve the stored private key for tunnel configuration.
     * Returns null if no key has been generated yet.
     */
    fun getPrivateKey(): String? = encryptedPrefs.getPrivateKey()

    /**
     * Retrieve the stored public key.
     * Returns null if no key has been generated yet.
     */
    fun getPublicKey(): String? = encryptedPrefs.getPublicKey()

    /**
     * Check if a keypair has been generated and stored.
     */
    fun hasKeyPair(): Boolean = encryptedPrefs.getPrivateKey() != null

    /**
     * Wipe all stored keys (e.g., on account reset).
     */
    fun clearKeys() {
        encryptedPrefs.clearAll()
    }
}
