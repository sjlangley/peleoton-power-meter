# UX Spec

This document captures the v1 UI behavior for the first recorder wedge.

It is not a full reusable design system. That broader `DESIGN.md` work is
intentionally deferred until after dogfooding.

## Product Posture

The UI should feel:

- calm
- trustworthy
- local-first
- passive during the ride
- explicit when data is degraded

The UI should not feel like:

- a generic startup dashboard
- a bike-computer cockpit
- a coaching app
- a broad fitness platform

## Screen Set

V1 has three primary user-facing screens:

1. setup readiness board
1. live ride screen
1. post-ride summary

## Setup Readiness Board

Use one screen, not a wizard.

### Purpose

Let the rider know whether the left pedal, right pedal, and heart-rate source
are ready before class starts.

### Hierarchy

1. title
1. one-sentence reassurance about local-first behavior
1. device readiness cluster
1. overall readiness message
1. primary action
1. secondary troubleshooting actions

### Layout

```text
READY TO RIDE
Your ride stays on this phone until you export it.

[ Left Pedal   Connected ]
[ Right Pedal  Connected ]
[ Heart Rate   Searching ]

Status: Waiting for heart-rate monitor

[ Continue Pairing ]
[ Retry Scan ]   [ Change Devices ]
```

### Rules

- Left and right pedals should read as one logical pair while keeping separate
  status visibility.
- `Start Ride` appears only when the required sensors are ready.
- No progress dots, no tutorial carousel, no multi-step setup ceremony.

## Live Ride Screen

This screen is passive. The rider should be able to glance, understand, and go
back to the class.

### Hierarchy

1. total power
1. cadence and heart rate
1. zone label and bar
1. recording state and elapsed time
1. degraded-data truth strip when needed

### Portrait Layout

```text
[ Recording ]  18:42

286
watts

Cadence 92 rpm      HR 154 bpm

Zone 4
[ zone progress bar ]
```

### Landscape Layout

Landscape support exists only for the live ride screen in v1.

Landscape should:

- place power on the left half
- place cadence, heart rate, and zone on the right half
- preserve glanceability, not add density

### Rules

- Power is the dominant visual anchor.
- Cadence and heart rate are always visible but secondary.
- Zone is persistent but quieter than numeric power.
- Do not use a balanced four-metric grid.
- Do not create a dense telemetry wall.

## Degraded Live State

When data becomes partial during a ride, the live screen keeps its structure and
shows a persistent truth strip above the metrics.

Example:

```text
Left pedal disconnected. Recording continues. Balance is partial.
```

Rules:

- Do not hide the main ride UI.
- Do not silently continue as if balance remains valid.
- Do not use color alone to communicate the issue.

## Post-Ride Summary

The first summary screen is totals-first with a strong asymmetry second section.

### Hierarchy

1. ride completion headline
1. headline totals
1. asymmetry block
1. export action
1. sync or export status message

### Layout

```text
Ride Complete
42:13 indoor ride

Avg Power   Avg Cadence   Avg HR
186 W       89 rpm        151 bpm

Asymmetry
[ compact whole-ride drift timeline ]
- 12:10-12:55 Right 54% / Left 46%
- 24:40-25:30 Left 53% / Right 47%
- 31:00-31:50 Right 55% / Left 45%

[ Export FIT ]
```

### Rules

- Totals lead the page.
- Asymmetry remains on the first screen, not a separate tab.
- Export is explicit and does not hide inside the asymmetry section.

## Asymmetry Block

The asymmetry block combines:

- a compact whole-ride drift timeline
- up to three notable moments

The notable moments use the explicit heuristic already captured in
[DECISIONS.md](/Users/stuartlangley/src/sjlangley/peleoton-power-meter/DECISIONS.md).

## Partial-Data Summary Behavior

If pedal data is incomplete for part of the ride:

- keep the asymmetry section visible
- explain that balance insight is limited
- mark unsupported intervals on the compact timeline
- suppress unsupported notable moments
- keep totals visible and stable

Do not:

- hide the whole asymmetry block
- replace the screen with an error slab
- imply confidence the data does not deserve

## Interaction States

| Feature | Loading | Empty / First-Time | Error | Success | Partial |
| --- | --- | --- | --- | --- | --- |
| Setup | Searching states in device rows | Explain required sensors and local-first posture | Device-specific plain-language retry guidance | All required sensors connected, ride can start | Show what is still missing |
| Live ride | Brief placeholders before first samples | Not applicable after ride start | Avoid full-screen errors unless recording itself failed | Power-first active ride view | Persistent truth strip explains what is partial |
| Summary totals | Summary shell appears immediately | Future history views can invite first ride | Export failure stays local and clear | Totals render first | Partial metrics labeled clearly |
| Asymmetry | `Analyzing balance...` if needed | `No sustained asymmetry intervals detected` when nothing qualifies | Explain that insight is unavailable without blocking totals | Timeline plus top moments | Explain limitation and suppress unsupported claims |

## Responsive Rules

- Setup: portrait-first only in v1
- Live ride: portrait and landscape in v1
- Summary: portrait-first only in v1
- Tablet behavior may reuse phone layouts if hierarchy and touch targets remain
  intact

## Accessibility Rules

- Minimum touch target: 44x44 px
- Status and warning states must use text, not color alone
- Labels for screen readers must name `Left pedal`, `Right pedal`, `Heart rate`,
  and recording state explicitly
- Live metrics should expose both label and value
- The degraded truth strip should be announced as a status update
- Text contrast must remain accessible in all states

## Visual Direction

- light-theme first
- warm neutral background
- dark ink text
- one accent color
- minimal chrome
- very few borders
- no dashboard-card mosaic
- no purple or indigo startup look
- no ornamental icons in colored circles

The design target is a trusted instrument panel, not a flashy product demo.
