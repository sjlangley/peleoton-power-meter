# peleoton-power-meter

`peleoton-power-meter` is an Android app for DIY indoor riders who use Peloton-style
class content with their own bike, Favero Assioma Duo pedals, and a BLE heart-rate
source.

The job is simple: capture the truth.

This app is not a coach, not a social network, and not a generic cycling platform.
It is the measurement layer for indoor rides. It pairs to both pedals plus heart
rate, records locally, shows a passive live ride screen, finishes with a totals-first
summary, and exports a valid FIT file. Post-ride asymmetry insight is part of the
product, but only when the data is trustworthy.

## Status

The project is no longer planning-only.

A working Android scaffold exists today for:

- setup screen
- real companion-device pairing and remembered setup state
- debug-only emulator shortcut for demo sensors
- demo ride start
- foreground-service-backed recording loop with service-owned session lifecycle
- persisted ride samples and derived summary storage
- post-ride summary rendering
- FIT export and manual share flow from stored ride data
- CI, lint, coverage, and unit tests

What does not exist yet is the real Assioma Duo plus heart-rate recorder path
that turns the scaffold into an MVP.

Current review state:

- CEO review: complete
- Engineering review: complete
- Design review: complete

Current build status:

- [x] Android project scaffold
- [x] Compose setup, live ride, and summary screens
- [x] Room-backed `RideStore`
- [x] Derived ride summary calculation
- [x] Post-ride asymmetry analysis
- [x] Foreground service-backed recording
- [x] Demo recorder flow with persisted samples
- [x] Real `CompanionDeviceManager` association flow
- [x] Remembered device identity in setup
- [x] Foreground service session ownership for ride start and finish
- [x] Debug-only demo sensors shortcut for emulator walkthroughs
- [x] CI, coverage, lint, ktlint, and detekt
- [ ] Real BLE pedal and heart-rate ingestion
- [x] FIT file generation from stored ride data
- [x] Manual FIT export/share
- [ ] Hardware-backed alpha dogfooding

## MVP Path

The clearest way to think about delivery is in three stages.

### Stage 0: Internal Demo Scaffold

This is the current repository state.

It is already good enough for:

- UI and state-flow testing
- persistence and summary testing
- demo ride walkthroughs in the emulator via debug demo sensors
- validating the foreground-service recording flow

It is not yet good enough for a real indoor ride.

### Stage 1: Basic Alpha

The basic alpha is ready when a rider can use real hardware to:

- pair the left pedal, right pedal, and one heart-rate source
- start a ride on-device
- record locally while backgrounded
- finish the ride and see a truthful summary

For clarity, the alpha does not need automatic sync, ride history, or every
reliability hardening feature. It does need real sensors and a real recorder
loop.

Remaining steps to reach the basic alpha:

1. Replace demo sample generation with real BLE sample ingestion from Assioma
   Duo and one heart-rate monitor.
1. Verify the summary still loads from persisted ride data after a real ride.

### Stage 2: MVP

MVP is the first version that actually replaces the manual truth-capture
workflow for a real class.

That means the basic alpha plus:

- valid FIT generation from stored ride data
- manual FIT export/share
- at least one successful full dogfood session end to end
- truthful degraded-state behavior when sensors partially drop out

Remaining steps from alpha to MVP:

1. Dogfood at least one full 30-minute ride and fix any trust-breaking issues.
1. Tighten the highest-risk gaps found during dogfooding.

The most important date-like answer is this:

- A demoable app exists now.
- Real pairing and remembered setup are implemented now.
- Debug emulator walkthroughs are implemented now.
- A basic alpha is ready after real BLE recording lands on top of that setup flow.
- MVP is ready after the real recorder path works and a full real ride succeeds end to end.

## Product Summary

This app is for:
- indoor sessions only
- Assioma Duo-first hardware support in v1
- one BLE heart-rate source
- riders who want truthful dual-sided power capture during Peloton or similar classes

This app is not for:
- outdoor rides
- coaching or class guidance
- broad hardware support in v1
- cloud-first ride storage

## V1 Scope

The first implementation slice is intentionally narrow:
- pair and remember left pedal, right pedal, and one BLE heart-rate source
- record rides locally with durable on-device samples
- keep recording while the app is backgrounded
- show a passive live ride screen with power, cadence, heart rate, and zone
- finish with a totals-first post-ride summary
- generate a valid FIT file
- support manual FIT export/share

V1 product posture:
- local-first
- calm and trustworthy
- passive during the ride
- explicit about degraded or partial data

## Summary Of Key Decisions

### Product

- Assioma Duo is the launch-critical power source, not just one supported option.
- Indoor rides only in v1.
- Strava is the future sync destination, but automatic sync is deferred until the
  recorder is stable.
- The app must never invent left/right truth when one pedal is missing.
- Totals come first in the ride summary. Asymmetry insight comes second.

### UX

- Setup is a single readiness board, not a multi-step pairing wizard.
- The live ride screen is power-first, not a balanced metric grid.
- Sensor degradation during a ride appears as a persistent truth strip, while
  recording continues.
- The live ride screen supports both portrait and landscape in v1.
- Setup and summary are portrait-first in v1.
- The post-ride asymmetry block combines a compact whole-ride drift timeline with
  up to three notable moments.
- If asymmetry data is partial, the summary keeps the section visible, explains the
  limitation, and suppresses unsupported claims.

### Engineering

- Use Android `CompanionDeviceManager` from the start for pairing/association.
- Model the core domain explicitly around `PedalPair`, `HeartRateSource`, and
  `RideSession`, not a vague generic sensor framework.
- Store ride samples durably during recording, not only in memory.
- Use the Garmin FIT Java SDK for FIT generation.
- Run post-ride asymmetry analysis as a pure derived-summary pass over stored samples,
  not as live recorder logic.

## Delivery Shape

Work now breaks down more clearly like this:

- Current scaffold: implemented
- Pairing slice: implemented
- Foreground-service ownership slice: implemented
- FIT export slice: implemented
- Basic alpha: real recording path on top of the pairing slice
- MVP: alpha plus full dogfood validation

See [ROADMAP.md](ROADMAP.md) for the step-by-step sequence.

## Deferred Work

Deferred items are tracked in [TODOS.md](TODOS.md).

Highlights:
- automatic Strava sync
- full interrupted-session recovery
- named ride presets
- manual ride markers
- ride history, only if it creates value beyond what Strava already provides
- reusable design system and `DESIGN.md`

## Documents

- [DECISIONS.md](DECISIONS.md): product, UX, and architecture decisions captured from planning
- [ARCHITECTURE.md](ARCHITECTURE.md): recorder, storage, summary, and export architecture
- [UX_SPEC.md](UX_SPEC.md): v1 setup, live ride, summary, states, and accessibility behavior
- [TEST_PLAN.md](TEST_PLAN.md): planned critical paths, edge cases, and test strategy
- [ROADMAP.md](ROADMAP.md): PR1, PR2, and deferred work sequencing
- [TODOS.md](TODOS.md): explicitly deferred work
- [CONTRIBUTING.md](CONTRIBUTING.md): local build, test, lint, and code-style expectations

## Development

Primary local checks:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew ktlintCheck
./gradlew :app:detekt
```

GitHub pull requests run assemble, unit test, coverage, Android Lint, ktlint, and detekt checks in CI.

Coverage check:

```bash
./gradlew jacocoDebugReport jacocoDebugCoverageVerification
```

Current style posture:

- Kotlin official style
- Android Lint enforced in CI
- ktlint enforced in CI
- detekt enforced in CI
- explicit domain types over broad abstractions
- boring, readable code over cleverness

## Build Philosophy

This project is taking the boring path on purpose.

Recorder reliability comes before feature count.
Truth comes before polish.
And we only widen the scope once the user would trust this app for a real class.
