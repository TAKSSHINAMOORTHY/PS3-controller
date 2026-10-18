# Controller Communication Protocol Specification (v1.0)

## Overview
This document specifies the network wire protocol between the **Android PS3 Controller App** (client) and the **macOS Companion App** (server) over local Wi-Fi.

The primary transport is **WebSocket over TCP** (local network interface only).

---

## 1. Network Transport & Addressing
- **Transport**: WebSocket (RFC 6455) over TCP.
- **Default Port**: `6000` (configurable in app settings).
- **Binding**: The Mac companion listens strictly on private/local network interfaces (e.g. `192.168.x.x` or `127.0.0.1`), never on public endpoints.
- **Frame Size Limit**: Maximum 4,096 bytes per message frame. Any message exceeding this limit must be rejected, and the connection terminated.

---

## 2. Session Lifecycle & Handshake

### 2.1 Pairing / Session Handshake
1. **Client -> Server (`pair_request`)**:
```json
{
  "type": "pair_request",
  "client_name": "Android Controller",
  "client_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "protocol_version": 1,
  "pairing_code": "123456"
}
```

2. **Server -> Client (`pair_response`)**:
```json
{
  "type": "pair_response",
  "status": "accepted",
  "session_id": "9dc1cf91-f3cc-4982-9063-cf0efef6a242",
  "heartbeat_interval_ms": 1000,
  "max_rate_hz": 60
}
```
If rejected (e.g., invalid code or unsupported version):
```json
{
  "type": "pair_response",
  "status": "rejected",
  "error": "invalid_pairing_code"
}
```

---

## 3. Streaming Controller State

### 3.1 Controller State Snapshot (`state`)
Sent periodically (recommended: 30–60 Hz while interacting) or upon state change.

```json
{
  "type": "state",
  "session_id": "9dc1cf91-f3cc-4982-9063-cf0efef6a242",
  "seq": 4201,
  "timestamp": 1729281000123,
  "buttons": {
    "cross": true,
    "circle": false,
    "square": false,
    "triangle": false,
    "l1": false,
    "r1": false,
    "l2": false,
    "r2": false,
    "l3": false,
    "r3": false,
    "start": false,
    "select": false,
    "ps": false
  },
  "dpad": {
    "up": false,
    "down": false,
    "left": false,
    "right": false
  },
  "axes": {
    "lx": 0.0,
    "ly": 0.0,
    "rx": 0.75,
    "ry": -0.25
  },
  "triggers": {
    "l2": 0.0,
    "r2": 0.85
  }
}
```

### 3.2 Field Specifications
| Field | Type | Domain / Constraints | Description |
|---|---|---|---|
| `type` | String | Fixed: `"state"` | Packet identifier |
| `session_id` | String | Active UUID from handshake | Validates authenticated session |
| `seq` | UInt32 | Increments by 1 | Rejects out-of-order and stale packets |
| `timestamp` | Int64 | Milliseconds since epoch | Latency and jitter measurements |
| `buttons.*` | Boolean | `true` (pressed) / `false` (released) | Digital buttons (Cross, Circle, etc.) |
| `dpad.*` | Boolean | `true` (pressed) / `false` (released) | Directional pad buttons |
| `axes.lx`, `ly` | Float | `[-1.0, 1.0]` (0.0 = centered) | Left analog stick normalized coordinates |
| `axes.rx`, `ry` | Float | `[-1.0, 1.0]` (0.0 = centered) | Right analog stick normalized coordinates |
| `triggers.l2`, `r2` | Float | `[0.0, 1.0]` (0.0 = resting) | Analog trigger travel distance |

---

## 4. Heartbeat & Liveness
- **Rate**: Client sends a heartbeat if no input changes occur for > 500ms.
- **Heartbeat Packet (`heartbeat`)**:
```json
{
  "type": "heartbeat",
  "session_id": "9dc1cf91-f3cc-4982-9063-cf0efef6a242",
  "seq": 4202,
  "timestamp": 1729281001123
}
```
- **Timeout Window**: 1,000ms. If the server receives no packet within 1,000ms, it marks the session timed out.

---

## 5. Fail-Safe Neutral State & Disconnection
1. **On Disconnect or Timeout**:
   - The Mac companion **must immediately release all virtual inputs**:
     - All digital buttons -> `false`
     - All D-pad buttons -> `false`
     - All stick axes -> `0.0`
     - All triggers -> `0.0`
   - This prevents "stuck inputs" in RPCS3 games when walking out of Wi-Fi range or killing the mobile app.
2. **Explicit Disconnect (`disconnect`)**:
```json
{
  "type": "disconnect",
  "session_id": "9dc1cf91-f3cc-4982-9063-cf0efef6a242",
  "reason": "user_exit"
}
```

---

## 6. Security & Threat Model
- Local Wi-Fi subnet only: Companion server rejects non-private IP addresses.
- Pairing verification: Unpaired connections cannot stream state.
- Bounded input buffers: Rejects oversized messages (> 4KB).
- Sequence monotonic checks: Replay attacks and out-of-order stale packets are discarded.
- No telemetry, no external accounts, no cloud relays.
