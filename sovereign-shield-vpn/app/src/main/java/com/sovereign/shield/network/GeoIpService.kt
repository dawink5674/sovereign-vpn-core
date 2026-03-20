package com.sovereign.shield.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Geo-IP lookup via ipapi.co (primary) + ip-api.com (fallback).
 * Used for the global threat map.
 */
data class GeoIpResponse(
    val ip: String = "",
    val country_name: String = "",
    val country_code: String = "",
    val region: String = "",
    val city: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val org: String = "",
    val error: Boolean = false
)

data class IpApiResponse(
    val query: String = "",
    val status: String = "",
    val country: String = "",
    val countryCode: String = "",
    val regionName: String = "",
    val city: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val org: String = ""
)

fun IpApiResponse.toGeoIpResponse(): GeoIpResponse = GeoIpResponse(
    ip = query,
    country_name = country,
    country_code = countryCode,
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

    fun clearCache(context: android.content.Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_CACHED_LOC).apply()
    }

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

    fun isLocationPoisoned(cached: GeoIpResponse, serverIp: String?): Boolean {
        if (serverIp == null) return false
        return cached.ip == serverIp
    }

    private const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 15; Pixel 10 Pro Fold) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

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
            try { return block() } catch (e: Exception) {
                val delay = if (e is retrofit2.HttpException && e.code() in listOf(429, 403)) 10_000L else currentDelay
                kotlinx.coroutines.delay(delay)
                currentDelay = (currentDelay * factor).toLong()
            }
        }
        return block()
    }

    suspend fun lookupWithFallback(ip: String): GeoIpResponse {
        return try {
            val primary = withRetry { api.lookup(ip) }
            if (!primary.error && primary.latitude != 0.0) primary
            else withRetry { fallbackApi.lookup(ip) }.toGeoIpResponse()
        } catch (_: Exception) {
            try { withRetry { fallbackApi.lookup(ip) }.toGeoIpResponse() }
            catch (_: Exception) { GeoIpResponse(error = true) }
        }
    }

    private suspend fun lookupSelfWithFallback(): GeoIpResponse {
        return try {
            val primary = withRetry { api.lookupSelf() }
            if (!primary.error && primary.latitude != 0.0) primary
            else withRetry { fallbackApi.lookupSelf() }.toGeoIpResponse()
        } catch (_: Exception) {
            try { withRetry { fallbackApi.lookupSelf() }.toGeoIpResponse() }
            catch (_: Exception) { GeoIpResponse(error = true) }
        }
    }

    suspend fun fetchAndCacheRealLocation(
        context: android.content.Context,
        serverIp: String? = null
    ): GeoIpResponse {
        val cached = getCachedLocation(context)
        if (cached != null && !cached.error && cached.latitude != 0.0) {
            if (isLocationPoisoned(cached, serverIp)) {
                clearCache(context)
            } else {
                return cached
            }
        }
        if (isVpnActive(context)) return GeoIpResponse(error = true)
        val result = try { lookupSelfWithFallback() } catch (e: Exception) { GeoIpResponse(error = true) }
        if (!result.error && result.latitude != 0.0) saveCachedLocation(context, result)
        return result
    }

    suspend fun lookupSelfBypassVpn(
        context: android.content.Context,
        serverIp: String? = null
    ): GeoIpResponse {
        val cached = getCachedLocation(context)
        if (cached != null && !cached.error && cached.latitude != 0.0) {
            if (isLocationPoisoned(cached, serverIp)) clearCache(context)
            else return cached
        }

        var hasVpn = false
        try {
            val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            @Suppress("DEPRECATION")
            val networks = cm.allNetworks
            var bypassNetwork: android.net.Network? = null
            for (network in networks) {
                val caps = cm.getNetworkCapabilities(network)
                if (caps != null) {
                    if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) hasVpn = true
                    if (caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) bypassNetwork = network
                }
            }
            if (bypassNetwork != null) {
                val client = okhttp3.OkHttpClient.Builder()
                    .socketFactory(bypassNetwork.socketFactory)
                    .dns(object : okhttp3.Dns {
                        override fun lookup(hostname: String): List<java.net.InetAddress> {
                            return try { bypassNetwork.getAllByName(hostname).toList() }
                            catch (e: Exception) { okhttp3.Dns.SYSTEM.lookup(hostname) }
                        }
                    })
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder().header("User-Agent", BROWSER_UA).build()
                        chain.proceed(request)
                    }
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val bypassApi = Retrofit.Builder().baseUrl("https://ipapi.co/").client(client)
                    .addConverterFactory(GsonConverterFactory.create()).build().create(GeoIpApi::class.java)
                val bypassFallbackApi = Retrofit.Builder().baseUrl("http://ip-api.com/").client(client)
                    .addConverterFactory(GsonConverterFactory.create()).build().create(IpApiFallbackApi::class.java)

                val r = try {
                    val primary = withRetry { bypassApi.lookupSelf() }
                    if (!primary.error && primary.latitude != 0.0) primary
                    else withRetry { bypassFallbackApi.lookupSelf() }.toGeoIpResponse()
                } catch (_: Exception) {
                    try { withRetry { bypassFallbackApi.lookupSelf() }.toGeoIpResponse() }
                    catch (_: Exception) { GeoIpResponse(error = true) }
                }
                if (!r.error && r.latitude != 0.0) { saveCachedLocation(context, r); return r }
            }
        } catch (_: Exception) {}

        if (hasVpn) return GeoIpResponse(error = true)

        val fallback = try { lookupSelfWithFallback() } catch (e: Exception) { GeoIpResponse(error = true) }
        if (!fallback.error && fallback.latitude != 0.0) saveCachedLocation(context, fallback)
        return fallback
    }
}
