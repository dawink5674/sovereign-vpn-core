import SwiftUI

struct StatsView: View {
    @ObservedObject var vpnManager = VPNManager.shared
    @ObservedObject var settings = VPNSettings.shared

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Header
                Text("NETWORK STATISTICS")
                    .font(.system(size: 18, weight: .bold))
                    .tracking(3)
                    .foregroundColor(Theme.textBright)
                    .padding(.top, 16)

                // Current Speed
                currentSpeedSection

                // Throughput History
                throughputChart

                // Session Stats
                sessionStatsSection

                // Lifetime Stats
                lifetimeStatsSection

                // Security Info
                securityInfoSection
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 32)
        }
        .background(Theme.spaceBlack)
    }

    // MARK: - Current Speed

    private var currentSpeedSection: some View {
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
                }
            }
        }
    }

    // MARK: - Throughput Chart

    private var throughputChart: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 8) {
                Text("THROUGHPUT HISTORY")
                    .font(.system(size: 10, weight: .bold))
                    .tracking(1)
                    .foregroundColor(Theme.textMuted)

                SpeedChartView(
                    rxHistory: vpnManager.rxSpeedHistory,
                    txHistory: vpnManager.txSpeedHistory
                )
                .frame(height: 160)

                HStack(spacing: 16) {
                    HStack(spacing: 4) {
                        Circle()
                            .fill(Theme.chartDownload)
                            .frame(width: 6, height: 6)
                        Text("Download")
                            .font(.labelSmall)
                            .foregroundColor(Theme.textSecondary)
                    }
                    HStack(spacing: 4) {
                        Circle()
                            .fill(Theme.chartUpload)
                            .frame(width: 6, height: 6)
                        Text("Upload")
                            .font(.labelSmall)
                            .foregroundColor(Theme.textSecondary)
                    }
                }
            }
        }
    }

    // MARK: - Session Stats

    private var sessionStatsSection: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("SESSION")
                    .font(.system(size: 10, weight: .bold))
                    .tracking(1)
                    .foregroundColor(Theme.textMuted)

                statsRow("Data Received", VPNManager.formatBytes(vpnManager.rxBytes))
                statsRow("Data Sent", VPNManager.formatBytes(vpnManager.txBytes))
                statsRow("Duration", vpnManager.connectionState == .connected
                         ? vpnManager.formattedDuration : "--:--:--")
                statsRow("Network", "WiFi")
            }
        }
    }

    // MARK: - Lifetime Stats

    private var lifetimeStatsSection: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("LIFETIME")
                    .font(.system(size: 10, weight: .bold))
                    .tracking(1)
                    .foregroundColor(Theme.textMuted)

                statsRow("Total Sessions", "\(settings.totalSessions)")
                statsRow("Total Downloaded",
                         VPNManager.formatBytes(settings.totalDownloaded + vpnManager.rxBytes))
                statsRow("Total Uploaded",
                         VPNManager.formatBytes(settings.totalUploaded + vpnManager.txBytes))
            }
        }
    }

    // MARK: - Security Info

    private var securityInfoSection: some View {
        GlassCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("SECURITY")
                    .font(.system(size: 10, weight: .bold))
                    .tracking(1)
                    .foregroundColor(Theme.textMuted)

                statsRow("Protocol", "WireGuard")
                statsRow("Cipher", "ChaCha20-Poly1305")
                statsRow("Key Exchange", "Curve25519 (ECDH)")
                statsRow("PFS", "Per-session preshared key")
                statsRow("Key Length", "256-bit")
            }
        }
    }

    // MARK: - Helpers

    private func statsRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label)
                .font(.bodyMedium)
                .foregroundColor(Theme.textSecondary)
            Spacer()
            Text(value)
                .font(.monoMedium)
                .foregroundColor(Theme.textPrimary)
        }
    }
}

// MARK: - Speed Chart

struct SpeedChartView: View {
    let rxHistory: [Float]
    let txHistory: [Float]

    var body: some View {
        GeometryReader { geo in
            let width = geo.size.width
            let height = geo.size.height

            ZStack {
                // Grid
                gridLines(width: width, height: height)

                // Download line
                chartLine(data: rxHistory, color: Theme.chartDownload,
                         width: width, height: height)

                // Upload line
                chartLine(data: txHistory, color: Theme.chartUpload,
                         width: width, height: height)
            }
        }
        .background(Theme.spaceCard.opacity(0.5))
        .cornerRadius(8)
    }

    private func gridLines(width: CGFloat, height: CGFloat) -> some View {
        Canvas { context, _ in
            let gridColor = Theme.chartGrid

            // Horizontal lines
            for i in 0...4 {
                let y = height * CGFloat(i) / 4
                var path = Path()
                path.move(to: CGPoint(x: 0, y: y))
                path.addLine(to: CGPoint(x: width, y: y))
                context.stroke(path, with: .color(gridColor), lineWidth: 0.5)
            }
        }
    }

    private func chartLine(data: [Float], color: Color, width: CGFloat, height: CGFloat) -> some View {
        Path { path in
            guard data.count > 1 else { return }

            let maxVal = max(data.max() ?? 1, 1)
            let stepX = width / CGFloat(max(data.count - 1, 1))

            for (index, value) in data.enumerated() {
                let x = CGFloat(index) * stepX
                let y = height - (CGFloat(value / maxVal) * height * 0.9) - height * 0.05

                if index == 0 {
                    path.move(to: CGPoint(x: x, y: y))
                } else {
                    path.addLine(to: CGPoint(x: x, y: y))
                }
            }
        }
        .stroke(color, lineWidth: 2)
    }
}

#Preview {
    StatsView()
}
