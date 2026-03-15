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

/** Third fallback API interface for ipwho.is */
interface IpWhoIsApi {
    @GET("{ip}")
    suspend fun lookup(@Path("ip") ip: String): IpWhoIsResponse

    @GET("/")
    suspend fun lookupSelf(): IpWhoIsResponse
}

/** Fourth fallback API interface for ipinfo.io */
interface IpInfoApi {
    @GET("{ip}/json")
    suspend fun lookup(@Path("ip") ip: String): IpInfoResponse

    @GET("json")
    suspend fun lookupSelf(): IpInfoResponse
}

/**
 * ipwho.is response model — third GeoIP provider as additional fallback.
 * Free, no API key required, generous rate limits (10k/month).
 */
data class IpWhoIsResponse(
    val ip: String = "",
    val success: Boolean = false,
    val country: String = "",
    val region: String = "",
    val city: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val connection: IpWhoIsConnection? = null
)

data class IpWhoIsConnection(
    val org: String = "",
    val isp: String = ""
)

/** Map ipwho.is response to the unified GeoIpResponse. */
fun IpWhoIsResponse.toGeoIpResponse(): GeoIpResponse = GeoIpResponse(
    ip = ip,
    country_name = country,
    region = region,
    city = city,
    latitude = latitude,
    longitude = longitude,
    org = connection?.org ?: connection?.isp ?: "",
    error = !success
)

/**
 * ipinfo.io response model — fourth GeoIP provider.
 * Free tier: 50k requests/month, no API key required for basic info.
 * Returns lat/lon as a comma-separated "loc" string.
 */
data class IpInfoResponse(
    val ip: String = "",
    val city: String = "",
    val region: String = "",
    val country: String = "",
    val loc: String = "",  // "lat,lon" format
    val org: String = ""
)

/** Map ipinfo.io response to the unified GeoIpResponse. */
fun IpInfoResponse.toGeoIpResponse(): GeoIpResponse {
    val parts = loc.split(",")
    val lat = parts.getOrNull(0)?.toDoubleOrNull() ?: 0.0
    val lon = parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0
    return GeoIpResponse(
        ip = ip,
        country_name = country,
        region = region,
        city = city,
        latitude = lat,
        longitude = lon,
        org = org,
        error = loc.isEmpty() || lat == 0.0
    )
}

object GeoIpClient {
    private const val PREFS_NAME = "GeoIpCache"
    private const val KEY_CACHED_LOC = "cached_user_location"
    private const val KEY_CACHED_TIMESTAMP = "cached_user_location_ts"
    /** Cache TTL: 30 minutes — prevents stale location from persisting forever */
    private const val CACHE_TTL_MS = 30 * 60 * 1000L

    private fun getCachedLocation(context: android.content.Context): GeoIpResponse? {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CACHED_LOC, null) ?: return null
        // Check cache age — expire after TTL to prevent stale data
        val ts = prefs.getLong(KEY_CACHED_TIMESTAMP, 0L)
        if (ts > 0 && System.currentTimeMillis() - ts > CACHE_TTL_MS) {
            clearCachedLocation(context)
            return null
        }
        return try { com.google.gson.Gson().fromJson(json, GeoIpResponse::class.java) } catch (e: Exception) { null }
    }

    private fun saveCachedLocation(context: android.content.Context, response: GeoIpResponse) {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_CACHED_LOC, com.google.gson.Gson().toJson(response))
            .putLong(KEY_CACHED_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    private fun clearCachedLocation(context: android.content.Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_CACHED_LOC).remove(KEY_CACHED_TIMESTAMP).apply()
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
        "Mozilla/5.0 (Linux; Android 15; Pixel 10) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    /** Shared OkHttpClient with browser User-Agent (ipapi.co 403-bans the default okhttp UA). */
    private val httpClient: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", BROWSER_UA)
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
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

    /** Third fallback API — ipwho.is (free, no key, generous limits). */
    private val ipWhoIsApi: IpWhoIsApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://ipwho.is/")
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(IpWhoIsApi::class.java)
    }

    /** Fourth fallback API — ipinfo.io (free tier 50k/month, no key for basic). */
    private val ipInfoApi: IpInfoApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://ipinfo.io/")
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(IpInfoApi::class.java)
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
    // Known server location overrides
    // -----------------------------------------------------------------

    /**
     * Known GCP server locations by IP prefix.
     *
     * GeoIP databases frequently misattribute Google Cloud IPs (e.g. reporting
     * California for servers in Iowa). Since we control the infrastructure and
     * know exactly where each server is deployed, we override the GeoIP result
     * with the correct coordinates when the server IP is recognized.
     *
     * us-central1 (Council Bluffs, Iowa): 41.2619, -95.8608
     */
    private data class KnownServer(
        val city: String,
        val region: String,
        val latitude: Double,
        val longitude: Double,
        val org: String = "Google Cloud"
    )

    private val knownServers = mapOf(
        // us-central1 — Council Bluffs, Iowa
        "35.206." to KnownServer("Council Bluffs", "Iowa", 41.2619, -95.8608),
        "35.192." to KnownServer("Council Bluffs", "Iowa", 41.2619, -95.8608),
        "35.193." to KnownServer("Council Bluffs", "Iowa", 41.2619, -95.8608),
        "35.194." to KnownServer("Council Bluffs", "Iowa", 41.2619, -95.8608),
        "35.202." to KnownServer("Council Bluffs", "Iowa", 41.2619, -95.8608),
        "35.208." to KnownServer("Council Bluffs", "Iowa", 41.2619, -95.8608),
        "34.68." to KnownServer("Council Bluffs", "Iowa", 41.2619, -95.8608),
        "34.69." to KnownServer("Council Bluffs", "Iowa", 41.2619, -95.8608),
        "34.121." to KnownServer("Council Bluffs", "Iowa", 41.2619, -95.8608),
        "34.122." to KnownServer("Council Bluffs", "Iowa", 41.2619, -95.8608),
        "34.123." to KnownServer("Council Bluffs", "Iowa", 41.2619, -95.8608),
        "34.128." to KnownServer("Council Bluffs", "Iowa", 41.2619, -95.8608),
        "34.132." to KnownServer("Council Bluffs", "Iowa", 41.2619, -95.8608),
        "34.133." to KnownServer("Council Bluffs", "Iowa", 41.2619, -95.8608),
        "34.134." to KnownServer("Council Bluffs", "Iowa", 41.2619, -95.8608),
        "34.135." to KnownServer("Council Bluffs", "Iowa", 41.2619, -95.8608),
        "34.136." to KnownServer("Council Bluffs", "Iowa", 41.2619, -95.8608),
        // us-west1 — The Dalles, Oregon
        "35.197." to KnownServer("The Dalles", "Oregon", 45.5946, -121.1787, "Google Cloud"),
        "35.199." to KnownServer("The Dalles", "Oregon", 45.5946, -121.1787, "Google Cloud"),
        "35.233." to KnownServer("The Dalles", "Oregon", 45.5946, -121.1787, "Google Cloud"),
        // us-west2 — Los Angeles, California
        "35.235." to KnownServer("Los Angeles", "California", 34.0522, -118.2437, "Google Cloud"),
        // us-east1 — Moncks Corner, South Carolina
        "35.196." to KnownServer("Moncks Corner", "South Carolina", 33.1960, -80.0131, "Google Cloud"),
        "35.229." to KnownServer("Moncks Corner", "South Carolina", 33.1960, -80.0131, "Google Cloud")
    )

    /**
     * Check if an IP matches a known GCP server and return its true location.
     * Returns null if the IP is not recognized.
     */
    private fun getKnownServerLocation(ip: String): GeoIpResponse? {
        for ((prefix, server) in knownServers) {
            if (ip.startsWith(prefix)) {
                return GeoIpResponse(
                    ip = ip,
                    country_name = "United States",
                    region = server.region,
                    city = server.city,
                    latitude = server.latitude,
                    longitude = server.longitude,
                    org = server.org,
                    error = false
                )
            }
        }
        return null
    }

    // -----------------------------------------------------------------
    // Fallback-aware lookup methods
    // -----------------------------------------------------------------

    /**
     * Look up a specific IP with fallback: try known servers first, then
     * ipapi.co, then ip-api.com, then ipwho.is.
     */
    suspend fun lookupWithFallback(ip: String): GeoIpResponse {
        // Check known server locations first (bypasses unreliable GeoIP for our own infra)
        getKnownServerLocation(ip)?.let { return it }

        return try {
            val primary = withRetry { api.lookup(ip) }
            if (!primary.error && primary.latitude != 0.0) primary
            else tryFallbacks(ip)
        } catch (_: Exception) {
            tryFallbacks(ip)
        }
    }

    /** Try ip-api.com → ipwho.is → ipinfo.io for a specific IP. */
    private suspend fun tryFallbacks(ip: String): GeoIpResponse {
        return try {
            val fb = withRetry { fallbackApi.lookup(ip) }.toGeoIpResponse()
            if (!fb.error && fb.latitude != 0.0) fb
            else tryIpWhoIsThenIpInfo(ip)
        } catch (_: Exception) {
            tryIpWhoIsThenIpInfo(ip)
        }
    }

    private suspend fun tryIpWhoIsThenIpInfo(ip: String): GeoIpResponse {
        return try {
            val r = withRetry { ipWhoIsApi.lookup(ip) }.toGeoIpResponse()
            if (!r.error && r.latitude != 0.0) r
            else withRetry { ipInfoApi.lookup(ip) }.toGeoIpResponse()
        } catch (_: Exception) {
            try {
                withRetry { ipInfoApi.lookup(ip) }.toGeoIpResponse()
            } catch (_: Exception) {
                GeoIpResponse(error = true)
            }
        }
    }

    /**
     * Look up own IP with triple fallback: ipapi.co → ip-api.com → ipwho.is.
     */
    private suspend fun lookupSelfWithFallback(): GeoIpResponse {
        return try {
            val primary = withRetry { api.lookupSelf() }
            if (!primary.error && primary.latitude != 0.0) primary
            else tryFallbacksSelf()
        } catch (_: Exception) {
            tryFallbacksSelf()
        }
    }

    /** Try ip-api.com → ipwho.is → ipinfo.io for own IP. */
    private suspend fun tryFallbacksSelf(): GeoIpResponse {
        return try {
            val fb = withRetry { fallbackApi.lookupSelf() }.toGeoIpResponse()
            if (!fb.error && fb.latitude != 0.0) fb
            else tryIpWhoIsThenIpInfoSelf()
        } catch (_: Exception) {
            tryIpWhoIsThenIpInfoSelf()
        }
    }

    private suspend fun tryIpWhoIsThenIpInfoSelf(): GeoIpResponse {
        return try {
            val r = withRetry { ipWhoIsApi.lookupSelf() }.toGeoIpResponse()
            if (!r.error && r.latitude != 0.0) r
            else withRetry { ipInfoApi.lookupSelf() }.toGeoIpResponse()
        } catch (_: Exception) {
            try {
                withRetry { ipInfoApi.lookupSelf() }.toGeoIpResponse()
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

        // 3. VPN is off — safe to fetch the user's real IP (with triple fallback)
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
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
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

                // Third fallback bypass API (ipwho.is)
                val bypassIpWhoIs = Retrofit.Builder()
                    .baseUrl("https://ipwho.is/")
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(IpWhoIsApi::class.java)

                // Fourth fallback bypass API (ipinfo.io)
                val bypassIpInfo = Retrofit.Builder()
                    .baseUrl("https://ipinfo.io/")
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(IpInfoApi::class.java)

                val r = try {
                    val primary = withRetry { bypassApi.lookupSelf() }
                    if (!primary.error && primary.latitude != 0.0) primary
                    else {
                        val fb = try {
                            withRetry { bypassFallbackApi.lookupSelf() }.toGeoIpResponse()
                        } catch (_: Exception) { GeoIpResponse(error = true) }
                        if (!fb.error && fb.latitude != 0.0) fb
                        else {
                            val ipw = try {
                                withRetry { bypassIpWhoIs.lookupSelf() }.toGeoIpResponse()
                            } catch (_: Exception) { GeoIpResponse(error = true) }
                            if (!ipw.error && ipw.latitude != 0.0) ipw
                            else withRetry { bypassIpInfo.lookupSelf() }.toGeoIpResponse()
                        }
                    }
                } catch (_: Exception) {
                    try {
                        val fb = withRetry { bypassFallbackApi.lookupSelf() }.toGeoIpResponse()
                        if (!fb.error && fb.latitude != 0.0) fb
                        else {
                            val ipw = try {
                                withRetry { bypassIpWhoIs.lookupSelf() }.toGeoIpResponse()
                            } catch (_: Exception) { GeoIpResponse(error = true) }
                            if (!ipw.error && ipw.latitude != 0.0) ipw
                            else withRetry { bypassIpInfo.lookupSelf() }.toGeoIpResponse()
                        }
                    } catch (_: Exception) {
                        try {
                            val ipw = withRetry { bypassIpWhoIs.lookupSelf() }.toGeoIpResponse()
                            if (!ipw.error && ipw.latitude != 0.0) ipw
                            else withRetry { bypassIpInfo.lookupSelf() }.toGeoIpResponse()
                        } catch (_: Exception) {
                            try {
                                withRetry { bypassIpInfo.lookupSelf() }.toGeoIpResponse()
                            } catch (_: Exception) {
                                GeoIpResponse(error = true)
                            }
                        }
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

        // No VPN active — safe to use the standard API (with triple fallback)
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
