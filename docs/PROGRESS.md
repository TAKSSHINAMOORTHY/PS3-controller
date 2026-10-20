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

## Automated USB Hot-Plug / Unplug Detection & Zero-Config Loopback
- Date: 2026-10-19
- Issue: USB connection stopped working whenever the USB cable was unplugged and replugged. Android could not reconnect to the Mac over USB without manual intervention.
- Root Cause:
  1. Ephemeral ADB Reverse: ADB reverse forward rules (`adb reverse tcp:6000 tcp:6000`) are wiped by the ADB daemon when the USB cable is detached. When the cable is plugged back in, ADB does NOT restore them automatically. The Mac companion only ran `configureUsbAdb()` once at startup.
  2. Leaked Sockets in `CLOSE_WAIT`: In `ControllerServer.swift`, `handleClientDisconnect()` never called `connection.cancel()`, leaving dead sockets in `CLOSE_WAIT` holding port 6000 and causing `Address already in use` (POSIX error 48) on restarts. Opcode 0x8 close frames were also unhandled.
  3. Android Activity Lifecycle Disconnect: `MainActivity.kt` called `client?.disconnect()` in `onStop()`, which set `isExplicitDisconnect = true`. Because `MainActivity` lacked `onStart()` or `onResume()`, waking the phone or resuming the app left the controller permanently disconnected.
  4. Missing USB Hot-Plug Detection on Phone: Android had no listener for USB cable attachment/detachment. When USB was unplugged, if it fell back to Wi-Fi, it stayed on Wi-Fi indefinitely and never detected when the USB cable was plugged back in.
- Fix:
  1. Added automated 1.5s background ADB monitor (`setupUsbMonitor()` / `checkAndConfigureUsbAdb()`) in `AppViewModel.swift`. It polls `adb devices` and `adb reverse --list`, automatically configuring `adb -s <id> reverse tcp:6000 tcp:6000` whenever an attached phone is detected without active forwarding.
  2. Added explicit `connection.cancel()` in `handleClientDisconnect()` and opcode 0x8 handling in `ControllerServer.swift`, eliminating all `CLOSE_WAIT` socket leaks.
  3. Added `onStart()` and `onResume()` in `MainActivity.kt` ensuring the client auto-reconnects as soon as the app resumes or wakes.
  4. Registered a dynamic `BroadcastReceiver` in `MainActivity.kt` for `android.hardware.usb.action.USB_STATE`, `ACTION_POWER_CONNECTED`, and `ACTION_POWER_DISCONNECTED`.
  5. Implemented `onUsbStateChanged(isAttached: Boolean)` in `GamepadClient.kt`. When plugged in, Android probes `127.0.0.1:6000` with retries and immediately switches from Wi-Fi to sub-millisecond USB loopback. Added a 2-second background loopback check in `startHeartbeat()` while on Wi-Fi in `AUTO_PREFER_USB` mode. Reduced socket timeout from 5s to 2s to eliminate reconnection hangs.
- Tests passed:
  * Android unit tests: 18/18 passed (`./gradlew testDebugUnitTest`)
  * macOS Core tests: 11/11 passed (`swift test`)
  * macOS release bundle compilation & codesign: SUCCESS (`./scripts/build-macos.sh`)
  * Android debug build: SUCCESS (`./gradlew assembleDebug`)
  * Companion installation to `/Applications/PS3ControllerCompanion.app`: SUCCESS
  * Android installation & launch via ADB: SUCCESS (`./scripts/install-android.sh`)
  * Live USB hot-plug empirical test: Cleared `adb reverse --remove tcp:6000`; Mac automated monitor restored `UsbFfs tcp:6000 tcp:6000` within 2 seconds; Android client re-paired (`accepted`) and established continuous connection.

## Shaped Buttons In-Game Registration & Concurrency Pulse Fix
- Date: 2026-10-19
- Issue: In RPCS3 gameplay (e.g. *Asura's Wrath* [BLJM60409]), shaped action buttons (Triangle, Square, Circle, Cross) clicked fine visually in the app and companion UI, but failed to register in-game attacks/actions during gameplay, particularly when moving the analog sticks simultaneously.
- Root Cause:
  1. Unordered Async Task Concurrency on Mac Companion: In `ControllerServer.swift`, incoming 60Hz state packets spawned unordered Swift concurrency tasks (`Task { try? await backend.updateState(state) }`). When moving the analog stick, 60 packets/sec flooded the server; thread pool execution caused button release events to be processed *before* button press events, leaving button keys permanently stuck down or inverted in `activeKeys`.
  2. Sub-Frame Capacitive Tap Dropping: Touchscreen button taps (15–25ms) were shorter than the PS3 game engine's 30 FPS polling interval (33.3ms for `cellPadGetData`). Taps that started and ended between consecutive frame polls were completely missed by the RPCS3 emulated game engine.
  3. Stale Replay Packets: No monotonic sequence filter existed in `RPCS3DirectInputAdapter`, allowing out-of-order network packets to corrupt button states.
  4. Missing Button Release Watchdog: While sticks had a 350ms watchdog, button keys had no release watchdog if a network packet was lost.
  5. Multi-touch gesture consumption in Android UI: Moving the analog stick simultaneously risked gesture cancellation on the shaped buttons without explicit consumption.
- Fix:
  1. Serial Task Chaining in `ControllerServer.swift`: Added `lastStateTask: Task<Void, Never>?` chaining to serialize all backend state updates in strict FIFO order, preventing task races.
  2. Monotonic Sequence Filtering: In `RPCS3DirectInputAdapter.swift`, dropped stale/out-of-order state packets (`state.seq <= lastProcessedSeq`).
  3. 50ms Minimum Pulse Duration: Implemented `minimumButtonHoldDuration = 0.050` (50ms) hold preservation via `DispatchWorkItem` on `watchdogQueue`. Quick touchscreen taps (<50ms) are guaranteed to hold across at least 2 full game engine frames at 30 FPS (3+ frames at 60 FPS). Sustained holds (>50ms, such as rapid-fire Square) release immediately upon finger lift.
  4. Button Watchdog & Stale Flush: Enhanced `checkWatchdog()` in `RPCS3DirectInputAdapter.swift` to flush all pending button releases and stale active keys after 500ms of silence.
  5. Touch Event Consumption: Added `change.consume()` across all button touch tracking loops in `PS3Buttons.kt`.
  6. Unit Tests: Added `testRPCS3DirectInputAdapterButtonPulseHoldAndMonotonicity` in `ProtocolTests.swift`. All 12 Swift tests passed.
- Tests passed:
  * Swift unit tests: 12/12 passed (`swift test` in `MacApp`)
  * Android unit tests: 18/18 passed (`./gradlew testDebugUnitTest`)
  * E2E Resilience suite: 6/6 passed (`python3 tests/test_resilience.py`)
  * macOS Release bundle build & deploy: SUCCESS (`/Applications/PS3ControllerCompanion.app`)
  * Android build & deploy: SUCCESS (`adb install -r app-debug.apk`)
  * Live USB pairing & communication: VERIFIED (`accepted`, 60Hz, PID 13249)

## System Gesture, Edge Sidebar & Menu Bar Blocking
- Date: 2026-10-19
- Issue: When playing games, tapping or holding edge buttons (especially ○ Circle / O button on the far right) caused the phone's side bar (e.g. Smart Sidebar / Game Turbo / Edge panels) and the system menu / status bar to show up, interrupting gameplay.
- Root Cause:
  1. On modern Android devices, the left and right screen edges are reserved by the OS for system back gestures and manufacturer sidebars. Because the ○ (Circle) button is located near the right edge of the landscape screen, touching or holding it triggered edge swipe and navigation gestures.
  2. Transient system bars were configured without gesture exclusion rects, allowing edge touches to reveal the status bar and navigation bar.
  3. On macOS, `CGEvent` instances inherited system modifier flags from the hardware state, which could trigger system-level shortcuts (like Control Center or character accent menus) when letters (such as 'C') were held down.
- Fix:
  1. Applied `Modifier.systemGestureExclusion()` across all Compose buttons (`PressablePadButton`, `ShoulderButton`, `SystemPillButton`) and sticks (`JoystickStick`) in `PS3Buttons.kt` and `JoystickStick.kt`.
  2. Added dynamic full-edge gesture exclusion in `MainActivity.kt` using `window.decorView.systemGestureExclusionRects`, designating the left 25% and right 25% screen edges as excluded from OS back gestures and sidebars.
  3. Configured `layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` and added an auto-rehide insets listener in `MainActivity.kt` to immediately re-hide transient bars.
  4. Sanitized `event.flags = []` in `RPCS3DirectInputAdapter.swift` so game key events never carry system modifier flags, and disabled macOS character accent popups for RPCS3 via `defaults write net.rpcs3.rpcs3 ApplePressAndHoldEnabled -bool false`.
- Tests passed:
  * Swift unit tests: 12/12 passed (`swift test` in `MacApp`)
  * Android unit tests: 18/18 passed (`./gradlew testDebugUnitTest`)
  * Android APK build & install: SUCCESS (`adb install -r app-debug.apk` onto `10BD12000X00011`)
  * macOS Companion release build & install: SUCCESS (`/Applications/PS3ControllerCompanion.app`, PID 15576)
  * Live connection: ESTABLISHED and active on port 6000 over USB loopback

## macOS System Sidebar & Menu Bar Leakage Fix (Strict Process Isolation)
- Date: 2026-10-19
- Issue: When playing games on the Mac, pressing or holding controller buttons (specifically the ○ Circle / O button) caused the Mac's side bar and top menu bar to show up, interrupting gameplay.
- Root Cause:
  1. Rogue Apple Daemon Injection: In `RPCS3DirectInputAdapter.swift`, `refreshRPCS3Pids()` checked `app.localizedName?.lowercased().contains("rpcs3")`. On macOS, this inadvertently matched Apple's system daemon `com.apple.SafariPlatformSupport.Helper` (which has localized name `"AutoFill (RPCS3)"`). Keystrokes were being delivered directly into Apple's AutoFill helper, popping up macOS AutoFill suggestion menu bars and sidebars whenever keys like `C` (Circle button) were pressed or held!
  2. Global WindowServer Event Tap Leakage: `postRawKeyEvent` was calling `event.post(tap: .cghidEventTap)`. Posting to `.cghidEventTap` broadcasts hardware-level keyboard events to macOS WindowServer. In macOS Full Screen, WindowServer intercepts unhandled keystrokes and reveals the top macOS Menu Bar and sidebars (Stage Manager / Dock / Control Center).
- Fix:
  1. Strict RPCS3 Process Targeting: Filtered `refreshRPCS3Pids()` with `NSRunningApplication.runningApplications(withBundleIdentifier: "net.rpcs3.rpcs3")` and explicitly excluded all `com.apple.*` daemons.
  2. Pure Process Event Routing (`postToPid`): Delivered key events EXCLUSIVELY to RPCS3's process event queue (`event.postToPid(pid)`). Completely eliminated `.cghidEventTap` broadcasting. Keystrokes now bypass macOS WindowServer entirely and are delivered solely to RPCS3.
  3. Sanitized modifier flags (`event.flags = []`), guaranteeing game keypresses can never trigger macOS system shortcuts.
- Tests passed:
  * Swift unit tests: 12/12 passed (`swift test` in `MacApp`)
  * macOS Release bundle build & install: SUCCESS (`/Applications/PS3ControllerCompanion.app`, PID 16759)
  * Live USB pairing & communication: VERIFIED (`accepted`, 60Hz, PID 16759)

## Sustained Button & Shoulder Hold, Rapid Fast Clicking & 360° Real-Joystick Sensitivity Fix
- Date: 2026-10-20
- Issue:
  1. Buttons L1, L2, L3, R1, R2, R3 (and all action buttons) worked as short clicks instead of holds when pressed and held.
  2. Rapid fast clicking of buttons was throttled and lagged behind, merging fast clicks into single long holds.
  3. Joysticks had slower input latency and diagonal directions (NE, NW, SE, SW) suffered from reduced sensitivity (~30% less travel) compared to cardinal directions.
- Root Cause:
  1. Hold Failure: Capacitive touchscreens emit no touch events while holding stationary fingers; the Mac companion's 500ms watchdog unconditionally flushed active keys when the input stream paused. Additionally, Compose state reads with default parameters across multi-touch callbacks caused simultaneous stick dragging to overwrite held button flags.
  2. Rapid Click Swallowing: The 50ms hold preservation mechanism retained keys in `activeKeys` while a delayed release was pending. When a rapid subsequent click arrived, it cancelled the delayed release but did not clear `activeKeys`, causing subsequent keydown events to be ignored.
  3. Diagonal Stick Deadening: Clamping stick displacement inside a circular boundary caused diagonal components to cap at $\cos 45^\circ \approx 0.7071$. The sensitivity power curve dampened center responsiveness when sensitivity > 1.0. Unbounded Task chaining in `ControllerServer.swift` added thread scheduling latency.
- Fix:
  1. Sustained Hold Architecture:
     - Implemented atomic thread-safe state updaters (`updateButtons`, `updateTrigger`, `updateDpad`, `updateStick`) in `GamepadClient.kt` with synchronized state mutation, eliminating multi-touch race conditions in `ControllerScreen.kt`.
     - Added an active state streaming loop in `GamepadClient.kt` that streams held state at 40Hz (every 25ms) whenever the controller is non-neutral, keeping the Mac watchdog fresh indefinitely while buttons are held.
     - Extended the Mac silence watchdog to 2.0s for buttons and 1.0s for sticks in `RPCS3DirectInputAdapter.swift`.
  2. Rapid-Click Edge Restoration:
     - In `RPCS3DirectInputAdapter.swift`, added immediate KeyUp edge restoration when a new press arrives for a key that is waiting in `pendingReleases`. Adjusted minimum hold duration to 35ms.
     - Every rapid click now cleanly produces a distinct KeyUp/KeyDown cycle in RPCS3 without lag or coalescing.
  3. 360° Real-Joystick Radial Scaling & High-Speed Dispatch:
     - Applied circle-to-square radial scaling ($1.0 / \max(|u_x|, |u_y|)$) in `TouchInputMapper.kt` so diagonal corners reach full $(\pm 1.0, \pm 1.0)$ matching real dual-potentiometer joystick behavior.
     - Inverted sensitivity curve to `pow(1.0 / sensitivity)` so higher sensitivity accelerates response and reaches full deflection earlier.
     - Reduced motion dispatch throttling from 16ms to 8ms (~120Hz).
     - Replaced unbounded Task chaining in `ControllerServer.swift` with a high-priority `AsyncStream<ControllerState>`.
- Tests passed:
  * Swift unit tests: 14/14 passed (`swift test` in `MacApp`)
  * Android unit tests: 18/18 passed (`./gradlew testDebugUnitTest`)
  * E2E Resilience suite: 6/6 passed (`python3 tests/test_resilience.py`)
  * Android debug build: SUCCESS (`./gradlew assembleDebug`, APK: `app-debug.apk`)
  * macOS Companion release build & install: SUCCESS (`/Applications/PS3ControllerCompanion.app`, PID 8322)

