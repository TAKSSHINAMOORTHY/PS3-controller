# Changelog

All notable changes to this project will be documented in this file.

## [1.0.0] - 2026-10-19

### Added
- **Phase 0 (Inventory & Preservation)**:
  - Pre-spike backup created at `backups/2026-10-19_pre-spike/`.
  - Machine environment documented (macOS 27.0.1, Apple M5, Xcode 27.1, Swift 6.4, OpenJDK 17.0.17, Android SDK 35/36, RPCS3 0.0.43).
- **Phase 1 (Genuine Virtual Gamepad Spike)**:
  - Verified DriverKit virtual gamepad backend (`com.coreorbits.gamerpad.macos`).
  - Implemented C monitor dynamically linking to RPCS3's bundled `libSDL3.0.dylib` (`spikes/monitor_sdl.c`).
  - Verified dynamic detection of `CoreOrbits GamerPad`, button down/up, and analog axes in RPCS3's SDL3.
- **Phase 2 (Architecture & Protocol Specification)**:
  - Specified full network protocol in `docs/PROTOCOL.md` (session handshake, heartbeat, monotonic sequence tracking, 1000ms idle neutralizer).
  - Defined decoupled `GamepadBackend` protocol.
- **Phase 3 (macOS Companion & Receiver)**:
  - Implemented `MacGamepadCore` Swift package with `ProtocolModels`, `ProtocolValidator`, `ControllerServer`, and `DriverKitGamerPadAdapter`.
  - Implemented `MacApp` with modern SwiftUI diagnostic dashboard showing live PS3 telemetry and stick crosshairs.
  - Added headless CLI mode (`--headless`).
  - Added 9 unit tests in `MacGamepadCoreTests` (all passing).
- **Phase 4 (Android Controller App)**:
  - Built Kotlin + Jetpack Compose landscape controller app (`AndroidApp/`).
  - Implemented `TouchInputMapper` with dead zone filtering, circular clamping, and sensitivity curves.
  - Implemented `GamepadClient` with OkHttp WebSocket, pairing handshake, heartbeat, and 60Hz rate limiting.
  - Implemented DualShock UI: Dual draggable analog sticks, Action buttons (△, ○, ✕, □), D-Pad, L1/R1 bumpers, L2/R2 triggers, and System buttons.
  - Implemented `SettingsDialog` with persistence for IP, port, dead zone, and sensitivity.
  - Added 9 unit tests in `app/src/test` (all passing).
  - Built release artifact `app-debug.apk` (16 MB).
- **Phase 5 (End-to-End Integration)**:
  - Implemented `tests/test_e2e_pipeline.py` verifying the full pipeline: WebSocket client -> `MacApp` -> DriverKit -> RPCS3 `libSDL3.0.dylib`.
  - Empirically proved button 0 down/up and analog axis motion in RPCS3.
- **Phase 6 (Resilience & Boundary Testing)**:
  - Implemented `tests/test_resilience.py` testing high-rate packet bursts (140 pkt/s), stale sequence drops, rogue sessions, and disconnect recovery.
- **Phase 7 (CI & GitHub Readiness)**:
  - Configured GitHub Actions CI workflow in `.github/workflows/ci.yml`.
  - Updated `README.md` with architecture diagrams, quickstart guide, and RPCS3 pad mapping steps.
