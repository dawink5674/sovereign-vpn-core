import SwiftUI

// MARK: - Color Extension for Hex
extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 6:
            (a, r, g, b) = (255, (int >> 16) & 0xFF, (int >> 8) & 0xFF, int & 0xFF)
        case 8:
            (a, r, g, b) = ((int >> 24) & 0xFF, (int >> 16) & 0xFF, (int >> 8) & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (255, 0, 0, 0)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}

// MARK: - Theme
enum Theme {
    // Primary Brand
    static let shieldBlue = Color(hex: "3B82F6")
    static let shieldBlueBright = Color(hex: "60A5FA")
    static let shieldBlueDim = Color(hex: "1D4ED8")
    static let shieldCyan = Color(hex: "06B6D4")
    static let shieldViolet = Color(hex: "8B5CF6")

    // Surfaces
    static let spaceBlack = Color(hex: "06080D")
    static let spaceDark = Color(hex: "0B0F18")
    static let spaceCard = Color(hex: "111827")
    static let spaceCardElevated = Color(hex: "1A2332")
    static let spaceCardHover = Color(hex: "1F2937")

    // Status
    static let statusConnected = Color(hex: "10B981")
    static let statusDisconnected = Color(hex: "EF4444")
    static let statusConnecting = Color(hex: "F59E0B")

    // Text
    static let textBright = Color(hex: "F1F5F9")
    static let textPrimary = Color(hex: "E2E8F0")
    static let textSecondary = Color(hex: "94A3B8")
    static let textMuted = Color(hex: "475569")
    static let textFaint = Color(hex: "334155")

    // Chart
    static let chartDownload = Color(hex: "06B6D4")
    static let chartUpload = Color(hex: "8B5CF6")
    static let chartGrid = Color(hex: "1E293B")

    // Glass
    static let glassWhite = Color.white.opacity(0.05)
    static let glassBorder = Color.white.opacity(0.10)
}

// MARK: - Typography
extension Font {
    static let displayLarge = Font.system(size: 57, weight: .black)
    static let displayMedium = Font.system(size: 45, weight: .bold)
    static let displaySmall = Font.system(size: 36, weight: .bold)
    static let headlineLarge = Font.system(size: 32, weight: .bold)
    static let headlineMedium = Font.system(size: 28, weight: .semibold)
    static let headlineSmall = Font.system(size: 24, weight: .semibold)
    static let titleLarge = Font.system(size: 22, weight: .semibold)
    static let titleMedium = Font.system(size: 16, weight: .medium)
    static let titleSmall = Font.system(size: 14, weight: .medium)
    static let bodyLarge = Font.system(size: 16, weight: .regular)
    static let bodyMedium = Font.system(size: 14, weight: .regular)
    static let bodySmall = Font.system(size: 12, weight: .regular)
    static let labelLarge = Font.system(size: 14, weight: .bold)
    static let labelMedium = Font.system(size: 12, weight: .medium)
    static let labelSmall = Font.system(size: 11, weight: .medium)
    static let monoMedium = Font.system(size: 14, design: .monospaced)
    static let monoLarge = Font.system(size: 22, weight: .bold, design: .monospaced)
    static let monoSmall = Font.system(size: 11, design: .monospaced)
}
