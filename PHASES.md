# Phased Implementation Plan

## Project objective

Build a free, unlimited-use Android PS3-style touchscreen controller
that communicates over local Wi-Fi to a native Mac companion and is
exposed to RPCS3 as a **genuine virtual gamepad**. USB may be added
later as an alternate transport. The primary target is Apple Silicon
(MacBook Pro M5) and the user's installed macOS/RPCS3 versions.

**Current state: planning and compatibility validation only.** Do not
start full app implementation until Phase 0 and Phase 1 gates pass.

## Operating principles

-   Work phase-by-phase; never skip a gate.
-   Do not delete or overwrite existing files.
-   Prefer free and open-source dependencies, and verify licenses before
    adoption.
-   Keep all runtime functions local; no account, subscription, cloud
    relay, analytics, or usage timer.
-   Keyboard mapping is not a valid substitute for the genuine gamepad
    requirement.
-   "Perfect/no errors" is an aspiration, not a guarantee. Prove
    behavior with tests and report unknowns honestly.
-   Overnight unattended work is best-effort only. Continue safe tasks
    autonomously, but stop at OS permissions, reboots, hardware
    requirements, security prompts, or unresolved blockers.

------------------------------------------------------------------------

## Phase 0 --- Workspace inventory, preservation, and environment report

### Tasks

1.  Inspect the entire current workspace and identify the real project
    root.
2.  Read existing documentation, source code, build files, scripts, Git
    status, and ignored files.
3.  Record macOS version/build, CPU architecture, Xcode/Swift version if
    installed, Android Studio/SDK versions, Java version, Git version,
    and RPCS3 version.
4.  Check whether CoreOrbits/GamerPad is currently installed or
    configured, without changing it.
5.  Create a dated backup of relevant project files and RPCS3
    configuration before any edits. Do not overwrite an existing backup.
6.  Merge these documents into the existing project only if safe. If a
    file name already exists, compare and append/merge instead of
    replacing.
7.  Create `docs/PROGRESS.md` and `docs/DECISION_LOG.md` if they do not
    exist.

### Acceptance gate

-   Existing work is preserved and Git status is recorded.
-   Environment report is complete or unknown values are explicitly
    marked.
-   Backup location is recorded.
-   No source implementation or driver installation has been attempted.

## Phase 1 --- Genuine virtual gamepad compatibility spike (BLOCKING)

### Objective

Prove that a programmatic input source can drive a virtual gamepad
backend on the exact target Mac and that RPCS3 receives those inputs as
controller events.

### Tasks

1.  Verify the exact macOS version/build and RPCS3 build. Do not assume
    macOS 27 compatibility merely from a prior report.
2.  Read the official OpenJoystickDriver repository, support matrix,
    license, install/uninstall guidance, and documented control/input
    APIs.
3.  Determine whether OpenJoystickDriver can accept programmatic input
    from a separate Mac companion. Do not assume it can just because it
    exposes virtual controllers.
4.  Prefer a documented, reversible test. Avoid kernel/system extension
    installation unless required and explicitly approved.
5.  If the candidate backend requires a physical controller, a private
    API, unsupported injection, a reboot, or security changes, record
    the limitation and do not bypass protections.
6.  If no direct input API exists, research other free, maintained,
    licensed, macOS-compatible backends and validate their input
    injection API before selecting one.
7.  Test RPCS3 device enumeration with SDL if available; record the
    device name and handler. Test buttons, both sticks, D-pad, triggers,
    simultaneous presses, and release.
8.  Save screenshots/logs with private data removed, and document exact
    reproduction steps.

### Acceptance gate

All must be true: - A documented API or supported mechanism allows the
Mac companion to set virtual gamepad state. - RPCS3 detects the device
on the target Mac. - A test program can set and clear a button and move
an axis, and RPCS3 receives the expected events. - No keyboard/mouse
emulation is used to pass the gate. - License and OS compatibility are
reviewed.

### If blocked

Do not begin full app implementation. Complete a blocker report with
candidate backends, evidence, why each failed, and the single next
manual action needed. A protocol simulator or UI mock may be built only
if it is clearly labeled non-integrated and does not pretend the gate
passed.

## Phase 2 --- Architecture and protocol specification

### Tasks

1.  Define typed controller state: digital buttons, D-pad, normalized
    stick axes, trigger axes, sequence number, protocol version, session
    ID.
2.  Choose a documented, maintained, free transport implementation.
    Initial recommendation: WebSocket over local Wi-Fi for reliable
    state snapshots and straightforward diagnostics; benchmark before
    tuning.
3.  Specify pairing, authentication, message limits, heartbeat,
    stale-packet rejection, reconnect, and safe neutral-state behavior.
4.  Define `GamepadBackend` protocol/interface independent of the
    selected driver.
5.  Write architecture decision records and threat model.

### Acceptance gate

-   Protocol schema and examples are documented.
-   Malformed/stale packets and disconnect behavior are specified.
-   No backend-specific assumptions leak into Android UI code.
-   Unit tests cover serialization and validation.

## Phase 3 --- Mac companion shell and receiver

### Tasks

1.  Create a native SwiftUI menu-bar/window app with connection status,
    pairing, diagnostics, and settings.
2.  Implement a receiver with bounded input sizes, authenticated
    pairing, sequence validation, heartbeat, reconnect, and logs that
    avoid secrets.
3.  Implement the `GamepadBackend` interface and a fake backend for unit
    tests.
4.  Implement the real backend only through the API verified in Phase 1.
5.  Add XCTest coverage for protocol parsing, stale messages,
    disconnect, neutral state, and button transitions.

### Acceptance gate

-   Mac app builds and tests pass on the target Mac.
-   Fake backend tests pass.
-   Real backend integration tests pass or are clearly BLOCKED by the
    Phase 1 compatibility decision.
-   Disconnect reliably releases all inputs.

## Phase 4 --- Android app and controller UI

### Tasks

1.  Create a Kotlin/Jetpack Compose app in landscape orientation.
2.  Build PS3-inspired controls: left/right analog sticks; D-pad; Cross,
    Circle, Square, Triangle; L1/R1; L2/R2; L3/R3; Start, Select, PS.
3.  Support multitouch without one finger stealing or cancelling another
    control.
4.  Normalize axes; implement configurable dead zones, sensitivity,
    layout scaling, and accessibility labels.
5.  Implement pairing, connection status, haptics only where supported,
    reconnect, and a safe disconnect control.
6.  Save user settings locally and provide a diagnostics screen.
7.  Add unit tests for state mapping and UI/instrumentation tests where
    practical.

### Acceptance gate

-   Android app builds, lint passes, and tests pass.
-   Multitouch and all digital/analog controls emit correct protocol
    states in a local test receiver.
-   UI works across portrait/landscape changes and common screen sizes,
    while preferring landscape for play.
-   No cloud account, paid SDK, or usage timer.

## Phase 5 --- End-to-end integration

### Tasks

1.  Pair Android and Mac on the same Wi-Fi network.
2.  Stream controller state to the Mac receiver.
3.  Convert state into the verified virtual gamepad API.
4.  Configure RPCS3 to use the detected gamepad and map all controls.
5.  Test press/release, diagonals, triggers, simultaneous combinations,
    app backgrounding, Wi-Fi interruption, Mac sleep/wake, and
    reconnect.
6.  Add diagnostics to distinguish transport, backend, and RPCS3 mapping
    failures.

### Acceptance gate

-   RPCS3 receives real gamepad events from the Android app.
-   No keyboard/mouse fallback is counted as success.
-   Disconnect and timeout tests show no stuck buttons.
-   Test log includes device/OS/RPCS3 versions and outcomes.

## Phase 6 --- Quality, latency, security, and resilience

### Tasks

1.  Measure end-to-end latency with timestamps and repeatable
    procedures; do not invent performance numbers.
2.  Tune update cadence only from measurements. Start with a modest
    configurable rate and verify CPU/network load before increasing it.
3.  Test a 60-minute sustained session.
4.  Test malformed messages, unauthorized peers, duplicate/stale
    sequence numbers, abrupt disconnect, app restart, and sleep/wake.
5.  Verify no credentials are logged and pairing secrets are stored
    safely.
6.  Run formatters, lint, unit tests, integration tests, and static
    analysis supported by the toolchain.
7.  Document known limitations and supported configurations.

### Acceptance gate

-   No known critical/high-severity defects remain open.
-   All available tests pass; unavailable hardware/manual tests are
    explicitly marked.
-   Sustained session and recovery results are recorded.
-   Latency figures are measured, not estimated.

## Phase 7 --- Documentation, licensing, and GitHub readiness

### Tasks

1.  Complete README with screenshots, supported versions, setup,
    pairing, RPCS3 mapping, troubleshooting, privacy, and limitations.
2.  Add a license after confirming the user's intended project license
    and third-party license compatibility. Do not silently choose a
    restrictive license.
3.  Add dependency/license inventory and notices.
4.  Add `.gitignore` for Android/Gradle, Xcode/Swift, macOS, IDE, local
    config, signing material, build output, and test artifacts.
5.  Add GitHub Actions for Android build/lint/tests and macOS
    build/tests on appropriate runners. Explain that real
    virtual-gamepad integration tests must run on a compatible Mac.
6.  Run secret scanning if a free tool is available; inspect every
    staged file and `git diff --check`.
7.  Create a release checklist and changelog.

### Acceptance gate

-   Clean clone instructions are tested where feasible.
-   CI jobs pass or platform restrictions are documented.
-   No secrets or machine-specific files are committed.
-   License and dependency notices are complete.
-   User explicitly approves publication and repository visibility
    before a public repo or release is created.

## Phase 8 --- Optional USB transport

Only after Wi-Fi integration passes: 1. Evaluate a supported
Android-to-Mac USB transport. 2. Reuse the same protocol and state
model. 3. Test USB permission handling, reconnection, and fallback to
Wi-Fi. 4. Keep USB optional; do not regress Wi-Fi functionality.

### Acceptance gate

-   USB works on the actual phone/Mac combination and Wi-Fi still works
    independently.

------------------------------------------------------------------------

## Required project tracking files

Maintain: - `docs/PROGRESS.md`: phase, tasks, tests, evidence, blockers,
next action. - `docs/DECISION_LOG.md`: dated decision, alternatives,
evidence, consequences. - `docs/COMPATIBILITY_MATRIX.md`: OS, Mac
architecture, RPCS3 build, handler, driver, test result. -
`docs/TEST_PLAN.md`: unit, integration, manual, resilience, and release
tests. - `CHANGELOG.md`: user-visible changes.

## Definition of done

The app is not complete until RPCS3 recognizes a genuine virtual gamepad
driven by Android input, all essential controls work, release/reconnect
safety tests pass, and the documented build/test/release checks are
truthful and reproducible.
