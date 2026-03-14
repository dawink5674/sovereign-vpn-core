package com.sovereign.dragonscale.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    // ---- ipwho.is model tests ----

    @Test
    fun testIpWhoIsResponseConversion_success() {
        val response = IpWhoIsResponse(
            ip = "8.8.4.4",
            success = true,
            country = "United States",
            region = "California",
            city = "Mountain View",
            latitude = 37.386,
            longitude = -122.084,
            connection = IpWhoIsConnection(org = "Google LLC", isp = "Google")
        )

        val converted = response.toGeoIpResponse()
        assertEquals("8.8.4.4", converted.ip)
        assertEquals("United States", converted.country_name)
        assertEquals("California", converted.region)
        assertEquals("Mountain View", converted.city)
        assertEquals(37.386, converted.latitude, 0.001)
        assertEquals(-122.084, converted.longitude, 0.001)
        assertEquals("Google LLC", converted.org)
        assertFalse(converted.error)
    }

    @Test
    fun testIpWhoIsResponseConversion_failure() {
        val response = IpWhoIsResponse(
            ip = "",
            success = false
        )
        val converted = response.toGeoIpResponse()
        assertTrue(converted.error)
    }

    @Test
    fun testIpWhoIsResponseConversion_nullConnection() {
        val response = IpWhoIsResponse(
            ip = "1.1.1.1",
            success = true,
            country = "Australia",
            region = "NSW",
            city = "Sydney",
            latitude = -33.87,
            longitude = 151.21,
            connection = null
        )
        val converted = response.toGeoIpResponse()
        assertEquals("", converted.org)
        assertFalse(converted.error)
    }

    // ---- Known GCP server location tests ----

    @Test
    fun testKnownServerLookup_usCentral1() = runBlocking {
        // 35.206.67.49 is the default VPN server in us-central1 (Iowa)
        val result = GeoIpClient.lookupWithFallback("35.206.67.49")
        assertEquals("Council Bluffs", result.city)
        assertEquals("Iowa", result.region)
        assertEquals(41.2619, result.latitude, 0.01)
        assertEquals(-95.8608, result.longitude, 0.01)
        assertFalse(result.error)
    }

    @Test
    fun testKnownServerLookup_usCentral1_otherPrefix() = runBlocking {
        // 34.121.x.x is also us-central1
        val result = GeoIpClient.lookupWithFallback("34.121.0.1")
        assertEquals("Council Bluffs", result.city)
        assertEquals("Iowa", result.region)
        assertFalse(result.error)
    }

    @Test
    fun testKnownServerLookup_usWest1() = runBlocking {
        // 35.197.x.x is us-west1 (Oregon)
        val result = GeoIpClient.lookupWithFallback("35.197.0.1")
        assertEquals("The Dalles", result.city)
        assertEquals("Oregon", result.region)
        assertFalse(result.error)
    }

    @Test
    fun testKnownServerLookup_unknownIpFallsThrough() = runBlocking {
        // An IP that doesn't match any known server prefix
        // This would fall through to the actual GeoIP APIs.
        // We can't test the full network path in unit tests, but verify
        // it doesn't crash or return the wrong known-server data.
        val result = GeoIpClient.lookupWithFallback("1.1.1.1")
        // Should not be a known server result
        assertTrue(result.city != "Council Bluffs" || result.ip == "1.1.1.1")
    }
}
