package com.sovereign.dragonscale.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class GeoIpTest {

    @Test
    fun testGeoIpResponseParsing() = runBlocking {
        val fakeResponse = GeoIpResponse(
            ip = "8.8.8.8",
            error = false,
            latitude = 37.386,
            longitude = -122.0838,
            org = "Google LLC"
        )

        assertEquals(false, fakeResponse.error)
        assertTrue(fakeResponse.latitude != 0.0)
        assertTrue(fakeResponse.longitude != 0.0)
        assertEquals("Google LLC", fakeResponse.org)
    }

    @Test
    fun testIpApiResponseConversion() {
        val ipApiResponse = IpApiResponse(
            query = "1.2.3.4",
            status = "success",
            country = "United States",
            regionName = "California",
            city = "Mountain View",
            lat = 37.3861,
            lon = -122.0839,
            org = "Google LLC"
        )

        val converted = ipApiResponse.toGeoIpResponse()
        assertEquals("1.2.3.4", converted.ip)
        assertEquals("United States", converted.country_name)
        assertEquals("California", converted.region)
        assertEquals("Mountain View", converted.city)
        assertEquals(37.3861, converted.latitude, 0.001)
        assertEquals(-122.0839, converted.longitude, 0.001)
        assertEquals("Google LLC", converted.org)
        assertFalse(converted.error)
    }

    @Test
    fun testIpApiResponseFailStatus() {
        val failResponse = IpApiResponse(
            query = "",
            status = "fail"
        )
        val converted = failResponse.toGeoIpResponse()
        assertTrue(converted.error)
    }

    @Test
    fun testIsLocationPoisoned_matchingIp() {
        val cached = GeoIpResponse(ip = "35.206.67.49", latitude = 41.0, longitude = -87.0)
        assertTrue(GeoIpClient.isLocationPoisoned(cached, "35.206.67.49"))
    }

    @Test
    fun testIsLocationPoisoned_differentIp() {
        val cached = GeoIpResponse(ip = "1.2.3.4", latitude = 37.0, longitude = -122.0)
        assertFalse(GeoIpClient.isLocationPoisoned(cached, "35.206.67.49"))
    }

    @Test
    fun testIsLocationPoisoned_nullServerIp() {
        val cached = GeoIpResponse(ip = "1.2.3.4", latitude = 37.0, longitude = -122.0)
        assertFalse(GeoIpClient.isLocationPoisoned(cached, null))
    }
}
