import SwiftUI

@main
struct SovereignShieldApp: App {
    @StateObject private var vpnManager = VPNManager.shared
    @StateObject private var settings = VPNSettings.shared

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(vpnManager)
                .environmentObject(settings)
                .preferredColorScheme(.dark)
        }
    }
}

struct ContentView: View {
    @State private var selectedTab = 0

    var body: some View {
        TabView(selection: $selectedTab) {
            DashboardView()
                .tabItem {
                    Image(systemName: "shield.fill")
                    Text("Shield")
                }
                .tag(0)

            StatsView()
                .tabItem {
                    Image(systemName: "chart.bar.fill")
                    Text("Stats")
                }
                .tag(1)

            GlobeView()
                .tabItem {
                    Image(systemName: "globe")
                    Text("Map")
                }
                .tag(2)

            LogView()
                .tabItem {
                    Image(systemName: "doc.text.fill")
                    Text("Log")
                }
                .tag(3)

            SettingsView()
                .tabItem {
                    Image(systemName: "gearshape.fill")
                    Text("Config")
                }
                .tag(4)
        }
        .tint(Theme.shieldBlue)
        .onAppear {
            configureTabBarAppearance()
        }
    }

    private func configureTabBarAppearance() {
        let appearance = UITabBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor(Theme.spaceDark)

        appearance.stackedLayoutAppearance.normal.iconColor = UIColor(Theme.textMuted)
        appearance.stackedLayoutAppearance.normal.titleTextAttributes = [
            .foregroundColor: UIColor(Theme.textMuted)
        ]
        appearance.stackedLayoutAppearance.selected.iconColor = UIColor(Theme.shieldBlue)
        appearance.stackedLayoutAppearance.selected.titleTextAttributes = [
            .foregroundColor: UIColor(Theme.shieldBlue)
        ]

        UITabBar.appearance().standardAppearance = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
    }
}

#Preview {
    ContentView()
}
