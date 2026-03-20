package com.sovereign.shield.crypto

import com.wireguard.crypto.KeyPair
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CryptoManager — handles local WireGuard key generation with rotation support.
 *
 * Keys are generated client-side using WireGuard's Curve25519 implementation.
 * The private key NEVER leaves the device. Only the public key is sent to
 * the Control Plane API for peer registration.
 *
 * Enhancement over Dragon Scale: automatic key rotation tracking
 * and key generation audit trail.
 */
@Singleton
class CryptoManager @Inject constructor(private val encryptedPrefs: EncryptedPrefs) {

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
        encryptedPrefs.incrementKeyRotationCount()
        encryptedPrefs.storeLastKeyRotation(System.currentTimeMillis())

        return publicKeyBase64
    }

    fun getPrivateKey(): String? = encryptedPrefs.getPrivateKey()
    fun getPublicKey(): String? = encryptedPrefs.getPublicKey()
    fun hasKeyPair(): Boolean = encryptedPrefs.getPrivateKey() != null

    /**
     * Rotate keys — clears existing keys and generates fresh ones.
     * Returns the new public key.
     */
    fun rotateKeys(): String {
        clearKeys()
        return generateAndStoreKeyPair()
    }

    fun clearKeys() {
        encryptedPrefs.clearAll()
    }

    fun getKeyRotationCount(): Int = encryptedPrefs.getKeyRotationCount()
    fun getLastKeyRotation(): Long = encryptedPrefs.getLastKeyRotation()
}
