import SwiftUI

struct GlassCard<Content: View>: View {
    let cornerRadius: CGFloat
    let content: () -> Content

    init(cornerRadius: CGFloat = 20, @ViewBuilder content: @escaping () -> Content) {
        self.cornerRadius = cornerRadius
        self.content = content
    }

    var body: some View {
        content()
            .padding(16)
            .background(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .fill(Theme.spaceCard.opacity(0.7))
            )
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .stroke(Theme.glassBorder, lineWidth: 1)
            )
    }
}

struct GlowCard<Content: View>: View {
    let accentColor: Color
    let cornerRadius: CGFloat
    let content: () -> Content

    init(accentColor: Color = Theme.shieldBlue, cornerRadius: CGFloat = 20, @ViewBuilder content: @escaping () -> Content) {
        self.accentColor = accentColor
        self.cornerRadius = cornerRadius
        self.content = content
    }

    var body: some View {
        content()
            .padding(16)
            .background(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .fill(
                        LinearGradient(
                            colors: [
                                Theme.spaceCard.opacity(0.9),
                                Theme.spaceBlack.opacity(0.95)
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
            )
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .stroke(
                        LinearGradient(
                            colors: [
                                accentColor.opacity(0.5),
                                accentColor.opacity(0.1)
                            ],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 1
                    )
            )
    }
}

#Preview {
    VStack(spacing: 16) {
        GlassCard {
            Text("Glass Card")
                .foregroundColor(Theme.textPrimary)
        }
        GlowCard(accentColor: Theme.shieldCyan) {
            Text("Glow Card")
                .foregroundColor(Theme.textPrimary)
        }
    }
    .padding()
    .background(Theme.spaceBlack)
}
