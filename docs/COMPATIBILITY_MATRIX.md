# Compatibility Matrix

Status at scaffold creation: **NOT TESTED**. Update only from actual evidence.

| Item | Target / candidate | Evidence required | Status |
|---|---|---|---|
| Mac hardware | Apple Silicon, MacBook Pro M5, 16 GB RAM | `sysctl -n machdep.cpu.brand_string`, `sysctl hw.memsize` | VERIFIED (Apple M5, 16 GB RAM) |
| macOS | macOS 27.0.1 (Build 26A434) | `sw_vers` output | VERIFIED (macOS 27.0.1 Build 26A434) |
| RPCS3 | Latest/recent build; record exact build | RPCS3 CLI/About | VERIFIED (RPCS3 0.0.43-20336-bd179ffd Alpha) |
| RPCS3 handler | SDL if available | Pads configuration screenshot/log | VERIFIED (SDL configured in Default.yml) |
| OpenJoystickDriver | Candidate only | Official docs, license, support matrix, supported programmatic input API | NOT INSTALLED (needs investigation) |
| CoreOrbits GamerPad | Candidate | Installed app / dext status | VERIFIED INSTALLED & ENABLED (`com.coreorbits.gamerpad.macos` dext 1.2.0/12) |
| IOHIDUserDevice | Candidate (Apple IOKit) | Test harness C/Swift program | NOT TESTED (Phase 1 Spike) |
| Android device | User's Android phone | Model, Android version, app build | NOT RECORDED |
| Wi-Fi transport | WebSocket candidate | Connect, stream, disconnect, recovery test | NOT TESTED |
| Genuine virtual gamepad | Required | RPCS3 sees device and receives button/axis events from test source | BLOCKING (Phase 1 Gate) |
| USB transport | Optional later | End-to-end test on actual devices | DEFERRED |

## Evidence record template

- Date/time:
- macOS version/build:
- CPU architecture:
- RPCS3 version:
- RPCS3 input handler:
- Virtual backend and version:
- Backend API used:
- Device name shown to RPCS3:
- Buttons tested:
- Axes/triggers tested:
- Disconnect neutralization:
- Test command/app:
- Result: PASS / FAIL / BLOCKED / NOT RUN
- Logs/screenshots location (sanitize private data):
- Notes:

Never mark the genuine gamepad requirement PASS if the implementation sends keyboard or mouse events.
