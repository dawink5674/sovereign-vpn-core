package com.sovereign.dragonscale

import com.google.gson.Gson
import com.sovereign.dragonscale.network.PeerRegistrationResponse
import com.sovereign.dragonscale.network.ServerConfig
import com.sovereign.dragonscale.network.PeerInfo
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for API response parsing.
 * Verifies that Gson correctly deserializes both the live and local server formats.
 */
class ApiParsingTest {

    private val gson = Gson()

    @Test
    fun `parse live server response with clientConfig string`() {
        val json = """
        {
            "message": "Peer \"Pixel 10\" provisioned successfully",
            "peer": {
                "name": "Pixel 10",
                "publicKey": "Z+YfZ9vXUdyzAgCmat6kVfU4P0w1oGn7wqpfFGfhYhs=",
                "assignedIP": "10.66.66.5/32",
                "createdAt": "2026-02-15T14:35:05.321Z"
            },
            "clientConfig": "[Interface]\nPrivateKey = 2LMfEuYmpXjVvxnMPiX7ZHgHb9TudgdAkRvEf0vDB1U=\nAddress = 10.66.66.5/32\nDNS = 1.1.1.1, 1.0.0.1\n\n[Peer]\nPublicKey = G1ReQCSgRG/MdfF5/SMrcnU+lKQMlwkr9aIA7/ZK5WI=\nPresharedKey = TkkJV/jK80QzbhJZJAUY54lwBZW2ObYRcDbE2bsMvSA=\nEndpoint = 35.206.67.49:51820\nAllowedIPs = 0.0.0.0/0, ::/0\nPersistentKeepalive = 25\n",
            "serverPeerBlock": "\n[Peer]\n# Pixel 10\nPublicKey = test\nAllowedIPs = 10.66.66.5/32\n",
            "instructions": {
                "android": "Import clientConfig as a .conf file in the WireGuard Android app",
                "desktop": "Save clientConfig as wg-client.conf",
                "server": "Append serverPeerBlock to /etc/wireguard/wg0.conf"
            }
        }
        """.trimIndent()

        val response = gson.fromJson(json, PeerRegistrationResponse::class.java)

        assertNotNull(response)
        assertEquals("Pixel 10", response.peer.name)
        assertEquals("10.66.66.5/32", response.peer.assignedIP)
        assertEquals("Z+YfZ9vXUdyzAgCmat6kVfU4P0w1oGn7wqpfFGfhYhs=", response.peer.publicKey)

        // serverConfig should be null for live server format
        assertNull(response.serverConfig)

        // clientConfig should contain the WireGuard .conf
        assertNotNull(response.clientConfig)
        assertTrue(response.clientConfig!!.contains("PrivateKey"))
        assertTrue(response.clientConfig!!.contains("PublicKey"))
        assertTrue(response.clientConfig!!.contains("Endpoint"))

        // instructions should be parsed
        assertNotNull(response.instructions)
        assertNotNull(response.instructions!!.android)
    }

    @Test
    fun `parse local server response with serverConfig object`() {
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

        // serverConfig should be present
        assertNotNull(response.serverConfig)
        assertEquals("G1ReQCSgRG/MdfF5/SMrcnU+lKQMlwkr9aIA7/ZK5WI=", response.serverConfig!!.serverPublicKey)
        assertEquals("35.206.67.49:51820", response.serverConfig!!.endpoint)
        assertEquals(25, response.serverConfig!!.persistentKeepalive)

        // clientConfig should be null for local server format
        assertNull(response.clientConfig)
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

    @Test
    fun `parse wireguard conf values from clientConfig`() {
        val conf = """
            [Interface]
            PrivateKey = 2LMfEuYmpXjVvxnMPiX7ZHgHb9TudgdAkRvEf0vDB1U=
            Address = 10.66.66.5/32
            DNS = 1.1.1.1, 1.0.0.1

            [Peer]
            PublicKey = G1ReQCSgRG/MdfF5/SMrcnU+lKQMlwkr9aIA7/ZK5WI=
            PresharedKey = TkkJV/jK80QzbhJZJAUY54lwBZW2ObYRcDbE2bsMvSA=
            Endpoint = 35.206.67.49:51820
            AllowedIPs = 0.0.0.0/0, ::/0
            PersistentKeepalive = 25
        """.trimIndent()

        // Verify regex parsing works for each key
        fun parseValue(key: String): String? {
            val regex = Regex("""(?m)^\s*$key\s*=\s*(.+)\s*$""")
            return regex.find(conf)?.groupValues?.get(1)?.trim()
        }

        assertEquals("2LMfEuYmpXjVvxnMPiX7ZHgHb9TudgdAkRvEf0vDB1U=", parseValue("PrivateKey"))
        assertEquals("G1ReQCSgRG/MdfF5/SMrcnU+lKQMlwkr9aIA7/ZK5WI=", parseValue("PublicKey"))
        assertEquals("TkkJV/jK80QzbhJZJAUY54lwBZW2ObYRcDbE2bsMvSA=", parseValue("PresharedKey"))
        assertEquals("35.206.67.49:51820", parseValue("Endpoint"))
        assertEquals("1.1.1.1, 1.0.0.1", parseValue("DNS"))
        assertEquals("0.0.0.0/0, ::/0", parseValue("AllowedIPs"))
        assertEquals("25", parseValue("PersistentKeepalive"))
    }
}
