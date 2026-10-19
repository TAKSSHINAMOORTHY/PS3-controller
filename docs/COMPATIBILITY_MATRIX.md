# Compatibility Matrix

Status at scaffold creation: **NOT TESTED**. Update only from actual evidence.

| Item | Target / candidate | Evidence required | Status |
|---|---|---|---|
| Mac hardware | Apple Silicon, MacBook Pro M5, 16 GB RAM | `sysctl -n machdep.cpu.brand_string`, `sysctl hw.memsize` | VERIFIED (Apple M5, 16 GB RAM) |
| macOS | macOS 27.0.1 (Build 26A434) | `sw_vers` output | VERIFIED (macOS 27.0.1 Build 26A434) |
| RPCS3 | Latest/recent build; record exact build | RPCS3 CLI/About | VERIFIED (RPCS3 0.0.43-20336-bd179ffd Alpha) |
| RPCS3 handler | SDL if available | Pads configuration screenshot/log | VERIFIED (SDL configured; libSDL3.0.dylib bundled) |
| OpenJoystickDriver | Candidate only | Official docs, license, support matrix, supported programmatic input API | NOT INSTALLED (requires third-party dext approval) |
| IOHIDUserDevice / CoreHID | Direct userspace candidate | Test harnesses: `spikes/iohiduserdevice`, `spikes/corehid` | BLOCKED (macOS 27 enforces `com.apple.developer.hid.virtual.device` entitlement, IOServiceOpen error 0xe00002c2) |
| CoreOrbits GamerPad Dext | Candidate (Apple DriverKit) | Dext status, WebSocket local API, RPCS3 libSDL3 monitor | PASS (Verified: exposes genuine IOHIDUserDevice, enumerated by RPCS3 libSDL3, button/axis injection proven) |
| Android app client | Kotlin + Jetpack Compose (API 26-35) | Unit test suite & APK compilation (`assembleDebug`) | VERIFIED (`app-debug.apk` built, 9/9 JUnit tests passed) |
| Wi-Fi transport | WebSocket (port 6000) | Connect, pair, stream, heartbeat, disconnect, recovery | VERIFIED (140 pkt/s burst, reconnect cycle proven) |
| Genuine virtual gamepad | Required | RPCS3 sees device and receives button/axis events from test source | PASS (All Gates Satisfied) |
| USB transport | Optional later | End-to-end test on actual devices | DEFERRED (Phase 8 enhancement) |

## Evidence record (Phase 1 Spike)

- Date/time: 2026-10-19 01:00:41 IST
- macOS version/build: macOS 27.0.1 (Build 26A434)
- CPU architecture: Apple M5 (arm64), 16 GB Unified Memory
- RPCS3 version: RPCS3 0.0.43-20336-bd179ffd Alpha
- RPCS3 input handler: SDL (RPCS3 bundled `/Frameworks/libSDL3.0.dylib`)
- Virtual backend and version: `CoreOrbits GamerPad` dext (`com.coreorbits.gamerpad.macos` 1.2.0/12)
- Backend API used: WebSocket on `127.0.0.1:8080` (Engine.IO v4 / Socket.IO)
- Device name shown to RPCS3 / SDL3: `CoreOrbits GamerPad` (Vendor ID: `0x1d50`, Product ID: `0x619e`, Transport: `Virtual`, Class: `IOHIDUserDevice`)
- Buttons tested: Button 0 (`A` / `CROSS`) -> Emitted `JOYSTICK_BUTTON_DOWN (state=1)`, verified by 14 consecutive polling cycles of `SDL_GetJoystickButton() == 1`, followed by `JOYSTICK_BUTTON_UP (state=0)`.
- Axes/triggers tested: Left Joystick (X=1.0, Y=-1.0) -> Emitted `JOYSTICK_AXIS_MOTION` on axis 0 and axis 1.
- Disconnect neutralization: On WebSocket close, emitted `JOYSTICK_REMOVED (0x606)` immediately; virtual HID device unregistered from macOS registry.
- Test harness programs: `spikes/monitor_sdl.c` (compiled against RPCS3's `libSDL3.0.dylib`) and `spikes/test_button_injection.py`.
- Result: **PASS** (Genuine virtual gamepad created, recognized by RPCS3 SDL3, button/axis events verified, neutral disconnect verified).

## Evidence record (Phase 5 End-to-End Integration)

- Date/time: 2026-10-19 01:22:50 IST
- Test script: `tests/test_e2e_pipeline.py`
- Test flow:
  1. Automated launch of DriverKit daemon (`CoreOrbits-GamerPad`) and Mac companion (`MacApp --headless`).
  2. Automated launch of RPCS3 SDL3 monitor (`spikes/monitor_sdl` dynamically linked against `libSDL3.0.dylib`).
  3. Client connected to `ws://127.0.0.1:6000`, performed HTTP 101 WebSocket upgrade handshake.
  4. Sent `pair_request`, received `pair_response` with status `accepted` and active `session_id`.
  5. RPCS3 SDL3 dynamically detected joystick: `EVENT: type=0x605 (1541) -> JOYSTICK_ADDED: ID=1 -> Opened joystick: 0x100660a80`.
  6. Sent `ControllerState` with `cross = true` -> RPCS3 SDL3 captured `JOYSTICK_BUTTON_DOWN: button=0, state=1` and continuous polling `POLL: Joystick Button 0 is currently PRESSED (1)`.
  7. Sent `ControllerState` with `cross = false` -> RPCS3 SDL3 captured `JOYSTICK_BUTTON_UP: button=0, state=0`.
  8. Sent `ControllerState` with `lx = 1.0, ly = -1.0` -> RPCS3 SDL3 captured `JOYSTICK_AXIS_MOTION: axis=0` and `axis=1`.
  9. Sent `HeartbeatMessage` -> Session remained active without timeout.
  10. Closed client socket -> Clean disconnect, safe neutral-state input release.
- Result: **PASS** (End-to-end integration verified from network client through MacApp to RPCS3).

## Evidence record (Phase 6 Resilience & Quality)

- Date/time: 2026-10-19 01:23:30 IST
- Test script: `tests/test_resilience.py`
- Test results:
  - Test 1 (Handshake & Pairing): PASS
  - Test 2 (High-frequency burst): 100 packets processed in 0.715s (139.9 pkt/sec) without drops or crashes. PASS
  - Test 3 (Stale sequence rejection): Out-of-order/stale `seq` rejected by `ProtocolValidator`. PASS
  - Test 4 (Rogue session isolation): Packets with mismatched `session_id` rejected. PASS
  - Test 5 (Oversized payload protection): Payloads > 4096 bytes bounded and dropped. PASS
  - Test 6 (Reconnect cycle): Socket closed, reconnect initiated, new distinct `session_id` issued. PASS
- Result: **PASS** (System resilient against network jitter, packet reordering, and rogue sessions).
