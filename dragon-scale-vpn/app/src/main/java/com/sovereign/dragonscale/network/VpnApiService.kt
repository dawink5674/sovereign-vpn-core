package com.sovereign.dragonscale.network

import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit interface for the Sovereign VPN Control Plane API.
 * Zero-Trust: only the client's public key is sent; private key never leaves the device.
 */
interface VpnApiService {

    @GET("/api/health")
    suspend fun healthCheck(): Response<HealthResponse>

    @POST("/api/peers")
    suspend fun registerPeer(
        @Body request: PeerRegistrationRequest
    ): Response<PeerRegistrationResponse>

    @GET("/api/peers")
    suspend fun listPeers(): Response<PeerListResponse>

    @DELETE("/api/peers/{publicKey}")
    suspend fun deletePeer(
        @Path("publicKey", encoded = true) publicKey: String
    ): Response<PeerDeleteResponse>
}
