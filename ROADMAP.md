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

- [README.md](/Users/stuartlangley/src/sjlangley/peleoton-power-meter/README.md)
- [DECISIONS.md](/Users/stuartlangley/src/sjlangley/peleoton-power-meter/DECISIONS.md)
- [ARCHITECTURE.md](/Users/stuartlangley/src/sjlangley/peleoton-power-meter/ARCHITECTURE.md)
- [UX_SPEC.md](/Users/stuartlangley/src/sjlangley/peleoton-power-meter/UX_SPEC.md)
- [TEST_PLAN.md](/Users/stuartlangley/src/sjlangley/peleoton-power-meter/TEST_PLAN.md)
- [TODOS.md](/Users/stuartlangley/src/sjlangley/peleoton-power-meter/TODOS.md)
