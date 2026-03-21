import Foundation

class GeoIpClient {
    static let shared = GeoIpClient()

    private let session: URLSession
    private let locationCacheKey = "cachedUserLocation"

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 10
        session = URLSession(configuration: config)
    }

    // MARK: - Public API

    func fetchCurrentLocation() async -> LocationInfo? {
        // Try cache first
        if let cached = loadCachedLocation() {
            return cached
        }

        // Try primary provider (ipapi.co)
        if let location = await fetchFromIpApiCo() {
            cacheLocation(location)
            return location
        }

        // Fallback to ip-api.com
        if let location = await fetchFromIpApi() {
            cacheLocation(location)
            return location
        }

        return nil
    }

    func fetchLocationForIP(_ ip: String) async -> LocationInfo? {
        // Try ipapi.co first
        if let location = await fetchFromIpApiCo(ip: ip) {
            return location
        }

        // Fallback
        if let location = await fetchFromIpApi(ip: ip) {
            return location
        }

        return nil
    }

    func clearCache() {
        UserDefaults.standard.removeObject(forKey: locationCacheKey)
    }

    // MARK: - ipapi.co

    private func fetchFromIpApiCo(ip: String? = nil) async -> LocationInfo? {
        let urlString = ip != nil ? "https://ipapi.co/\(ip!)/json/" : "https://ipapi.co/json/"
        guard let url = URL(string: urlString) else { return nil }

        do {
            let (data, _) = try await session.data(from: url)
            let response = try JSONDecoder().decode(GeoIpResponse.self, from: data)

            guard let ipAddr = response.ip ?? ip,
                  let lat = response.latitude,
                  let lon = response.longitude else { return nil }

            return LocationInfo(
                ip: ipAddr,
                city: response.city ?? "Unknown",
                region: response.region ?? response.regionCode ?? "",
                country: response.countryName ?? response.country ?? "",
                latitude: lat,
                longitude: lon
            )
        } catch {
            return nil
        }
    }

    // MARK: - ip-api.com

    private func fetchFromIpApi(ip: String? = nil) async -> LocationInfo? {
        let urlString = ip != nil ? "http://ip-api.com/json/\(ip!)" : "http://ip-api.com/json/"
        guard let url = URL(string: urlString) else { return nil }

        do {
            let (data, _) = try await session.data(from: url)
            let response = try JSONDecoder().decode(IpApiResponse.self, from: data)

            guard response.status == "success",
                  let lat = response.lat,
                  let lon = response.lon else { return nil }

            return LocationInfo(
                ip: response.query ?? ip ?? "",
                city: response.city ?? "Unknown",
                region: response.regionName ?? "",
                country: response.country ?? "",
                latitude: lat,
                longitude: lon
            )
        } catch {
            return nil
        }
    }

    // MARK: - Cache

    private func cacheLocation(_ location: LocationInfo) {
        if let data = try? JSONEncoder().encode(location) {
            UserDefaults.standard.set(data, forKey: locationCacheKey)
        }
    }

    private func loadCachedLocation() -> LocationInfo? {
        guard let data = UserDefaults.standard.data(forKey: locationCacheKey) else { return nil }
        return try? JSONDecoder().decode(LocationInfo.self, from: data)
    }
}
