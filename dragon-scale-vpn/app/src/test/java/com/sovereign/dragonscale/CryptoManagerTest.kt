package com.sovereign.dragonscale

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for CryptoManager key generation logic.
 * Tests run without Android framework dependencies by validating
 * the WireGuard crypto primitives directly.
 */
class CryptoManagerTest {

    @Test
    fun `generated keypair has valid key lengths`() {
        // Simulate what CryptoManager does internally
        val keyPair = com.wireguard.crypto.KeyPair()

        val privateKey = keyPair.privateKey
        val publicKey = keyPair.publicKey

        // WireGuard keys are 32 bytes
        assertEquals(32, privateKey.bytes.size)
        assertEquals(32, publicKey.bytes.size)

        // Base64 encoding should be 44 chars
        assertEquals(44, privateKey.toBase64().length)
        assertEquals(44, publicKey.toBase64().length)
    }

    @Test
    fun `each keypair is unique`() {
        val keyPair1 = com.wireguard.crypto.KeyPair()
        val keyPair2 = com.wireguard.crypto.KeyPair()

        assertNotEquals(
            keyPair1.privateKey.toBase64(),
            keyPair2.privateKey.toBase64()
        )
        assertNotEquals(
            keyPair1.publicKey.toBase64(),
            keyPair2.publicKey.toBase64()
        )
    }

    @Test
    fun `public key derives from private key deterministically`() {
        val keyPair = com.wireguard.crypto.KeyPair()
        val privateKeyBase64 = keyPair.privateKey.toBase64()

        // Recreate from private key
        val privateKey = com.wireguard.crypto.Key.fromBase64(privateKeyBase64)
        val derivedKeyPair = com.wireguard.crypto.KeyPair(privateKey)

        assertEquals(
            keyPair.publicKey.toBase64(),
            derivedKeyPair.publicKey.toBase64()
        )
    }

    @Test
    fun `base64 key round-trips correctly`() {
        val keyPair = com.wireguard.crypto.KeyPair()
        val base64 = keyPair.publicKey.toBase64()

        val restored = com.wireguard.crypto.Key.fromBase64(base64)
        assertEquals(base64, restored.toBase64())
    }

    @Test
    fun `private key is never equal to public key`() {
        val keyPair = com.wireguard.crypto.KeyPair()

        assertNotEquals(
            keyPair.privateKey.toBase64(),
            keyPair.publicKey.toBase64()
        )
    }
}
