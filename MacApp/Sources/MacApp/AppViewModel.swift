import Foundation
import SwiftUI
import Combine
import MacGamepadCore
import Darwin

@MainActor
public final class AppViewModel: ObservableObject {
    @Published public var serverStatusText: String = "Stopped"
    @Published public var isListening: Bool = false
    @Published public var isClientConnected: Bool = false
    @Published public var clientEndpoint: String = "None"
    @Published public var backendStatusText: String = "Connecting..."
    @Published public var isBackendConnected: Bool = false
    @Published public var localIPs: [String] = []
    @Published public var currentState: ControllerState = ControllerState.neutral()
    @Published public var logs: [String] = []
    @Published public var portString: String = "6000"

    private var server: ControllerServer?
    private var backend: GamepadBackend?

    public init() {
        refreshNetworkInterfaces()
    }

    public func refreshNetworkInterfaces() {
        var addresses: [String] = []
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0, let firstAddr = ifaddr else { return }
        defer { freeifaddrs(ifaddr) }

        var ptr: UnsafeMutablePointer<ifaddrs>? = firstAddr
        while let current = ptr {
            let interface = current.pointee
            let addrFamily = interface.ifa_addr.pointee.sa_family
            if addrFamily == UInt8(AF_INET) {
                let name = String(cString: interface.ifa_name)
                var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                if getnameinfo(interface.ifa_addr, socklen_t(interface.ifa_addr.pointee.sa_len),
                               &hostname, socklen_t(hostname.count),
                               nil, socklen_t(0), NI_NUMERICHOST) == 0 {
                    let ip = String(cString: &hostname)
                    if ip != "127.0.0.1" && (name.hasPrefix("en") || name.hasPrefix("wi")) {
                        addresses.append("\(ip) (\(name))")
                    }
                }
            }
            ptr = interface.ifa_next
        }
        self.localIPs = addresses.isEmpty ? ["127.0.0.1 (localhost)"] : addresses
    }

    public func startServices() {
        let port = UInt16(portString) ?? 6000
        addLog("Initializing RPCS3 Direct Input Gamepad backend...")

        let adapter = RPCS3DirectInputAdapter()
        self.backend = adapter

        Task {
            do {
                try await adapter.connect()
                self.isBackendConnected = true
                self.backendStatusText = "Active: \(adapter.deviceName)"
                self.addLog("Backend connected successfully.")
            } catch {
                self.isBackendConnected = false
                self.backendStatusText = "Unavailable (\(error.localizedDescription))"
                self.addLog("Adapter connection failed: \(error.localizedDescription)")
            }
        }

        let srv = ControllerServer(port: port, backend: adapter)
        srv.onStatusChanged = { [weak self] status in
            Task { @MainActor in
                guard let self = self else { return }
                switch status {
                case .stopped:
                    self.serverStatusText = "Stopped"
                    self.isListening = false
                    self.isClientConnected = false
                    self.clientEndpoint = "None"
                    self.addLog("Server stopped.")
                case .listening(let p):
                    self.serverStatusText = "Listening on port \(p)"
                    self.isListening = true
                    self.isClientConnected = false
                    self.clientEndpoint = "None"
                    self.addLog("Server listening on port \(p)...")
                case .clientConnected(let peer):
                    self.serverStatusText = "Client Connected"
                    self.isListening = true
                    self.isClientConnected = true
                    self.clientEndpoint = peer
                    self.addLog("Client connected from \(peer)")
                case .failed(let err):
                    self.serverStatusText = "Error: \(err)"
                    self.isListening = false
                    self.isClientConnected = false
                    self.addLog("Server error: \(err)")
                }
            }
        }

        srv.onStateReceived = { [weak self] state in
            Task { @MainActor in
                self?.currentState = state
            }
        }

        srv.onError = { [weak self] msg in
            Task { @MainActor in
                self?.addLog("Server message: \(msg)")
            }
        }

        do {
            try srv.start()
            self.server = srv
        } catch {
            self.addLog("Failed to start server on port \(port): \(error)")
        }
    }

    public func stopServices() {
        server?.stop()
        server = nil
        Task {
            await backend?.disconnect()
            backend = nil
            isBackendConnected = false
            backendStatusText = "Disconnected"
        }
    }

    public func injectTestButton(name: String) {
        guard let b = backend else {
            addLog("Cannot inject: backend not initialized")
            return
        }

        addLog("Injecting test pulse for button: \(name)")
        Task {
            var pressed = ControllerState.neutral()
            switch name.uppercased() {
            case "CROSS": pressed.buttons.cross = true
            case "CIRCLE": pressed.buttons.circle = true
            case "SQUARE": pressed.buttons.square = true
            case "TRIANGLE": pressed.buttons.triangle = true
            case "L1": pressed.buttons.l1 = true
            case "R1": pressed.buttons.r1 = true
            default: break
            }
            try? await b.updateState(pressed)
            self.currentState = pressed

            try? await Task.sleep(nanoseconds: 200_000_000)

            let neutral = ControllerState.neutral()
            try? await b.updateState(neutral)
            self.currentState = neutral
            self.addLog("Pulse completed for \(name)")
        }
    }

    public func releaseAll() {
        Task {
            await backend?.releaseAll()
            currentState = ControllerState.neutral()
            addLog("All inputs released and centered.")
        }
    }

    public func addLog(_ message: String) {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss"
        let timestamp = formatter.string(from: Date())
        logs.append("[\(timestamp)] \(message)")
        if logs.count > 50 {
            logs.removeFirst()
        }
    }
}
