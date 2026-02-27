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
    private const val PREFS_NAME = "GeoIpCache"
    private const val KEY_CACHED_LOC = "cached_user_location"
    
    private fun getCachedLocation(context: android.content.Context): GeoIpResponse? {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CACHED_LOC, null) ?: return null
        return try { com.google.gson.Gson().fromJson(json, GeoIpResponse::class.java) } catch (e: Exception) { null }
    }
    
    private fun saveCachedLocation(context: android.content.Context, response: GeoIpResponse) {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CACHED_LOC, com.google.gson.Gson().toJson(response)).apply()
    }

    val api: GeoIpApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://ipapi.co/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeoIpApi::class.java)
    }

    private suspend fun <T> withRetry(
        times: Int = 3,
        initialDelay: Long = 1000,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(times - 1) {
            try {
                return block()
            } catch (e: Exception) {
                kotlinx.coroutines.delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            }
        }
        return block() // last attempt
    }

    /**
     * Look up own IP while bypassing an active VPN tunnel.
     * Uses ConnectivityManager to find the underlying Wi-Fi/Cellular network,
     * then binds both socket and DNS to it.
     * Falls back to the standard [api] if bypass is unavailable.
     */
    suspend fun lookupSelfBypassVpn(context: android.content.Context): GeoIpResponse {
        // 1. Try Cache First to survive App restarts while VPN is active
        val cached = getCachedLocation(context)
        if (cached != null && !cached.error && cached.latitude != 0.0) {
            return cached
        }

        var hasVpn = false
        try {
            val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                    as android.net.ConnectivityManager

            @Suppress("DEPRECATION")
            val networks = cm.allNetworks
            var bypassNetwork: android.net.Network? = null

            for (network in networks) {
                val caps = cm.getNetworkCapabilities(network)
                if (caps != null) {
                    if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) {
                        hasVpn = true
                    }
                    if (caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
                    ) {
                        bypassNetwork = network
                    }
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

                val r = try {
                    withRetry { bypassApi.lookupSelf() }
                } catch (e: Exception) {
                    GeoIpResponse(error = true)
                }

                if (!r.error && r.latitude != 0.0) {
                    saveCachedLocation(context, r)
                    return r
                }
            }
        } catch (_: Exception) {}

        // Fallback: use the standard API (works when VPN is off, but fetches VPN IP if on)
        val fallback = try {
            withRetry { api.lookupSelf() }
        } catch (e: Exception) {
            GeoIpResponse(error = true)
        }
        
        // Only cache fallback if VPN is OFF. If VPN is ON, the fallback is the VPN's IP.
        if (!hasVpn && !fallback.error && fallback.latitude != 0.0) {
            saveCachedLocation(context, fallback)
        }
        return fallback
    }
}
