# Alpha Testing Guide

## Overview

The Peleoton Power Meter app has reached **Basic Alpha** readiness. This document
defines what needs to be tested, how to test it, and what gates must pass for
the alpha to be considered successful.

## Alpha Definition

The Basic Alpha is the first build that a rider can test on a real bike with
real hardware. It demonstrates end-to-end functionality from device pairing
through ride recording to summary generation and FIT export.

**Alpha is NOT:**
- A feature-complete MVP
- Ready for public distribution
- Expected to have perfect reliability
- Including automatic sync, ride history, or every edge case

**Alpha IS:**
- Proof that the core measurement loop works with real hardware
- Verification that the technical architecture is sound
- A foundation for iterative hardening toward MVP
- Ready for controlled dogfooding on a real indoor bike

## Exit Criteria Summary

The alpha is ready when:

1. ✅ All P0 issues are closed (#26-#30)
2. ✅ All BLE implementation PRs are merged
3. ✅ CI passes with 80%+ test coverage
4. ✅ Both demo and real BLE paths work
5. ⏳ Manual testing confirms core flows work
6. ⏳ At least one full 30-minute dogfooding session completes
7. ⏳ FIT export validates successfully

## Pre-Testing Setup

### Hardware Requirements

For **real BLE testing**, you need:
- Android phone running Android 8.0 (API 26) or higher
- Favero Assioma Duo pedals (left + right)
- BLE heart rate monitor (any compatible HR strap/chest strap)
- Indoor bike trainer or stationary bike

For **emulator testing**, you need:
- Android emulator (no hardware required)
- Debug build with demo sensors enabled

### Software Requirements

- Latest build from `main` branch
- Debug build configuration (for demo sensors access)
- ADB access for log monitoring (optional but recommended)

### Android Permissions Required

The app requires these permissions (granted at runtime):
- Bluetooth (BLUETOOTH, BLUETOOTH_ADMIN)
- Bluetooth Scan (BLUETOOTH_SCAN) [Android 12+]
- Bluetooth Connect (BLUETOOTH_CONNECT) [Android 12+]
- Bluetooth Advertise (BLUETOOTH_ADVERTISE) [Android 12+]
- Location (ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)

**Note:** Location permission is required for Bluetooth scanning on Android 8-11.

---

## Installing on Real Device

### Step 1: Build the APK

From the project root directory:

```bash
# Build debug APK with demo sensors enabled
./gradlew assembleDebug

# APK will be generated at:
# app/build/outputs/apk/debug/app-debug.apk
```

**Verification:**
- Build should complete with `BUILD SUCCESSFUL`
- APK file should exist and be ~10-15 MB

### Step 2: Prepare Android Device

**Enable Developer Options:**
1. Open **Settings** → **About phone**
2. Tap **Build number** 7 times (you'll see "You are now a developer!")
3. Go back to **Settings** → **System** → **Developer options**
4. Enable **USB debugging**
5. (Optional) Enable **Stay awake** to prevent screen timeout during testing

**Connect Device:**
1. Connect phone to computer via USB cable
2. When prompted "Allow USB debugging?", tap **Allow**
3. Check "Always allow from this computer" (optional)

**Verify Connection:**
```bash
# List connected devices
adb devices

# Should show something like:
# List of devices attached
# ABC123XYZ    device
```

### Step 3: Install the App

**Option A: Using ADB (Command Line)**

```bash
# Install APK (use -r flag to replace existing installation)
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Should see:
# Performing Streamed Install
# Success
```

**Option B: Using Android Studio**

1. Open project in Android Studio
2. Select your physical device from the device dropdown
3. Click **Run** (green play button) or press **Shift+F10**
4. App will build and install automatically

**Verification:**
- App icon appears in device's app drawer
- App name is "Peleoton Power Meter"
- Version shows debug variant

### Step 4: Grant Runtime Permissions

**First Launch - Permission Flow:**

When you first launch the app, it will request permissions:

1. **Bluetooth permissions** (Android 12+)
   - Tap **Allow** when prompted for Nearby devices/Bluetooth
   
2. **Location permissions** (Android 8-11)
   - Tap **While using the app** or **Allow**
   - Note: Required for Bluetooth scanning on older Android versions

**Manual Permission Granting (if needed):**

If permissions are denied or you need to re-grant:

1. Long-press the Peleoton Power Meter app icon
2. Tap **App info**
3. Tap **Permissions**
4. Enable:
   - **Nearby devices** (Android 12+) → Allow
   - **Location** (Android 8-11) → Allow all the time or While using the app

**Verify Permissions:**
```bash
# Check granted permissions
adb shell dumpsys package com.sjlangley.peleotonpowermeter | grep permission

# Should see BLUETOOTH, BLUETOOTH_SCAN, BLUETOOTH_CONNECT granted
```

### Step 5: Prepare Hardware

**Assioma Duo Pedals:**
1. Ensure pedals are charged (green LED when awake)
2. Wake up pedals by rotating cranks 2-3 times
3. Pedals should start broadcasting BLE immediately
4. LED will blink briefly to indicate active BLE advertising

**Heart Rate Monitor:**
1. Wet electrodes (for chest straps) or ensure proper skin contact (wrist/arm bands)
2. Put on HR monitor and wait 10-15 seconds for heart rate lock
3. Some monitors have indicator lights showing transmission status
4. Verify HR monitor is transmitting (many fitness apps can scan for it)

**Phone Setup:**
1. Ensure Bluetooth is **ON** (Settings → Bluetooth)
2. Ensure Location is **ON** (if required for your Android version)
3. Place phone within 3 meters of bike/sensors
4. Disable battery saver mode (can interfere with Bluetooth)
5. Set screen timeout to 10+ minutes or enable "Stay awake while charging"

**Pre-Flight Check:**
```bash
# Optional: Monitor logs during testing
adb logcat -s PeletonPowerMeter:* AndroidRuntime:E

# Or filter for BLE-specific logs
adb logcat | grep -i "ble\|bluetooth\|connection"
```

### Troubleshooting Install Issues

**"adb: device unauthorized"**
- Revoke USB debugging authorizations on phone
- Disconnect and reconnect USB
- Accept the new authorization prompt

**"INSTALL_FAILED_UPDATE_INCOMPATIBLE"**
- Uninstall existing version: `adb uninstall com.sjlangley.peleotonpowermeter`
- Re-run install command

**App won't launch after install**
- Check logcat for crash logs: `adb logcat -d | grep AndroidRuntime`
- Verify Android version is 8.0+ (API 26+)
- Try clean build: `./gradlew clean assembleDebug`

**Bluetooth permissions denied**
- Manually grant in Settings → Apps → Peleoton Power Meter → Permissions
- On Android 12+, ensure "Nearby devices" is allowed
- On Android 8-11, ensure "Location" is allowed

---

## Test Flows

### Flow 1: Device Pairing (Setup Path)

**Purpose:** Verify device association and remembered setup state.

**Test Steps:**

1. Launch app from clean install
2. Verify Setup screen appears with "Ready to pair" state
3. Tap "Pair Left Pedal"
   - Verify CompanionDeviceManager picker appears
   - Select left Assioma pedal
   - Verify association completes (no crash, no timeout)
   - Verify left pedal state changes to "Paired ✓"
4. Tap "Pair Right Pedal"
   - Repeat verification steps
   - Verify right pedal state changes to "Paired ✓"
5. Tap "Pair Heart Rate"
   - Repeat verification steps
   - Verify HR monitor state changes to "Paired ✓"
6. Verify primary action button changes to "Start Ride"

**Expected Results:**
- All three devices pair successfully
- Device names/IDs are displayed
- Setup state persists (verify by force-closing and reopening app)
- No Bluetooth crashes or ANRs

**Pass Criteria:**
- ✅ All three devices can be paired
- ✅ Paired state persists across app restarts
- ✅ Primary button becomes "Start Ride"

---

### Flow 2: Demo Sensors (Emulator Path)

**Purpose:** Verify demo path for emulator testing and development.

**Prerequisites:** Debug build only (BuildConfig.DEBUG == true)

**Test Steps:**

1. Launch app in emulator (no real Bluetooth needed)
2. Verify "Use Demo Sensors" debug button appears
3. Tap "Use Demo Sensors"
   - Verify ride starts immediately (no pairing needed)
   - Verify live ride screen appears
4. Monitor live ride screen for 60+ seconds
   - Verify power values update (150-300W typical range)
   - Verify cadence values update (80-95 RPM typical)
   - Verify heart rate updates (140-160 BPM typical)
   - Verify elapsed time increments every second
5. Tap "Finish Ride"
   - Verify summary screen appears
   - Verify metrics match expected demo pattern

**Expected Results:**
- Demo sensors generate deterministic sample data
- Live ride screen updates every ~1 second
- Summary calculation works from demo samples
- No crashes or freezes

**Pass Criteria:**
- ✅ Demo path works without real hardware
- ✅ Sample generation is smooth and consistent
- ✅ Summary correctly aggregates demo data

---

### Flow 3: Real BLE Ride Recording

**Purpose:** Core alpha validation - record a real ride with real hardware.

**Prerequisites:**
- All three devices paired (Flow 1 complete)
- On a real bike/trainer
- Phone charged and screen timeout disabled

**Test Steps:**

1. Launch app
2. Verify Setup screen shows "Paired ✓" for all devices
3. Tap "Start Ride"
   - Verify service notification appears
   - Verify live ride screen transitions in
4. Begin pedaling at moderate effort (150-200W target)
5. Monitor live ride screen during ride:
   - **Power:** Should reflect pedaling effort (0W when stopped, 100-300W when pedaling)
   - **Cadence:** Should match actual RPM (typically 80-100 RPM)
   - **Heart Rate:** Should track your HR (verify against watch/other source)
   - **Elapsed Time:** Should increment every second
   - **Connection Status:** Watch for Left/Right/HR badges (should stay green)
6. Test backgrounding:
   - Press home button
   - Verify notification stays active
   - Wait 30 seconds
   - Return to app
   - Verify ride continues, time didn't pause
7. Test dropout scenarios:
   - Stop pedaling completely (power should drop to 0W)
   - Start pedaling again (power should recover)
   - Verify data remains truthful (no invented values)
8. After 5+ minutes of riding, tap "Finish Ride"
   - Verify summary screen appears
   - Verify metrics are reasonable:
     - Average power: ~your effort level
     - Total time: matches elapsed time from ride
     - Average cadence: ~your typical RPM
     - Average HR: ~your effort level

**Expected Results:**
- BLE connections establish successfully
- Live data reflects actual sensor readings
- Recording continues when backgrounded
- No data is invented during dropouts
- Summary correctly aggregates real ride data

**Pass Criteria:**
- ✅ All three sensors connect and stream data
- ✅ Live ride screen updates reflect real sensor values
- ✅ Backgrounding doesn't stop recording
- ✅ Summary metrics are truthful
- ✅ No crashes during 5+ minute ride

---

### Flow 4: Post-Ride Summary

**Purpose:** Verify summary calculation and presentation.

**Test Steps:**

1. Complete a ride (demo or real BLE)
2. On summary screen, verify all metrics are present:
   - Total time
   - Average power (W)
   - Average cadence (RPM)
   - Average heart rate (BPM)
   - Left/Right balance (%)
   - Asymmetry indicators (if applicable)
3. Verify metrics are reasonable:
   - No impossible values (e.g., 9999W, -1 RPM)
   - Balance should be ~50/50 for typical riders (45-55% range)
   - Average power should match perceived effort
4. Scroll through entire summary
5. Tap "Export FIT" (Flow 5)
6. Tap "Done" to return to Setup screen

**Expected Results:**
- All metrics render without crashes
- Values are within reasonable ranges
- Summary derives from actual stored samples (not hardcoded)
- No missing or null values

**Pass Criteria:**
- ✅ Summary renders all expected metrics
- ✅ Values match ride characteristics
- ✅ No crashes or blank screens

---

### Flow 5: FIT Export

**Purpose:** Verify FIT file generation and export flow.

**Test Steps:**

1. From summary screen, tap "Export FIT"
2. Verify Android share sheet appears
3. Select a share target (e.g., email, Google Drive, Strava app)
   - For validation: Select "Save to Files" or similar
4. Verify file is exported successfully
5. **Validation (critical):**
   - Copy FIT file to computer
   - Validate using FitCSVTool: `java -jar FitCSVTool.jar <ride.fit>`
   - Verify FIT file is not corrupt
   - Verify it contains:
     - File header
     - Session message
     - Record messages (one per second of ride)
     - Lap messages
     - Power, cadence, heart rate data in records

**Expected Results:**
- FIT file exports without errors
- File is valid according to FitCSVTool
- Data matches ride (spot-check a few samples)
- File imports successfully into Strava/TrainingPeaks/GoldenCheetah

**Pass Criteria:**
- ✅ FIT file exports successfully
- ✅ FitCSVTool validates file structure
- ✅ File imports into at least one third-party platform

**FitCSVTool Download:**
https://developer.garmin.com/fit/fitcsvtool/

---

### Flow 6: Disassociate and Re-pair

**Purpose:** Verify device management and reset flows.

**Test Steps:**

1. From Setup screen with paired devices, tap "Clear All Devices"
2. Verify confirmation or immediate action
3. Verify all device states reset to "Not paired"
4. Repeat pairing for all three devices (Flow 1)
5. Verify devices pair successfully again

**Expected Results:**
- Device disassociation works cleanly
- No stale state persists
- Re-pairing works without app restart

**Pass Criteria:**
- ✅ Clear devices resets pairing state
- ✅ Re-pairing works immediately

---

## Dogfooding Session

### 30-Minute Real Ride

**Purpose:** Validate alpha readiness with realistic usage.

**Test Plan:**

1. Pair all devices fresh (clear any previous state)
2. Mount phone visibly on bike
3. Start a 30-minute structured indoor ride:
   - Warmup: 5min at 120-150W
   - Main set: 20min at tempo effort (200-250W)
   - Cooldown: 5min at 100-150W
4. Monitor live ride screen continuously:
   - Verify power tracks effort changes
   - Verify HR responds to effort
   - Verify no connection drops
   - Note any UI issues, freezes, or crashes
5. Background the app twice:
   - At minute 10, press home for 2 minutes
   - At minute 20, open another app briefly
   - Verify recording continues uninterrupted
6. Finish ride and review summary
7. Export FIT and validate with external tool
8. Import FIT into Strava/TrainingPeaks
9. Compare metrics to expected values

**Success Criteria:**
- ✅ Complete 30 minutes without crashes
- ✅ Live data stays accurate throughout
- ✅ Backgrounding doesn't corrupt recording
- ✅ Summary metrics match expected effort
- ✅ FIT export works and imports successfully
- ✅ No silent data inventions during dropouts

**Documentation:**
- Record screen during ride (optional)
- Note any issues or unexpected behavior
- Save FIT file for analysis
- Save Logcat logs if any issues occur

---

## Quality Gates

### Automated Gates (CI)

These must pass before alpha:

1. ✅ **All Unit Tests Pass**
   - Current: 190+ tests
   - Target: 100% pass rate
   - Command: `./gradlew test`

2. ✅ **Code Coverage ≥ 80%**
   - Current: 80%+ line coverage
   - Command: `./gradlew jacocoDebugCoverageVerification`

3. ✅ **ktlint Clean**
   - No Kotlin style violations
   - Command: `./gradlew ktlintCheck`

4. ✅ **Detekt Clean**
   - No static analysis issues
   - Command: `./gradlew detekt`

5. ✅ **Build Success**
   - Debug and release builds complete
   - Command: `./gradlew assembleDebug assembleRelease`

### Manual Gates

These must pass through manual testing:

1. ⏳ **Device Pairing Works**
   - All three devices pair successfully
   - State persists across app restarts

2. ⏳ **Demo Path Works**
   - Emulator demo sensors function correctly
   - No hardware needed for basic validation

3. ⏳ **Real BLE Recording Works**
   - Live data reflects actual sensor readings
   - No crashes during 5+ minute rides
   - Backgrounding doesn't stop recording

4. ⏳ **Summary Generation Works**
   - Metrics are accurate and reasonable
   - No missing or impossible values

5. ⏳ **FIT Export Works**
   - File exports successfully
   - FitCSVTool validates structure
   - Imports into third-party platforms

6. ⏳ **30-Minute Dogfood Complete**
   - No crashes or freezes
   - Data remains truthful throughout
   - FIT export validates and imports

### Documentation Gates

Before alpha release:

1. ⏳ **Update README.md**
   - Mark "Recorder service integration" as complete ✅
   - Mark "Real BLE pedal and heart-rate ingestion" as complete ✅
   - Update status to "Basic Alpha Ready"

2. ⏳ **Update ROADMAP.md**
   - Mark "Milestone 1: Basic Alpha" as complete
   - Document what was delivered

3. ⏳ **Update ARCHITECTURE.md**
   - Document RecorderSessionControllerFactory
   - Document BLE vs Demo controller selection
   - Document service lifecycle with controller integration

4. ⏳ **Create TESTING.md** (this document)
   - Moved to repository
   - Versioned for future reference

---

## Known Limitations (Not Release Blockers)

These are expected gaps that don't block alpha but should be documented:

1. **No Ride History**
   - App only shows last completed ride
   - Previous rides are in database but not accessible via UI

2. **No Automatic Sync**
   - Manual FIT export required
   - No direct Strava upload

3. **Limited Error Recovery**
   - BLE connection drops require manual ride restart
   - No automatic reconnection during ride

4. **No Interrupted Session Recovery**
   - If app crashes mid-ride, ride data is lost
   - No session persistence/recovery

5. **Basic UI Polish**
   - Minimal visual design
   - No fancy animations or transitions
   - Functional over beautiful

6. **No Power Zone Training**
   - No FTP-based zone calculations
   - No zone display during ride

These are fine for alpha dogfooding but should be addressed for MVP.

---

## Failure Scenarios

### If Tests Fail

**Automated Test Failures:**
- Review test output for specific failures
- Fix issues and re-run full test suite
- Do not proceed to manual testing if CI fails

**Device Pairing Failures:**
- Verify Bluetooth is enabled
- Verify permissions are granted
- Try unpairing devices and re-pairing
- Check Logcat for BLE errors

**BLE Recording Failures:**
- Verify devices are paired
- Verify devices are in range (~10 feet)
- Verify device batteries are charged
- Try power cycling devices
- Check Logcat for connection errors

**Crash During Ride:**
- Save Logcat immediately
- Note exact actions before crash
- File GitHub issue with full details
- Do not proceed to alpha until fixed

**FIT Export Failures:**
- Verify ride completed (not abandoned)
- Check storage permissions
- Try exporting again
- Verify FIT file structure with FitCSVTool

### If Dogfooding Fails

If the 30-minute dogfooding session doesn't complete successfully:

1. **Document the failure mode** (crash, data loss, freeze, etc.)
2. **Capture logs** (Logcat, crash reports)
3. **File a critical bug** with reproduction steps
4. **Roll back to a stable commit** if needed
5. **Fix the issue** before declaring alpha ready
6. **Re-run the full dogfooding session**

**Alpha is NOT ready until dogfooding succeeds.**

---

## Success Declaration

Alpha is ready when:

- ✅ All automated gates pass (CI green, coverage ≥80%)
- ✅ All manual test flows pass
- ✅ 30-minute dogfooding session completes successfully
- ✅ FIT export validates and imports
- ✅ Documentation is updated
- ✅ No critical bugs remain open

Once all gates pass, the alpha build can be:
- Tagged as `v0.1.0-alpha`
- Distributed to select testers
- Used for structured dogfooding sessions
- Iterated upon for MVP hardening

---

## Next Steps After Alpha

Once alpha is validated:

1. **Collect Feedback** from dogfooding sessions
2. **File Issues** for bugs and UX improvements
3. **Prioritize MVP Work**:
   - Ride history UI
   - Interrupted session recovery
   - BLE reconnection during ride
   - Error handling improvements
4. **Continue Dogfooding** throughout MVP development
5. **Harden Reliability** based on real-world usage

---

## Contact

For questions about testing or alpha gates:
- Review CONTRIBUTING.md
- Check ARCHITECTURE.md for technical details
- File GitHub issues for bugs
- See DECISIONS.md for design rationale

---

**Document Version:** 1.0  
**Last Updated:** April 6, 2026  
**Alpha Target:** Milestone 1 - Basic Alpha
