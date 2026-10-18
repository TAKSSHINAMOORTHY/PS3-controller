# Test Plan

## Unit tests
- Protocol encode/decode round trip.
- Reject malformed, oversized, unsupported-version, and out-of-range messages.
- Reject stale sequence numbers.
- Button press and release transitions.
- Axis normalization, dead zones, and trigger normalization.
- Disconnect/timeout releases all controls.

## Mac integration tests
- WebSocket pairing and reconnect.
- Receiver validation and neutral-state recovery.
- Fake backend receives the expected states.
- Real backend can set and clear at least one button and axis.
- RPCS3 enumerates the virtual gamepad and receives input.

## Android tests
- Multitouch button combinations.
- Stick tracking and return-to-center.
- UI rotation/resizing behavior.
- Network disconnect/reconnect.
- Settings persistence.
- Protocol payload validation.

## End-to-end/manual tests
- Every PS3 control maps correctly.
- Diagonal stick movement.
- Both triggers and both sticks simultaneously.
- Rapid repeated presses.
- App background/foreground.
- Wi-Fi loss and restoration.
- Mac sleep/wake.
- Android app termination while buttons are held.
- 60-minute sustained session.
- RPCS3 restart and controller reconnection.

## Release checks
- Android build, lint, and tests.
- Swift build and XCTest.
- `git diff --check`.
- Dependency/license review.
- Secret scan.
- Verify clean clone/build instructions.
- No unreported failed or skipped tests.
