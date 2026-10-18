# Architecture

## Components

### Android app
- Kotlin + Jetpack Compose.
- `ControllerScreen`: landscape PS3-style UI.
- `ControllerState`: immutable current state for every button and axis.
- `TouchInputMapper`: multitouch-to-state mapping.
- `PairingClient`: pairing and connection status.
- `ControllerTransport`: WebSocket client abstraction.
- `SettingsRepository`: local preferences only.
- `DiagnosticsViewModel`: last send time, round-trip observations if measured, reconnect/error state.

### Mac companion
- Swift + SwiftUI.
- `PairingManager`: local pairing and peer authorization.
- `ControllerServer`: WebSocket listener bound to the local network interface, not exposed to the public internet.
- `ProtocolValidator`: schema, size, sequence, and session validation.
- `ControllerStateStore`: latest accepted state.
- `GamepadBackend`: abstraction for button/axis output.
- `DiagnosticsModel`: sanitized event and error reporting.

### Backend boundary

The backend exposes the `GamepadBackend` protocol:
```swift
public protocol GamepadBackend: AnyObject {
    var isConnected: Bool { get }
    var deviceName: String { get }
    func connect() async throws
    func updateState(_ state: ControllerState) async throws
    func releaseAll() async
    func disconnect() async
}
```

The concrete adapter (`DriverKitGamerPadAdapter`) translates incoming `ControllerState` snapshots into discrete and analog events for the local virtual gamepad daemon (`localhost:8080`), ensuring genuine virtual HID output without kernel panic risks. In testing/CI, `FakeGamepadBackend` simulates virtual device output for unit and regression tests.

See [`docs/PROTOCOL.md`](PROTOCOL.md) for full wire specification.

## Initial transport recommendation

Use WebSocket over local Wi-Fi for the first version because it is reliable, easy to inspect, and adequate for a controller state stream when implemented carefully. Send compact state snapshots, not only edge-triggered button events, so the receiver can recover after a dropped update. Add sequence numbers and heartbeat. Treat a lost connection as a command to neutralize all controls.

Benchmark latency on the actual phone, Wi-Fi network, Mac, backend, and RPCS3 build. Avoid claiming a fixed latency target until measured.

## Security and privacy
- Pair using a short-lived pairing code or QR-based token exchange.
- Authenticate subsequent sessions.
- Never listen on public/cloud endpoints.
- Rate-limit and size-limit incoming messages.
- Reject stale sequence numbers and invalid axis ranges.
- Store pairing secrets in platform secure storage where feasible.
- Never log secrets or publish local network details.
- Do not add analytics or telemetry.
