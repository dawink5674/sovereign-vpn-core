package com.sovereign.shield.network

import org.junit.Assert.*
import org.junit.Test

class GeoIpTest {

    @Test
    fun testGeoIpResponseParsing() {
        val response = GeoIpResponse(
            ip = "8.8.8.8", error = false,
            latitude = 37.386, longitude = -122.0838, org = "Google LLC"
        )
        assertFalse(response.error)
        assertTrue(response.latitude != 0.0)
        assertEquals("Google LLC", response.org)
    }

    @Test
    fun testIpApiResponseConversion() {
        val ipApiResponse = IpApiResponse(
            query = "1.2.3.4", status = "success",
            country = "United States", regionName = "California",
            city = "Mountain View", lat = 37.3861, lon = -122.0839, org = "Google LLC"
        )
        val converted = ipApiResponse.toGeoIpResponse()
        assertEquals("1.2.3.4", converted.ip)
        assertEquals("United States", converted.country_name)
        assertFalse(converted.error)
    }

    @Test
    fun testIpApiResponseFailStatus() {
        val failResponse = IpApiResponse(query = "", status = "fail")
        assertTrue(failResponse.toGeoIpResponse().error)
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
