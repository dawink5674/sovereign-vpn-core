package com.sovereign.dragonscale.network

import com.google.gson.annotations.SerializedName

// ---- Request Models ----

data class PeerRegistrationRequest(
    val name: String,
    val publicKey: String
)

// ---- Response Models ----

data class HealthResponse(
    val status: String,
    val service: String,
    val activePeers: Int,
    val timestamp: String
)

data class PeerRegistrationResponse(
    val message: String,
    val peer: PeerInfo,
    val serverConfig: ServerConfig,
    val serverPeerBlock: String
)

data class PeerInfo(
    val name: String,
    val assignedIP: String,
    val createdAt: String
)

data class ServerConfig(
    val serverPublicKey: String,
    val endpoint: String,
    val presharedKey: String,
    val dns: String,
    val allowedIPs: String,
    val persistentKeepalive: Int
)

data class PeerListResponse(
    val count: Int,
    val peers: List<PeerSummary>
)

data class PeerSummary(
    val name: String,
    val publicKey: String,
    val assignedIP: String,
    val createdAt: String
)

data class PeerDeleteResponse(
    val message: String,
    val removedPeer: PeerSummary,
    val serverAction: String
)

data class ApiError(
    val error: String
)
