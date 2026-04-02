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

The project is still in planning. The core product, design, and engineering decisions
have been reviewed and written down before implementation starts.

Current review state:
- CEO review: complete
- Engineering review: complete
- Design review: complete

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

## Planned PR Shape

### PR1

- companion association
- recorder spine
- passive live ride UI
- totals-first summary
- FIT export/share
- test harness
- CI and Codecov setup

### PR2

- post-ride asymmetry analysis over stored samples
- deterministic flagged intervals
- top-3 notable moments
- partial-data suppression rules in the summary

## Deferred Work

Deferred items are tracked in [TODOS.md](/Users/stuartlangley/src/sjlangley/peleoton-power-meter/TODOS.md).

Highlights:
- automatic Strava sync
- full interrupted-session recovery
- named ride presets
- manual ride markers
- ride history, only if it creates value beyond what Strava already provides
- reusable design system and `DESIGN.md`

## Documents

- [DECISIONS.md](/Users/stuartlangley/src/sjlangley/peleoton-power-meter/DECISIONS.md): product, UX, and architecture decisions captured from planning
- [ARCHITECTURE.md](/Users/stuartlangley/src/sjlangley/peleoton-power-meter/ARCHITECTURE.md): recorder, storage, summary, and export architecture
- [UX_SPEC.md](/Users/stuartlangley/src/sjlangley/peleoton-power-meter/UX_SPEC.md): v1 setup, live ride, summary, states, and accessibility behavior
- [TEST_PLAN.md](/Users/stuartlangley/src/sjlangley/peleoton-power-meter/TEST_PLAN.md): planned critical paths, edge cases, and test strategy
- [ROADMAP.md](/Users/stuartlangley/src/sjlangley/peleoton-power-meter/ROADMAP.md): PR1, PR2, and deferred work sequencing
- [TODOS.md](/Users/stuartlangley/src/sjlangley/peleoton-power-meter/TODOS.md): explicitly deferred work
- [CONTRIBUTING.md](/Users/stuartlangley/src/sjlangley/peleoton-power-meter/CONTRIBUTING.md): local build, test, lint, and code-style expectations

## Development

Primary local checks:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

GitHub pull requests run the same three checks in CI.

Current style posture:

- Kotlin official style
- Android Lint enforced in CI
- explicit domain types over broad abstractions
- boring, readable code over cleverness

## Build Philosophy

This project is taking the boring path on purpose.

Recorder reliability comes before feature count.
Truth comes before polish.
And we only widen the scope once the user would trust this app for a real class.
