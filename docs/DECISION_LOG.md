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

## D-007 — Android 16 WindowInsets compatibility & Cleartext WebSockets
- Status: Accepted
- Decision: Handle system bars in `onWindowFocusChanged(hasFocus)` via `WindowCompat.getInsetsController(window, window.decorView)` instead of directly accessing `window.insetsController` in `onCreate()`. Enable `android:usesCleartextTraffic="true"` in the Android Manifest.
- Reason: On Android 16 (API 36+), accessing `window.insetsController` in `onCreate()` before DecorView attachment causes a `NullPointerException`. Cleartext traffic is required by default for local LAN/USB WebSocket (`ws://`) connections.

## D-008 — Zero-Latency USB Port Forwarding & Auto-Connect
- Status: Accepted
- Decision: Configure `adb reverse tcp:6000 tcp:6000` for physical USB connections while retaining automatic broadcast/Wi-Fi connection capabilities on `192.168.1.0/24`. Default host fallback set to `127.0.0.1` when tethered, auto-connecting immediately on app launch.
- Reason: USB reverse proxy eliminates Wi-Fi packet jitter and interference, delivering sub-millisecond input responsiveness while allowing seamless fallback to local Wi-Fi.

## D-009 — Interactive Touch Overlay Customization (Move, Resize, Nudge)
- Status: Accepted
- Decision: Implement an interactive Edit Mode in Compose allowing users to select touch clusters (D-Pad, Left Stick, Right Stick, Action Buttons, Left/Right Shoulders, Center Controls), drag them freely across the screen, nudge them in discrete 6px increments (←, ↑, ↓, →), and scale them between 60% and 180%. Persist the full layout configuration as JSON in `SharedPreferences`.
- Reason: Mobile screen dimensions and finger ergonomics vary widely across phone models and grip styles. Users need flexible customization to avoid mispresses during gameplay.
- Consequence: Added `LayoutConfig.kt` with per-cluster offsets, scales, and sensitivities, plus an `EditInspectorToolbar` with discrete nudge and scale buttons.

## D-010 — Per-Element and Global Button/Trigger Sensitivity Curve
- Status: Accepted
- Decision: Provide both a global button/trigger sensitivity slider in the Settings Dialog (0.5x to 2.5x) and a per-cluster sensitivity multiplier in the Edit Mode Inspector. The effective sensitivity scales the pressure thresholds for buttons and applies a responsive curve `clamp(value * sensitivity, 0.0, 1.0)` to analog triggers (L2/R2) in `TouchInputMapper`.
- Reason: Enables players to tune hair-trigger responsiveness for fast-paced action or set lower sensitivity for high-precision throttle control in racing titles.

## D-011 — AwaitEachGesture Pointer-ID Tracking & Immediate Neutral Dispatch
- Status: Accepted
- Decision: Replace `detectDragGestures` in `JoystickStick.kt` with `awaitEachGesture` pointer-ID tracking and calculate stick displacement using absolute `touchPos - center`. In `GamepadClient.kt`, exempt neutral/release state transitions from 60Hz rate limiting, while buffering trailing updates to guarantee the final (0, 0) resting state is always sent.
- Reason: `detectDragGestures` accumulated deltas on a clamped variable, causing persistent stick drift when dragging outside the stick radius, and rate-limiting dropped the final release packet if it occurred within 16ms of the prior touch move.
- Consequence: Completely eliminates stick drift and stuck movement on finger lift.

## D-012 — Dual-Mapped RPCS3 Native Input Adapter with Clean Event Tap
- Status: Accepted
- Decision: In `RPCS3DirectInputAdapter.swift`, map the right analog stick to RPCS3's native `Default.yml` Player 1 keyboard bindings (Up: Home 115, Down: End 119, Left: Forward Delete 117, Right: Page Down 121) alongside IKJL aliases. Map L3 to F (keycode 3) and PS to Backspace (keycode 51). Use `CGEventSource(stateID: .hidSystemState)` and eliminate arbitrary mouse clicks.
- Reason: RPCS3's default keyboard handler was configured for navigation keys rather than IKJL for the right stick. Synthetic mouse clicks at (500, 100) caused focus issues during cutscenes and menus.

## D-013 — Resilient Stream Buffer Framing & Extended Inactivity Timeout
- Status: Accepted
- Decision: Upgrade `ControllerServer` to parse all WebSocket frames present in a stream buffer via `extractAllWebSocketPayloads`, and increase session timeout to 8.0 seconds of idle time.
- Reason: Cutscenes and menus often involve zero touch input for multiple seconds. Short timeouts coupled with dropped trailing frames caused unexpected session resets.

## D-014 — Granular 19-Control Independent Layout Model
- Status: Accepted
- Decision: Replace coarse 7-cluster layout model with a granular `ControlId` enumeration covering all 19 individual controls (Cross, Circle, Square, Triangle, D-pad Up/Down/Left/Right, L1, R1, L2, R2, L3, R3, Start, Select, PS, Left Stick, Right Stick). Each control stores independent position $(x, y)$, scale factor, opacity, and sensitivity in `ControllerLayoutConfig`.
- Reason: Users require complete freedom to reposition or resize each individual button (e.g. moving D-pad Up without affecting Down/Left/Right, or resizing Cross without resizing Square).

## D-015 — Non-Blocking Server Stop and Persistent TCC Accessibility Signing
- Status: Accepted
- Decision:
  1. Replace `NSLock` with `NSRecursiveLock` in `ControllerServer.swift` and release lock prior to invoking `updateStatus(.stopped)` to prevent recursive deadlock and UI hang on "Stop Server".
  2. Sign `PS3ControllerCompanion.app` with `--requirements '=designated => identifier "com.antigravity.ps3companion"'` so macOS TCC Accessibility grants persist across builds instead of being tied to ephemeral CDHashes.
  3. Include an immediate "Relaunch App" and "Dismiss" workflow in `ContentView.swift` and `AppViewModel.swift`, accommodating the macOS requirement that already-running processes must be restarted for newly toggled Accessibility rights to take effect.
  4. Embed high-resolution controller `AppIcon.icns` in the application bundle to ensure legitimate visual presence in System Settings and the macOS Dock.
- Reason: macOS freezes when a thread acquires a non-recursive lock twice, and macOS TCC never applies accessibility trust dynamically to an active running process until it is restarted.

## D-016 — Elimination of System Alert Sounds and Wi-Fi UDP Auto-Discovery
- Status: Accepted
- Decision:
  1. Eliminate macOS alert beep ("alarm like sound"):
     - Remove destructive Forward Delete (117) and Home/End editing keys from right stick mapping in `RPCS3DirectInputAdapter.swift`, replacing with clean alphanumeric keys (I, K, J, L).
     - Isolate synthetic keyboard events strictly to RPCS3's process (`event.postToPid`) when RPCS3 is detected, preventing keystroke leakage into the active window.
     - Add `NSEvent.addLocalMonitorForEvents` in `AppViewModel.swift` to consume unhandled key events inside `PS3ControllerCompanion`'s window, preventing AppKit from invoking `NSBeep()`.
     - Set macOS system alert volume to 0.0 (`defaults write -g com.apple.sound.beep.volume -float 0.0`).
  2. Fix Wi-Fi Connectivity and Auto-Discovery:
     - Remove interface name suffix (`" (en0)"`) from `localIPs` in `AppViewModel.swift` so clean IP strings (`192.168.1.24`) are emitted.
     - Add 1-click "Copy URL" and "Copy IP" buttons in `ContentView.swift`.
     - Implement UDP discovery beacon on port 6001 on the Mac companion and client auto-discovery in `GamepadClient.kt` so the mobile app detects the Mac automatically over Wi-Fi without manual IP entry.
- Reason: Unhandled keys posted to non-text controls triggered macOS system beeps, and corrupted IP strings broke Wi-Fi connection attempts.
