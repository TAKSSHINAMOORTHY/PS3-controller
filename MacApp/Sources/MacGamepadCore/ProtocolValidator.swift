import Foundation

public enum ValidationError: Error, Equatable, Sendable {
    case payloadTooLarge(Int)
    case unauthorizedSession(String)
    case staleSequenceNumber(current: UInt32, received: UInt32)
    case invalidAxisValue(Float)
    case invalidTriggerValue(Float)
    case decodingError(String)
}

public final class ProtocolValidator: @unchecked Sendable {
    public static let maxPayloadSize = 4096

    private var activeSessionId: String?
    private var lastSequenceNumber: UInt32 = 0
    private let lock = NSLock()

    public init(sessionId: String? = nil) {
        self.activeSessionId = sessionId
    }

    public func setSessionId(_ sessionId: String) {
        lock.lock()
        defer { lock.unlock() }
        self.activeSessionId = sessionId
        self.lastSequenceNumber = 0
    }

    public func validateSize(_ data: Data) throws {
        if data.count > Self.maxPayloadSize {
            throw ValidationError.payloadTooLarge(data.count)
        }
    }

    public func validateAndNormalizeState(_ state: inout ControllerState) throws {
        lock.lock()
        defer { lock.unlock() }

        if let expectedSession = activeSessionId, !expectedSession.isEmpty {
            guard state.sessionId == expectedSession else {
                throw ValidationError.unauthorizedSession(state.sessionId)
            }
        }

        guard state.seq > lastSequenceNumber else {
            throw ValidationError.staleSequenceNumber(current: lastSequenceNumber, received: state.seq)
        }

        lastSequenceNumber = state.seq

        // Normalize / Clamp axes to [-1.0, 1.0]
        state.axes.lx = min(max(state.axes.lx, -1.0), 1.0)
        state.axes.ly = min(max(state.axes.ly, -1.0), 1.0)
        state.axes.rx = min(max(state.axes.rx, -1.0), 1.0)
        state.axes.ry = min(max(state.axes.ry, -1.0), 1.0)

        // Normalize / Clamp triggers to [0.0, 1.0]
        state.triggers.l2 = min(max(state.triggers.l2, 0.0), 1.0)
        state.triggers.r2 = min(max(state.triggers.r2, 0.0), 1.0)
    }

    public func reset() {
        lock.lock()
        defer { lock.unlock() }
        self.activeSessionId = nil
        self.lastSequenceNumber = 0
    }
}
