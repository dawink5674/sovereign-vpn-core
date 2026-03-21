import Foundation
import NetworkExtension
import Combine

class VPNManager: ObservableObject {
    static let shared = VPNManager()

    @Published var connectionState: VPNConnectionState = .disconnected
    @Published var connectionStartTime: Date?
    @Published var logs: [LogEntry] = []
    @Published var rxBytes: Int64 = 0
    @Published var txBytes: Int64 = 0
    @Published var rxRate: String = "0 B/s"
    @Published var txRate: String = "0 B/s"
    @Published var rxSpeedHistory: [Float] = []
    @Published var txSpeedHistory: [Float] = []

    private var manager: NETunnelProviderManager?
    private var statusObserver: NSObjectProtocol?
    private let maxLogEntries = 200
    private let maxHistoryPoints = 60
    private let settings = VPNSettings.shared

    private let defaultServerPublicKey = "G1ReQCSgRG/MdfF5/SMrcnU+lKQMlwkr9aIA7/ZK5WI="
    private let defaultEndpoint = "35.206.67.49:51820"

    var connectionDuration: TimeInterval {
        guard let start = connectionStartTime else { return 0 }
        return Date().timeIntervalSince(start)
    }

    var formattedDuration: String {
        let duration = connectionDuration
        let hours = Int(duration) / 3600
        let minutes = (Int(duration) % 3600) / 60
        let seconds = Int(duration) % 60
        return String(format: "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private init() {
        loadOrCreateManager()
        observeStatus()
    }

    // MARK: - Manager Lifecycle

    func loadOrCreateManager() {
        NETunnelProviderManager.loadAllFromPreferences { [weak self] managers, error in
            guard let self = self else { return }
            if let error = error {
                self.addLog("Failed to load VPN config: \(error.localizedDescription)", type: .error)
                return
            }

            if let existing = managers?.first {
                self.manager = existing
                self.addLog("Loaded existing VPN configuration", type: .info)
            } else {
                let newManager = NETunnelProviderManager()
                newManager.localizedDescription = "Sovereign Shield VPN"
                newManager.isEnabled = true

                let proto = NETunnelProviderProtocol()
                proto.providerBundleIdentifier = "com.sovereign.shield.wireguard-extension"
                proto.serverAddress = self.defaultEndpoint
                proto.providerConfiguration = [:]
                newManager.protocolConfiguration = proto

                self.manager = newManager
                self.addLog("Created new VPN configuration", type: .info)
            }

            DispatchQueue.main.async {
                self.updateStateFromNE()
            }
        }
    }

    // MARK: - Connect Flow

    func registerAndConnect() async {
        await MainActor.run {
            connectionState = .connecting
            addLog("Starting connection sequence...", type: .info)
        }

        do {
            // Step 1: Generate or load key pair
            let publicKey: String
            if CryptoManager.shared.hasKeyPair, let existingKey = CryptoManager.shared.publicKey {
                publicKey = existingKey
                await MainActor.run {
                    addLog("Using existing key pair", type: .security)
                }
            } else {
                publicKey = CryptoManager.shared.generateAndStoreKeyPair()
                await MainActor.run {
                    addLog("Generated new Curve25519 key pair", type: .security)
                }
            }

            // Step 2: Register with API
            await MainActor.run {
                addLog("Registering with control plane...", type: .info)
            }

            let response: PeerRegistrationResponse
            do {
                response = try await ApiClient.shared.registerPeer(
                    name: "iOS Device",
                    publicKey: publicKey
                )
            } catch ApiClientError.conflict {
                // Key already registered, rotate and retry
                await MainActor.run {
                    addLog("Key conflict detected, rotating keys...", type: .security)
                }
                let newKey = CryptoManager.shared.rotateKeys()
                response = try await ApiClient.shared.registerPeer(
                    name: "iOS Device",
                    publicKey: newKey
                )
            }

            // Step 3: Extract server config
            var assignedIP: String?
            var serverPublicKey = defaultServerPublicKey
            var endpoint = defaultEndpoint
            var presharedKey: String?
            var dns = settings.dnsServers

            if let serverConfig = response.serverConfig {
                serverPublicKey = serverConfig.serverPublicKey ?? defaultServerPublicKey
                endpoint = serverConfig.endpoint ?? defaultEndpoint
                presharedKey = serverConfig.presharedKey
                if let serverDns = serverConfig.dns, !serverDns.isEmpty {
                    dns = serverDns
                }
            }

            if let peerInfo = response.peer {
                assignedIP = peerInfo.assignedIP
            }

            // Also try parsing clientConfig string
            if let clientConfig = response.clientConfig {
                let parsed = WireGuardConfigBuilder.parseClientConfig(clientConfig)
                if assignedIP == nil { assignedIP = parsed.assignedIP }
                if presharedKey == nil { presharedKey = parsed.presharedKey }
                if parsed.serverPublicKey != nil { serverPublicKey = parsed.serverPublicKey! }
                if parsed.endpoint != nil { endpoint = parsed.endpoint! }
                if parsed.dns != nil { dns = parsed.dns! }
            }

            guard let ip = assignedIP else {
                throw VPNManagerError.noAssignedIP
            }

            // Step 4: Store server config
            CryptoManager.shared.storeServerPublicKey(serverPublicKey)
            CryptoManager.shared.storeEndpoint(endpoint)
            if let psk = presharedKey {
                CryptoManager.shared.storePresharedKey(psk)
            }
            CryptoManager.shared.storeAssignedIP(ip)

            await MainActor.run {
                addLog("Registered: IP \(ip)", type: .info)
                addLog("Server: \(endpoint)", type: .network)
            }

            // Step 5: Build WireGuard config and start tunnel
            guard let privKey = CryptoManager.shared.privateKey else {
                throw VPNManagerError.noPrivateKey
            }

            let wgConfig = WireGuardConfigBuilder.buildConfig(
                privateKey: privKey,
                assignedIP: ip,
                dns: dns,
                serverPublicKey: serverPublicKey,
                presharedKey: presharedKey,
                endpoint: endpoint
            )

            try await startTunnel(wgConfig: wgConfig)

        } catch {
            await MainActor.run {
                connectionState = .error
                addLog("Connection failed: \(error.localizedDescription)", type: .error)

                // Reset to disconnected after showing error
                DispatchQueue.main.asyncAfter(deadline: .now() + 2) { [weak self] in
                    self?.connectionState = .disconnected
                }
            }
        }
    }

    // MARK: - Tunnel Control

    private func startTunnel(wgConfig: String) async throws {
        guard let manager = manager else {
            throw VPNManagerError.noManager
        }

        // Update protocol configuration
        if let proto = manager.protocolConfiguration as? NETunnelProviderProtocol {
            proto.providerConfiguration = ["wgConfig": wgConfig]
            proto.serverAddress = CryptoManager.shared.endpoint ?? defaultEndpoint
        }

        manager.isEnabled = true

        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            manager.saveToPreferences { error in
                if let error = error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume()
                }
            }
        }

        // Reload from preferences after save
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            manager.loadFromPreferences { error in
                if let error = error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume()
                }
            }
        }

        try manager.connection.startVPNTunnel()

        await MainActor.run {
            addLog("VPN tunnel starting...", type: .info)
        }
    }

    func disconnect() {
        manager?.connection.stopVPNTunnel()
        addLog("Disconnecting...", type: .info)
    }

    func toggleConnection() async {
        if connectionState == .connected || connectionState == .connecting {
            disconnect()
        } else {
            await registerAndConnect()
        }
    }

    // MARK: - Status Observation

    private func observeStatus() {
        statusObserver = NotificationCenter.default.addObserver(
            forName: .NEVPNStatusDidChange,
            object: nil,
            queue: .main
        ) { [weak self] notification in
            self?.updateStateFromNE()
        }
    }

    private func updateStateFromNE() {
        guard let connection = manager?.connection else { return }

        let previousState = connectionState

        switch connection.status {
        case .connected:
            connectionState = .connected
            if previousState != .connected {
                connectionStartTime = Date()
                settings.incrementSessions()
                addLog("Tunnel UP - shield active", type: .security)
                addLog("Protocol: WireGuard | Encryption: ChaCha20-Poly1305", type: .info)
            }

        case .connecting:
            connectionState = .connecting
            addLog("Establishing secure tunnel...", type: .info)

        case .disconnected:
            connectionState = .disconnected
            if previousState == .connected {
                // Persist session stats
                settings.addDownloaded(rxBytes)
                settings.addUploaded(txBytes)
                rxBytes = 0
                txBytes = 0
                rxRate = "0 B/s"
                txRate = "0 B/s"
                connectionStartTime = nil
                addLog("Tunnel DOWN - shield inactive", type: .info)
            }

        case .disconnecting:
            connectionState = .disconnecting
            addLog("Disconnecting tunnel...", type: .info)

        case .reasserting:
            connectionState = .connecting
            addLog("Reasserting tunnel...", type: .network)

        case .invalid:
            connectionState = .disconnected
            addLog("VPN configuration invalid", type: .error)

        @unknown default:
            break
        }
    }

    // MARK: - Logging

    func addLog(_ message: String, type: LogType) {
        let entry = LogEntry(message: message, type: type, timestamp: Date())
        if logs.count >= maxLogEntries {
            logs.removeFirst()
        }
        logs.append(entry)
    }

    func clearLogs() {
        logs.removeAll()
    }

    // MARK: - Stats Formatting

    static func formatBytes(_ bytes: Int64) -> String {
        let units = ["B", "KB", "MB", "GB", "TB"]
        var value = Double(bytes)
        var unitIndex = 0
        while value >= 1024 && unitIndex < units.count - 1 {
            value /= 1024
            unitIndex += 1
        }
        if unitIndex == 0 {
            return "\(Int(value)) \(units[unitIndex])"
        }
        return String(format: "%.1f %@", value, units[unitIndex])
    }

    static func formatRate(_ bytesPerSecond: Int64) -> String {
        return "\(formatBytes(bytesPerSecond))/s"
    }

    deinit {
        if let observer = statusObserver {
            NotificationCenter.default.removeObserver(observer)
        }
    }
}

// MARK: - Errors

enum VPNManagerError: LocalizedError {
    case noManager
    case noAssignedIP
    case noPrivateKey
    case configurationFailed

    var errorDescription: String? {
        switch self {
        case .noManager: return "VPN manager not initialized"
        case .noAssignedIP: return "No IP address assigned by server"
        case .noPrivateKey: return "No private key available"
        case .configurationFailed: return "Failed to configure VPN"
        }
    }
}
