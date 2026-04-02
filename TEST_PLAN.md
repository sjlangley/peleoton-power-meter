# Test Plan

This document captures the planned testing shape for the first two delivery
slices.

The core principle is simple: the recorder path must be more tested than the
insight path, and the insight path must be deterministic.

## Test Goals

- prove the recorder is trustworthy
- prove degraded data is surfaced truthfully
- prove FIT export succeeds from real stored ride data
- prove asymmetry analysis is deterministic and never invents claims

## Affected Surfaces

- setup readiness flow
- live ride screen
- post-ride summary
- FIT export and share flow
- post-ride asymmetry analysis

## Critical Paths

### PR1

1. Pair both pedals and one heart-rate source.
1. Start a ride.
1. Record a full indoor session.
1. Finish the ride.
1. Show a truthful totals-first summary.
1. Export a valid FIT file.

### PR1 Backgrounding Path

1. Start a ride.
1. Background the UI.
1. Return to the app.
1. Confirm recording continuity and truthful current state.

### PR1 Degraded Path

1. Start a ride.
1. Simulate one pedal dropout.
1. Continue recording.
1. Finish the ride.
1. Confirm live warning behavior and partial-data summary behavior.

### PR2 Analysis Path

1. Load a completed ride fixture.
1. Run post-ride asymmetry analysis.
1. Confirm deterministic flagged intervals.
1. Confirm top-3 notable moments.
1. Confirm unsupported intervals do not produce claims.

## Test Layers

### Unit Tests

Use unit tests for:

- zone calculation
- sample-to-summary aggregation
- partial-data suppression rules
- flagged interval detection
- notable-moment ranking
- FIT export model preparation

### Integration Tests

Use integration tests for:

- companion association and remembered identity behavior
- recorder service plus persistence flow
- session finalization
- summary generation from stored ride data
- FIT export from completed session data

### UI And End-To-End Tests

Use targeted end-to-end coverage for:

- first successful setup and ride start
- background and return during a live ride
- degraded live-state warning behavior
- totals-first summary rendering
- PR2 asymmetry summary rendering from deterministic fixtures

The UI suite should stay small. Most confidence should come from recorder and
summary logic tests.

## Edge Cases

- only one pedal connects during setup
- heart-rate source never connects
- left pedal disconnects mid-ride
- right pedal disconnects mid-ride
- both pedals disconnect
- heart rate disconnects
- app backgrounds during ride
- ride finishes offline
- FIT export fails
- asymmetry interval is shorter than threshold
- asymmetry interval exists during degraded pedal data

## Fixture Strategy

Use stored ride fixtures to cover:

- clean full-data ride
- one-pedal dropout ride
- no notable asymmetry ride
- multiple qualifying interval ride
- short-wobble ride that should not qualify

These fixtures should drive both summary-generation tests and PR2 asymmetry
tests.

## Truth Rules To Test

The tests should explicitly prove:

- the app never invents left/right balance during missing-pedal intervals
- the summary can still succeed when HR is partial
- FIT export failure does not erase the local ride
- asymmetry notable moments come only from qualifying full-data intervals
- the summary keeps structure stable even when asymmetry insight is limited

## CI Expectations

CI is part of the first implementation shape.

The repo should add:

- automated test runs on push and pull request
- required merge checks
- coverage upload to Codecov

Detailed deferred and infra follow-up items remain in
[TODOS.md](/Users/stuartlangley/src/sjlangley/peleoton-power-meter/TODOS.md).
