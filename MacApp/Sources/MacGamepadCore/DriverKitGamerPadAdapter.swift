import Foundation

public final class DriverKitGamerPadAdapter: GamepadBackend, @unchecked Sendable {
    public private(set) var isConnected: Bool = false
    public let deviceName: String = "CoreOrbits GamerPad (DriverKit)"

    private let host: String
    private let port: Int
    private var webSocketTask: URLSessionWebSocketTask?
    private var session: URLSession?
    private var previousState: ControllerState?
    private let lock = NSLock()

    public init(host: String = "127.0.0.1", port: Int = 8080) {
        self.host = host
        self.port = port
    }

    public func connect() async throws {
        guard let url = URL(string: "ws://\(host):\(port)/socket.io/?EIO=4&transport=websocket") else {
            throw URLError(.badURL)
        }

        let config = URLSessionConfiguration.default
        let session = URLSession(configuration: config)
        self.session = session

        let task = session.webSocketTask(with: url)
        self.webSocketTask = task
        task.resume()

        // Wait for Engine.IO open packet
        let initialMessage = try await task.receive()
        if case .string(let text) = initialMessage {
            // Engine.IO open packet starts with '0'
            guard text.hasPrefix("0") else {
                throw NSError(domain: "DriverKitGamerPadAdapter", code: 1, userInfo: [NSLocalizedDescriptionKey: "Unexpected Engine.IO handshake: \(text)"])
            }
        }

        // Send Socket.IO connect packet "40"
        try await task.send(.string("40"))

        // Receive server_info or connected ack
        _ = try await task.receive()

        // Send controller_info with compact button mapping
        let sdlMapping: [String: Int] = [
            "A": 0, "CROSS": 0,
            "B": 1, "CIRCLE": 1,
            "X": 2, "SQUARE": 2,
            "Y": 3, "TRIANGLE": 3,
            "L1": 4, "R1": 5,
            "L2": 6, "R2": 7,
            "SELECT": 8, "START": 9,
            "L3": 10, "R3": 11,
            "PS": 12
        ]

        let controllerInfoPayload: [Any] = [
            "controller_info",
            [
                "name": "PS3 Controller",
                "guid": "0300",
                "sdl_mapping": sdlMapping
            ] as [String: Any]
        ]

        let data = try JSONSerialization.data(withJSONObject: controllerInfoPayload, options: [])
        if let jsonStr = String(data: data, encoding: .utf8) {
            try await task.send(.string("42" + jsonStr))
        }

        lock.withLock {
            isConnected = true
            previousState = nil
        }
    }

    public func updateState(_ state: ControllerState) async throws {
        guard isConnected, let task = webSocketTask else { return }

        let prev = lock.withLock { () -> ControllerState? in
            let old = previousState
            previousState = state
            return old
        }

        // Diff buttons and send transitions
        try await diffAndSendButtons(prev: prev?.buttons, current: state.buttons, task: task)

        // Diff D-Pad and send transitions
        try await diffAndSendDPad(prev: prev?.dpad, current: state.dpad, task: task)

        // Update left joystick if changed significantly
        if prev?.axes.lx != state.axes.lx || prev?.axes.ly != state.axes.ly {
            let joyPayload: [Any] = ["left_joystick", ["x": state.axes.lx, "y": state.axes.ly]]
            if let str = jsonString(joyPayload) {
                try await task.send(.string("42" + str))
            }
        }

        // Update right joystick if changed significantly
        if prev?.axes.rx != state.axes.rx || prev?.axes.ry != state.axes.ry {
            let joyPayload: [Any] = ["right_joystick", ["x": state.axes.rx, "y": state.axes.ry]]
            if let str = jsonString(joyPayload) {
                try await task.send(.string("42" + str))
            }
        }

        // Update triggers
        if prev?.triggers.l2 != state.triggers.l2 {
            let trigPayload: [Any] = ["left_trigger", ["value": state.triggers.l2]]
            if let str = jsonString(trigPayload) {
                try await task.send(.string("42" + str))
            }
        }
        if prev?.triggers.r2 != state.triggers.r2 {
            let trigPayload: [Any] = ["right_trigger", ["value": state.triggers.r2]]
            if let str = jsonString(trigPayload) {
                try await task.send(.string("42" + str))
            }
        }
    }

    private func diffAndSendButtons(prev: ControllerButtons?, current: ControllerButtons, task: URLSessionWebSocketTask) async throws {
        let buttonsToCheck: [(keyPath: KeyPath<ControllerButtons, Bool>, id: String)] = [
            (\.cross, "CROSS"),
            (\.circle, "CIRCLE"),
            (\.square, "SQUARE"),
            (\.triangle, "TRIANGLE"),
            (\.l1, "L1"),
            (\.r1, "R1"),
            (\.l2, "L2"),
            (\.r2, "R2"),
            (\.l3, "L3"),
            (\.r3, "R3"),
            (\.start, "START"),
            (\.select, "SELECT"),
            (\.ps, "PS")
        ]

        for item in buttonsToCheck {
            let wasPressed = prev?[keyPath: item.keyPath] ?? false
            let isPressed = current[keyPath: item.keyPath]

            if wasPressed != isPressed {
                let eventName = isPressed ? "press_button" : "release_button"
                let payload: [Any] = [eventName, ["id": item.id]]
                if let str = jsonString(payload) {
                    try await task.send(.string("42" + str))
                }
            }
        }
    }

    private func diffAndSendDPad(prev: ControllerDPad?, current: ControllerDPad, task: URLSessionWebSocketTask) async throws {
        let directions: [(keyPath: KeyPath<ControllerDPad, Bool>, action: String)] = [
            (\.up, "up"),
            (\.down, "down"),
            (\.left, "left"),
            (\.right, "right")
        ]

        for item in directions {
            let wasActive = prev?[keyPath: item.keyPath] ?? false
            let isActive = current[keyPath: item.keyPath]

            if wasActive != isActive {
                let payload: [Any] = ["dpad", ["action": item.action, "pressed": isActive]]
                if let str = jsonString(payload) {
                    try await task.send(.string("42" + str))
                }
            }
        }
    }

    public func releaseAll() async {
        guard isConnected, webSocketTask != nil else { return }
        let neutral = ControllerState.neutral()
        try? await updateState(neutral)
    }

    public func disconnect() async {
        lock.withLock {
            webSocketTask?.cancel(with: .normalClosure, reason: nil)
            webSocketTask = nil
            session?.invalidateAndCancel()
            session = nil
            isConnected = false
            previousState = nil
        }
    }

    private func jsonString(_ object: Any) -> String? {
        guard let data = try? JSONSerialization.data(withJSONObject: object, options: []) else { return nil }
        return String(data: data, encoding: .utf8)
    }
}
