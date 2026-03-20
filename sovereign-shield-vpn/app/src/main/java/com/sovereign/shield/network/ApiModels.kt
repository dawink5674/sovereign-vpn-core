package com.sovereign.shield.network

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
    val timestamp: String,
    val sshConfigured: Boolean? = null,
    val serverApplied: Boolean? = null
)

data class PeerRegistrationResponse(
    val message: String,
    val peer: PeerInfo,
    val clientConfig: String? = null,
    val serverConfig: ServerConfig? = null,
    val serverPeerBlock: String? = null,
    val serverApplied: Boolean? = null,
    val instructions: Instructions? = null
)

data class PeerInfo(
    val name: String,
    val publicKey: String? = null,
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

data class Instructions(
    val android: String? = null,
    val desktop: String? = null,
    val server: String? = null
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
    val serverAction: String? = null,
    val serverRemoved: Boolean? = null
)

data class ApiError(
    val error: String
)
