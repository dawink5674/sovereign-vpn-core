import Foundation

// MARK: - Request Models

struct PeerRegistrationRequest: Codable {
    let name: String
    let publicKey: String
}

// MARK: - Response Models

struct PeerRegistrationResponse: Codable {
    let message: String?
    let peer: PeerInfo?
    let clientConfig: String?
    let serverConfig: ServerConfig?
    let serverApplied: Bool?
}

struct PeerInfo: Codable {
    let publicKey: String?
    let assignedIP: String?
    let createdAt: String?

    enum CodingKeys: String, CodingKey {
        case publicKey
        case assignedIP
        case createdAt
    }
}

struct ServerConfig: Codable {
    let serverPublicKey: String?
    let endpoint: String?
    let presharedKey: String?
    let dns: String?
    let allowedIPs: String?
    let persistentKeepalive: Int?
}

struct PeerListResponse: Codable {
    let count: Int?
    let peers: [PeerSummary]?
}

struct PeerSummary: Codable {
    let publicKey: String?
    let assignedIP: String?
    let lastHandshake: String?
    let transferRx: Int?
    let transferTx: Int?
}

struct PeerDeleteResponse: Codable {
    let message: String?
    let removedIP: String?
}

struct HealthResponse: Codable {
    let status: String?
    let activePeers: Int?
    let sshConfigured: Bool?
    let serverApplied: Bool?
}

struct ApiError: Codable {
    let error: String?
    let message: String?
}

// MARK: - GeoIP Models

struct GeoIpResponse: Codable {
    let ip: String?
    let city: String?
    let region: String?
    let regionCode: String?
    let country: String?
    let countryName: String?
    let countryCode: String?
    let latitude: Double?
    let longitude: Double?
    let timezone: String?
    let org: String?

    enum CodingKeys: String, CodingKey {
        case ip
        case city
        case region
        case regionCode = "region_code"
        case country
        case countryName = "country_name"
        case countryCode = "country_code"
        case latitude
        case longitude
        case timezone
        case org
    }
}

struct IpApiResponse: Codable {
    let status: String?
    let country: String?
    let regionName: String?
    let city: String?
    let lat: Double?
    let lon: Double?
    let query: String?
    let isp: String?
    let org: String?
    let timezone: String?
}

// MARK: - App State Models

enum VPNConnectionState: String {
    case disconnected
    case connecting
    case connected
    case disconnecting
    case error
}

struct LocationInfo: Codable {
    let ip: String
    let city: String
    let region: String
    let country: String
    let latitude: Double
    let longitude: Double
}

struct LogEntry: Identifiable {
    let id = UUID()
    let message: String
    let type: LogType
    let timestamp: Date

    var formattedTime: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss"
        return formatter.string(from: timestamp)
    }

    var icon: String {
        switch type {
        case .info: return "info.circle.fill"
        case .error: return "exclamationmark.triangle.fill"
        case .traffic: return "chart.bar.fill"
        case .network: return "globe"
        case .dns: return "magnifyingglass"
        case .security: return "lock.fill"
        }
    }

    var color: Color {
        switch type {
        case .info: return Theme.shieldBlue
        case .error: return Theme.statusDisconnected
        case .traffic: return Theme.shieldCyan
        case .network: return Theme.shieldViolet
        case .dns: return Theme.statusConnecting
        case .security: return Theme.statusConnected
        }
    }
}

import SwiftUI

enum LogType: String {
    case info
    case error
    case traffic
    case network
    case dns
    case security
}

// MARK: - WireGuard Config Builder

struct WireGuardConfigBuilder {
    static func buildConfig(
        privateKey: String,
        assignedIP: String,
        dns: String,
        serverPublicKey: String,
        presharedKey: String?,
        endpoint: String,
        allowedIPs: String = "0.0.0.0/0, ::/0",
        persistentKeepalive: Int = 25
    ) -> String {
        var config = """
        [Interface]
        PrivateKey = \(privateKey)
        Address = \(assignedIP)
        DNS = \(dns)

        [Peer]
        PublicKey = \(serverPublicKey)
        """

        if let psk = presharedKey, !psk.isEmpty {
            config += "\nPresharedKey = \(psk)"
        }

        config += """

        Endpoint = \(endpoint)
        AllowedIPs = \(allowedIPs)
        PersistentKeepalive = \(persistentKeepalive)
        """

        return config
    }

    static func parseClientConfig(_ configString: String) -> (serverPublicKey: String?, presharedKey: String?, endpoint: String?, dns: String?, assignedIP: String?) {
        var serverPublicKey: String?
        var presharedKey: String?
        var endpoint: String?
        var dns: String?
        var assignedIP: String?

        for line in configString.components(separatedBy: "\n") {
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            if trimmed.hasPrefix("PublicKey") {
                serverPublicKey = trimmed.components(separatedBy: "=").dropFirst().joined(separator: "=").trimmingCharacters(in: .whitespaces)
            } else if trimmed.hasPrefix("PresharedKey") {
                presharedKey = trimmed.components(separatedBy: "=").dropFirst().joined(separator: "=").trimmingCharacters(in: .whitespaces)
            } else if trimmed.hasPrefix("Endpoint") {
                endpoint = trimmed.components(separatedBy: "=").dropFirst().joined(separator: "=").trimmingCharacters(in: .whitespaces)
            } else if trimmed.hasPrefix("DNS") {
                dns = trimmed.components(separatedBy: "=").dropFirst().joined(separator: "=").trimmingCharacters(in: .whitespaces)
            } else if trimmed.hasPrefix("Address") {
                assignedIP = trimmed.components(separatedBy: "=").dropFirst().joined(separator: "=").trimmingCharacters(in: .whitespaces)
            }
        }

        return (serverPublicKey, presharedKey, endpoint, dns, assignedIP)
    }
}
