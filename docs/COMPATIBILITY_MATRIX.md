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
| Android device | User's Android phone | Model, Android version, app build | NOT RECORDED (to be paired in Phase 5) |
| Wi-Fi transport | WebSocket candidate | Connect, stream, disconnect, recovery test | VERIFIED on local loopback |
| Genuine virtual gamepad | Required | RPCS3 sees device and receives button/axis events from test source | PASS (Phase 1 Gate Satisfied) |
| USB transport | Optional later | End-to-end test on actual devices | DEFERRED |

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
- Next gate: Phase 2 Architecture and Protocol Specification.
