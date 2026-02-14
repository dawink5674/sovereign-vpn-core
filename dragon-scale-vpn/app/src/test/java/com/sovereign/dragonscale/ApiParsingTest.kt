package com.sovereign.dragonscale

import com.google.gson.Gson
import com.sovereign.dragonscale.network.PeerRegistrationResponse
import com.sovereign.dragonscale.network.ServerConfig
import com.sovereign.dragonscale.network.PeerInfo
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for API response parsing.
 * Verifies that Gson correctly deserializes the zero-trust API responses.
 */
class ApiParsingTest {

    private val gson = Gson()

    @Test
    fun `parse peer registration response`() {
        val json = """
        {
            "message": "Peer \"Pixel 10\" registered",
            "peer": {
                "name": "Pixel 10",
                "assignedIP": "10.66.66.2/32",
                "createdAt": "2026-02-14T04:00:00.000Z"
            },
            "serverConfig": {
                "serverPublicKey": "G1ReQCSgRG/MdfF5/SMrcnU+lKQMlwkr9aIA7/ZK5WI=",
                "endpoint": "35.206.67.49:51820",
                "presharedKey": "aBcDeFgHiJkLmNoPqRsTuVwXyZ0123456789ABCDE=",
                "dns": "1.1.1.1, 1.0.0.1",
                "allowedIPs": "0.0.0.0/0, ::/0",
                "persistentKeepalive": 25
            },
            "serverPeerBlock": "[Peer]\nPublicKey = test\n"
        }
        """.trimIndent()

        val response = gson.fromJson(json, PeerRegistrationResponse::class.java)

        assertNotNull(response)
        assertEquals("Pixel 10", response.peer.name)
        assertEquals("10.66.66.2/32", response.peer.assignedIP)
        assertEquals("G1ReQCSgRG/MdfF5/SMrcnU+lKQMlwkr9aIA7/ZK5WI=", response.serverConfig.serverPublicKey)
        assertEquals("35.206.67.49:51820", response.serverConfig.endpoint)
        assertEquals(25, response.serverConfig.persistentKeepalive)
        assertEquals("1.1.1.1, 1.0.0.1", response.serverConfig.dns)
        assertEquals("0.0.0.0/0, ::/0", response.serverConfig.allowedIPs)
    }

    @Test
    fun `server config contains all required fields`() {
        val config = ServerConfig(
            serverPublicKey = "testKey==",
            endpoint = "1.2.3.4:51820",
            presharedKey = "psk==",
            dns = "1.1.1.1",
            allowedIPs = "0.0.0.0/0",
            persistentKeepalive = 25
        )

        assertNotNull(config.serverPublicKey)
        assertNotNull(config.endpoint)
        assertNotNull(config.presharedKey)
        assertTrue(config.endpoint.contains(":"))
        assertEquals(25, config.persistentKeepalive)
    }

    @Test
    fun `peer info model preserves data`() {
        val peer = PeerInfo(
            name = "Test Device",
            assignedIP = "10.66.66.5/32",
            createdAt = "2026-01-01T00:00:00.000Z"
        )

        assertEquals("Test Device", peer.name)
        assertTrue(peer.assignedIP.startsWith("10.66.66."))
        assertTrue(peer.assignedIP.endsWith("/32"))
    }

    @Test
    fun `parse empty peer list`() {
        val json = """{"count": 0, "peers": []}"""
        val response = gson.fromJson(json, com.sovereign.dragonscale.network.PeerListResponse::class.java)

        assertEquals(0, response.count)
        assertTrue(response.peers.isEmpty())
    }

    @Test
    fun `server public key is valid base64 length`() {
        // WireGuard Curve25519 public keys are 32 bytes = 44 base64 chars
        val key = "G1ReQCSgRG/MdfF5/SMrcnU+lKQMlwkr9aIA7/ZK5WI="
        val decoded = java.util.Base64.getDecoder().decode(key)
        assertEquals(32, decoded.size)
    }
}
