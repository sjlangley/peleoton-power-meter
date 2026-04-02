# Decisions

This file captures the major product, UX, and architecture decisions that were made
before implementation.

The goal is to keep the repository's source of truth inside the repository, not only
inside external planning artifacts.

## Product Direction

### Core Wedge

Build an Android indoor-ride recorder for DIY Peloton-style setups that use:
- Favero Assioma Duo pedals
- one BLE heart-rate source
- separate class content on a TV or other screen

The app is the truth layer for the ride.

### What The Product Is

- an indoor-session recorder
- dual-sided power capture with left/right truth preserved
- a passive live ride display
- a post-ride summary with asymmetry insight
- a local-first app that can export a FIT file

### What The Product Is Not

- a coach
- a generic cycling app
- an outdoor ride tracker
- a cloud ride platform
- a broad hardware-integration layer in v1

## V1 Scope Decisions

- Indoor bikes only.
- Android only.
- Assioma Duo-first in product story and implementation.
- Strava is the only planned sync destination in v1 planning, but automatic sync is
  deferred from the first implementation slice.
- FIT is the canonical export format.
- FTP is manual entry only in v1.
- Heart-rate source is any BLE heart-rate peripheral the phone can pair with directly.

## Recording Truth Contract

- Ride samples are stored durably on device during the ride.
- Backgrounding the UI should not stop recording.
- If one pedal disconnects, the ride continues but left/right balance for that window
  is treated as partial.
- If heart rate disappears, the ride continues and the summary marks HR as partial.
- If recording is interrupted, the ride should preserve what was captured and mark
  the affected interval clearly.
- The app must never invent balance or asymmetry claims from incomplete pedal data.

## Post-Ride Asymmetry Decisions

- Post-ride asymmetry analysis is a real feature, not just a chart.
- The summary is totals-first, then asymmetry.
- The asymmetry section lives on the same first summary screen, not in a separate tab.
- The asymmetry section combines:
  - a compact whole-ride drift timeline
  - up to three notable moments
- A flagged interval is based on an explicit v1 heuristic:
  - rolling 30-second average left/right split greater than 3 percentage points
  - both pedals connected
  - intervals shorter than 30 seconds do not qualify
- Top moments are ranked by largest absolute average split, then duration.
- If pedal data is partial, the summary keeps the asymmetry section visible, explains
  the limitation, marks unsupported intervals, and suppresses unsupported callouts.

## UX Decisions

### Setup

- Use one setup readiness board, not a multi-step wizard.
- Show left pedal, right pedal, and heart rate together on the same screen.
- The two pedals should read visually as one logical pair while preserving separate
  status.
- The primary action becomes `Start Ride` only when the required sensors are ready.

### Live Ride Screen

- The screen is passive: start, glance, trust.
- Total power is the dominant metric.
- Cadence and heart rate are secondary but always visible.
- Zone stays visible, but quieter than numeric power.
- A degraded-data truth strip appears above the metrics when needed.
- The live ride screen intentionally supports portrait and landscape in v1.

### Summary

- Totals headline first.
- Asymmetry is the strong second section.
- Export remains explicit and separate from analysis.
- Partial-data behavior must preserve structure and tell the truth.

## Visual Direction

- Calm and trustworthy, not dense cockpit energy.
- Light-theme first in v1.
- Warm neutral surfaces, dark text, one accent color.
- Minimal chrome, minimal decorative borders, no dashboard-card mosaic.
- No purple/indigo gradient startup look.
- No ornamental icons-in-colored-circles.

## Architecture Decisions

- Use Android `CompanionDeviceManager` from day one.
- Keep the domain model explicit:
  - `PedalPair`
  - `HeartRateSource`
  - `RideSession`
- Avoid a generic sensor abstraction that hides the product wedge.
- Use durable local persistence during recording.
- Use Room for local ride persistence in v1.
- Persist one logical sample frame per second in v1, reducing faster BLE events
  into a calm, explicit recorder cadence.
- Allow higher-frequency live recorder state in memory only, while keeping the
  durable persisted ride contract at 1 Hz.
- Use the Garmin FIT Java SDK for FIT generation.
- Keep asymmetry analysis out of the live recording path.
- Run post-ride asymmetry analysis as a pure pass over stored samples.

## Delivery Shape

### PR1

- companion association
- recorder spine
- passive live ride UI
- totals-first summary
- FIT export/share
- tests
- CI and Codecov

### PR2

- post-ride asymmetry analysis
- deterministic flagged intervals
- top-3 notable moments
- partial-data asymmetry summary behavior

## Explicit Non-Goals For The First Slice

- automatic Strava sync
- full process-kill recovery beyond durable recording and visible interruption markers
- broader hardware support
- ride history
- named presets
- manual ride markers
- a reusable global design system

## Deferred Work

Deferred work lives in [TODOS.md](/Users/stuartlangley/src/sjlangley/peleoton-power-meter/TODOS.md).

Key deferred themes:
- sync
- recovery
- product memory
- reusable design system
