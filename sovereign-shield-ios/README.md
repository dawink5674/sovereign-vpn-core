# Sovereign Shield VPN - iOS

A native iOS VPN client powered by real WireGuard encryption via Apple's NetworkExtension framework. This is a direct port of the Android Sovereign Shield VPN app, maintaining feature parity with the same dark, military-grade UI aesthetic.

## Prerequisites

- **macOS** Ventura 13.0 or later
- **Xcode** 15.0 or later
- **Apple Developer Account** (required for Network Extension entitlement)
- **iOS 16.0+** device or simulator (VPN functionality requires a physical device)
- **Swift 5.9+**

## Quick Start

### 1. Open the Project

```bash
open SovereignShield.xcodeproj
```

### 2. Resolve Swift Package Dependencies

Xcode should automatically resolve the WireGuard dependency. If not:

1. Go to **File > Packages > Resolve Package Versions**
2. Or manually add the package:
   - Go to **File > Add Package Dependencies...**
   - Enter: `https://github.com/WireGuard/wireguard-apple`
   - Select **WireGuardKit** product
   - Add to **both** targets: `SovereignShield` and `WireGuardExtension`

### 3. Configure Signing

1. Select the **SovereignShield** project in the navigator
2. For **SovereignShield** target:
   - Go to **Signing & Capabilities**
   - Select your **Team**
   - Change the **Bundle Identifier** if needed (e.g., `com.yourname.sovereign.shield`)
3. For **WireGuardExtension** target:
   - Select your **Team**
   - Ensure bundle ID matches: `com.yourname.sovereign.shield.wireguard-extension`
   - The extension bundle ID must be a child of the main app's bundle ID

### 4. Enable Network Extension Capability

If the entitlement isn't already configured:

1. Select the **SovereignShield** target
2. Go to **Signing & Capabilities**
3. Click **+ Capability**
4. Search for **Network Extensions**
5. Enable **Packet Tunnel** checkbox
6. Repeat for the **WireGuardExtension** target

> **Note:** The Network Extension capability requires an Apple Developer Program membership. It will not work with a free developer account.

### 5. App Groups

Both targets need the same App Group for shared data:

1. Under **Signing & Capabilities**, ensure both targets have:
   - App Group: `group.com.sovereign.shield`
   - (Update if you changed the bundle ID)

### 6. Build and Run

1. Select an **iOS device** (VPN won't work on Simulator)
2. Press **Cmd+R** or click the Run button
3. On first launch, grant VPN permission when prompted

## Architecture

```
sovereign-shield-ios/
├── SovereignShield.xcodeproj/
│   └── project.pbxproj          # Xcode project with 2 targets + WireGuardKit SPM
├── SovereignShield/
│   ├── App/
│   │   └── SovereignShieldApp.swift    # @main entry, TabView navigation
│   ├── Models/
│   │   └── ApiModels.swift             # Codable DTOs, WireGuard config builder
│   ├── Network/
│   │   ├── ApiClient.swift             # URLSession async/await API client
│   │   └── GeoIpClient.swift           # ipapi.co + ip-api.com fallback
│   ├── Crypto/
│   │   └── CryptoManager.swift         # CryptoKit Curve25519 + Keychain storage
│   ├── VPN/
│   │   ├── VPNManager.swift            # NETunnelProviderManager wrapper
│   │   └── VPNSettings.swift           # @AppStorage user preferences
│   ├── Views/
│   │   ├── DashboardView.swift         # Main shield + connect button
│   │   ├── GlobeView.swift             # 3D SceneKit globe with continent data
│   │   ├── StatsView.swift             # Download/upload stats + chart
│   │   ├── LogView.swift               # Connection event log
│   │   ├── SettingsView.swift          # Kill switch, DNS, auto-connect
│   │   └── Components/
│   │       ├── GlassCard.swift         # Glass morphism card component
│   │       ├── ShieldButton.swift      # Animated circular connect button
│   │       └── StatusBadge.swift       # Status pill badges
│   ├── Theme/
│   │   └── Theme.swift                 # Color palette matching Android
│   ├── Info.plist
│   └── SovereignShield.entitlements
├── WireGuardExtension/
│   ├── PacketTunnelProvider.swift       # NEPacketTunnelProvider + WireGuardAdapter
│   ├── Info.plist
│   └── WireGuardExtension.entitlements
└── README.md
```

## Connection Flow

The single-tap connect flow:

1. **Generate Key Pair** — CryptoKit Curve25519 ECDH key generation
2. **Register with API** — POST to control plane with device name + public key
3. **Handle 409 Conflict** — Automatic key rotation and retry on conflict
4. **Parse Server Config** — Supports both `serverConfig` object and `clientConfig` string formats
5. **Build WireGuard Config** — Constructs standard wg-quick format configuration
6. **Store in Keychain** — Private key, preshared key, assigned IP stored securely
7. **Start VPN Tunnel** — NETunnelProviderManager saves and starts the tunnel
8. **WireGuardKit** — PacketTunnelProvider uses WireGuardAdapter for real encryption

## Server Details

| Parameter | Value |
|-----------|-------|
| API Base URL | `https://vpn-control-plane-vqkyeuhxnq-uc.a.run.app/` |
| WireGuard Server | `35.206.67.49:51820` |
| Server Public Key | `G1ReQCSgRG/MdfF5/SMrcnU+lKQMlwkr9aIA7/ZK5WI=` |
| Protocol | WireGuard |
| Encryption | ChaCha20-Poly1305 |
| Key Exchange | Curve25519 ECDH |
| Keepalive | 25 seconds |

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/health` | Server health check |
| POST | `/api/peers` | Register new peer (returns server config) |
| GET | `/api/peers` | List all registered peers |
| DELETE | `/api/peers/{publicKey}` | Remove a peer |

## Features

### Security
- **Real WireGuard encryption** via Apple's NetworkExtension framework
- **Curve25519 key generation** using Apple CryptoKit
- **Keychain storage** for private keys and credentials (never UserDefaults)
- **Kill Switch** — blocks all traffic when VPN disconnects
- **Biometric Lock** — Face ID / Touch ID app protection
- **Automatic key rotation** on registration conflicts

### VPN
- **Single-tap connect** — full registration and tunnel setup
- **Auto-Connect** on app launch
- **Auto-Reconnect** on unexpected disconnections
- **Multiple DNS providers** — Cloudflare, Google, Quad9
- **On-demand rules** for kill switch functionality

### UI
- **5-tab navigation** — Shield, Stats, Map, Log, Config
- **Dark space theme** matching Android app
- **Glass morphism components** with transparency and glow effects
- **Animated shield button** with state-dependent glow rings
- **3D SceneKit globe** with real continent outlines from Natural Earth data
- **Live speed charts** for download/upload throughput
- **Connection log** with colored, categorized entries

### Globe (3D Threat Map)
- **SceneKit rendering** with real 3D sphere
- **Continent outlines** — 176 polygons, 2143+ coordinate points from Natural Earth
- **Latitude/longitude grid** every 30 degrees
- **User location pin** (cyan) via GeoIP lookup
- **Server location pin** (green) when connected
- **Great circle arc** between user and server
- **Atmosphere glow** effect
- **Slow rotation** (120 seconds per revolution)
- **Interactive** — supports camera rotation via touch

## Troubleshooting

### "Network Extension" capability not available
You need an Apple Developer Program membership ($99/year). Free accounts cannot use Network Extensions.

### WireGuardKit package resolution fails
1. Ensure you have internet connectivity
2. Try **File > Packages > Reset Package Caches**
3. If that fails, manually clone and reference the package

### VPN doesn't connect on Simulator
VPN functionality requires a **physical iOS device**. The NetworkExtension framework doesn't work in the Simulator.

### Signing errors
1. Ensure both targets use the **same development team**
2. The extension bundle ID must be a **child** of the main app's bundle ID
3. Both targets need the **Network Extension** entitlement
4. Both targets need the **same App Group**

### Build error about WireGuardKit
The WireGuardKit library requires building the wireguard-go backend. Ensure:
1. You have Go installed (`brew install go`) — only needed if building from source
2. Xcode command line tools are installed: `xcode-select --install`

## License

This project uses WireGuard, which is licensed under the MIT license.
WireGuard is a registered trademark of Jason A. Donenfeld.
