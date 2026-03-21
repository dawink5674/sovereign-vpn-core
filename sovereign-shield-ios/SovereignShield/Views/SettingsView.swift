import SwiftUI

struct SettingsView: View {
    @ObservedObject var settings = VPNSettings.shared

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Header
                Text("CONFIGURATION")
                    .font(.system(size: 18, weight: .bold))
                    .tracking(3)
                    .foregroundColor(Theme.textBright)
                    .padding(.top, 16)

                // Security Section
                settingsSection(title: "SECURITY") {
                    settingsToggle(
                        icon: "shield.fill",
                        title: "Kill Switch",
                        subtitle: "Block all traffic if VPN disconnects",
                        isOn: $settings.killSwitch
                    )
                    settingsToggle(
                        icon: "faceid",
                        title: "Biometric Lock",
                        subtitle: "Require Face ID / Touch ID to open",
                        isOn: $settings.biometricLock
                    )
                }

                // Connection Section
                settingsSection(title: "CONNECTION") {
                    settingsToggle(
                        icon: "play.fill",
                        title: "Auto-Connect",
                        subtitle: "Connect when app launches",
                        isOn: $settings.autoConnect
                    )
                    settingsToggle(
                        icon: "arrow.clockwise",
                        title: "Auto-Reconnect",
                        subtitle: "Reconnect on unexpected drops",
                        isOn: $settings.autoReconnect
                    )
                }

                // DNS Provider Section
                dnsSection

                // About Section
                aboutSection
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 32)
        }
        .background(Theme.spaceBlack)
    }

    // MARK: - Section Container

    private func settingsSection<Content: View>(title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.system(size: 10, weight: .bold))
                .tracking(2)
                .foregroundColor(Theme.textMuted)
                .padding(.leading, 4)

            VStack(spacing: 2) {
                content()
            }
        }
    }

    // MARK: - Toggle Row

    private func settingsToggle(icon: String, title: String, subtitle: String, isOn: Binding<Bool>) -> some View {
        GlassCard(cornerRadius: 12) {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.system(size: 18))
                    .foregroundColor(isOn.wrappedValue ? Theme.shieldBlue : Theme.textMuted)
                    .frame(width: 24)

                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)
                    Text(subtitle)
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textSecondary)
                }

                Spacer()

                Toggle("", isOn: isOn)
                    .labelsHidden()
                    .tint(Theme.shieldBlue)
            }
        }
    }

    // MARK: - DNS Section

    private var dnsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("DNS PROVIDER")
                .font(.system(size: 10, weight: .bold))
                .tracking(2)
                .foregroundColor(Theme.textMuted)
                .padding(.leading, 4)

            VStack(spacing: 2) {
                dnsOption(
                    name: "Cloudflare",
                    servers: "1.1.1.1, 1.0.0.1",
                    description: "Fast and privacy-focused",
                    value: "cloudflare"
                )
                dnsOption(
                    name: "Google",
                    servers: "8.8.8.8, 8.8.4.4",
                    description: "Reliable and widely used",
                    value: "google"
                )
                dnsOption(
                    name: "Quad9",
                    servers: "9.9.9.9, 149.112.112.112",
                    description: "Security-focused with threat blocking",
                    value: "quad9"
                )
            }
        }
    }

    private func dnsOption(name: String, servers: String, description: String, value: String) -> some View {
        let isSelected = settings.dnsProvider == value

        return Button(action: { settings.dnsProvider = value }) {
            GlassCard(cornerRadius: 12) {
                HStack(spacing: 12) {
                    ZStack {
                        Circle()
                            .stroke(isSelected ? Theme.shieldBlue : Theme.textMuted, lineWidth: 2)
                            .frame(width: 20, height: 20)
                        if isSelected {
                            Circle()
                                .fill(Theme.shieldBlue)
                                .frame(width: 12, height: 12)
                        }
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        HStack {
                            Text(name)
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(Theme.textPrimary)
                            Spacer()
                            Text(servers)
                                .font(.monoSmall)
                                .foregroundColor(Theme.textMuted)
                        }
                        Text(description)
                            .font(.system(size: 11))
                            .foregroundColor(Theme.textSecondary)
                    }
                }
            }
        }
        .buttonStyle(PlainButtonStyle())
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(isSelected ? Theme.shieldBlue.opacity(0.3) : Color.clear, lineWidth: 1)
                .padding(1)
        )
    }

    // MARK: - About Section

    private var aboutSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("ABOUT")
                .font(.system(size: 10, weight: .bold))
                .tracking(2)
                .foregroundColor(Theme.textMuted)
                .padding(.leading, 4)

            GlassCard(cornerRadius: 12) {
                VStack(spacing: 12) {
                    Image(systemName: "shield.fill")
                        .font(.system(size: 32))
                        .foregroundColor(Theme.shieldBlue)

                    Text("Sovereign Shield VPN")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Theme.textPrimary)

                    Text("Version 1.0.0")
                        .font(.bodySmall)
                        .foregroundColor(Theme.textSecondary)

                    Text("Military-grade encryption powered by WireGuard protocol with Curve25519 key exchange and ChaCha20-Poly1305 cipher.")
                        .font(.bodySmall)
                        .foregroundColor(Theme.textMuted)
                        .multilineTextAlignment(.center)

                    HStack(spacing: 16) {
                        infoPill("WireGuard")
                        infoPill("ChaCha20")
                        infoPill("Curve25519")
                    }
                }
                .frame(maxWidth: .infinity)
            }
        }
    }

    private func infoPill(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 9, weight: .medium))
            .foregroundColor(Theme.shieldCyan)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(
                Capsule()
                    .fill(Theme.shieldCyan.opacity(0.1))
            )
    }
}

#Preview {
    SettingsView()
}
