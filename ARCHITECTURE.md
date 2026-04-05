# Architecture

This document captures the planned technical shape of
`peleoton-power-meter` before implementation starts.

The product goal is narrow on purpose: record an indoor ride truthfully,
preserve dual-sided pedal data, and produce a trustworthy summary and FIT
export.

## Principles

- Assioma Duo-first, not generic-sensor-first
- Local-first ride storage
- Recorder reliability before feature breadth
- Truthful degraded-state behavior
- Derived insight after recording, not inside the recording hot path

## Scope Boundary

The first implementation covers:

- companion pairing and device association
- local ride recording
- passive live ride UI
- totals-first post-ride summary
- FIT export/share

The first implementation does not cover:

- automatic Strava sync
- full interrupted-session recovery after process death
- broad hardware support
- ride history
- named presets
- manual ride markers

## System Overview

The system should be shaped around one recorder path and one post-ride
summary path.

```text
Companion Association
        |
        v
 Sensor Identity
        |
        v
 Foreground Recorder Service
        |
        v
 Durable Sample Storage
        |
        +--> Live Ride UI
        |
        +--> Post-Ride Summary Builder
                 |
                 +--> Totals Summary
                 +--> Asymmetry Analysis (PR2)
                 +--> FIT Export
```

## Core Components

### Companion Association

Use Android `CompanionDeviceManager` from the start.

Why:

- better long-term pairing behavior
- explicit device identity
- cleaner trust story for "pair once, trust it every ride"

Responsibilities:

- associate left pedal
- associate right pedal
- associate one BLE heart-rate source
- persist stable device identity for reconnect behavior

### BLE Connection Management

After companion device pairing establishes device identity, the BLE Connection
Manager handles the Bluetooth LE connection lifecycle.

Responsibilities:

- establish GATT connections to paired devices
- track connection state for each device independently
- handle reconnection with exponential backoff on connection loss
- provide StateFlow of connection state for each device
- support multiple simultaneous connections (left pedal, right pedal, HR monitor)

Implementation:

- uses Android BluetoothGatt API
- manages reconnection attempts with 1s, 2s, 4s, 8s, 16s backoff (max 32s)
- exposes per-device connection state (Disconnected, Connecting, Connected, Error)
- cancels reconnection when explicitly disconnected
- cleans up GATT resources on disconnect

Critical assumption:

- BLUETOOTH_CONNECT permission is already granted before BleConnectionManager is created
- permission checking happens during companion device association flow

### Cycling Power Message Parsing

After BLE connection is established, the Cycling Power Parser interprets raw
characteristic data from Assioma Duo pedals following the Bluetooth SIG Cycling
Power Service specification (characteristic UUID 0x2A63).

Responsibilities:

- parse binary Cycling Power Measurement characteristic bytes
- interpret flags byte to determine which optional fields are present
- extract instantaneous power (watts)
- extract pedal power balance (left/right percentage)
- extract crank revolution data for cadence calculation
- handle optional fields (accumulated energy, accumulated torque)
- provide null-safe parsing with validation

Implementation:

- `CyclingPowerData`: immutable data class holding parsed measurements
- `CyclingPowerParser`: stateless parser object with parse() method
- handles little-endian byte order per Bluetooth SIG spec
- validates field constraints (instantaneous power is signed and may be negative; balance 0-100% when present)
- returns null for malformed or truncated messages
- cadence calculation handles 16-bit wraparound of crank revolution counters
- supports helper methods to check for optional field presence

Parsing rules:

- first 2 bytes: flags (uint16, little-endian)
- next 2 bytes: instantaneous power (sint16, little-endian) - always present
- remaining bytes: optional fields based on flag bits
- parser skips unsupported optional fields (wheel data, torque magnitudes, angles)
- Assioma Duo typically sends: power + balance + crank data + accumulated energy

Intent:

- pure parsing layer with no BLE dependencies
- no integration with connection manager or recorder yet
- enables isolated testing with real message fixtures
- prepares for future recorder integration (PR3)

### Heart Rate Message Parsing

The Heart Rate Parser interprets raw characteristic data from BLE heart rate
monitors following the Bluetooth SIG Heart Rate Service specification
(characteristic UUID 0x2A37).

Responsibilities:

- parse binary Heart Rate Measurement characteristic bytes
- interpret flags byte to determine value format (UINT8 vs UINT16) and optional fields
- extract heart rate BPM value
- extract sensor contact status (detected/not detected/not supported)
- handle optional energy expended field
- handle optional RR-interval data for heart rate variability analysis
- provide null-safe parsing with validation

Implementation:

- `HeartRateData`: immutable data class holding parsed measurements
- `HeartRateParser`: stateless parser object with parse() method
- handles little-endian byte order per Bluetooth SIG spec
- validates field constraints (heart rate 1-255 BPM, positive RR-intervals)
- returns null for malformed or truncated messages
- recognizes both UINT8 and UINT16 heart rate encodings per the Bluetooth SIG spec
- accepts only heart rate values in the 1-255 BPM range; UINT16-encoded values above 255 BPM are treated as malformed by validation
- supports helper methods to check for optional field presence

Parsing rules:

- first byte: flags (uint8)
- next 1 or 2 bytes: heart rate BPM (uint8 or uint16, little-endian)
- remaining bytes: optional fields based on flag bits
- energy expended: uint16, kilojoules (if present)
- RR-intervals: one or more uint16 values, 1/1024 second resolution (if present)
- sensor contact status determined from flag bits (supported/detected)

Intent:

- pure parsing layer with no BLE dependencies
- no integration with connection manager or recorder yet
- enables isolated testing with realistic HR monitor message fixtures
- supports common HR monitors (Polar, Garmin, Wahoo, etc.)
- prepares for future recorder integration (PR3)

### BLE Recorder Session Controller

The BLE Recorder Session Controller implements the recorder path for real
BLE-connected power meters and heart rate monitors, bridging the message
parsers and the recorder session interface.

Responsibilities:

- connect to three BLE devices (left pedal, right pedal, heart rate monitor)
- subscribe to characteristic notifications from each device
- parse incoming notifications using CyclingPowerParser and HeartRateParser
- normalize irregular BLE notifications to 1-second samples using BleSampleCollector
- write samples to RideStore at 1 Hz
- emit RecorderSessionState updates (Idle/Active/Completed)
- handle sensor dropouts gracefully (partial data, continued recording)
- manage BLE connection lifecycle (connect, disconnect, reconnect)

Implementation:

- `BleRecorderSessionController`: implements RecorderSessionController interface
- `BleSampleCollector`: collects and normalizes sensor data to 1-second RideSample intervals
- uses BleConnectionManager for device connections
- uses CyclingPowerParser for left/right pedal data
- uses HeartRateParser for heart rate data
- tracks connection state for each sensor independently
- calculates power zone from FTP and current power
- generates truthful samples even with missing sensors

Data flow:

1. Controller connects to three BLE devices via BleConnectionManager
2. Characteristic notifications arrive at irregular intervals
3. Parsers convert raw bytes to CyclingPowerData and HeartRateData
4. BleSampleCollector maintains most recent data from each sensor
5. Every second, BleSampleCollector generates a normalized RideSample
6. Sample includes total power (sum of both pedals or single pedal), cadence, HR, zone
7. Missing data is marked with null values, connection states tracked separately
8. Samples written to RideStore at 1 Hz

BleSampleCollector design:

- maintains most recent parsed data from each of three sensors
- tracks previous power data for cadence calculation
- generates RideSample on demand with current timestamp
- handles partial data: missing HR, missing one pedal, missing both pedals
- calculates power zones from FTP (7 zones: Z1-Z7)
- resets state when session ends

Connection handling:

- tracks left/right/HR connection states independently
- clears sensor data when connection lost
- continues recording with partial data during dropouts
- reflects connection states in RideSample for truthful reporting

Status:

- Basic implementation complete: controller, sample collector, lifecycle management
- BLE connection management integrated via BleConnectionManager
- Characteristic notification handling: pending full integration
- Parser integration: pending characteristic data flow
- NOT wired into RideRecorderService yet (separate PR)

Intent:

- real BLE recording path ready for integration
- truthful degraded-state behavior (missing sensors marked correctly)
- no demo mode (use DemoRecorderSessionController for demos)
- prepares for recorder service integration

### Sensor Identity Layer

The product wedge should stay visible in the domain model.

Use explicit types:

- `PedalPair`
- `HeartRateSource`
- `RideSession`

Do not begin with a generic "sensor bus" abstraction that treats the two
pedals like interchangeable metrics.

### Foreground Recorder Service

The recorder runs as a foreground service during rides.

Responsibilities:

- own the active session lifecycle
- read pedal and heart-rate samples
- timestamp samples
- publish current values to the live UI
- write samples durably during the ride
- track degraded connection state truthfully

Rules:

- backgrounding the UI must not stop recording
- one pedal disconnect must not end the ride
- missing pedal data must not become invented balance data

Current implementation note:

- the foreground service now owns the demo recorder session lifecycle
- the app observes recorder state through a shared state bridge rather than
  owning the active ride directly
- debug emulator builds expose a setup shortcut that starts the demo recorder
  without real paired sensors so the full flow can be exercised in the emulator

### Durable Local Storage

Samples must be written durably during the ride, not held only in memory.

Use Room as the default local persistence layer for v1.

Why:

- explicit schema
- straightforward Android integration
- reliable transaction boundaries
- easier recorder and summary testing than ad hoc file storage

Stored session data should include:

- ride metadata
- associated device identities
- FTP snapshot used for zones
- per-sample metrics
- connection state flags
- derived summary output

Expected storage layers:

- session metadata table
- sample table
- derived summary table or summary blob
- pending export or sync status

### Sample Cadence

Use a 1 Hz logical sample frame for persisted ride samples in v1.

Why:

- it is explicit and easy to reason about
- it matches the passive, glance-driven ride UX
- it keeps persistence volume and summary math simple for the first wedge

Implementation shape:

- sensor events may arrive faster than 1 Hz
- the recorder reduces incoming sensor state into one persisted sample frame per
  second
- higher-frequency transient live state may exist in memory only and is not part
  of the durable ride contract for v1
- connection-state changes should still be captured truthfully as part of those
  frames
- the live UI should refresh from the current recorder state at roughly the same
  calm cadence, not at raw BLE event frequency

### Live Ride UI

The live ride UI is not the source of truth. It is a view over the active
recorder state.

It should refresh from current recorder state at a calmer cadence than the raw
sample ingest path.

Responsibilities:

- show current total power
- show cadence and heart rate
- show zone
- show elapsed ride time and recording state
- surface degraded-state truth strip

### Post-Ride Summary Builder

Summary generation happens after ride completion.

PR1 responsibilities:

- compute totals
- compute average balance
- flag partial-data conditions
- generate the summary model for the totals-first screen
- generate FIT export input

PR2 responsibilities:

- run deterministic asymmetry analysis over stored samples
- build compact timeline data
- build top-3 notable moments
- suppress unsupported claims for degraded intervals

### FIT Export

Use the Garmin FIT Java SDK.

Why:

- avoids custom FIT format risk
- lowers protocol ambiguity
- fits the narrow wedge better than inventing an exporter

Export order:

1. ride completes
1. summary is finalized
1. FIT file is generated
1. user can export or share the file

## Domain Model

### RideSession

Represents one indoor ride from start to finish.

Expected fields:

- ride ID
- start time
- end time
- elapsed time
- associated devices
- FTP snapshot
- session status
- interruption markers
- export status

### Sample

Represents one timestamped measurement frame.

Expected fields:

- timestamp
- left power
- right power
- total power
- cadence
- heart rate
- balance
- zone index
- left pedal connected flag
- right pedal connected flag
- heart-rate connected flag

### DerivedSummary

Represents the post-ride view model built from stored samples.

Expected fields:

- average power
- max power
- average cadence
- average heart rate
- time in zone
- average balance
- partial-data flags
- asymmetry timeline data
- notable moments

## Data Flow

### Setup To Ride Start

1. User opens the readiness screen.
1. App loads known companion associations.
1. App reconnects to left pedal, right pedal, and heart-rate source.
1. UI shows readiness state.
1. User starts ride.

### During Ride

1. Foreground service owns recording.
1. Samples arrive from pedals and heart-rate source.
1. Sample frame is timestamped.
1. Sample frame is written durably.
1. Current recorder state is exposed to UI.
1. Connection-state changes are recorded as truth flags.

### Ride Completion

1. User ends ride.
1. Recorder finalizes the session.
1. Totals summary is generated.
1. PR2 only: asymmetry analysis runs over stored samples.
1. FIT export model is built.
1. Summary screen renders from stored derived output.

## Failure Handling

### One Pedal Disconnects

- continue recording
- show degraded-state truth strip
- preserve total session continuity
- mark balance as partial for the affected interval
- do not generate asymmetry claims from unsupported data

### Both Pedals Disconnect

- keep session alive
- continue reconnect attempts
- record affected interval as degraded
- do not hide the recording state

### Heart Rate Disconnects

- continue ride recording
- mark HR as partial in the summary

### App Backgrounded

- recording continues in foreground service
- returning to the UI should show the current ride state without rebuilding the
  session from scratch

### Export Failure

- ride remains saved locally
- export failure is surfaced clearly
- failure does not block summary viewing

## Performance Shape

- sample ingest path should stay simple
- UI refresh cadence should be slower than ingest cadence
- asymmetry analysis runs after ride completion, not live
- derived summary should be persisted so summary rendering is cheap

## Delivery Plan

### PR1

- companion association
- recorder spine
- durable sample storage
- passive live ride UI
- totals-first summary
- FIT export/share
- initial test harness
- CI and Codecov setup

### PR2

- pure post-ride asymmetry analysis
- deterministic flagged intervals
- top-3 notable moments
- partial-data suppression behavior

## Deferred Architecture Work

Deferred items live in
[TODOS.md](TODOS.md).

The most important deferred architectural items are:

- automatic Strava sync flow
- trusted token exchange story for Strava auth
- full interrupted-session recovery
- history-oriented storage and query surfaces
