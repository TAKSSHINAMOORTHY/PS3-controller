import Foundation

public protocol GamepadBackend: AnyObject, Sendable {
    var isConnected: Bool { get }
    var deviceName: String { get }
    func connect() async throws
    func updateState(_ state: ControllerState) async throws
    func releaseAll() async
    func disconnect() async
}

public final class FakeGamepadBackend: GamepadBackend, @unchecked Sendable {
    public var isConnected: Bool = false
    public var deviceName: String = "Fake PS3 Gamepad"
    public var latestState: ControllerState?
    public var connectCallCount: Int = 0
    public var disconnectCallCount: Int = 0
    public var releaseAllCallCount: Int = 0
    public var stateUpdateCount: Int = 0

    private let lock = NSLock()

    public init() {}

    public func connect() async throws {
        lock.withLock {
            connectCallCount += 1
            isConnected = true
        }
    }

    public func updateState(_ state: ControllerState) async throws {
        lock.withLock {
            latestState = state
            stateUpdateCount += 1
        }
    }

    public func releaseAll() async {
        lock.withLock {
            releaseAllCallCount += 1
            if let current = latestState {
                latestState = ControllerState.neutral(sessionId: current.sessionId, seq: current.seq + 1)
            }
        }
    }

    public func disconnect() async {
        lock.withLock {
            disconnectCallCount += 1
            isConnected = false
        }
    }
}
