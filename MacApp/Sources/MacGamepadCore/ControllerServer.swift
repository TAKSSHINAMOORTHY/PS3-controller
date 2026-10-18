import Foundation
import Network

public enum ServerStatus: Equatable, Sendable {
    case stopped
    case listening(port: UInt16)
    case clientConnected(peer: String)
    case failed(String)
}

public final class ControllerServer: @unchecked Sendable {
    public let port: UInt16
    public let backend: GamepadBackend
    public let validator: ProtocolValidator

    public private(set) var status: ServerStatus = .stopped
    public var onStatusChanged: ((ServerStatus) -> Void)?
    public var onStateReceived: ((ControllerState) -> Void)?
    public var onError: ((String) -> Void)?

    private var listener: NWListener?
    private var activeConnection: NWConnection?
    private var activeSessionId: String?
    private var lastMessageDate: Date = .distantPast
    private var timeoutTimer: DispatchSourceTimer?
    private let queue = DispatchQueue(label: "com.antigravity.controllerserver", qos: .userInteractive)
    private let lock = NSLock()

    public init(port: UInt16 = 6000, backend: GamepadBackend) {
        self.port = port
        self.backend = backend
        self.validator = ProtocolValidator()
    }

    public func start() throws {
        let params = NWParameters.tcp
        let nwPort = NWEndpoint.Port(rawValue: port) ?? NWEndpoint.Port(integerLiteral: 6000)
        let listener = try NWListener(using: params, on: nwPort)

        listener.stateUpdateHandler = { [weak self] state in
            guard let self = self else { return }
            switch state {
            case .ready:
                self.updateStatus(.listening(port: self.port))
            case .failed(let error):
                self.updateStatus(.failed(error.localizedDescription))
            case .cancelled:
                self.updateStatus(.stopped)
            default:
                break
            }
        }

        listener.newConnectionHandler = { [weak self] connection in
            self?.handleIncomingConnection(connection)
        }

        self.listener = listener
        listener.start(queue: queue)
        startTimeoutMonitor()
    }

    public func stop() {
        lock.lock()
        defer { lock.unlock() }

        timeoutTimer?.cancel()
        timeoutTimer = nil

        activeConnection?.cancel()
        activeConnection = nil

        listener?.cancel()
        listener = nil

        validator.reset()
        updateStatus(.stopped)

        Task {
            await backend.releaseAll()
        }
    }

    private func handleIncomingConnection(_ connection: NWConnection) {
        lock.lock()
        // If already connected to a client, reject new connection
        if activeConnection != nil {
            lock.unlock()
            connection.cancel()
            return
        }
        activeConnection = connection
        lock.unlock()

        connection.stateUpdateHandler = { [weak self] state in
            guard let self = self else { return }
            switch state {
            case .ready:
                let peer = connection.endpoint.debugDescription
                self.updateStatus(.clientConnected(peer: peer))
                self.receiveNextMessage(on: connection)
            case .failed(let err):
                self.handleClientDisconnect(reason: err.localizedDescription)
            case .cancelled:
                self.handleClientDisconnect(reason: "Connection cancelled")
            default:
                break
            }
        }

        connection.start(queue: queue)
    }

    private func receiveNextMessage(on connection: NWConnection) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: ProtocolValidator.maxPayloadSize + 128) { [weak self] content, context, isComplete, error in
            guard let self = self else { return }

            if let data = content, !data.isEmpty {
                self.processIncomingData(data, on: connection)
            }

            if isComplete || error != nil {
                self.handleClientDisconnect(reason: error?.localizedDescription ?? "Client closed stream")
            } else {
                self.receiveNextMessage(on: connection)
            }
        }
    }

    public func processIncomingData(_ data: Data, on connection: NWConnection) {
        lock.lock()
        lastMessageDate = Date()
        lock.unlock()

        // Check if this is an HTTP WebSocket Upgrade Request
        if let text = String(data: data, encoding: .utf8), text.contains("Upgrade: websocket") {
            handleWebSocketHandshake(text, on: connection)
            return
        }

        // Parse WebSocket frame or raw JSON
        let payloadData = extractWebSocketPayload(data) ?? data

        do {
            try validator.validateSize(payloadData)

            // Inspect packet type
            if let json = try? JSONSerialization.jsonObject(with: payloadData) as? [String: Any],
               let type = json["type"] as? String {
                switch type {
                case "pair_request":
                    handlePairRequest(payloadData, on: connection)
                case "state":
                    handleStateUpdate(payloadData)
                case "heartbeat":
                    // Valid heartbeat resets timeout
                    break
                case "disconnect":
                    handleClientDisconnect(reason: "Client sent explicit disconnect")
                default:
                    onError?("Unknown packet type: \(type)")
                }
            }
        } catch {
            onError?("Validation error: \(error)")
        }
    }

    private func handleWebSocketHandshake(_ requestText: String, on connection: NWConnection) {
        var secKey = ""
        for line in requestText.components(separatedBy: "\r\n") {
            if line.lowercased().hasPrefix("sec-websocket-key:") {
                secKey = line.components(separatedBy: ":")[1].trimmingCharacters(in: .whitespaces)
            }
        }

        let guid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        let combined = secKey + guid
        let hash = Insecure.SHA1.hash(data: Data(combined.utf8))
        let acceptKey = Data(hash).base64EncodedString()

        let response = "HTTP/1.1 101 Switching Protocols\r\n" +
                       "Upgrade: websocket\r\n" +
                       "Connection: Upgrade\r\n" +
                       "Sec-WebSocket-Accept: \(acceptKey)\r\n\r\n"

        connection.send(content: Data(response.utf8), completion: .contentProcessed({ _ in }))
    }

    private func handlePairRequest(_ data: Data, on connection: NWConnection) {
        guard (try? JSONDecoder().decode(PairRequest.self, from: data)) != nil else {
            sendResponse(PairResponse(status: "rejected", error: "malformed_request"), on: connection)
            return
        }

        let newSessionId = UUID().uuidString
        lock.lock()
        self.activeSessionId = newSessionId
        self.validator.setSessionId(newSessionId)
        lock.unlock()

        let response = PairResponse(status: "accepted", sessionId: newSessionId, heartbeatIntervalMs: 1000, maxRateHz: 60)
        sendResponse(response, on: connection)
    }

    private func handleStateUpdate(_ data: Data) {
        guard var state = try? JSONDecoder().decode(ControllerState.self, from: data) else { return }

        do {
            try validator.validateAndNormalizeState(&state)
            onStateReceived?(state)

            Task {
                try? await backend.updateState(state)
            }
        } catch {
            onError?("Dropped invalid state: \(error)")
        }
    }

    private func handleClientDisconnect(reason: String) {
        lock.lock()
        activeConnection = nil
        activeSessionId = nil
        validator.reset()
        lock.unlock()

        updateStatus(.listening(port: self.port))
        onError?("Client disconnected: \(reason)")

        Task {
            await backend.releaseAll()
        }
    }

    private func sendResponse<T: Encodable>(_ response: T, on connection: NWConnection) {
        guard let jsonData = try? JSONEncoder().encode(response) else { return }
        let frame = makeWebSocketFrame(jsonData)
        connection.send(content: frame, completion: .contentProcessed({ _ in }))
    }

    private func extractWebSocketPayload(_ data: Data) -> Data? {
        guard data.count >= 2 else { return nil }
        let byte1 = data[1]
        let isMasked = (byte1 & 0x80) != 0
        var payloadLen = Int(byte1 & 0x7F)
        var offset = 2

        if payloadLen == 126 {
            guard data.count >= 4 else { return nil }
            payloadLen = Int(data[2]) << 8 | Int(data[3])
            offset = 4
        } else if payloadLen == 127 {
            return nil // We do not accept > 64KB frames
        }

        if isMasked {
            guard data.count >= offset + 4 + payloadLen else { return nil }
            let mask = [data[offset], data[offset + 1], data[offset + 2], data[offset + 3]]
            offset += 4
            var unmasked = Data(count: payloadLen)
            for i in 0..<payloadLen {
                unmasked[i] = data[offset + i] ^ mask[i % 4]
            }
            return unmasked
        } else {
            guard data.count >= offset + payloadLen else { return nil }
            return data.subdata(in: offset..<(offset + payloadLen))
        }
    }

    private func makeWebSocketFrame(_ payload: Data) -> Data {
        var frame = Data()
        frame.append(0x81) // Text frame, FIN=1
        if payload.count < 126 {
            frame.append(UInt8(payload.count))
        } else {
            frame.append(126)
            frame.append(UInt8((payload.count >> 8) & 0xFF))
            frame.append(UInt8(payload.count & 0xFF))
        }
        frame.append(payload)
        return frame
    }

    private func startTimeoutMonitor() {
        let timer = DispatchSource.makeTimerSource(queue: queue)
        timer.schedule(deadline: .now() + 1.0, repeating: 0.5)
        timer.setEventHandler { [weak self] in
            guard let self = self else { return }
            let (conn, last) = self.lock.withLock {
                (self.activeConnection, self.lastMessageDate)
            }
            if conn != nil && Date().timeIntervalSince(last) > 1.0 {
                self.handleClientDisconnect(reason: "Session timed out (> 1000ms idle)")
            }
        }
        self.timeoutTimer = timer
        timer.resume()
    }

    private func updateStatus(_ newStatus: ServerStatus) {
        lock.lock()
        self.status = newStatus
        lock.unlock()
        onStatusChanged?(newStatus)
    }
}

import CryptoKit
