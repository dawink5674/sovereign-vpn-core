# sovereign-vpn-core

Personal VPN Project Using GCP

## Projects

### Dragon Scale VPN (main branch)
Original WireGuard VPN Android app with zero-trust architecture, SOC-style US threat map, and Pixel 10 Pro Fold adaptive layout.

### Sovereign Shield VPN (sovereign-shield branch)
Next-generation VPN app — a complete redesign with premium glassmorphic UI, enhanced security features, and advanced monitoring capabilities.

**Key Improvements over Dragon Scale:**
- Premium glassmorphic dark theme with animated glow effects
- Bottom navigation with 5 dedicated screens (Dashboard, Stats, Map, Log, Settings)
- Real-time speed charts with download/upload history visualization
- Global threat map (world projection vs. US-only)
- Kill Switch, Auto-reconnect, Biometric Lock
- Quick Settings tile for one-tap VPN toggle
- DataStore-backed settings with DNS provider selection
- Hilt dependency injection throughout
- Connection timing, session counting, lifetime statistics
- Certificate pinning scaffold for production deployment
- Key rotation tracking and audit trail

## Architecture
- **Android App**: Jetpack Compose + Material3 + WireGuard GoBackend
- **Control Plane**: Node.js Express API on Cloud Run
- **VPN Server**: WireGuard on GCE (e2-micro, us-central1)
- **Infrastructure**: Terraform-managed GCP resources
