import Foundation

class ApiClient {
    static let shared = ApiClient()

    private let baseURL = "https://vpn-control-plane-vqkyeuhxnq-uc.a.run.app"
    private let session: URLSession

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 30
        config.httpAdditionalHeaders = [
            "X-Client-Version": "1.0.0",
            "X-Client-App": "SovereignShield-iOS",
            "Content-Type": "application/json"
        ]
        session = URLSession(configuration: config)
    }

    // MARK: - Health Check

    func healthCheck() async throws -> HealthResponse {
        let url = URL(string: "\(baseURL)/api/health")!
        let (data, response) = try await session.data(from: url)
        try validateResponse(response)
        return try JSONDecoder().decode(HealthResponse.self, from: data)
    }

    // MARK: - Peer Registration

    func registerPeer(name: String, publicKey: String) async throws -> PeerRegistrationResponse {
        let url = URL(string: "\(baseURL)/api/peers")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"

        let body = PeerRegistrationRequest(name: name, publicKey: publicKey)
        request.httpBody = try JSONEncoder().encode(body)

        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw ApiClientError.invalidResponse
        }

        if httpResponse.statusCode == 409 {
            throw ApiClientError.conflict
        }

        if httpResponse.statusCode < 200 || httpResponse.statusCode >= 300 {
            if let apiError = try? JSONDecoder().decode(ApiError.self, from: data) {
                throw ApiClientError.serverError(apiError.error ?? apiError.message ?? "Unknown error")
            }
            throw ApiClientError.httpError(httpResponse.statusCode)
        }

        return try JSONDecoder().decode(PeerRegistrationResponse.self, from: data)
    }

    // MARK: - List Peers

    func listPeers() async throws -> PeerListResponse {
        let url = URL(string: "\(baseURL)/api/peers")!
        let (data, response) = try await session.data(from: url)
        try validateResponse(response)
        return try JSONDecoder().decode(PeerListResponse.self, from: data)
    }

    // MARK: - Delete Peer

    func deletePeer(publicKey: String) async throws -> PeerDeleteResponse {
        let encoded = publicKey.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? publicKey
        let url = URL(string: "\(baseURL)/api/peers/\(encoded)")!
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"

        let (data, response) = try await session.data(for: request)
        try validateResponse(response)
        return try JSONDecoder().decode(PeerDeleteResponse.self, from: data)
    }

    // MARK: - Helpers

    private func validateResponse(_ response: URLResponse) throws {
        guard let httpResponse = response as? HTTPURLResponse else {
            throw ApiClientError.invalidResponse
        }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw ApiClientError.httpError(httpResponse.statusCode)
        }
    }
}

// MARK: - Errors

enum ApiClientError: LocalizedError {
    case invalidResponse
    case httpError(Int)
    case conflict
    case serverError(String)

    var errorDescription: String? {
        switch self {
        case .invalidResponse: return "Invalid server response"
        case .httpError(let code): return "HTTP error: \(code)"
        case .conflict: return "Peer already registered"
        case .serverError(let msg): return msg
        }
    }
}
