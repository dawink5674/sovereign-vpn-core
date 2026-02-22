package com.sovereign.dragonscale.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Geo-IP lookup via ip-api.com (free, no key required).
 * Used for the SOC-style threat map.
 */
data class GeoIpResponse(
    val ip: String = "",
    val country_name: String = "",
    val region: String = "",
    val city: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val org: String = "",
    val error: Boolean = false
)

interface GeoIpApi {
    @GET("{ip}/json/")
    suspend fun lookup(@Path("ip") ip: String): GeoIpResponse

    @GET("json/")
    suspend fun lookupSelf(): GeoIpResponse
}

object GeoIpClient {
    val api: GeoIpApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://ipapi.co/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeoIpApi::class.java)
    }

    /**
     * Look up own IP while bypassing an active VPN tunnel.
     * Uses ConnectivityManager to find the underlying Wi-Fi/Cellular network,
     * then binds both socket and DNS to it.
     * Falls back to the standard [api] if bypass is unavailable.
     */
    suspend fun lookupSelfBypassVpn(context: android.content.Context): GeoIpResponse {
        try {
            val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                    as android.net.ConnectivityManager

            @Suppress("DEPRECATION")
            val networks = cm.allNetworks
            var bypassNetwork: android.net.Network? = null

            for (network in networks) {
                val caps = cm.getNetworkCapabilities(network)
                if (caps != null &&
                    caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
                ) {
                    bypassNetwork = network
                    break
                }
            }

            if (bypassNetwork != null) {
                val client = okhttp3.OkHttpClient.Builder()
                    .socketFactory(bypassNetwork.socketFactory)
                    .dns(object : okhttp3.Dns {
                        override fun lookup(hostname: String): List<java.net.InetAddress> {
                            return try {
                                bypassNetwork.getAllByName(hostname).toList()
                            } catch (_: Exception) {
                                okhttp3.Dns.SYSTEM.lookup(hostname)
                            }
                        }
                    })
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val bypassApi = Retrofit.Builder()
                    .baseUrl("https://ipapi.co/")
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(GeoIpApi::class.java)

                val r = bypassApi.lookupSelf()
                if (!r.error && r.latitude != 0.0) return r
            }
        } catch (_: Exception) {}

        // Fallback: use the standard API (works when VPN is off)
        return api.lookupSelf()
    }
}
