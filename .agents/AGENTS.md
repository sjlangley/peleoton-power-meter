# Agent Instructions for peleoton-power-meter

## Critical Pre-Commit Checks

**MANDATORY**: Before pushing any code or creating pull requests, you MUST run the full CI validation suite and ensure all checks pass.

### Required CI Checks

Run the following command and ensure BUILD SUCCESSFUL:

```bash
./gradlew clean build test lint ktlintCheck detekt
```

### CI Validation Workflow

1. **After making any code changes**: Run `./gradlew clean build test lint ktlintCheck detekt`
2. **If any check fails**: Fix the issues immediately before proceeding
3. **Never commit or push**: Code that will not pass CI
4. **Before creating a PR**: Verify all checks pass locally

### Common CI Failures and Fixes

#### Detekt Issues

- **TooGenericExceptionCaught**: Catch specific exceptions (IllegalArgumentException, SecurityException) instead of Exception
- **UnusedPrivateProperty**: Remove unused properties or use them in tests

#### Lint Issues

- **MissingPermission**: Add `@SuppressLint("MissingPermission")` with documentation explaining permission assumptions
- **InlinedApi**: Add SDK version checks using `Build.VERSION.SDK_INT >= Build.VERSION_CODES.*`

#### Test Failures

- **Suspend functions**: Tests calling suspend functions must be wrapped in `runTest { }`
- **Mock setup**: Ensure all required mocks are configured before use
- **State assertions**: Consider all possible states (Disconnected, Connecting, Connected, Error)

### Quality Gates

All these must pass:
- ✅ All unit tests passing
- ✅ Lint with `abortOnError=true` and `warningsAsErrors=true`
- ✅ ktlintCheck for code formatting
- ✅ detekt for static analysis

### Pre-existing Test Failures

If a test is failing in the main branch (verify with `git checkout main && ./gradlew test`), document it and continue with tests specific to your changes.

## Code Review Response Guidelines

When addressing PR review comments:

1. **Fix all review comments comprehensively**: Don't fix items in isolation
2. **Test each fix**: Run CI after each major change
3. **Update related code**: If one area has an issue, check for similar patterns elsewhere
4. **Thread safety**: When accessing shared mutable state from multiple threads, use appropriate synchronization (Mutex, synchronized, withLock)
5. **Resource management**: Always close GATT connections and clean up resources properly
6 **Error handling**: Handle all possible error cases explicitly (null returns, status codes, exceptions)

## Pull Request Standards

Before creating a PR ensure:

1. All CI checks pass locally
2. Documentation is updated (ARCHITECTURE.md, README.md, ROADMAP.md as appropriate)
3. Tests cover new functionality and edge cases
4. Commit messages follow conventional commits format
5. Code follows project patterns and conventions

### Deferring Work to Keep PRs Small

When deferring fixes or enhancements to keep a PR focused:

1. **Always create a GitHub issue immediately** for the deferred work
2. Include in the issue:
   - Context: why was it deferred?
   - When should it be done? (e.g., "during BLE integration phase")
   - What specific work needs to happen?
3. Reference the issue number in PR comments explaining the deferral
4. Example: "Will add detailed timing tests in #34 during integration phase"

**Critical**: Never defer work without creating an issue to track it. Deferred work without an issue gets forgotten and creates technical debt.

## Threading and Concurrency

For BLE and Android components:

- **BluetoothGattCallback**: Runs on a Binder thread, not the same thread as your initialization code
- **Shared mutable state**: Protect with Mutex or other synchronization primitives
- **Suspend functions**: Use `withLock` for atomic operations on shared state
- **Coroutine safety**: Operations touching shared maps should acquire locks before accessing

## BLE Best Practices

- Always check `status == BluetoothGatt.GATT_SUCCESS` for connection state changes
- Handle null returns from `connectGatt()` explicitly
- Close GATT connections in all disconnect/error paths to avoid leaks
- Clear references to closed GATT objects
- Use exponential backoff for reconnection attempts
- Remove disconnected devices from connection tracking to enable fresh reconnects
