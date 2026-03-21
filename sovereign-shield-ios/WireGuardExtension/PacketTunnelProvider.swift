import NetworkExtension
import WireGuardKit

class PacketTunnelProvider: NEPacketTunnelProvider {

    private lazy var adapter = WireGuardAdapter(with: self) { [weak self] logLevel, message in
        self?.log("[\(logLevel)] \(message)")
    }

    override func startTunnel(options: [String: NSObject]?, completionHandler: @escaping (Error?) -> Void) {
        guard let config = protocolConfiguration as? NETunnelProviderProtocol,
              let providerConfig = config.providerConfiguration,
              let wgConfigString = providerConfig["wgConfig"] as? String else {
            log("Error: No WireGuard configuration found in provider configuration")
            completionHandler(PacketTunnelError.missingConfiguration)
            return
        }

        log("Starting tunnel with WireGuard configuration...")

        do {
            let tunnelConfiguration = try TunnelConfiguration(fromWgQuickConfig: wgConfigString, called: "sovereign")

            adapter.start(tunnelConfiguration: tunnelConfiguration) { [weak self] adapterError in
                if let adapterError = adapterError {
                    self?.log("Error starting WireGuard adapter: \(adapterError.localizedDescription)")
                    completionHandler(adapterError)
                } else {
                    self?.log("WireGuard tunnel started successfully")
                    completionHandler(nil)
                }
            }
        } catch {
            log("Error parsing WireGuard configuration: \(error.localizedDescription)")
            completionHandler(error)
        }
    }

    override func stopTunnel(with reason: NEProviderStopReason, completionHandler: @escaping () -> Void) {
        log("Stopping tunnel, reason: \(reason)")

        adapter.stop { [weak self] error in
            if let error = error {
                self?.log("Error stopping WireGuard adapter: \(error.localizedDescription)")
            } else {
                self?.log("WireGuard tunnel stopped successfully")
            }
            completionHandler()
        }
    }

    override func handleAppMessage(_ messageData: Data, completionHandler: ((Data?) -> Void)?) {
        // Handle messages from the main app if needed
        // Can be used for retrieving tunnel statistics
        if let message = String(data: messageData, encoding: .utf8) {
            log("Received app message: \(message)")

            if message == "getTransferredBytes" {
                adapter.getRuntimeConfiguration { [weak self] config in
                    if let config = config,
                       let data = config.data(using: .utf8) {
                        completionHandler?(data)
                    } else {
                        self?.log("Could not retrieve runtime configuration")
                        completionHandler?(nil)
                    }
                }
                return
            }
        }

        completionHandler?(nil)
    }

    // MARK: - Logging

    private func log(_ message: String) {
        NSLog("[SovereignShield Tunnel] %@", message)
    }
}

// MARK: - Errors

enum PacketTunnelError: LocalizedError {
    case missingConfiguration
    case invalidConfiguration
    case adapterError(String)

    var errorDescription: String? {
        switch self {
        case .missingConfiguration:
            return "WireGuard configuration not found"
        case .invalidConfiguration:
            return "Invalid WireGuard configuration"
        case .adapterError(let message):
            return "WireGuard adapter error: \(message)"
        }
    }
}
