import SwiftUI

struct ShieldButton: View {
    let state: VPNConnectionState
    let action: () -> Void

    @State private var outerPulse: CGFloat = 0.6
    @State private var innerPulse: CGFloat = 0.8
    @State private var rotationAngle: Double = 0

    private var primaryColor: Color {
        switch state {
        case .connected: return Theme.statusConnected
        case .connecting, .disconnecting: return Theme.statusConnecting
        case .disconnected, .error: return Theme.shieldBlue
        }
    }

    private var secondaryColor: Color {
        switch state {
        case .connected: return Theme.statusConnected.opacity(0.5)
        case .connecting, .disconnecting: return Theme.statusConnecting.opacity(0.5)
        case .disconnected, .error: return Theme.shieldBlueDim
        }
    }

    private var buttonText: String {
        switch state {
        case .disconnected, .error: return "CONNECT"
        case .connecting: return "SECURING"
        case .connected: return "PROTECTED"
        case .disconnecting: return "STOPPING"
        }
    }

    private var isAnimating: Bool {
        state == .connecting || state == .disconnecting
    }

    var body: some View {
        ZStack {
            // Outer glow ring 3
            Circle()
                .stroke(primaryColor.opacity(outerPulse * 0.15), lineWidth: 2)
                .frame(width: 220, height: 220)

            // Outer glow ring 2
            Circle()
                .stroke(primaryColor.opacity(outerPulse * 0.25), lineWidth: 2)
                .frame(width: 200, height: 200)

            // Outer glow ring 1
            Circle()
                .stroke(primaryColor.opacity(innerPulse * 0.4), lineWidth: 3)
                .frame(width: 180, height: 180)
                .rotationEffect(.degrees(isAnimating ? rotationAngle : 0))

            // Main button
            Button(action: action) {
                ZStack {
                    // Gradient fill
                    Circle()
                        .fill(
                            RadialGradient(
                                colors: [
                                    primaryColor.opacity(0.3),
                                    secondaryColor.opacity(0.1),
                                    Theme.spaceBlack.opacity(0.8)
                                ],
                                center: .center,
                                startRadius: 10,
                                endRadius: 80
                            )
                        )
                        .frame(width: 160, height: 160)

                    // Glass border
                    Circle()
                        .stroke(primaryColor.opacity(0.6), lineWidth: 2)
                        .frame(width: 160, height: 160)

                    // Shield icon + text
                    VStack(spacing: 8) {
                        Image(systemName: "shield.fill")
                            .font(.system(size: 40))
                            .foregroundColor(primaryColor)

                        Text(buttonText)
                            .font(.system(size: 14, weight: .bold))
                            .tracking(2)
                            .foregroundColor(primaryColor)
                    }
                }
            }
            .buttonStyle(PlainButtonStyle())
            .disabled(isAnimating)
        }
        .onAppear {
            withAnimation(.easeInOut(duration: 2).repeatForever(autoreverses: true)) {
                outerPulse = 1.0
            }
            withAnimation(.easeInOut(duration: 1.5).repeatForever(autoreverses: true)) {
                innerPulse = 1.0
            }
            if isAnimating {
                withAnimation(.linear(duration: 3).repeatForever(autoreverses: false)) {
                    rotationAngle = 360
                }
            }
        }
        .onChange(of: state) { newState in
            if newState == .connecting || newState == .disconnecting {
                rotationAngle = 0
                withAnimation(.linear(duration: 3).repeatForever(autoreverses: false)) {
                    rotationAngle = 360
                }
            }
        }
    }
}

#Preview {
    VStack(spacing: 40) {
        ShieldButton(state: .disconnected) {}
        ShieldButton(state: .connected) {}
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Theme.spaceBlack)
}
