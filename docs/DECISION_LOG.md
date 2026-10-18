# Decision Log

## D-001 — Compatibility before full implementation
- Status: Accepted
- Decision: Verify a programmatically controllable virtual gamepad on the exact target macOS/RPCS3 setup before implementing the full Android app.
- Reason: macOS virtual gamepad input is the main technical risk.
- Consequence: Phase 1 is a hard gate.

## D-002 — Native application stack
- Status: Proposed
- Android: Kotlin + Jetpack Compose.
- Mac: Swift + SwiftUI.
- Build/test: Gradle Wrapper, Swift Package Manager/Xcode, XCTest, Android tests.
- Reason: Native APIs and tooling are free and fit each platform.

## D-003 — WebSocket as initial transport
- Status: Proposed; validate during Phase 2
- Reason: Reliable, inspectable local transport with simple diagnostics and reconnection.
- Consequence: Benchmark actual latency and CPU/network use before finalizing update rate.

## D-004 — Virtual gamepad backend
- Status: UNDECIDED
- Candidates must be evaluated for a supported programmatic input API, macOS version, Apple Silicon, RPCS3/SDL recognition, license, maintenance, and installation/security requirements.
- Do not infer input injection capability from the fact that a driver can enumerate virtual controllers.
