# TODOS

## Infrastructure

### CI, Required Checks, and Codecov

**What:** Add GitHub Actions to run the test suite on push and pull request, make test status a required merge check, and upload coverage to Codecov.

**Why:** This turns tests from a local habit into a project rule and makes coverage drift visible from the first implementation PR.

**Context:** The repo is currently empty, so this is the cheapest moment to establish release-quality engineering plumbing. This should cover a test workflow, PR status checks, Codecov upload, and the minimum project scaffolding needed so future PRs are reviewed against CI instead of trust.

**Effort:** M
**Priority:** P1
**Depends on:** Initial test harness

## Sync

### Automatic Strava Sync After Recorder Stability

**What:** Add automatic Strava sync after the local recorder, summary flow, and FIT export are proven stable through dogfooding.

**Why:** This completes the replacement workflow and removes the manual export step once the ride data is trustworthy.

**Context:** The first shipped slice should stop at valid FIT generation plus manual export to keep the initial PR small. This follow-up should include Strava OAuth, upload queue and retry behavior, expired-auth handling, and clear sync-status UI. It depends on the local ride model and FIT generation already being solid.

**Effort:** M
**Priority:** P1
**Depends on:** Stable local ride model, valid FIT generation, real-world dogfood rides

## Reliability

### Full Interrupted-Session Recovery

**What:** Add full recovery for interrupted in-progress rides after app or OS interruption.

**Why:** Background durability is not the same as true reliability. This closes the biggest trust gap left out of the first shipped slice.

**Context:** The first implementation should write ride data durably and survive backgrounding, but full process-kill recovery is intentionally deferred until the recorder has real usage data. This follow-up should cover resuming from persisted state, clearly marking sample gaps, and proving the behavior with targeted tests.

**Effort:** M
**Priority:** P1
**Depends on:** Stable session persistence model, chosen sampling interval, recorder dogfood results

## Product

### Named Ride Presets

**What:** Add named ride presets with a saved sensor bundle and FTP value.

**Why:** This turns persistent device memory into a real product feature for riders with multiple setups or multiple users.

**Context:** This should stay out of the first wedge, but it is one of the highest-value expansions once the recorder and summary loop are trusted. Start with simple saved preset selection, not a full setup-management system.

**Effort:** M
**Priority:** P2
**Depends on:** Stable pairing model, stable local session model, first dogfood release

### Manual Ride Markers

**What:** Add lightweight in-ride markers that appear in the post-ride summary timeline.

**Why:** This ties the measurement layer back to what actually happened in class without turning the app into a coach.

**Context:** Keep this deferred until the passive ride experience is proven. If it lands later, it should be intentionally lightweight, one-tap capture, not annotation theater.

**Effort:** S
**Priority:** P2
**Depends on:** Stable live ride screen, summary timeline, trusted passive recorder flow

### Basic Ride History

**What:** Add basic ride history with past rides and summary metrics only if it creates value beyond what Strava already provides.

**Why:** This creates product memory only if it becomes the home for unique in-app asymmetry insight over time, not generic workout history that Strava already handles well.

**Context:** Strava is already the natural destination for generic ride history. Revisit this only when in-app longitudinal asymmetry insight becomes compelling enough to justify a dedicated history surface. If built, start with a simple list of past rides and summary cards, not a giant analytics dashboard.

**Effort:** M
**Priority:** P2
**Depends on:** Stable local ride model, trusted summary generation, successful first dogfood release

### Reusable Design System and DESIGN.md

**What:** Create a lightweight reusable design system and write it down in `DESIGN.md` after the first dogfood release.

**Why:** The current plan is now specific enough to build setup, live ride, and summary well, but future surfaces like ride history, presets, and sync UI will drift unless the visual language becomes explicit and reusable.

**Context:** This is not MVP-critical. The design review already locked the first wedge's UI behavior and hierarchy. This follow-up is about turning those decisions into durable tokens, typography choices, spacing rules, status styles, and layout patterns that later features can reuse without re-litigating the basics.

**Effort:** M
**Priority:** P2
**Depends on:** First dogfood release, confirmed live ride and summary patterns worth standardizing

## Completed
