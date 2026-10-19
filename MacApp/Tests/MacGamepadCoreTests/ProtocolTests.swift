import XCTest
@testable import MacGamepadCore

final class ProtocolTests: XCTestCase {

    func testControllerStateEncodingDecoding() throws {
        let buttons = ControllerButtons(cross: true, circle: false, square: false, triangle: true, l1: true)
        let dpad = ControllerDPad(up: true, down: false, left: false, right: false)
        let axes = ControllerAxes(lx: -0.75, ly: 0.5, rx: 1.0, ry: -1.0)
        let triggers = ControllerTriggers(l2: 0.8, r2: 0.0)

        let original = ControllerState(
            sessionId: "test-session-123",
            seq: 42,
            timestamp: 1698000000000,
            buttons: buttons,
            dpad: dpad,
            axes: axes,
            triggers: triggers
        )

        let encoder = JSONEncoder()
        let data = try encoder.encode(original)

        let decoder = JSONDecoder()
        let decoded = try decoder.decode(ControllerState.self, from: data)

        XCTAssertEqual(decoded.type, "state")
        XCTAssertEqual(decoded.sessionId, "test-session-123")
        XCTAssertEqual(decoded.seq, 42)
        XCTAssertEqual(decoded.timestamp, 1698000000000)
        XCTAssertEqual(decoded.buttons.cross, true)
        XCTAssertEqual(decoded.buttons.triangle, true)
        XCTAssertEqual(decoded.buttons.circle, false)
        XCTAssertEqual(decoded.buttons.l1, true)
        XCTAssertEqual(decoded.dpad.up, true)
        XCTAssertEqual(decoded.axes.lx, -0.75)
        XCTAssertEqual(decoded.axes.ly, 0.5)
        XCTAssertEqual(decoded.triggers.l2, 0.8)
        XCTAssertEqual(decoded.triggers.r2, 0.0)
    }

    func testPairRequestResponseEncodingDecoding() throws {
        let request = PairRequest(clientName: "Pixel 8 Pro", clientId: "android-dev-01", pairingCode: "1234")
        let reqData = try JSONEncoder().encode(request)
        let decodedReq = try JSONDecoder().decode(PairRequest.self, from: reqData)
        XCTAssertEqual(decodedReq.type, "pair_request")
        XCTAssertEqual(decodedReq.clientName, "Pixel 8 Pro")
        XCTAssertEqual(decodedReq.clientId, "android-dev-01")
        XCTAssertEqual(decodedReq.protocolVersion, 1)

        let response = PairResponse(status: "accepted", sessionId: "sess-abc", heartbeatIntervalMs: 1000, maxRateHz: 60)
        let respData = try JSONEncoder().encode(response)
        let decodedResp = try JSONDecoder().decode(PairResponse.self, from: respData)
        XCTAssertEqual(decodedResp.type, "pair_response")
        XCTAssertEqual(decodedResp.status, "accepted")
        XCTAssertEqual(decodedResp.sessionId, "sess-abc")
        XCTAssertEqual(decodedResp.heartbeatIntervalMs, 1000)
        XCTAssertEqual(decodedResp.maxRateHz, 60)
    }

    func testHeartbeatEncodingDecoding() throws {
        let heartbeat = HeartbeatMessage(sessionId: "sess-abc", seq: 100, timestamp: 123456789)
        let data = try JSONEncoder().encode(heartbeat)
        let decoded = try JSONDecoder().decode(HeartbeatMessage.self, from: data)
        XCTAssertEqual(decoded.type, "heartbeat")
        XCTAssertEqual(decoded.sessionId, "sess-abc")
        XCTAssertEqual(decoded.seq, 100)
        XCTAssertEqual(decoded.timestamp, 123456789)
    }

    func testProtocolValidatorSizeEnforcement() {
        let validator = ProtocolValidator()
        let validData = Data(count: 4096)
        XCTAssertNoThrow(try validator.validateSize(validData))

        let oversizedData = Data(count: 4097)
        XCTAssertThrowsError(try validator.validateSize(oversizedData)) { error in
            XCTAssertEqual(error as? ValidationError, ValidationError.payloadTooLarge(4097))
        }
    }

    func testProtocolValidatorSequenceEnforcement() throws {
        let validator = ProtocolValidator(sessionId: "sess-1")

        var state1 = ControllerState(sessionId: "sess-1", seq: 1)
        XCTAssertNoThrow(try validator.validateAndNormalizeState(&state1))

        var state2 = ControllerState(sessionId: "sess-1", seq: 2)
        XCTAssertNoThrow(try validator.validateAndNormalizeState(&state2))

        var staleState = ControllerState(sessionId: "sess-1", seq: 2)
        XCTAssertThrowsError(try validator.validateAndNormalizeState(&staleState)) { error in
            XCTAssertEqual(error as? ValidationError, ValidationError.staleSequenceNumber(current: 2, received: 2))
        }

        var oldState = ControllerState(sessionId: "sess-1", seq: 1)
        XCTAssertThrowsError(try validator.validateAndNormalizeState(&oldState)) { error in
            XCTAssertEqual(error as? ValidationError, ValidationError.staleSequenceNumber(current: 2, received: 1))
        }
    }

    func testProtocolValidatorSessionValidation() {
        let validator = ProtocolValidator(sessionId: "sess-authorized")

        var validSessionState = ControllerState(sessionId: "sess-authorized", seq: 1)
        XCTAssertNoThrow(try validator.validateAndNormalizeState(&validSessionState))

        var rogueSessionState = ControllerState(sessionId: "sess-rogue", seq: 2)
        XCTAssertThrowsError(try validator.validateAndNormalizeState(&rogueSessionState)) { error in
            XCTAssertEqual(error as? ValidationError, ValidationError.unauthorizedSession("sess-rogue"))
        }
    }

    func testProtocolValidatorClamping() throws {
        let validator = ProtocolValidator(sessionId: "sess-clamp")

        var outOfBoundsState = ControllerState(
            sessionId: "sess-clamp",
            seq: 1,
            axes: ControllerAxes(lx: -2.5, ly: 3.14, rx: 1.0, ry: -0.5),
            triggers: ControllerTriggers(l2: -0.5, r2: 1.8)
        )

        try validator.validateAndNormalizeState(&outOfBoundsState)

        XCTAssertEqual(outOfBoundsState.axes.lx, -1.0)
        XCTAssertEqual(outOfBoundsState.axes.ly, 1.0)
        XCTAssertEqual(outOfBoundsState.axes.rx, 1.0)
        XCTAssertEqual(outOfBoundsState.axes.ry, -0.5)
        XCTAssertEqual(outOfBoundsState.triggers.l2, 0.0)
        XCTAssertEqual(outOfBoundsState.triggers.r2, 1.0)
    }

    func testNeutralStateGeneration() {
        let neutral = ControllerState.neutral(sessionId: "sess-neutral", seq: 5)
        XCTAssertEqual(neutral.type, "state")
        XCTAssertEqual(neutral.sessionId, "sess-neutral")
        XCTAssertEqual(neutral.seq, 5)
        XCTAssertFalse(neutral.buttons.cross)
        XCTAssertFalse(neutral.buttons.circle)
        XCTAssertFalse(neutral.buttons.start)
        XCTAssertFalse(neutral.dpad.up)
        XCTAssertEqual(neutral.axes.lx, 0.0)
        XCTAssertEqual(neutral.axes.ly, 0.0)
        XCTAssertEqual(neutral.triggers.l2, 0.0)
        XCTAssertEqual(neutral.triggers.r2, 0.0)
    }

    func testFakeGamepadBackendLifecycle() async throws {
        let backend = FakeGamepadBackend()
        XCTAssertFalse(backend.isConnected)
        XCTAssertEqual(backend.deviceName, "Fake PS3 Gamepad")

        try await backend.connect()
        XCTAssertTrue(backend.isConnected)
        XCTAssertEqual(backend.connectCallCount, 1)

        let state = ControllerState(sessionId: "sess-fake", seq: 10, buttons: ControllerButtons(cross: true))
        try await backend.updateState(state)
        XCTAssertEqual(backend.stateUpdateCount, 1)
        XCTAssertEqual(backend.latestState?.buttons.cross, true)

        await backend.releaseAll()
        XCTAssertEqual(backend.releaseAllCallCount, 1)
        XCTAssertEqual(backend.latestState?.buttons.cross, false)
        XCTAssertEqual(backend.latestState?.seq, 11)

        await backend.disconnect()
        XCTAssertFalse(backend.isConnected)
        XCTAssertEqual(backend.disconnectCallCount, 1)
    }

    func testSimultaneousAnalogSticksState() throws {
        let validator = ProtocolValidator(sessionId: "sess-both-sticks")
        var state = ControllerState(
            sessionId: "sess-both-sticks",
            seq: 1,
            axes: ControllerAxes(lx: -0.8, ly: 0.9, rx: 0.5, ry: -0.7)
        )

        try validator.validateAndNormalizeState(&state)

        XCTAssertEqual(state.axes.lx, -0.8, accuracy: 0.001)
        XCTAssertEqual(state.axes.ly, 0.9, accuracy: 0.001)
        XCTAssertEqual(state.axes.rx, 0.5, accuracy: 0.001)
        XCTAssertEqual(state.axes.ry, -0.7, accuracy: 0.001)

        // Serialize and deserialize to ensure both sticks survive the wire
        let data = try JSONEncoder().encode(state)
        let decoded = try JSONDecoder().decode(ControllerState.self, from: data)

        XCTAssertEqual(decoded.axes.lx, -0.8, accuracy: 0.001)
        XCTAssertEqual(decoded.axes.ly, 0.9, accuracy: 0.001)
        XCTAssertEqual(decoded.axes.rx, 0.5, accuracy: 0.001)
        XCTAssertEqual(decoded.axes.ry, -0.7, accuracy: 0.001)
    }

    func testRPCS3DirectInputAdapterLifecycleAndMappings() async throws {
        let adapter = RPCS3DirectInputAdapter()
        XCTAssertFalse(adapter.isConnected)

        try await adapter.connect()
        XCTAssertTrue(adapter.isConnected)

        // Test updating with left stick, right stick, face buttons and triggers
        let activeState = ControllerState(
            sessionId: "sess-direct",
            seq: 1,
            buttons: ControllerButtons(cross: true, circle: true, square: true, triangle: true, l1: true, r1: true, l3: true, r3: true, start: true, select: true, ps: true),
            dpad: ControllerDPad(up: true, down: false, left: true, right: false),
            axes: ControllerAxes(lx: 0.9, ly: -0.9, rx: -0.8, ry: 0.8),
            triggers: ControllerTriggers(l2: 1.0, r2: 1.0)
        )

        try await adapter.updateState(activeState)

        // Test neutral release
        await adapter.releaseAll()

        await adapter.disconnect()
        XCTAssertFalse(adapter.isConnected)
    }
}
