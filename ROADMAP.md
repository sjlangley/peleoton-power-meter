<!-- /autoplan restore point: /Users/stuartlangley/.gstack/projects/sjlangley-peleoton-power-meter/main-autoplan-restore-20260402-163645.md -->
# Roadmap

This document captures the intended delivery order for the first versions of
`peleoton-power-meter`.

The roadmap is shaped around one rule: make the recorder trustworthy before
adding breadth.

## Current Phase

Planning is complete enough to begin implementation.

Completed planning work:

- product direction reviewed
- engineering plan reviewed
- design plan reviewed
- core decisions captured in-repo

## PR1

PR1 is the first dogfoodable slice.

Goals:

- pair and remember the pedals and heart-rate source
- record rides locally
- survive app backgrounding
- render a passive live ride screen
- render a totals-first summary
- export a valid FIT file
- establish the initial test and CI baseline

Deliverables:

- companion association
- recorder spine
- durable sample persistence
- passive live ride UI
- totals-first post-ride summary
- FIT export/share
- tests
- CI and Codecov

Success bar:

- complete at least one full 30-minute indoor session end to end
- no silent left/right data invention
- degraded-state behavior remains truthful
- FIT export succeeds from a completed ride

## PR2

PR2 upgrades the post-ride summary from logging to teaching.

Goals:

- analyze stored ride samples after completion
- identify deterministic flagged asymmetry intervals
- surface top-3 notable moments
- handle partial-data intervals truthfully

Deliverables:

- post-ride asymmetry analysis module
- compact timeline output
- top-3 notable moments
- partial-data suppression behavior in the summary
- deterministic test fixtures

Success bar:

- full-data rides produce stable, repeatable analysis output
- short non-qualifying intervals stay suppressed
- degraded intervals never produce unsupported insight

## After PR2

Only widen scope after recorder trust is established through dogfooding.

Next likely items:

- automatic Strava sync
- stronger interrupted-session recovery
- named presets
- manual markers
- ride history, only if it creates value beyond Strava
- reusable design system and `DESIGN.md`

## Non-Goals For The First Wedge

- coaching features
- outdoor support
- broad sensor compatibility
- cloud ride backup
- generic social or community features

## Reference Docs

- [README.md](README.md)
- [DECISIONS.md](DECISIONS.md)
- [ARCHITECTURE.md](ARCHITECTURE.md)
- [UX_SPEC.md](UX_SPEC.md)
- [TEST_PLAN.md](TEST_PLAN.md)
- [TODOS.md](TODOS.md)

## What Already Exists

This repository now has planning coverage for the whole first wedge:

| Sub-Problem | Existing Source Of Truth |
| --- | --- |
| Product scope and non-goals | [README.md](README.md), [DECISIONS.md](DECISIONS.md) |
| Recorder and storage shape | [ARCHITECTURE.md](ARCHITECTURE.md) |
| Screen hierarchy and states | [UX_SPEC.md](UX_SPEC.md) |
| Test strategy and edge cases | [TEST_PLAN.md](TEST_PLAN.md) |
| Deferred work | [TODOS.md](TODOS.md) |

What does not exist yet:

- implementation code
- Android project scaffolding
- tests
- CI
- a reusable design system

## NOT In Scope

These are explicitly not part of the first wedge:

- coaching or class guidance
- outdoor rides
- broad hardware compatibility
- cloud ride storage
- automatic Strava sync
- full process-kill recovery
- ride history
- presets
- markers
- reusable design system work

## CEO Review

### Premise Challenge

The core premises hold.

- An Android indoor recorder is the right first product, because the real user
  job is "capture the truth during a class I am already taking elsewhere."
- Assioma Duo-first is the right wedge, because broad hardware support would make
  the app more generic and less defensible before the recorder is even reliable.
- Passive in-ride UX is correct. If this becomes interactive or coach-like, it
  starts solving a different problem.

The biggest 6-month regret risk would be widening into "generic cycling app"
before the recorder is boringly reliable.

### Dream State Delta

```text
CURRENT
No app. Split stack. Truth is fragile and workflow is clumsy.

THIS PLAN
Reliable indoor recorder for Assioma Duo + HR, local-first, totals-first summary,
FIT export, then post-ride asymmetry insight.

12-MONTH IDEAL
Trusted truth layer for DIY indoor riders, with recorder reliability established,
asymmetry history that actually teaches something, and polished sync/recovery.
```

### Alternatives Considered

| Approach | Why It Looks Attractive | Why It Loses |
| --- | --- | --- |
| Generic cycling recorder from day one | Feels bigger and more reusable | Kills differentiation and slows reliability |
| Third-party app bridge or exporter only | Smaller implementation surface | Leaves the core truth problem unsolved |
| Current recorder wedge | Narrow, honest, dogfoodable | Requires discipline to stay narrow |

### Error And Rescue Registry

| Failure | User Sees | Rescue |
| --- | --- | --- |
| One pedal missing at setup | Not ready state on readiness board | Retry scan, change devices, or fix hardware before ride |
| One pedal drops mid-ride | Persistent truth strip, ride continues | Reconnect in background, suppress unsupported balance claims |
| HR drops mid-ride | Partial-data warning, ride continues | Mark HR partial in summary |
| FIT export fails | Clear export failure message | Keep ride local and allow retry |

### CEO Completion Summary

- Mode: selective expansion remains correct
- Strategic gaps found: 1
- Strategic gaps fixed: 1
- Main fix: future Strava sync now explicitly carries the tiny trusted backend
  expectation for secure token exchange and refresh

## Design Review

The repo-native UX plan is strong. The core hierarchy is now explicit, not just
implied.

Scores:

- Information architecture: 9/10
- Interaction states: 9/10
- User journey: 9/10
- AI slop resistance: 9/10
- Design-system alignment: 7/10, still limited by the intentional absence of a
  reusable `DESIGN.md`
- Responsive and accessibility: 9/10

Design gap found and fixed:

- export feedback and large-text behavior are now explicit in
  [UX_SPEC.md](UX_SPEC.md)

## Eng Review

The architecture is sound, but the repo docs were still missing two choices that
would otherwise get made ad hoc during implementation.

Engineering gaps found and fixed:

1. Local persistence strategy was implicit.
1. Recorder sample cadence was implicit.

Both are now explicit in
[ARCHITECTURE.md](ARCHITECTURE.md)
and
[DECISIONS.md](DECISIONS.md):

- Room for local persistence
- 1 Hz logical sample frames in v1, reducing faster BLE events into one persisted
  frame per second

### Failure Modes Registry

| Failure Mode | Covered? | Notes |
| --- | --- | --- |
| Setup cannot find one sensor | Yes | Readiness board keeps missing sensor explicit |
| UI backgrounds during ride | Yes | Foreground recorder service remains the plan |
| One pedal dropout | Yes | Partial-data rules are explicit in UX and decisions |
| FIT export failure | Yes | Export failure keeps ride local and retryable |
| Automatic sync secret handling | Deferred but acknowledged | Future sync now notes likely tiny trusted backend |
| Full process-kill recovery | Deferred | Already captured in [TODOS.md](TODOS.md) |

### Eng Completion Summary

- Architecture: 7/10 -> 9/10
- Test plan: 8/10 -> 9/10 after adding the coverage map to
  [TEST_PLAN.md](TEST_PLAN.md)
- Critical gaps: 0

## Cross-Phase Themes

- Trust beats breadth. This came up in product scope, UX posture, and engineering
  error handling.
- Partial-data truthfulness is the heart of the product, not an edge case.
- Recorder boringness is the moat. The app becomes generic if reliability slips
  behind feature expansion.

## Decision Audit Trail

| # | Phase | Decision | Classification | Principle | Rationale | Rejected |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | CEO | Keep the Assioma Duo-first recorder wedge | Mechanical | Completeness | This is still the clearest truthful wedge and avoids generic cycling-app drift | Broad hardware support now |
| 2 | CEO | Keep PR1 recorder and PR2 asymmetry split | Mechanical | Pragmatic | It preserves insight without complicating the recorder hot path | Collapsing both into one first slice |
| 3 | CEO | Restore the Strava tiny-backend expectation into repo docs | Mechanical | Explicit over clever | The secure sync shape should be stated now, not rediscovered later | Mobile-only secret handling ambiguity |
| 4 | Design | Keep one-screen setup, passive live ride, totals-first summary | Mechanical | Explicit over clever | These decisions are already coherent and reduce implementer drift | Wizard setup or chart-first summary |
| 5 | Design | Add explicit export feedback and large-text behavior | Mechanical | Completeness | These are real user states and should not be left to UI improvisation | Deferring export/a11y polish |
| 6 | Eng | Choose Room for v1 local persistence | Mechanical | Explicit over clever | It is the clearest Android fit for durable recorder data | Ad hoc file storage or unspecified persistence |
| 7 | Eng | Persist 1 Hz logical sample frames, while allowing higher-frequency live state in memory only | Taste | Pragmatic | It keeps the durable ride contract explicit without forcing the live recorder state to be equally coarse | Leaving cadence unspecified or persisting raw event frequency |
| 8 | Eng | Add a coverage map to the test plan | Mechanical | Completeness | It closes the gap between test intent and codepath coverage | A high-level test plan only |
