# Contributing

This project is still early, so the contribution rules should stay small and
sharp.

## Toolchain

- Android Studio current stable
- JDK 21 is the working local setup today
- Gradle wrapper from the repo

## Build And Test

Run these from the repo root:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew jacocoDebugReport jacocoDebugCoverageVerification
```

If all four pass locally, you are in good shape for a pull request.

## Code Style

The project uses:

- Kotlin official style
- clear, explicit naming over generic abstractions
- small, readable composables and domain models
- comments only when they explain non-obvious behavior

Style rules in practice:

- prefer explicit domain types like `PedalPair` over vague "sensor" wrappers
- keep recorder truth rules obvious in code
- avoid clever abstractions before the recorder path is proven
- keep UI copy plain and product-facing, not framework-facing

## Quality Gates

GitHub Actions runs on push and pull request:

- `assembleDebug`
- `testDebugUnitTest`
- `jacocoDebugCoverageVerification`
- `lintDebug`

Lint is configured to fail the build on warnings, not just errors.

## Scope Discipline

The first wedge is still:

- pair sensors
- record locally
- show passive live ride state
- show totals-first summary
- export FIT

Do not widen scope casually. Broader sync, history, presets, and richer product
surfaces already live in [TODOS.md](TODOS.md).
