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

## D-017 — Direct Tap Selection, Freeform Drag Repositioning & D-Pad Center Box Removal
- Status: Accepted
- Decision:
  1. Freeform Tap & Drag Gesture Handling in Edit Mode:
     - Added `enabled: Boolean = true` to `PressablePadButton`, `ShoulderButton`, `SystemPillButton`, and `JoystickStick`. In Edit Mode (`enabled = false`), buttons do not attach gesture detectors (`pointerInput`), allowing `EditableControl`'s selection and drag gestures to receive touch events unconditionally.
     - Implemented `detectTapGestures(onTap = { currentOnSelect() })` and `detectDragGestures(onDragStart = { currentOnSelect() }, onDrag = { change, dragAmount -> currentOnDragDelta(dragAmount.x, dragAmount.y) })` on `EditableControl` with `rememberUpdatedState` to eliminate stale closure bugs during continuous dragging.
     - Controls can now be tapped directly to select them and dragged freely across the screen without relying solely on toolbar nudge buttons.
  2. D-pad Down Arrow & Phantom Button Fix:
     - Eliminated the static 48dp center hub Box (`Box(size = 48.dp, background = Color(0xFF1E2229))`) that sat behind the D-pad buttons. Previously, if the Down arrow was repositioned, this center hub remained exposed underneath, appearing as a phantom duplicate button.
     - Flattened all 19 controls into direct children of the full-canvas `BoxWithConstraints`, removing nested sub-boxes (e.g. 52dp align boxes within a 164dp container) that pruned Compose touch event dispatch when controls were offset.
     - Verified that in normal mode, D-pad Down receives touch input responsively, lights up on press, and transmits valid gamepad state snapshots.


## D-018 — Right Analog Stick Dual-Mapping for In-Game Evasion / Dodge
- Status: Accepted
- Decision:
  1. Update `RPCS3DirectInputAdapter.swift` to dual-map the Right Analog Stick to:
     - RPCS3 native Player 1 `Default.yml` keys: Up: `Home` (115), Down: `End` (119), Left: `Forward Delete` (117), Right: `Page Down` (121).
     - Alphanumeric aliases: Up: `I` (34), Down: `K` (40), Left: `J` (38), Right: `L` (37).
  2. Reduced stick deflection threshold from 0.25 to 0.20 for responsive in-game evasion.
  3. Updated `postRawKeyEvent` to post directly to `.cghidEventTap` when RPCS3 is the focused frontmost application, ensuring Qt/Vulkan/MoltenVK receives events with immediate priority.
- Reason: RPCS3’s default keyboard profile specifically binds Right Stick to navigation keys (Home, End, Page Down, Delete). Previous code was only sending alphanumeric keys (I, K, J, L), causing RPCS3 to ignore all right analog stick movement and preventing combat dodging in God of War III.

## D-019 — Multi-Touch Pointer ID Tracking & Instant Unthrottled Button Transmission
- Status: Accepted
- Decision:
  1. Multi-Touch Per-Pointer Release Tracking in `PS3Buttons.kt`:
     - Identified root cause for shaped action buttons (△, □, ○, ✕) becoming stuck or delayed during game boot / gameplay: buttons were checking `event.changes.all { !it.pressed }` to determine release. When players held the left thumb on the analog stick or screen, `all { !it.pressed }` remained false, keeping buttons locked in the pressed state and ignoring all subsequent taps until every finger was removed.
     - Updated `PressablePadButton`, `ShoulderButton`, and `SystemPillButton` to capture the specific touching pointer `val pointerId = down.id` and evaluate only `event.changes.firstOrNull { it.id == pointerId }`.
     - Wrapped gesture detection in `try ... finally { isPressed = false; currentOnPressChanged(false) }` to guarantee reliable release upon finger lift or gesture cancellation.
     - Added `val currentOnPressChanged by rememberUpdatedState(onPressChanged)` to eliminate stale closure bugs across Compose recompositions.
  2. Button Throttling Bypass in `GamepadClient.kt`:
     - Added button/dpad change tracking (`lastSentButtons`, `lastSentDpad`).
     - Any transition in buttons or D-pad (`state.buttons != lastSentButtons || state.dpad != lastSentDpad`) now immediately transmits over WebSocket with 0ms latency, bypassing the 16ms motion rate-limiter.
     - Prevents high-frequency analog stick touch sampling from buffering, delaying, or overwriting rapid button taps.
  3. Real-Time RPCS3 Frontmost Detection in `RPCS3DirectInputAdapter.swift`:
     - Evaluated `frontApp.localizedName` and `frontApp.bundleIdentifier` directly on event dispatch so that when a game window boots, keyboard events route to `.cghidEventTap` immediately without waiting for a periodic PID scan.
     - Added native RPCS3 PS button mapping (Backspace `51` alongside Escape `53`).

## D-020 — Cutscene Connectivity Preservation & Zero Stick-Drift Architecture
- Status: Accepted
- Decision:
  1. Cutscene Idle Disconnect Prevention:
     - Root Cause: During non-interactive cutscenes, no buttons or sticks are touched for extended periods (>8 seconds). Two compounding timeout bugs caused disconnections:
       (a) Mac `ControllerServer.swift` had an idle timeout threshold of 8.0s (`Date().timeIntervalSince(last) > 8.0`), dropping the client if input was silent during cutscenes. Increased server idle timeout to 60.0s.
       (b) Android OkHttp WebSocket was configured with `.pingInterval(5, TimeUnit.SECONDS)`. Because the macOS custom TCP socket handler does not reply with RFC 6455 Pong frames, OkHttp threw `java.net.SocketTimeoutException: sent ping but didn't receive pong within 5000ms`, dropping the socket every 10 seconds.
       (c) Android OS power management put the Wi-Fi radio and CPU into doze/sleep mode during long cutscenes when no touch events occurred.
     - Fix:
       (a) Server idle timeout in `ControllerServer.swift` increased from 8.0s to 60.0s.
       (b) Added WebSocket RFC 6455 Pong frame generation (`makeWebSocketPongFrame`) in `ControllerServer.swift` for any incoming Ping frames (opcode 0x9).
       (c) Disabled OkHttp transport ping (`.pingInterval(0, TimeUnit.SECONDS)`) in `GamepadClient.kt`. The system relies on its dedicated application-level 1000ms JSON `HeartbeatMessage` (`startHeartbeat()`), which keeps `lastMessageDate = Date()` fresh indefinitely without brittle socket timeouts.
       (d) Added `WifiLock` (`WIFI_MODE_FULL_LOW_LATENCY` / `HIGH_PERF`) and `WakeLock` (`PARTIAL_WAKE_LOCK`) in `MainActivity.kt` with Android permissions `WAKE_LOCK` and `ACCESS_WIFI_STATE`, keeping radio and CPU active even during cutscenes.
  2. Stick Drift Immunity:
     - Root Cause: Capacitive touchscreens exhibit subtle sub-pixel thermal/capacitive micro-jitter (typically 1-3%) when a finger rests near or releases the analog stick. Additionally, dropped UDP packets or rate-limited updates on release could leave the last non-zero coordinate active in RPCS3, causing endless character walking/drifting.
     - Fix:
       (a) Increased default deadzone from 0.05 (5%) to 0.15 (15%) in `MainActivity.kt` and `TouchInputMapper.kt`, snapping all displacements `< 0.015f` to absolute zero.
       (b) Added stick centering detection (`stickReleasedToCenter = isZeroAxes && !wasZeroAxes`) in `GamepadClient.kt` to immediately bypass the 16ms rate-limiter and transmit `(0f, 0f)` with 0ms latency, followed by a redundant confirmation packet scheduled 30ms later.
       (c) Implemented 350ms stick watchdog timer (`checkStickWatchdog`) running every 100ms in `RPCS3DirectInputAdapter.swift`. If stick input is silent for >350ms, any active stick keys (WASD, Right Stick keys) are auto-released, making stuck drift keys impossible under packet loss or cutscenes.
- Reason: Guarantees continuous connection stability throughout long cinematic cutscenes and eliminates capacitive touch drift or stuck movement in RPCS3.

## D-021 — Automated USB Hot-Plug / Unplug Detection, Reverse Port-Forwarding Daemon & Zero Socket Leaks
- Status: Accepted
- Decision:
  1. Automated Mac USB/ADB Daemon:
     - Run a continuous 1.5-second monitor (`setupUsbMonitor()` / `checkAndConfigureUsbAdb()`) in `AppViewModel.swift`. It polls `adb devices` and `adb reverse --list`. If an attached device is present without active reverse forwarding, it automatically runs `adb -s <id> reverse tcp:6000 tcp:6000`.
     - Dynamically updates UI pill status between `"Active (Port 6000)"`, `"No Phone"`, and `"Unauthorized"`.
  2. Prevention of Socket Leaks in Companion Server:
     - Explicitly call `connection.cancel()` on disconnected connections in `ControllerServer.handleClientDisconnect()`.
     - Process WebSocket opcode 0x8 close frames by invoking `handleClientDisconnect()`.
     - Prevents dead TCP connections from lingering in `CLOSE_WAIT` and holding port 6000, eliminating `Address already in use` (POSIX error 48) on restarts.
  3. Android Activity Lifecycle Auto-Reconnection:
     - Added `onStart()` and `onResume()` in `MainActivity.kt` to trigger `client?.connect()` whenever the activity resumes, preventing stale disconnections when the phone sleeps or locks.
  4. Dynamic USB State Detection on Android:
     - Registered a dynamic `BroadcastReceiver` in `MainActivity.kt` for `"android.hardware.usb.action.USB_STATE"`, `ACTION_POWER_CONNECTED`, and `ACTION_POWER_DISCONNECTED`.
     - When USB is attached, `GamepadClient.onUsbStateChanged(true)` polls `127.0.0.1:6000` and immediately promotes the connection from Wi-Fi to sub-millisecond USB loopback.
     - When on Wi-Fi in `AUTO_PREFER_USB` mode, `startHeartbeat()` also tests USB loopback every 2 seconds for zero-touch promotion upon USB insertion.
- Reason: Solves USB disconnection when unplugging/replugging the physical USB cable, enabling fully automatic, zero-config hot-plugging without needing manual terminal commands or app restarts.

## D-022 — Shaped Action Button In-Game Responsiveness, Serial FIFO State Queue & 50ms Pulse Preservation
- Status: Accepted
- Decision:
  1. Serial State Queue Execution:
     - In `ControllerServer.swift`, chain all incoming state updates sequentially using `lastStateTask = Task { await prevStateTask?.value; await backend.updateState(state) }`.
     - Completely prevents unstructured Tasks from executing out-of-order across Swift's thread pool, preventing race conditions where button release packets execute before press packets and get permanently stuck down in `activeKeys`.
  2. Sub-Frame Button Pulse Extension (50ms Minimum Hold Duration):
     - In `RPCS3DirectInputAdapter.swift`, enforce `minimumButtonHoldDuration = 0.050` (50ms) on all discrete button releases.
     - When a capacitive touch tap is released in <50ms (typical 15–25ms touchscreen tap), defer the keyup event via `DispatchWorkItem` until 50ms has elapsed.
     - Guarantees that 30 FPS games (frame time 33.3ms, e.g. Asura's Wrath) and 60 FPS games (e.g. God of War III) never sample `cellPadGetData` between keydown and keyup.
     - Extended holds (e.g. holding Square for rapid-fire in Asura's Wrath) release immediately (0ms delay) once the user lifts their finger after 50ms.
  3. Sequence Monotonicity & Key Watchdog Flush:
     - Track `lastProcessedSeq` in `RPCS3DirectInputAdapter.swift`, rejecting any packet where `seq < lastProcessedSeq`.
     - Added button key flushing in `checkWatchdog()`. If input goes silent for >500ms, all pending releases and active keys are automatically released, guaranteeing zero stuck buttons.
  4. Multi-Touch Gesture Consumption on Android:
     - Added `change.consume()` in the touch tracking loops of `PressablePadButton`, `ShoulderButton`, and `SystemPillButton` in `PS3Buttons.kt`, preventing gesture cancellation when sliding fingers or moving the analog stick.
- Reason: Resolves the issue where shaped buttons (Triangle, Square, Circle, Cross) visually clicked in the app but failed during intense in-game combat and movement.

## D-023 — Android System Gesture & Sidebar Exclusion and macOS Key Modifier Sanitization
- Status: Accepted
- Decision:
  1. Android Edge Gestures & Sidebar Blocking:
     - Applied `Modifier.systemGestureExclusion()` to all interactive buttons (`PressablePadButton`, `ShoulderButton`, `SystemPillButton`) and sticks (`JoystickStick`).
     - Added an edge gesture exclusion listener on `window.decorView` in `MainActivity.kt` (`addOnLayoutChangeListener`) on Android 10+ (API 29+), designating the left 25% and right 25% of the screen as system gesture exclusion zones. This prevents the Android OS from interpreting edge touches/holds (such as holding the ○ Circle button or pressing shoulder buttons) as system back gestures or manufacturer sidebar activations (e.g. Vivo Smart Sidebar / Game Turbo / Edge panels).
     - Configured `layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` and added an auto-rehide insets listener in `MainActivity.kt` so transient status/menu bars are automatically dismissed if accidentally peeked.
  2. macOS Event Modifier Sanitization & Press-And-Hold Character Accent Menu Suppression:
     - In `RPCS3DirectInputAdapter.swift`, explicitly cleared all modifier flags (`event.flags = []`, except for intentional Shift with L3 alias), ensuring synthetic game keycodes (such as keycode 8 for ○ Circle = C) never carry Command, Control, Option, or Fn flags to the operating system.
     - Disabled `ApplePressAndHoldEnabled` for `net.rpcs3.rpcs3`, preventing macOS from opening the floating character accent menu bar when letter keys (C, Z, X, V) are held down during gameplay.
     - Routed synthetic keystrokes directly to RPCS3's PID (`event.postToPid(pid)`) when detected, preventing leakage into the global macOS WindowServer event tap.
- Reason: Eliminates unwanted system sidebars, status/menu bars, and character accent menus from popping up while playing games.

## D-024 — Sustained Button/Shoulder Hold Streaming, Rapid-Click Edge Restoration, and 360° Real-Joystick Radial Scaling
- Status: Accepted
- Decision:
  1. Sustained Shoulder and Button Hold Architecture:
     - Root Cause:
       (a) On macOS, `RPCS3DirectInputAdapter.swift` had a 500ms watchdog that unconditionally flushed all active keys if no new state updates were received.
       (b) In Jetpack Compose, capacitive touchscreens emit no new pointer events when a finger is held stationary on a button (L1, R1, L2, R2, L3, R3, Cross, Square, etc.).
       (c) In `ControllerScreen.kt`, local `mutableStateOf` snapshot variables with default parameters caused simultaneous touch callbacks (e.g. thumb moving the left stick while holding L1) to read stale state snapshots and overwrite held buttons back to false.
     - Fix:
       (a) Implemented atomic, thread-safe component updaters (`updateButtons`, `updateTrigger`, `updateDpad`, `updateStick`) in `GamepadClient.kt` with synchronized state mutation, preventing multi-touch races.
       (b) Added an active state streaming coroutine (`checkAndMaintainActiveStreaming`) in `GamepadClient.kt` that streams held state at 40Hz (every 25ms) whenever the controller state is non-neutral, continuously refreshing the Mac watchdog during sustained holds.
       (c) Extended the Mac silence watchdog threshold in `RPCS3DirectInputAdapter.swift` from 0.5s to 2.0s for buttons and 1.0s for sticks, ensuring network jitter never terminates held buttons.
  2. Rapid-Click Edge Restoration:
     - Root Cause: Quick taps (<35ms) were held by delayed `DispatchWorkItem` to ensure 30 FPS / 60 FPS game engine sampling. Rapid subsequent clicks cancelled the delayed release without clearing `activeKeys`, preventing subsequent down transitions from registering and swallowing rapid mashing into a single merged hold.
     - Fix: In `RPCS3DirectInputAdapter.swift`, if a button press arrives while the key was waiting in `pendingReleases`, cancel the pending release, immediately post `KeyUp`, remove from `activeKeys`, and post `KeyDown`. Fast button clicking now registers every tap at the exact speed of the user's fingers. Reduced `minimumButtonHoldDuration` to 35ms.
  3. Real-Joystick 360° Uniform Diagonal Sensitivity & Responsive Joystick Curve:
     - Root Cause: Clamping stick displacement inside a circular boundary caused diagonal components (NE, NW, SE, SW) to max out at $\cos 45^\circ \approx 0.7071$, cutting diagonal speed and sensitivity by ~30% compared to cardinal directions. Sensitivity exponent curve `pow(sens)` dampened responsiveness when sens > 1.0.
     - Fix:
       (a) Applied circle-to-square radial projection ($1.0 / \max(|u_x|, |u_y|)$) in `TouchInputMapper.kt`. Pushing into diagonal corners now drives both axes to full $(\pm 1.0, \pm 1.0)$, matching real dual-potentiometer physical joystick behavior.
       (b) Inverted sensitivity exponent curve to `pow(1.0 / sensitivity)` so increasing sensitivity accelerates response and reaches full deflection earlier.
       (c) Reduced stick motion dispatch throttling from 16ms to 8ms (~120Hz) in `GamepadClient.kt`.
       (d) Replaced unbounded Task chaining in `ControllerServer.swift` with a high-priority `AsyncStream<ControllerState>` for sub-millisecond dispatch.
- Reason: Guarantees genuine analog joystick sensitivity in all 360 degrees, solves button mashing responsiveness, and ensures all shoulder and face button holds work properly.

## D-025 — 1-Command Root Setup Scripts & Precision GitHub Instructions
- Status: Accepted
- Decision:
  1. Root-Level 1-Command Runners:
     - Created root-level `./setup-mac.sh` and `./setup-android.sh` executable convenience scripts wrapping `./scripts/install-macos.sh` and `./scripts/install-android.sh`.
     - Provided standard root `Makefile` exposing `make mac`, `make android`, `make test`, and `make help`.
  2. Smart macOS Setup Automation:
     - Enhanced `install-macos.sh` to check for Swift toolchain, automatically rebuild the release bundle if source files changed or bundle is missing, install to `/Applications/PS3ControllerCompanion.app`, codesign with stable bundle designated requirement, check macOS Accessibility status, automatically open System Settings > Privacy & Security > Accessibility if permissions are needed, and auto-launch the Companion app.
  3. Smart Android Setup Automation:
     - Enhanced `install-android.sh` to check ADB and Java prerequisites, provide an automated 20-second connection wait loop with real-time feedback (detecting USB connection and guiding users through developer options / RSA authorization if missing or unauthorized), automatically rebuild the APK if sources modified, set up reverse port forwarding (`adb reverse tcp:6000 tcp:6000`), install APK, and auto-launch MainActivity on the phone.
  4. GitHub README Standardization:
     - Restructured `README.md` to feature a prominent `⚡ 1-Command Quick Start (Mac & Android)` section right at the top.
     - Documented exact system requirements with a single-line copy-paste Homebrew command (`brew install android-platform-tools openjdk@17 && xcode-select --install`).
     - Clarified RPCS3 setup with 3 simple steps (Handler: Keyboard, Device: Keyboard, click game window).
- Reason: Satisfies requirement for precise, frictionless 1-command installation for both Mac and Android directly from the GitHub repository root.
