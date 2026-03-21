import SwiftUI

struct StatusBadge: View {
    let state: VPNConnectionState

    @State private var dotPulse: Bool = false

    private var text: String {
        switch state {
        case .disconnected, .error: return "UNPROTECTED"
        case .connecting: return "SECURING CONNECTION"
        case .connected: return "SHIELD ACTIVE"
        case .disconnecting: return "DISCONNECTING"
        }
    }

    private var color: Color {
        switch state {
        case .disconnected, .error: return Theme.statusDisconnected
        case .connecting, .disconnecting: return Theme.statusConnecting
        case .connected: return Theme.statusConnected
        }
    }

    var body: some View {
        HStack(spacing: 8) {
            Circle()
                .fill(color)
                .frame(width: 8, height: 8)
                .scaleEffect(dotPulse ? 1.3 : 1.0)
                .opacity(dotPulse ? 0.6 : 1.0)

            Text(text)
                .font(.system(size: 12, weight: .bold))
                .tracking(1.5)
                .foregroundColor(color)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .background(
            Capsule()
                .fill(color.opacity(0.1))
        )
        .overlay(
            Capsule()
                .stroke(color.opacity(0.3), lineWidth: 1)
        )
        .onAppear {
            withAnimation(.easeInOut(duration: 1.5).repeatForever(autoreverses: true)) {
                dotPulse = true
            }
        }
    }
}

struct EncryptionBadge: View {
    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: "lock.fill")
                .font(.system(size: 10))
            Text("AES-256 + Curve25519")
                .font(.system(size: 10, weight: .medium))
                .tracking(0.5)
        }
        .foregroundColor(Theme.shieldBlue)
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(
            Capsule()
                .fill(Theme.shieldBlue.opacity(0.1))
        )
        .overlay(
            Capsule()
                .stroke(Theme.shieldBlue.opacity(0.2), lineWidth: 1)
        )
    }
}

struct NetworkTypeBadge: View {
    let networkType: String

    private var icon: String {
        switch networkType.lowercased() {
        case "wifi": return "wifi"
        case "cellular": return "antenna.radiowaves.left.and.right"
        case "ethernet": return "cable.connector"
        default: return "shield.fill"
        }
    }

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: icon)
                .font(.system(size: 10))
            Text(networkType)
                .font(.system(size: 10, weight: .medium))
        }
        .foregroundColor(Theme.textSecondary)
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
        .background(
            Capsule()
                .fill(Theme.spaceCard.opacity(0.8))
        )
    }
}

#Preview {
    VStack(spacing: 16) {
        StatusBadge(state: .disconnected)
        StatusBadge(state: .connecting)
        StatusBadge(state: .connected)
        EncryptionBadge()
        NetworkTypeBadge(networkType: "WiFi")
    }
    .padding()
    .background(Theme.spaceBlack)
}
