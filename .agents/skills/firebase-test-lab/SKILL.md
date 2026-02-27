---
name: Firebase Test Lab Specialist
description: Automated Android testing via Firebase Test Lab — robo tests, instrumentation tests, device matrix configuration, and test result analysis.
---

# Firebase Test Lab Specialist

## Overview

Firebase Test Lab is the primary testing mechanism for the Dragon Scale VPN project. It runs tests on real and virtual Android devices in Google's cloud infrastructure, providing screenshots, logs, video recordings, and crash reports.

**Primary Test Device:** Pixel 10 Pro Fold (codename `rango`, API 36, physical device)

## Core Commands

### 1. Running Robo Tests (No Test Code Required)

Robo tests automatically crawl the app UI and detect crashes, layout issues, and ANRs.

```bash
# Primary: Pixel 10 Pro Fold (user's actual device)
gcloud firebase test android run --type=robo \
  --app=dragon-scale-vpn/app/build/outputs/apk/debug/app-debug.apk \
  --device "model=rango,version=36,locale=en,orientation=portrait" \
  --timeout=300s --project=cloud-vpn-12110

# Secondary: Medium Phone virtual device (API 34, fast feedback)
gcloud firebase test android run --type=robo \
  --app=dragon-scale-vpn/app/build/outputs/apk/debug/app-debug.apk \
  --device "model=MediumPhone.arm,version=34" \
  --timeout=300s --project=cloud-vpn-12110

# Full matrix: Pixel 10 Pro Fold + Medium Phone + Pixel Fold (legacy foldable)
gcloud firebase test android run --type=robo \
  --app=dragon-scale-vpn/app/build/outputs/apk/debug/app-debug.apk \
  --device "model=rango,version=36" \
  --device "model=MediumPhone.arm,version=34" \
  --device "model=felix,version=34" \
  --timeout=300s --project=cloud-vpn-12110
```

### 2. Running Instrumentation Tests

For targeted verification of specific features (GeoIP, NetworkMonitor, etc.).

```bash
# Build test APK first
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew assembleDebug assembleDebugAndroidTest

# Run on Pixel 10 Pro Fold
gcloud firebase test android run --type=instrumentation \
  --app=dragon-scale-vpn/app/build/outputs/apk/debug/app-debug.apk \
  --test=dragon-scale-vpn/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  --device "model=rango,version=36" \
  --timeout=600s --project=cloud-vpn-12110
```

### 3. Viewing Results

```bash
# List recent test runs
gcloud firebase test android list --project=cloud-vpn-12110

# Get detailed results (URL provided after test run)
gcloud firebase test android results describe <TEST_MATRIX_ID> --project=cloud-vpn-12110
```

## Device Matrix

| Priority | Model ID | Device Name | API | Type |
|----------|----------|-------------|-----|------|
| **Primary** | `rango` | Pixel 10 Pro Fold | 36 | Physical |
| Secondary | `MediumPhone.arm` | Medium Phone 6.4" | 34 | Virtual |
| Foldable Legacy | `felix` | Pixel Fold | 34 | Physical |

## Testing Strategy for Dragon Scale VPN

### What Firebase CAN Test
- **UI rendering** — Compose layouts, foldable adaptations, theme colors
- **GeoIP fetch logic** — Network calls to ipapi.co (no VPN bypass needed)
- **App stability** — Crash detection, ANR detection, memory leaks
- **Navigation** — Drawer menu, pager swipes, button states
- **Registration flow** — API calls to control plane (mock or real)
- **Foldable posture** — Fold/unfold behavior on Pixel 10 Pro Fold

### What Firebase CANNOT Test
- **Active VPN tunnel** — `VpnService.prepare()` shows a system dialog that can't be auto-dismissed
- **VPN bypass networking** — Requires an active WireGuard tunnel
- **Download/upload stats** — Requires live tunnel traffic

## Integration with Dev Workflow

### The Verification Gate

When changes impact UI, networking, or stability:

```bash
# 1. Build APK
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew assembleDebug

# 2. Run robo test on Pixel 10 Pro Fold
gcloud firebase test android run --type=robo \
  --app=dragon-scale-vpn/app/build/outputs/apk/debug/app-debug.apk \
  --device "model=rango,version=36" --project=cloud-vpn-12110

# 3. Review the results URL in the output
```

### Senior Decision Logic

1. **Discovery (Gemini):** Architect determines the minimal change required.
2. **Implementation (Gemini):** Execute code changes locally.
3. **Verification (Firebase):** Run robo/instrumentation tests on Pixel 10 Pro Fold to validate.
4. **Manual Verification:** Sideload APK on physical Pixel 10 Pro Fold for VPN-specific testing.
5. **Deployment:** Only push to GitHub after both automated and manual verification pass.
