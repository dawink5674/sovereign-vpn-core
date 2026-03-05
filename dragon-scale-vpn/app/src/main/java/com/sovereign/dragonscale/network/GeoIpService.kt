package com.sovereign.dragonscale.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Geo-IP lookup via ipapi.co (primary) + ip-api.com (fallback).
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

/**
 * ip-api.com response model — different field names from ipapi.co.
 * Free tier, no key required, separate rate-limit pool.
 */
data class IpApiResponse(
    val query: String = "",
    val status: String = "",
    val country: String = "",
    val regionName: String = "",
    val city: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val org: String = ""
)

/** Map ip-api.com response to the unified GeoIpResponse. */
fun IpApiResponse.toGeoIpResponse(): GeoIpResponse = GeoIpResponse(
    ip = query,
    country_name = country,
    region = regionName,
    city = city,
    latitude = lat,
    longitude = lon,
    org = org,
    error = status != "success"
)

interface GeoIpApi {
    @GET("{ip}/json/")
    suspend fun lookup(@Path("ip") ip: String): GeoIpResponse

    @GET("json/")
    suspend fun lookupSelf(): GeoIpResponse
}

/** Fallback API interface for ip-api.com */
interface IpApiFallbackApi {
    @GET("json/{ip}")
    suspend fun lookup(@Path("ip") ip: String): IpApiResponse

    @GET("json/")
    suspend fun lookupSelf(): IpApiResponse
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

    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"

    /** Shared OkHttpClient with browser User-Agent (ipapi.co 403-bans the default okhttp UA). */
    private val httpClient: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", BROWSER_UA)
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    val api: GeoIpApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://ipapi.co/")
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeoIpApi::class.java)
    }

    /** Fallback API — ip-api.com (free, no key, different rate-limit pool). */
    val fallbackApi: IpApiFallbackApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://ip-api.com/")
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(IpApiFallbackApi::class.java)
    }

    private suspend fun <T> withRetry(
        times: Int = 3,
        initialDelay: Long = 3000,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(times - 1) {
            try {
                return block()
            } catch (e: Exception) {
                // If rate-limited (429/403), wait much longer
                val delay = if (e is retrofit2.HttpException && e.code() in listOf(429, 403)) {
                    10_000L // 10s for rate limits
                } else {
                    currentDelay
                }
                kotlinx.coroutines.delay(delay)
                currentDelay = (currentDelay * factor).toLong()
            }
        }
        return block() // last attempt
    }

    /**
     * Check if cached location data was poisoned by the VPN.
     * Detects poisoning via IP string match. Coordinate proximity
     * can be added in future if server coords are known at check time.
     */
    fun isLocationPoisoned(cached: GeoIpResponse, serverIp: String?): Boolean {
        if (serverIp == null) return false
        if (cached.ip == serverIp) return true
        return false
    }

    /** Public cache clear — call when cache poisoning is suspected. */
    fun clearCache(context: android.content.Context) {
        clearCachedLocation(context)
    }

    // -----------------------------------------------------------------
    // Fallback-aware lookup methods
    // -----------------------------------------------------------------

    /**
     * Look up a specific IP with fallback: try ipapi.co first, then ip-api.com.
     */
    suspend fun lookupWithFallback(ip: String): GeoIpResponse {
        return try {
            val primary = withRetry { api.lookup(ip) }
            if (!primary.error && primary.latitude != 0.0) primary
            else withRetry { fallbackApi.lookup(ip) }.toGeoIpResponse()
        } catch (_: Exception) {
            try {
                withRetry { fallbackApi.lookup(ip) }.toGeoIpResponse()
            } catch (_: Exception) {
                GeoIpResponse(error = true)
            }
        }
    }

    /**
     * Look up own IP with fallback: try ipapi.co first, then ip-api.com.
     */
    private suspend fun lookupSelfWithFallback(): GeoIpResponse {
        return try {
            val primary = withRetry { api.lookupSelf() }
            if (!primary.error && primary.latitude != 0.0) primary
            else withRetry { fallbackApi.lookupSelf() }.toGeoIpResponse()
        } catch (_: Exception) {
            try {
                withRetry { fallbackApi.lookupSelf() }.toGeoIpResponse()
            } catch (_: Exception) {
                GeoIpResponse(error = true)
            }
        }
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
            if (isLocationPoisoned(cached, serverIp)) {
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

        // 3. VPN is off — safe to fetch the user's real IP (with fallback)
        val result = try {
            lookupSelfWithFallback()
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
            if (isLocationPoisoned(cached, serverIp)) {
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
                            return try {
                                bypassNetwork.getAllByName(hostname).toList()
                            } catch (e: Exception) {
                                // Physical network DNS failed — fall back to system DNS
                                okhttp3.Dns.SYSTEM.lookup(hostname)
                            }
                        }
                    })
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("User-Agent", BROWSER_UA)
                            .build()
                        chain.proceed(request)
                    }
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                // Primary bypass API (ipapi.co)
                val bypassApi = Retrofit.Builder()
                    .baseUrl("https://ipapi.co/")
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(GeoIpApi::class.java)

                // Fallback bypass API (ip-api.com)
                val bypassFallbackApi = Retrofit.Builder()
                    .baseUrl("http://ip-api.com/")
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(IpApiFallbackApi::class.java)

                val r = try {
                    val primary = withRetry { bypassApi.lookupSelf() }
                    if (!primary.error && primary.latitude != 0.0) primary
                    else withRetry { bypassFallbackApi.lookupSelf() }.toGeoIpResponse()
                } catch (_: Exception) {
                    try {
                        withRetry { bypassFallbackApi.lookupSelf() }.toGeoIpResponse()
                    } catch (_: Exception) {
                        GeoIpResponse(error = true)
                    }
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

        // No VPN active — safe to use the standard API (with fallback)
        val fallback = try {
            lookupSelfWithFallback()
        } catch (e: Exception) {
            GeoIpResponse(error = true)
        }

        if (!fallback.error && fallback.latitude != 0.0) {
            saveCachedLocation(context, fallback)
        }
        return fallback
    }
}
