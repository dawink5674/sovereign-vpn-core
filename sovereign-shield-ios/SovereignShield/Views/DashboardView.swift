import SwiftUI

struct DashboardView: View {
    @ObservedObject var vpnManager = VPNManager.shared
    @ObservedObject var settings = VPNSettings.shared
    @State private var timerTick: Int = 0
    @State private var timer: Timer?

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Title
                titleSection

                // Status badge
                StatusBadge(state: vpnManager.connectionState)

                // Shield connect button
                ShieldButton(state: vpnManager.connectionState) {
                    Task {
                        await vpnManager.toggleConnection()
                    }
                }
                .padding(.vertical, 8)

                // Encryption badge
                if vpnManager.connectionState == .connected {
                    EncryptionBadge()
                }

                // Connection info
                connectionInfoSection

                // Speed stats
                speedSection

                // Session info
                sessionInfoSection

                // Server info
                serverInfoSection
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 32)
        }
        .background(Theme.spaceBlack)
        .onAppear { startTimer() }
        .onDisappear { stopTimer() }
    }

    // MARK: - Title

    private var titleSection: some View {
        VStack(spacing: 4) {
            Text("SOVEREIGN SHIELD")
                .font(.system(size: 22, weight: .black))
                .tracking(4)
                .foregroundColor(Theme.textBright)

            Text("QUANTUM-RESISTANT VPN")
                .font(.system(size: 10, weight: .medium))
                .tracking(2)
                .foregroundColor(Theme.textMuted)
        }
        .padding(.top, 16)
    }

    // MARK: - Connection Info

    private var connectionInfoSection: some View {
        GlassCard {
            HStack(spacing: 0) {
                infoItem(
                    title: "DURATION",
                    value: vpnManager.connectionState == .connected
                        ? vpnManager.formattedDuration
                        : "--:--:--"
                )
                Spacer()
                infoItem(title: "NETWORK", value: "WiFi")
                Spacer()
                infoItem(
                    title: "HANDSHAKE",
                    value: vpnManager.connectionState == .connected ? "Active" : "None"
                )
            }
        }
    }

    // MARK: - Speed Stats

    private var speedSection: some View {
        HStack(spacing: 12) {
            GlowCard(accentColor: Theme.chartDownload) {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Image(systemName: "arrow.down.circle.fill")
                            .foregroundColor(Theme.chartDownload)
                        Text("DOWNLOAD")
                            .font(.system(size: 10, weight: .bold))
                            .tracking(1)
                            .foregroundColor(Theme.textSecondary)
                    }
                    Text(vpnManager.rxRate)
                        .font(.monoLarge)
                        .foregroundColor(Theme.chartDownload)
                    Text(VPNManager.formatBytes(vpnManager.rxBytes) + " total")
                        .font(.monoSmall)
                        .foregroundColor(Theme.textMuted)
                }
            }

            GlowCard(accentColor: Theme.chartUpload) {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Image(systemName: "arrow.up.circle.fill")
                            .foregroundColor(Theme.chartUpload)
                        Text("UPLOAD")
                            .font(.system(size: 10, weight: .bold))
                            .tracking(1)
                            .foregroundColor(Theme.textSecondary)
                    }
                    Text(vpnManager.txRate)
                        .font(.monoLarge)
                        .foregroundColor(Theme.chartUpload)
                    Text(VPNManager.formatBytes(vpnManager.txBytes) + " total")
                        .font(.monoSmall)
                        .foregroundColor(Theme.textMuted)
                }
            }
        }
    }

    // MARK: - Session Info

    private var sessionInfoSection: some View {
        GlassCard {
            HStack(spacing: 0) {
                infoItem(title: "SESSIONS", value: "\(settings.totalSessions)")
                Spacer()
                infoItem(title: "PROTOCOL", value: "WireGuard")
                Spacer()
                infoItem(title: "ENCRYPTION", value: "ChaCha20")
            }
        }
    }

    // MARK: - Server Info

    private var serverInfoSection: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 8) {
                Text("SERVER")
                    .font(.system(size: 10, weight: .bold))
                    .tracking(1)
                    .foregroundColor(Theme.textMuted)

                HStack {
                    Image(systemName: "server.rack")
                        .foregroundColor(Theme.shieldCyan)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("G1ReQC... - Curve25519")
                            .font(.monoMedium)
                            .foregroundColor(Theme.textPrimary)
                        Text("35.206.67.49:51820")
                            .font(.monoSmall)
                            .foregroundColor(Theme.textSecondary)
                    }
                    Spacer()
                    Circle()
                        .fill(vpnManager.connectionState == .connected
                              ? Theme.statusConnected
                              : Theme.textMuted)
                        .frame(width: 8, height: 8)
                }
            }
        }
    }

    // MARK: - Helpers

    private func infoItem(title: String, value: String) -> some View {
        VStack(spacing: 4) {
            Text(title)
                .font(.system(size: 9, weight: .bold))
                .tracking(1)
                .foregroundColor(Theme.textMuted)
            Text(value)
                .font(.monoMedium)
                .foregroundColor(Theme.textPrimary)
        }
        .frame(maxWidth: .infinity)
    }

    // MARK: - Timer

    private func startTimer() {
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            timerTick += 1
        }
    }

    private func stopTimer() {
        timer?.invalidate()
        timer = nil
    }
}

#Preview {
    DashboardView()
}
