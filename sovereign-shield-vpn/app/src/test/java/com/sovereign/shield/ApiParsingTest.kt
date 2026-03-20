package com.sovereign.shield

import com.google.gson.Gson
import com.sovereign.shield.network.*
import org.junit.Assert.*
import org.junit.Test

class ApiParsingTest {

    private val gson = Gson()

    @Test
    fun `parse live server response with clientConfig string`() {
        val json = """
        {
            "message": "Peer \"Pixel 10 Pro Fold\" provisioned successfully",
            "peer": {
                "name": "Pixel 10 Pro Fold",
                "publicKey": "Z+YfZ9vXUdyzAgCmat6kVfU4P0w1oGn7wqpfFGfhYhs=",
                "assignedIP": "10.66.66.5/32",
                "createdAt": "2026-03-15T14:35:05.321Z"
            },
            "clientConfig": "[Interface]\nPrivateKey = 2LMfEuYmpXjVvxnMPiX7ZHgHb9TudgdAkRvEf0vDB1U=\nAddress = 10.66.66.5/32\nDNS = 1.1.1.1, 1.0.0.1\n\n[Peer]\nPublicKey = G1ReQCSgRG/MdfF5/SMrcnU+lKQMlwkr9aIA7/ZK5WI=\nPresharedKey = TkkJV/jK80QzbhJZJAUY54lwBZW2ObYRcDbE2bsMvSA=\nEndpoint = 35.206.67.49:51820\nAllowedIPs = 0.0.0.0/0, ::/0\nPersistentKeepalive = 25\n",
            "serverApplied": true
        }
        """.trimIndent()

        val response = gson.fromJson(json, PeerRegistrationResponse::class.java)
        assertNotNull(response)
        assertEquals("Pixel 10 Pro Fold", response.peer.name)
        assertEquals("10.66.66.5/32", response.peer.assignedIP)
        assertNull(response.serverConfig)
        assertNotNull(response.clientConfig)
        assertTrue(response.clientConfig!!.contains("PrivateKey"))
        assertTrue(response.serverApplied == true)
    }

    @Test
    fun `parse local server response with serverConfig object`() {
        val json = """
        {
            "message": "Peer \"Pixel 10 Pro Fold\" registered",
            "peer": {
                "name": "Pixel 10 Pro Fold",
                "assignedIP": "10.66.66.2/32",
                "createdAt": "2026-03-14T04:00:00.000Z"
            },
            "serverConfig": {
                "serverPublicKey": "G1ReQCSgRG/MdfF5/SMrcnU+lKQMlwkr9aIA7/ZK5WI=",
                "endpoint": "35.206.67.49:51820",
                "presharedKey": "aBcDeFgHiJkLmNoPqRsTuVwXyZ0123456789ABCDE=",
                "dns": "1.1.1.1, 1.0.0.1",
                "allowedIPs": "0.0.0.0/0, ::/0",
                "persistentKeepalive": 25
            },
            "serverApplied": true
        }
        """.trimIndent()

        val response = gson.fromJson(json, PeerRegistrationResponse::class.java)
        assertNotNull(response)
        assertNotNull(response.serverConfig)
        assertEquals("G1ReQCSgRG/MdfF5/SMrcnU+lKQMlwkr9aIA7/ZK5WI=", response.serverConfig!!.serverPublicKey)
        assertEquals("35.206.67.49:51820", response.serverConfig!!.endpoint)
        assertNull(response.clientConfig)
    }

    @Test
    fun `health response parsing`() {
        val json = """{"status":"ok","service":"sovereign-vpn-control-plane","activePeers":3,"timestamp":"2026-03-19T21:00:00Z","sshConfigured":true}"""
        val response = gson.fromJson(json, HealthResponse::class.java)
        assertEquals("ok", response.status)
        assertEquals(3, response.activePeers)
        assertTrue(response.sshConfigured == true)
    }

    @Test
    fun `parse wireguard conf values`() {
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

        fun parseValue(key: String): String? {
            val regex = Regex("""(?m)^\s*$key\s*=\s*(.+)\s*$""")
            return regex.find(conf)?.groupValues?.get(1)?.trim()
        }

        assertEquals("G1ReQCSgRG/MdfF5/SMrcnU+lKQMlwkr9aIA7/ZK5WI=", parseValue("PublicKey"))
        assertEquals("35.206.67.49:51820", parseValue("Endpoint"))
        assertEquals("25", parseValue("PersistentKeepalive"))
    }
}
