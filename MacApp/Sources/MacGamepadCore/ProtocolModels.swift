import Foundation

public struct ControllerButtons: Codable, Equatable, Sendable {
    public var cross: Bool
    public var circle: Bool
    public var square: Bool
    public var triangle: Bool
    public var l1: Bool
    public var r1: Bool
    public var l2: Bool
    public var r2: Bool
    public var l3: Bool
    public var r3: Bool
    public var start: Bool
    public var select: Bool
    public var ps: Bool

    public init(
        cross: Bool = false,
        circle: Bool = false,
        square: Bool = false,
        triangle: Bool = false,
        l1: Bool = false,
        r1: Bool = false,
        l2: Bool = false,
        r2: Bool = false,
        l3: Bool = false,
        r3: Bool = false,
        start: Bool = false,
        select: Bool = false,
        ps: Bool = false
    ) {
        self.cross = cross
        self.circle = circle
        self.square = square
        self.triangle = triangle
        self.l1 = l1
        self.r1 = r1
        self.l2 = l2
        self.r2 = r2
        self.l3 = l3
        self.r3 = r3
        self.start = start
        self.select = select
        self.ps = ps
    }
}

public struct ControllerDPad: Codable, Equatable, Sendable {
    public var up: Bool
    public var down: Bool
    public var left: Bool
    public var right: Bool

    public init(up: Bool = false, down: Bool = false, left: Bool = false, right: Bool = false) {
        self.up = up
        self.down = down
        self.left = left
        self.right = right
    }
}

public struct ControllerAxes: Codable, Equatable, Sendable {
    public var lx: Float // [-1.0, 1.0]
    public var ly: Float // [-1.0, 1.0]
    public var rx: Float // [-1.0, 1.0]
    public var ry: Float // [-1.0, 1.0]

    public init(lx: Float = 0.0, ly: Float = 0.0, rx: Float = 0.0, ry: Float = 0.0) {
        self.lx = lx
        self.ly = ly
        self.rx = rx
        self.ry = ry
    }
}

public struct ControllerTriggers: Codable, Equatable, Sendable {
    public var l2: Float // [0.0, 1.0]
    public var r2: Float // [0.0, 1.0]

    public init(l2: Float = 0.0, r2: Float = 0.0) {
        self.l2 = l2
        self.r2 = r2
    }
}

public struct ControllerState: Codable, Equatable, Sendable {
    public var type: String
    public var sessionId: String
    public var seq: UInt32
    public var timestamp: Int64
    public var buttons: ControllerButtons
    public var dpad: ControllerDPad
    public var axes: ControllerAxes
    public var triggers: ControllerTriggers

    public enum CodingKeys: String, CodingKey {
        case type
        case sessionId = "session_id"
        case seq
        case timestamp
        case buttons
        case dpad
        case axes
        case triggers
    }

    public init(
        sessionId: String,
        seq: UInt32,
        timestamp: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        buttons: ControllerButtons = ControllerButtons(),
        dpad: ControllerDPad = ControllerDPad(),
        axes: ControllerAxes = ControllerAxes(),
        triggers: ControllerTriggers = ControllerTriggers()
    ) {
        self.type = "state"
        self.sessionId = sessionId
        self.seq = seq
        self.timestamp = timestamp
        self.buttons = buttons
        self.dpad = dpad
        self.axes = axes
        self.triggers = triggers
    }

    public static func neutral(sessionId: String = "", seq: UInt32 = 0) -> ControllerState {
        return ControllerState(
            sessionId: sessionId,
            seq: seq,
            timestamp: Int64(Date().timeIntervalSince1970 * 1000),
            buttons: ControllerButtons(),
            dpad: ControllerDPad(),
            axes: ControllerAxes(lx: 0.0, ly: 0.0, rx: 0.0, ry: 0.0),
            triggers: ControllerTriggers(l2: 0.0, r2: 0.0)
        )
    }
}

public struct PairRequest: Codable, Equatable, Sendable {
    public var type: String
    public var clientName: String
    public var clientId: String
    public var protocolVersion: Int
    public var pairingCode: String

    public enum CodingKeys: String, CodingKey {
        case type
        case clientName = "client_name"
        case clientId = "client_id"
        case protocolVersion = "protocol_version"
        case pairingCode = "pairing_code"
    }

    public init(clientName: String, clientId: String, protocolVersion: Int = 1, pairingCode: String) {
        self.type = "pair_request"
        self.clientName = clientName
        self.clientId = clientId
        self.protocolVersion = protocolVersion
        self.pairingCode = pairingCode
    }
}

public struct PairResponse: Codable, Equatable, Sendable {
    public var type: String
    public var status: String // "accepted" or "rejected"
    public var sessionId: String?
    public var heartbeatIntervalMs: Int?
    public var maxRateHz: Int?
    public var error: String?

    public enum CodingKeys: String, CodingKey {
        case type
        case status
        case sessionId = "session_id"
        case heartbeatIntervalMs = "heartbeat_interval_ms"
        case maxRateHz = "max_rate_hz"
        case error
    }

    public init(
        status: String,
        sessionId: String? = nil,
        heartbeatIntervalMs: Int? = 1000,
        maxRateHz: Int? = 60,
        error: String? = nil
    ) {
        self.type = "pair_response"
        self.status = status
        self.sessionId = sessionId
        self.heartbeatIntervalMs = heartbeatIntervalMs
        self.maxRateHz = maxRateHz
        self.error = error
    }
}

public struct HeartbeatMessage: Codable, Equatable, Sendable {
    public var type: String
    public var sessionId: String
    public var seq: UInt32
    public var timestamp: Int64

    public enum CodingKeys: String, CodingKey {
        case type
        case sessionId = "session_id"
        case seq
        case timestamp
    }

    public init(sessionId: String, seq: UInt32, timestamp: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) {
        self.type = "heartbeat"
        self.sessionId = sessionId
        self.seq = seq
        self.timestamp = timestamp
    }
}
