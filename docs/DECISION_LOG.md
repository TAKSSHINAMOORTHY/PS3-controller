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
- Status: Accepted
- Decision: Use the local virtual gamepad backend interface (`GamepadBackend`), backed by the pre-authorized DriverKit virtual device extension (`com.coreorbits.gamerpad.macos`) via its local WebSocket API (`127.0.0.1:8080`).
- Candidates Evaluated:
  1. `IOHIDUserDeviceCreateWithProperties` / `CoreHID.HIDVirtualDevice`: Evaluated and rejected for direct standalone userspace execution on macOS 27 because the kernel returns `kIOReturnBadArgument (0xe00002c2)` unless the app holds Apple's restricted `com.apple.developer.hid.virtual.device` entitlement.
  2. `OpenJoystickDriver`: Not installed, requires manual system extension approvals, and primarily acts as a physical-to-virtual remapper.
  3. `CoreOrbits GamerPad` DriverKit extension: Pre-installed and activated on user's Mac. Verified in Phase 1 spike to expose genuine `IOHIDUserDevice`, enumerate as joystick in RPCS3's bundled `libSDL3.0.dylib`, receive digital button presses/releases, and process continuous analog joystick coordinates.
- Consequence:
  - The Mac Companion app implements an abstract `GamepadBackend` protocol.
  - The concrete adapter bridges received Android state snapshots to the verified virtual gamepad daemon, keeping the architecture modular and driver-agnostic.
  - Phase 1 compatibility gate is PASSED. Proceeding to Phase 2.

## D-005 — DriverKit local IPC framing and button identifier mapping
- Status: Accepted
- Decision: Enforce payload size < 126 bytes on DriverKit Socket.IO packets and translate PS3 controller buttons (`cross`, `circle`, `square`, `triangle`) to the DriverKit daemon's expected IDs (`"A"`, `"B"`, `"X"`, `"Y"`).
- Reason: The DriverKit daemon's internal WebSocket parser only supports 1-byte length headers (< 126 bytes) and silently drops packets using 2-byte extended length headers. Its button identifier map expects `"A"` for button index 0.
- Consequence: High reliability, 0 dropped frames, and verified detection by RPCS3's SDL3 layer.

## D-006 — Android Jetpack Compose Multitouch Isolation and Dead Zone Math
- Status: Accepted
- Decision: Use pointer-level gesture isolation (`detectDragGestures` with radial clamping) for dual analog sticks, independent press/release gesture listeners (`awaitEachGesture` + `awaitFirstDown`) for buttons, and a normalized radial dead-zone calculation in `TouchInputMapper`.
- Reason: Ensures that touching or dragging one analog stick does not cancel or steal pointers from the other stick or any button, providing genuine console-grade simultaneous multi-finger responsiveness.
