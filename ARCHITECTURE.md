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
