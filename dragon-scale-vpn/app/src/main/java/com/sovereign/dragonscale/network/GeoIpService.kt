package com.sovereign.dragonscale.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Geo-IP lookup with multiple API fallbacks.
 * Used for the SOC-style threat map.
 */
data class GeoIpResponse(
    // ipapi.co fields
    val ip: String = "",
    val country_name: String = "",
    val region: String = "",
    val city: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val org: String = "",
    val error: Boolean = false,
    // ip-api.com fields (mapped in merge)
    val query: String = "",
    val country: String = "",
    val regionName: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0
) {
    /** Normalize across API formats */
    fun normalized(): GeoIpResponse = if (ip.isNotEmpty()) this else copy(
        ip = query,
        country_name = country,
        region = regionName,
        latitude = if (latitude != 0.0) latitude else lat,
        longitude = if (longitude != 0.0) longitude else lon
    )
}

// ---------- Primary API: ipapi.co ----------
interface IpApiCoApi {
    @GET("{ip}/json/")
    suspend fun lookup(@Path("ip") ip: String): GeoIpResponse

    @GET("json/")
    suspend fun lookupSelf(): GeoIpResponse
}

// ---------- Fallback API: ip-api.com ----------
interface IpApiComApi {
    @GET("json/{ip}")
    suspend fun lookup(@Path("ip") ip: String): GeoIpResponse

    @GET("json/")
    suspend fun lookupSelf(): GeoIpResponse
}

/**
 * GeoIP client with automatic fallback.
 * Tries ipapi.co first, then ip-api.com, then returns null.
 */
object GeoIpClient {
    private val primaryApi: IpApiCoApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://ipapi.co/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(IpApiCoApi::class.java)
    }

    private val fallbackApi: IpApiComApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://ip-api.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(IpApiComApi::class.java)
    }

    /** Combined API that handles fallback automatically */
    val api = object : IpApiCoApi {
        override suspend fun lookupSelf(): GeoIpResponse {
            // Try primary
            try {
                val r = primaryApi.lookupSelf()
                if (!r.error && r.latitude != 0.0) return r.normalized()
            } catch (_: Exception) {}
            // Fallback
            try {
                val r = fallbackApi.lookupSelf()
                if (r.lat != 0.0) return r.normalized()
            } catch (_: Exception) {}
            throw Exception("All GeoIP APIs failed")
        }

        override suspend fun lookup(ip: String): GeoIpResponse {
            // Try primary
            try {
                val r = primaryApi.lookup(ip)
                if (!r.error && r.latitude != 0.0) return r.normalized()
            } catch (_: Exception) {}
            // Fallback
            try {
                val r = fallbackApi.lookup(ip)
                if (r.lat != 0.0) return r.normalized()
            } catch (_: Exception) {}
            throw Exception("All GeoIP APIs failed for $ip")
        }
    }
}
