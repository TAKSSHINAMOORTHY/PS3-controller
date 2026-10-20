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
    private let lock = NSRecursiveLock()
    private let stateContinuation: AsyncStream<ControllerState>.Continuation
    private var stateProcessingTask: Task<Void, Never>?

    public init(port: UInt16 = 6000, backend: GamepadBackend) {
        self.port = port
        self.backend = backend
        self.validator = ProtocolValidator()

        let (stream, continuation) = AsyncStream.makeStream(of: ControllerState.self)
        self.stateContinuation = continuation
        self.stateProcessingTask = Task.detached(priority: .high) { [weak backend] in
            for await state in stream {
                guard let backend = backend else { break }
                try? await backend.updateState(state)
            }
        }
    }

    public func start() throws {
        let params = NWParameters.tcp
        params.allowLocalEndpointReuse = true
        params.includePeerToPeer = true
        if let ipOptions = params.defaultProtocolStack.internetProtocol as? NWProtocolIP.Options {
            ipOptions.version = .v4
        }
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
        timeoutTimer?.cancel()
        timeoutTimer = nil

        activeConnection?.cancel()
        activeConnection = nil

        listener?.cancel()
        listener = nil

        validator.reset()
        lock.unlock()

        updateStatus(.stopped)

        Task {
            await backend.releaseAll()
        }
    }

    private func handleIncomingConnection(_ connection: NWConnection) {
        lock.lock()
        if let existing = activeConnection {
            existing.cancel()
        }
        activeConnection = connection
        lastMessageDate = Date()
        lock.unlock()

        connection.stateUpdateHandler = { [weak self] state in
            guard let self = self else { return }
            switch state {
            case .ready:
                let peer = connection.endpoint.debugDescription
                self.updateStatus(.clientConnected(peer: peer))
                self.receiveNextMessage(on: connection)
            case .failed(let err):
                self.handleClientDisconnect(connection: connection, reason: err.localizedDescription)
            case .cancelled:
                self.handleClientDisconnect(connection: connection, reason: "Connection cancelled")
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
                self.handleClientDisconnect(connection: connection, reason: error?.localizedDescription ?? "Client closed stream")
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

        // Parse WebSocket frames or raw JSON
        let payloads = extractAllWebSocketPayloads(data, on: connection)

        for payloadData in payloads {
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
                        handleClientDisconnect(connection: connection, reason: "Client sent explicit disconnect")
                    default:
                        onError?("Unknown packet type: \(type)")
                    }
                }
            } catch {
                onError?("Validation error: \(error)")
            }
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
            stateContinuation.yield(state)
        } catch {
            onError?("Dropped invalid state: \(error)")
        }
    }

    private func handleClientDisconnect(connection: NWConnection, reason: String) {
        lock.lock()
        guard activeConnection === connection else {
            lock.unlock()
            connection.cancel()
            return
        }
        activeConnection = nil
        activeSessionId = nil
        validator.reset()
        lock.unlock()

        connection.cancel()
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

    private func extractAllWebSocketPayloads(_ data: Data, on connection: NWConnection) -> [Data] {
        var results: [Data] = []
        var offset = 0
        let count = data.count

        while offset + 2 <= count {
            let byte0 = data[offset]
            let opcode = byte0 & 0x0F
            let byte1 = data[offset + 1]
            let isMasked = (byte1 & 0x80) != 0
            var payloadLen = Int(byte1 & 0x7F)
            var headerSize = 2

            if payloadLen == 126 {
                guard offset + 4 <= count else { break }
                payloadLen = Int(data[offset + 2]) << 8 | Int(data[offset + 3])
                headerSize = 4
            } else if payloadLen == 127 {
                break // Drop jumbo frames > 64KB
            }

            let maskSize = isMasked ? 4 : 0
            let totalFrameSize = headerSize + maskSize + payloadLen
            guard offset + totalFrameSize <= count else { break }

            if opcode == 0x1 { // Text frame
                if isMasked {
                    let maskOffset = offset + headerSize
                    let mask = [data[maskOffset], data[maskOffset + 1], data[maskOffset + 2], data[maskOffset + 3]]
                    let payloadOffset = maskOffset + 4
                    var unmasked = Data(count: payloadLen)
                    for i in 0..<payloadLen {
                        unmasked[i] = data[payloadOffset + i] ^ mask[i % 4]
                    }
                    results.append(unmasked)
                } else {
                    let payloadOffset = offset + headerSize
                    results.append(data.subdata(in: payloadOffset..<(payloadOffset + payloadLen)))
                }
            } else if opcode == 0x8 { // Connection close frame
                handleClientDisconnect(connection: connection, reason: "Client sent close frame")
                break
            } else if opcode == 0x9 { // Ping frame
                // Respond immediately with Pong frame (opcode 0xA) to keep socket alive
                let maskOffset = offset + headerSize
                let payloadOffset = isMasked ? (maskOffset + 4) : maskOffset
                var pongPayload = Data()
                if payloadLen > 0 && payloadOffset + payloadLen <= count {
                    if isMasked {
                        let mask = [data[maskOffset], data[maskOffset + 1], data[maskOffset + 2], data[maskOffset + 3]]
                        var unmasked = Data(count: payloadLen)
                        for i in 0..<payloadLen {
                            unmasked[i] = data[payloadOffset + i] ^ mask[i % 4]
                        }
                        pongPayload = unmasked
                    } else {
                        pongPayload = data.subdata(in: payloadOffset..<(payloadOffset + payloadLen))
                    }
                }
                let pongFrame = makeWebSocketPongFrame(pongPayload)
                connection.send(content: pongFrame, completion: .contentProcessed({ _ in }))
            } else if opcode == 0xA { // Pong frame
                // Client Pong received, keeps connection active
            }

            offset += totalFrameSize
        }

        if results.isEmpty && !data.isEmpty {
            return [data]
        }
        return results
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

    private func makeWebSocketPongFrame(_ payload: Data) -> Data {
        var frame = Data()
        frame.append(0x8A) // Pong frame (opcode 0xA), FIN=1
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
        timer.schedule(deadline: .now() + 2.0, repeating: 1.0)
        timer.setEventHandler { [weak self] in
            guard let self = self else { return }
            let (conn, hasSession, last) = self.lock.withLock {
                (self.activeConnection, self.activeSessionId != nil, self.lastMessageDate)
            }
            if let conn = conn, hasSession && Date().timeIntervalSince(last) > 60.0 {
                self.handleClientDisconnect(connection: conn, reason: "Session timed out (> 60s idle)")
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
