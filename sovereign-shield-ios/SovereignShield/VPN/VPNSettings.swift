import Foundation
import SwiftUI

class VPNSettings: ObservableObject {
    static let shared = VPNSettings()

    @AppStorage("killSwitch") var killSwitch: Bool = false
    @AppStorage("autoConnect") var autoConnect: Bool = false
    @AppStorage("autoReconnect") var autoReconnect: Bool = true
    @AppStorage("biometricLock") var biometricLock: Bool = false
    @AppStorage("dnsProvider") var dnsProvider: String = "cloudflare"
    @AppStorage("showThreatMap") var showThreatMap: Bool = true
    @AppStorage("notifyOnStateChange") var notifyOnStateChange: Bool = true
    @AppStorage("totalDownloaded") var totalDownloaded: Int64 = 0
    @AppStorage("totalUploaded") var totalUploaded: Int64 = 0
    @AppStorage("totalSessions") var totalSessions: Int = 0

    var dnsServers: String {
        switch dnsProvider {
        case "google":
            return killSwitch ? "8.8.8.8" : "8.8.8.8, 8.8.4.4"
        case "quad9":
            return killSwitch ? "9.9.9.9" : "9.9.9.9, 149.112.112.112"
        default: // cloudflare
            return killSwitch ? "1.1.1.1" : "1.1.1.1, 1.0.0.1"
        }
    }

    func incrementSessions() {
        totalSessions += 1
    }

    func addDownloaded(_ bytes: Int64) {
        totalDownloaded += bytes
    }

    func addUploaded(_ bytes: Int64) {
        totalUploaded += bytes
    }
}
