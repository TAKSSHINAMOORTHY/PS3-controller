# Android PS3-Style Controller for macOS

A free, local-first Android touchscreen controller for RPCS3 on Apple Silicon Macs.

## Status

**Compatibility-first planning scaffold. Full app implementation must not begin until a genuine virtual gamepad can be driven by the planned Mac receiver and recognized by RPCS3.**

This repository scaffold is intentionally documentation-first. It does not claim that virtual gamepad compatibility is already solved.

## Read these first

1. [`AGENTS.md`](AGENTS.md) — mandatory rules for Antigravity.
2. [`PHASES.md`](PHASES.md) — phase-by-phase plan and acceptance gates.
3. [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — proposed components and boundaries.
4. [`docs/COMPATIBILITY_MATRIX.md`](docs/COMPATIBILITY_MATRIX.md) — evidence required before selecting a backend.
5. [`docs/DECISION_LOG.md`](docs/DECISION_LOG.md) — record decisions and evidence.

## Proposed stack (free tools)

- Android: Kotlin, Jetpack Compose, Android Studio, Android SDK, Gradle Wrapper.
- Mac companion: Swift, SwiftUI, Swift Package Manager, Xcode, XCTest.
- Transport: WebSocket over the local network for the first implementation; use state snapshots, sequence numbers, heartbeat, authenticated pairing, and safe neutral-state recovery.
- Controller integration: an adapter interface. A concrete virtual gamepad backend is **unverified** until tested on the target Mac/macOS/RPCS3 combination.
- Version control: Git, GitHub, `.gitignore`, pull requests/branches if desired.
- CI: GitHub Actions for build, lint, and test jobs where runners support the required toolchain. macOS-specific integration tests may need to run locally on the user's Mac.
- Documentation and rules: Markdown, Antigravity workspace rules. No paid plugin is required.

Use the Java/JDK version supported by the selected stable Android Gradle Plugin; prefer Android Studio's bundled JDK unless a documented dependency requires otherwise. Do not hard-code tool versions from memory—record the versions actually installed and verify official release requirements.

## Critical limitation

No software can guarantee “zero errors” or complete unattended overnight in every environment. The process must report verified tests, skipped tests, and blockers honestly. If a required approval, reboot, OS permission, unavailable device, or unresolved driver compatibility blocks progress, document it and stop at the relevant gate instead of fabricating success or implementing a keyboard-only substitute.
