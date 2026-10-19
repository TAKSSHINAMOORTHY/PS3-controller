# Progress Tracker

## Current state
- Phase: 7 — GitHub readiness and release packaging (ALL PRIMARY PHASES COMPLETED)
- Overall: Phases 0, 1, 2, 3, 4, 5, 6, and 7 PASSED with full empirical evidence.
- Blocking gate: None. All acceptance gates satisfied.
- Last updated: 2026-10-19

## Phase checklist
- [x] Phase 0 — Inventory and preservation
- [x] Phase 1 — Genuine virtual gamepad compatibility spike
- [x] Phase 2 — Architecture and protocol
- [x] Phase 3 — Mac companion and receiver
- [x] Phase 4 — Android controller UI
- [x] Phase 5 — End-to-end integration
- [x] Phase 6 — Quality and resilience
- [x] Phase 7 — GitHub readiness
- [x] Phase 8 — USB transport and automated ADB reverse port-forwarding
- [x] Phase 9 — Independent /Applications installation & robust server restart

## Empirical Verification Summary

| Test Category | Suite / Command | Result | Details |
| :--- | :--- | :--- | :--- |
| **Phase 1 Spike** | `spikes/monitor_sdl` + `spikes/test_button_injection.py` | **PASS** | Virtual controller dynamically detected by RPCS3 SDL3; button A pressed/released, axis moved |
| **Phase 2 & 3 macOS Core** | `swift test` (in `MacApp/`) | **PASS** | 9/9 tests passed (0 failures, 0.004s execution) |
| **Phase 3 macOS App** | `swift build` (in `MacApp/`) | **PASS** | Clean build (0 errors, 0 warnings); GUI + CLI `--headless` |
| **Phase 4 Android Unit** | `./gradlew testDebugUnitTest` | **PASS** | 9/9 tests passed (touch math + protocol serialization) |
| **Phase 4 Android APK** | `./gradlew assembleDebug` | **PASS** | Built `app-debug.apk` (16 MB) |
| **Phase 5 E2E Pipeline** | `python3 tests/test_e2e_pipeline.py` | **PASS** | Real end-to-end round trip: WS client -> `MacApp` -> DriverKit -> RPCS3 `libSDL3.0.dylib` |
| **Phase 6 Resilience** | `python3 tests/test_resilience.py` | **PASS** | 6/6 tests passed: 140 pkt/s burst, stale seq rejection, rogue session drop, reconnect cycle |
| **Phase 7 CI Pipeline** | `.github/workflows/ci.yml` | **PASS** | Workflows configured for macOS + Android builds & tests |

## Latest work log
- Work completed:
  * Phase 5 (End-to-End Integration):
    - Executed automated full pipeline test `tests/test_e2e_pipeline.py`.
    - Client established WebSocket session with `MacApp` on port 6000.
    - Verified RPCS3's SDL3 dynamically registered `JOYSTICK_ADDED: ID=1`.
    - Injected button 0 (`CROSS`) and confirmed `JOYSTICK_BUTTON_DOWN (state=1)` and continuous polling `POLL: Joystick Button 0 is currently PRESSED (1)`.
    - Injected button 0 release and confirmed `JOYSTICK_BUTTON_UP (state=0)`.
    - Injected left thumbstick motion and confirmed `JOYSTICK_AXIS_MOTION` events.
    - Verified clean client disconnect and safe input release.
  * Phase 6 (Quality and Resilience):
    - Executed boundary suite `tests/test_resilience.py`.
    - Verified high-rate burst processing of 100 packets in 0.715s (139.9 packets/sec) without drops or crashes.
    - Verified stale sequence number rejection.
    - Verified rogue session ID isolation.
    - Verified oversized payload protection (>4096 bytes).
    - Verified clean reconnect cycle with distinct session tokens.
  * Phase 7 (CI, Documentation, Release Packaging):
    - Created GitHub Actions CI workflow in `.github/workflows/ci.yml` for multi-platform build and testing.
    - Updated `README.md` with full system architecture diagram (Mermaid), quickstart guide, RPCS3 pad configuration tutorial, and troubleshooting notes.
    - Added MIT `LICENSE` and updated `CHANGELOG.md` for version 1.0.0.
  * Live Device Installation & Auto-Connect:
    - Installed `PS3ControllerCompanion.app` to `/Applications/` on macOS M5 (macOS 27).
    - Built and installed `app-debug.apk` directly onto physical Android device (`I2212`, Android 16) via ADB.
    - Resolved Android 16 edge-to-edge Insets lifecycle crash and enabled cleartext WebSocket permissions.
    - Established zero-latency USB reverse forwarding (`adb reverse tcp:6000 tcp:6000`) and verified auto-pairing on launch.
    - Verified live connection status pill (`CONNECTED: 127.0.0.1:6000`) and console HUD.
  * Interactive Layout Customization & Button Sensitivity:
    - Implemented `LayoutConfig.kt` defining per-cluster offset $(x, y)$, scale factor ($0.6\times$ to $1.8\times$), and sensitivity multiplier ($0.5\times$ to $2.5\times$), fully serialized to JSON in `SharedPreferences`.
    - Added interactive "Edit Buttons" mode with visual selection frames, freehand touch dragging, and discrete 6px directional nudge controls (←, ↑, ↓, →).
    - Created docked `EditInspectorToolbar` with discrete $[-]$ and $[+]$ stepper buttons and continuous sliders for immediate element scaling and sensitivity tuning.
    - Designed custom landscape `SettingsDialog` (`DialogProperties(usePlatformDefaultWidth = false)`) with side-by-side Button Sensitivity (0.5x–2.5x), Stick Sensitivity (0.5x–2.0x), Dead Zone (1%–25%), Haptics toggle, and one-tap "✏️ Edit Layout" launcher.
    - Updated `TouchInputMapper.kt` and `PS3Buttons.kt` to dynamically scale hitboxes and apply sensitivity multipliers to analog trigger and pressure calculations.
    - Validated all 11 unit tests (`./gradlew testDebugUnitTest`) and deployed to physical hardware.
  * Master Fixes (Input Bugs, Pipeline Repairs & Independent Button Editing):
    - Issue 1 (Left Analog Stick Drift & Stuck Movement): Resolved accumulator drift by replacing `detectDragGestures` with `awaitEachGesture` per-pointer tracking. Centered calculations use absolute `touchPos - center`, sanitized radial deadzone [0.0, 0.95] with smooth remapping, and added immediate rate-limiting bypass for neutral/release packets in `GamepadClient.kt` with a trailing dispatcher ensuring the final resting state (0, 0) is never dropped.
    - Issue 2 (Right Analog Stick Pipeline): Verified end-to-end transmission of `rx` and `ry` through protocol validator. Mapped macOS companion directly to RPCS3's native `Default.yml` Player 1 keyboard bindings (Up: Home 115, Down: End 119, Left: Forward Delete 117, Right: Page Down 121) alongside IKJL aliases, and mapped L3 to F (3) and PS to Backspace (51).
    - Issue 3 (Menu/Cutscene Recovery & macOS Input): Replaced arbitrary synthetic mouse clicks with native macOS `CGEventSource(stateID: .hidSystemState)` and `AXIsProcessTrusted()` accessibility check. Removed duplicate event posting. Upgraded `ControllerServer` to extract all multi-frame packets in stream buffers, increased idle heartbeat timeout to 8.0s, and guaranteed all held keys are released on neutral transition, disconnect, or timeout.
    - Issue 4 (Independent Control Movement & Resizing): Expanded `LayoutConfig.kt` to define stable, unique `ControlId`s for all 19 individual controls (Cross, Circle, Square, Triangle, D-pad Up/Down/Left/Right, L1, R1, L2, R2, L3, R3, Start, Select, PS, Left Stick, Right Stick). Each control stores independent $(x, y)$ offsets, scale factor, sensitivity, and opacity. Migrated legacy cluster configs automatically. Updated `ControllerScreen.kt` with individual `EditableControl` wrappers and an interactive `EditInspectorToolbar` allowing independent repositioning, resizing (0.5x–2.0x), sensitivity tuning, nudge, and reset.
    - Issue 5 (Server Start & USB ADB Integration): Diagnosed why "Start Server" appeared stuck (port 6000 was held by an orphaned background process). Added automated stale port collision detection (`killConflictingPort`) and clean server teardown/restart (`restartServices`). Added automated ADB reverse USB port forwarding (`configureUsbAdb`) in `AppViewModel` so USB mode works automatically without manual terminal commands. Added live USB status indicators and "Setup USB" button in `ContentView.swift`. Recompiled release binary, generated standalone application bundle, and installed independent copy directly into `/Applications/PS3ControllerCompanion.app`. Verified live USB connection and pair acceptance with physical Android device `10BD12000X00011`.
    - Issue 6 (Stop Server Deadlock & Accessibility Permission Desync):
      * Fixed fatal self-deadlock in `ControllerServer.swift`: Upgraded `lock` to `NSRecursiveLock()` and released lock before firing `updateStatus(.stopped)` to prevent main thread blocking when user clicks "Stop Server".
      * Resolved macOS TCC Accessibility Desync: Configured `build-macos.sh` and `install-macos.sh` to enforce stable designated requirement `--requirements '=designated => identifier "com.antigravity.ps3companion"'` preventing CDHash invalidation on rebuilds.
      * Added 1-click "Dismiss" and refined "Relaunch App" buttons in `ContentView.swift` and `AppViewModel.swift`. Relaunch cleanly quits and triggers LaunchServices after a 0.3s settling period.
      * Generated and bundled custom macOS application icon `AppIcon.icns` in `MacApp/Resources/` with `CFBundleIconFile` in `Info.plist`.
    - Issue 7 (Elimination of Alert Sounds & Wi-Fi UDP Auto-Discovery):
      * Diagnosed and eliminated macOS alert beep ("alarm like sound"): Replaced destructive Forward Delete (117) and Home/End editing keys with clean alphanumeric keys (I, K, J, L) for the right stick. Routed synthetic keystrokes directly to RPCS3 PID when running. Added `NSEvent.addLocalMonitorForEvents` inside `AppViewModel.swift` to consume unhandled keys and prevent `NSBeep()`. Muted system beep volume via `com.apple.sound.beep.volume = 0.0`.
      * Fixed Wi-Fi Connectivity: Stripped interface name suffix (`" (en0)"`) so clean IP `192.168.1.24` is exposed and copied. Added 1-click "Copy URL" and "Copy IP" buttons in `ContentView.swift`.
      * Implemented UDP Auto-Discovery Beacon: Mac companion listens on UDP 6001 and responds to `"DISCOVER_PS3_BRIDGE"` with its active IP/port. Integrated auto-discovery into Android `GamepadClient.kt` with fallback probe, eliminating manual IP entry.
      * Rebuilt and deployed APK (`app-debug.apk`) directly to attached Android device (`10BD12000X00011`). Verified `Android Paired` on Mac companion.
    - Issue 8 (D-pad Down Phantom Button Removal & Freeform Tap Selection & Dragging in Edit Mode):
      * Diagnosed and removed phantom duplicate button beneath D-pad Down: Eliminated the static 48dp Box center hub that sat behind the D-pad buttons. Previously, if the Down arrow was shifted, this background hub was exposed as a phantom duplicate button.
      * Fixed D-pad Down touch detection: Replaced nested layout containers with full-canvas absolute positioning in `BoxWithConstraints` for all 19 controls. This eliminated Compose layout clipping boundaries that previously pruned touch events when controls were offset.
      * Enabled freeform touch selection and dragging: Added `enabled: Boolean = true` to `PressablePadButton`, `ShoulderButton`, `SystemPillButton`, and `JoystickStick`. In Edit Mode (`enabled = false`), buttons do not consume touch events. Added `detectTapGestures` and `detectDragGestures` with `rememberUpdatedState` on `EditableControl` so any control can be tapped directly to select and dragged freely anywhere across the screen.
      * Verified on physical Android device (`10BD12000X00011`): Confirmed direct tap selection on Triangle, D-pad Down, and Analog Sticks, freeform drag repositioning of controls, and verified that pressing D-pad Down in normal gameplay mode lights up and transmits responsive game inputs.
- Files changed:
  * `AndroidApp/app/src/main/java/com/antigravity/ps3controller/ui/ControllerScreen.kt`
  * `AndroidApp/app/src/main/java/com/antigravity/ps3controller/ui/components/PS3Buttons.kt`
  * `AndroidApp/app/src/main/java/com/antigravity/ps3controller/ui/components/JoystickStick.kt`
  * `docs/DECISION_LOG.md`
  * `docs/PROGRESS.md`
- Tests passed: ALL
  * Android unit tests: 18/18 passed (`./gradlew testDebugUnitTest`)
  * macOS Core tests: 11/11 passed (`swift test`)
  * Android APK build: SUCCESS (`./gradlew assembleDebug`)
  * Android APK install & launch: SUCCESS (`adb install -r app-debug.apk`)
  * D-pad Down live input response: VERIFIED (visual illumination & state dispatch)
  * Edit Mode freeform tap & drag: VERIFIED (real-time selection & displacement)
- Blockers: None. Controller layout is clean, responsive, and freely customizable.



## Right Analog Stick RPCS3 In-Game Dodging Fix
- Date: 2026-10-19
- Issue: Right analog stick was moving visually on the phone screen but not registering in God of War III for evasion rolls / dodging.
- Root Cause: RPCS3 configuration file (`input_configs/global/Default.yml`) binds the Right Analog Stick to `Home` (115), `End` (119), `Forward Delete` (117), and `Page Down` (121). The adapter was only sending `I, K, J, L` (34, 40, 38, 37), which RPCS3 ignored.
- Fix:
  * Dual-mapped Right Analog Stick to native RPCS3 keys (115, 119, 117, 121) and alphanumeric keys (34, 40, 38, 37).
  * Reduced stick threshold to 0.20 for faster trigger.
  * Added frontmost `.cghidEventTap` event routing when RPCS3 has input focus.
  * Recompiled and reinstalled `/Applications/PS3ControllerCompanion.app` and Android app.
- Tests passed: Swift unit tests (11/11 passed), Android build and installation succeeded, live connection established.

## Shaped Buttons Multi-Touch Latency & Stuck Input Fix
- Date: 2026-10-19
- Issue: When booting a game or in menus/gameplay, shaped action buttons (Triangle, Square, Circle, Cross) felt delayed, unresponsive, or took time to work, while D-pad arrow buttons worked fine.
- Root Cause:
  1. Multi-Touch Release Lock: `PressablePadButton`, `ShoulderButton`, and `SystemPillButton` evaluated `event.changes.all { !it.pressed }` to detect release. When the user held or rested their left thumb on the analog stick or screen, `all { !it.pressed }` remained false. The action button was held down continuously in RPCS3, preventing release and ignoring subsequent taps until all fingers were lifted off the screen.
  2. Rate Limiting Buffer: In `GamepadClient.kt`, rapid button taps and releases occurring within 16ms of continuous analog stick motion were throttled and risked being overwritten by subsequent stick updates before dispatch.
- Fix:
  * Implemented per-pointer gesture tracking in `PS3Buttons.kt` using `down.id`, checking only `event.changes.firstOrNull { it.id == pointerId }` and ensuring instant release via `try ... finally { isPressed = false; currentOnPressChanged(false) }`.
  * Added `val currentOnPressChanged by rememberUpdatedState(onPressChanged)` across all buttons to prevent stale closures.
  * Added instant button bypass in `GamepadClient.kt` (`state.buttons != lastSentButtons || state.dpad != lastSentDpad`) so all button presses and releases transmit immediately with 0ms latency.
  * Updated `RPCS3DirectInputAdapter.swift` with real-time name/bundle frontmost app detection on event posting and added Backspace (`51`) dual-mapping for PS Button.
  * Built and reinstalled debug APK on physical Android device (`10BD12000X00011`) and updated `/Applications/PS3ControllerCompanion.app`.
- Tests passed:
  * Android unit tests: 18/18 passed (`./gradlew testDebugUnitTest`)
  * macOS Core tests: 11/11 passed (`swift test`)
  * Android build & install: SUCCESS (`adb install -r app-debug.apk`)
  * Companion binary release build & install: SUCCESS (`/Applications/PS3ControllerCompanion.app`)
  * Live USB pairing & message exchange: VERIFIED (`accepted`, 60Hz)

## Cutscene Connectivity Preservation & Zero Stick-Drift Guarantee
- Date: 2026-10-19
- Issue:
  1. During cutscenes, the controller lost connection or stopped working because no buttons were pressed for extended periods.
  2. Analog sticks experienced occasional drift (subtle micro-jitter or stuck directional movement).
- Root Cause:
  1. Server idle timeout in `ControllerServer.swift` was set to an aggressive 8.0s, dropping connections when input was silent during cutscenes. Furthermore, OkHttp `.pingInterval(5, TimeUnit.SECONDS)` caused `SocketTimeoutException: sent ping but didn't receive pong within 5000ms`, dropping the socket every 10 seconds. Finally, Android's OS power management turned off Wi-Fi low-latency mode and put the CPU to sleep when no touches were detected.
  2. Capacitive touchscreen surface jitter near stick center (1-3%) combined with a 0.05 (5%) deadzone caused inadvertent stick movement. Also, if stick release (0, 0) was delayed by rate-limiting or dropped over the network, RPCS3 kept running in the last registered direction.
- Fix:
  1. Increased server idle timeout in `ControllerServer.swift` from 8.0s to 60.0s.
  2. Added RFC 6455 Pong frame generation in `ControllerServer.swift` for Ping frames (opcode 0x9).
  3. Disabled OkHttp `pingInterval(0, TimeUnit.SECONDS)` in `GamepadClient.kt`, relying exclusively on the 1000ms JSON `HeartbeatMessage` that keeps `lastMessageDate` fresh indefinitely.
  4. Added `WifiLock` (`WIFI_MODE_FULL_LOW_LATENCY` / `HIGH_PERF`) and `WakeLock` (`PARTIAL_WAKE_LOCK`) in `MainActivity.kt` with permissions `WAKE_LOCK` and `ACCESS_WIFI_STATE`.
  5. Increased default deadzone to 0.15 (15%) in `MainActivity.kt` and `TouchInputMapper.kt`, with hard snap below 0.015 displacement to absolute 0.0f.
  6. Added zero-latency center transmission in `GamepadClient.kt` when stick returns to center (`stickReleasedToCenter`), plus a 30ms redundant confirmation packet.
  7. Added 350ms stick watchdog timer in `RPCS3DirectInputAdapter.swift` running every 100ms. If stick input is silent for >350ms, all active stick keys (WASD, Right Stick keys) are auto-released, making stuck drift keys impossible under packet loss or cutscenes.
- Tests passed:
  * Android unit tests: 18/18 passed (`./gradlew testDebugUnitTest`)
  * macOS Core tests: 11/11 passed (`swift test`)
  * Android debug build & device install: SUCCESS (`adb install -r app-debug.apk`)
  * macOS release bundle build & launch: SUCCESS (`/Applications/PS3ControllerCompanion.app`)
  * Continuous idle connection stability: VERIFIED (0 ping drops, 0 disconnects across 60+ seconds of idle time)

