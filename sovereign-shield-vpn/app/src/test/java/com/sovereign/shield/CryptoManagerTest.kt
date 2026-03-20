package com.sovereign.shield

import org.junit.Assert.*
import org.junit.Test

class CryptoManagerTest {

    @Test
    fun `generated keypair has valid key lengths`() {
        val keyPair = com.wireguard.crypto.KeyPair()
        assertEquals(32, keyPair.privateKey.bytes.size)
        assertEquals(32, keyPair.publicKey.bytes.size)
        assertEquals(44, keyPair.privateKey.toBase64().length)
        assertEquals(44, keyPair.publicKey.toBase64().length)
    }

    @Test
    fun `each keypair is unique`() {
        val keyPair1 = com.wireguard.crypto.KeyPair()
        val keyPair2 = com.wireguard.crypto.KeyPair()
        assertNotEquals(keyPair1.privateKey.toBase64(), keyPair2.privateKey.toBase64())
        assertNotEquals(keyPair1.publicKey.toBase64(), keyPair2.publicKey.toBase64())
    }

    @Test
    fun `public key derives from private key deterministically`() {
        val keyPair = com.wireguard.crypto.KeyPair()
        val privateKeyBase64 = keyPair.privateKey.toBase64()
        val privateKey = com.wireguard.crypto.Key.fromBase64(privateKeyBase64)
        val derivedKeyPair = com.wireguard.crypto.KeyPair(privateKey)
        assertEquals(keyPair.publicKey.toBase64(), derivedKeyPair.publicKey.toBase64())
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
        assertNotEquals(keyPair.privateKey.toBase64(), keyPair.publicKey.toBase64())
    }
}
