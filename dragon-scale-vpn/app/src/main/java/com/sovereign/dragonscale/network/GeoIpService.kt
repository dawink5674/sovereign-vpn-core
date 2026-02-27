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

    private fun clearCachedLocation(context: android.content.Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_CACHED_LOC).apply()
    }

    /** Check if any VPN transport is active on the device right now. */
    private fun isVpnActive(context: android.content.Context): Boolean {
        return try {
            val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                    as android.net.ConnectivityManager
            @Suppress("DEPRECATION")
            cm.allNetworks.any { network ->
                cm.getNetworkCapabilities(network)
                    ?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
            }
        } catch (_: Exception) { false }
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
     * Eagerly fetch and cache the user's real location.
     *
     * CRITICAL: This function checks if a VPN is active before making any
     * network request. If the VPN is already on, the standard API would
     * return the VPN server's IP — poisoning the cache and causing the
     * "both pins on the same dot" bug.
     *
     * @param serverIp Optional VPN server IP. If provided, the cache is
     *   validated: if the cached IP matches the server IP, it was poisoned
     *   by a previous fetch-through-VPN and is cleared.
     */
    suspend fun fetchAndCacheRealLocation(
        context: android.content.Context,
        serverIp: String? = null
    ): GeoIpResponse {
        // 1. Check cache first
        val cached = getCachedLocation(context)
        if (cached != null && !cached.error && cached.latitude != 0.0) {
            // Validate: if cached IP == VPN server IP, the cache was poisoned
            if (serverIp != null && cached.ip == serverIp) {
                clearCachedLocation(context)
                // Fall through to re-fetch (only if VPN is off)
            } else {
                return cached
            }
        }

        // 2. If VPN is active, DO NOT fetch — the result would be the VPN IP
        if (isVpnActive(context)) {
            return GeoIpResponse(error = true)
        }

        // 3. VPN is off — safe to fetch the user's real IP
        val result = try {
            withRetry { api.lookupSelf() }
        } catch (e: Exception) {
            GeoIpResponse(error = true)
        }
        if (!result.error && result.latitude != 0.0) {
            saveCachedLocation(context, result)
        }
        return result
    }

    /**
     * Look up own IP while bypassing an active VPN tunnel.
     *
     * Strategy:
     * 1. Return cache if available and not poisoned.
     * 2. Attempt bypass via non-VPN network with strict DNS binding.
     * 3. If VPN is active and bypass fails, return error (never return VPN IP).
     * 4. If VPN is NOT active, use the standard API and cache.
     */
    suspend fun lookupSelfBypassVpn(
        context: android.content.Context,
        serverIp: String? = null
    ): GeoIpResponse {
        // 1. Try Cache — this is the primary path when VPN is active.
        val cached = getCachedLocation(context)
        if (cached != null && !cached.error && cached.latitude != 0.0) {
            // Validate: if cached IP == VPN server IP, it's poisoned
            if (serverIp != null && cached.ip == serverIp) {
                clearCachedLocation(context)
            } else {
                return cached
            }
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
                            // Strict bypass: NEVER fall back to Dns.SYSTEM (goes through VPN)
                            return bypassNetwork.getAllByName(hostname).toList()
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

        if (hasVpn) {
            // VPN active, bypass failed, no valid cache — return error
            return GeoIpResponse(error = true)
        }

        // No VPN active — safe to use the standard API
        val fallback = try {
            withRetry { api.lookupSelf() }
        } catch (e: Exception) {
            GeoIpResponse(error = true)
        }

        if (!fallback.error && fallback.latitude != 0.0) {
            saveCachedLocation(context, fallback)
        }
        return fallback
    }
}
