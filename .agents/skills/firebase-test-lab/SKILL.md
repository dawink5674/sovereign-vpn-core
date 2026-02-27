---
name: Firebase Test Lab Specialist
description: Automated Android testing via Firebase Test Lab — robo tests, instrumentation tests, device matrix configuration, and test result analysis.
---

# Firebase Test Lab Specialist

## Overview

Firebase Test Lab replaces Jules as the primary testing mechanism for the Dragon Scale VPN project. It runs tests on real and virtual Android devices in Google's cloud infrastructure, providing screenshots, logs, video recordings, and crash reports.

## Core Commands

### 1. Running Robo Tests (No Test Code Required)

Robo tests automatically crawl the app UI and detect crashes, layout issues, and ANRs.

```bash
# Basic robo test on a virtual device
gcloud firebase test android run \
  --type=robo \
  --app=dragon-scale-vpn/app/build/outputs/apk/debug/app-debug.apk \
  --device model=Pixel2,version=30,locale=en,orientation=portrait \
  --timeout=300s

# Multi-device matrix (test on several configs simultaneously)
gcloud firebase test android run \
  --type=robo \
  --app=dragon-scale-vpn/app/build/outputs/apk/debug/app-debug.apk \
  --device model=Pixel2,version=30 \
  --device model=Pixel6,version=33 \
  --device model=MediumPhone.arm,version=34 \
  --timeout=300s
```

### 2. Running Instrumentation Tests

For targeted verification of specific features (GeoIP, NetworkMonitor, etc.).

```bash
# Build test APK first
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew assembleDebug assembleDebugAndroidTest

# Run instrumentation tests
gcloud firebase test android run \
  --type=instrumentation \
  --app=dragon-scale-vpn/app/build/outputs/apk/debug/app-debug.apk \
  --test=dragon-scale-vpn/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  --device model=Pixel2,version=30 \
  --timeout=600s
```

### 3. Viewing Results

```bash
# List recent test runs
gcloud firebase test android list

# Get detailed results (URL provided after test run)
gcloud firebase test android results describe <TEST_MATRIX_ID>
```

### 4. Available Virtual Devices

```bash
# List all available device models
gcloud firebase test android models list

# List available API versions
gcloud firebase test android versions list
```

## Testing Strategy for Dragon Scale VPN

### What Firebase CAN Test
- **UI rendering** — Compose layouts, foldable adaptations, theme colors
- **GeoIP fetch logic** — Network calls to ipapi.co (no VPN bypass needed)
- **App stability** — Crash detection, ANR detection, memory leaks
- **Navigation** — Drawer menu, pager swipes, button states
- **Registration flow** — API calls to control plane (mock or real)

### What Firebase CANNOT Test
- **Active VPN tunnel** — `VpnService.prepare()` shows a system dialog that can't be auto-dismissed
- **VPN bypass networking** — Requires an active WireGuard tunnel
- **Download/upload stats** — Requires live tunnel traffic

### Recommended Test Matrix

For each build, run:
1. **Robo test** on 2-3 device models (catches crashes and UI issues)
2. **Instrumentation tests** for GeoIP, NetworkMonitor, and CryptoManager logic
3. **Manual sideload** on physical device for VPN tunnel verification

## Integration with Dev Workflow

### The Verification Gate

When changes impact UI, networking, or stability:

```bash
# 1. Build both APKs
.\gradlew assembleDebug assembleDebugAndroidTest

# 2. Run robo test
gcloud firebase test android run --type=robo \
  --app=dragon-scale-vpn/app/build/outputs/apk/debug/app-debug.apk \
  --device model=MediumPhone.arm,version=34

# 3. Review results
# Firebase provides a URL in the output — open it to view
# screenshots, video recordings, logs, and crash traces.
```

### Senior Decision Logic

1. **Discovery (Gemini):** Architect determines the minimal change required.
2. **Implementation (Gemini):** Execute code changes locally.
3. **Verification (Firebase):** Run robo/instrumentation tests to validate stability before pushing.
4. **Manual Verification:** Sideload APK on physical device for VPN-specific testing.
5. **Deployment:** Only push to GitHub after both automated and manual verification pass.

> **Conclusion:** Firebase Test Lab handles automated crash detection, UI validation, and unit testing. Manual physical-device testing remains necessary for VPN tunnel-specific behavior. Both gates must pass before a commit is pushed.
