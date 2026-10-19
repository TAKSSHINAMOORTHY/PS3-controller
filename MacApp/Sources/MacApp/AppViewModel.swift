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
    @Published public var isAccessibilityGranted: Bool = true
    @Published public var usbStatusText: String = "Checking..."
    @Published public var isUsbAdbConfigured: Bool = false
    @Published public var isStarting: Bool = false

    private var server: ControllerServer?
    private var backend: GamepadBackend?
    private var retryCount: Int = 0
    private var accessibilityCancellable: AnyCancellable?

    public init() {
        refreshNetworkInterfaces()
        refreshAccessibilityStatus()
        setupAccessibilityMonitor()
    }

    public func setupAccessibilityMonitor() {
        NotificationCenter.default.addObserver(
            forName: NSApplication.didBecomeActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                self?.refreshAccessibilityStatus()
            }
        }

        accessibilityCancellable = Timer.publish(every: 1.5, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in
                guard let self = self else { return }
                if !self.isAccessibilityGranted {
                    self.refreshAccessibilityStatus()
                }
            }
    }

    public func refreshAccessibilityStatus() {
        let current = RPCS3DirectInputAdapter.isAccessibilityPermissionGranted
        if current != self.isAccessibilityGranted {
            self.isAccessibilityGranted = current
            if current {
                addLog("Accessibility authorization verified by system.")
            }
        }
    }

    public func requestAccessibilityPermission() {
        let trusted = RPCS3DirectInputAdapter.requestAccessibilityPrompt()
        self.isAccessibilityGranted = trusted
        if !trusted {
            addLog("Requested macOS Accessibility prompt. Opening System Settings...")
            if let url = URL(string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility") {
                NSWorkspace.shared.open(url)
            }
        } else {
            addLog("Accessibility permission is already active.")
        }
    }

    public func openAccessibilitySettings() {
        requestAccessibilityPermission()
    }

    public func killConflictingPort(port: UInt16) {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/sbin/lsof")
        process.arguments = ["-ti", ":\(port)"]
        let pipe = Pipe()
        process.standardOutput = pipe
        do {
            try process.run()
            process.waitUntilExit()
            let data = pipe.fileHandleForReading.readDataToEndOfFile()
            if let output = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines), !output.isEmpty {
                let myPid = ProcessInfo.processInfo.processIdentifier
                let pids = output.components(separatedBy: .newlines).compactMap { Int32($0.trimmingCharacters(in: .whitespaces)) }
                for pid in pids where pid != myPid {
                    addLog("Releasing port \(port) held by stale PID \(pid)...")
                    kill(pid, SIGKILL)
                }
                usleep(100_000)
            }
        } catch {
            // Non-critical if lsof cannot be run
        }
    }

    public func configureUsbAdb() {
        let port = UInt16(portString) ?? 6000
        Task.detached {
            let possibleAdbPaths = [
                "/Users/\(NSUserName())/Library/Android/sdk/platform-tools/adb",
                "\(NSHomeDirectory())/Library/Android/sdk/platform-tools/adb",
                "/opt/homebrew/bin/adb",
                "/usr/local/bin/adb"
            ]
            let adbURL = possibleAdbPaths.first { FileManager.default.isExecutableFile(atPath: $0) }.map { URL(fileURLWithPath: $0) }

            guard let url = adbURL else {
                await MainActor.run {
                    self.isUsbAdbConfigured = false
                    self.usbStatusText = "ADB Not Found"
                    self.addLog("[USB] adb CLI not located in standard paths.")
                }
                return
            }

            let process = Process()
            process.executableURL = url
            process.arguments = ["reverse", "tcp:\(port)", "tcp:\(port)"]
            let pipe = Pipe()
            process.standardOutput = pipe
            process.standardError = pipe

            do {
                try process.run()
                process.waitUntilExit()
                let data = pipe.fileHandleForReading.readDataToEndOfFile()
                let output = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""

                await MainActor.run {
                    if process.terminationStatus == 0 {
                        self.isUsbAdbConfigured = true
                        self.usbStatusText = "Active (Port \(port))"
                        self.addLog("[USB] Configured ADB reverse tcp:\(port) -> tcp:\(port) successfully.")
                    } else {
                        self.isUsbAdbConfigured = false
                        self.usbStatusText = "No Phone"
                        self.addLog("[USB] ADB reverse note: \(output)")
                    }
                }
            } catch {
                await MainActor.run {
                    self.isUsbAdbConfigured = false
                    self.usbStatusText = "Error"
                    self.addLog("[USB] Error running ADB reverse: \(error.localizedDescription)")
                }
            }
        }
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
        guard !isStarting else { return }
        isStarting = true
        serverStatusText = "Starting..."

        stopServices()

        let port = UInt16(portString) ?? 6000
        killConflictingPort(port: port)
        refreshAccessibilityStatus()
        configureUsbAdb()

        // Allow kernel socket to finish teardown before binding new NWListener
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [weak self] in
            guard let self = self else { return }
            self.doStartServer(port: port)
        }
    }

    private func doStartServer(port: UInt16) {
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
                    self.isStarting = false
                    self.isClientConnected = false
                    self.clientEndpoint = "None"
                    self.addLog("Server stopped.")
                case .listening(let p):
                    self.serverStatusText = "Listening on port \(p)"
                    self.isListening = true
                    self.isStarting = false
                    self.isClientConnected = false
                    self.clientEndpoint = "None"
                    self.retryCount = 0
                    self.addLog("Server listening on port \(p)...")
                case .clientConnected(let peer):
                    self.serverStatusText = "Client Connected"
                    self.isListening = true
                    self.isStarting = false
                    self.isClientConnected = true
                    self.clientEndpoint = peer
                    self.addLog("Client connected from \(peer)")
                case .failed(let err):
                    self.addLog("Server error: \(err)")
                    if err.contains("Address already in use") && self.retryCount < 2 {
                        self.retryCount += 1
                        self.serverStatusText = "Port releasing, retrying..."
                        self.addLog("Port \(port) still releasing, retrying in 400ms (attempt \(self.retryCount))...")
                        self.killConflictingPort(port: port)
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
                            self.doStartServer(port: port)
                        }
                    } else {
                        self.serverStatusText = "Error: \(err)"
                        self.isListening = false
                        self.isStarting = false
                        self.isClientConnected = false
                        self.retryCount = 0
                    }
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
            self.serverStatusText = "Error: \(error.localizedDescription)"
            self.isListening = false
            self.isStarting = false
            self.addLog("Failed to start server on port \(port): \(error)")
        }
    }

    public func restartServices() {
        addLog("Restarting services...")
        stopServices()
        isListening = false
        isStarting = true
        serverStatusText = "Restarting..."
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) { [weak self] in
            guard let self = self else { return }
            self.isStarting = false
            self.startServices()
        }
    }

    public func stopServices() {
        server?.stop()
        server = nil
        isListening = false
        isStarting = false
        isClientConnected = false
        clientEndpoint = "None"
        serverStatusText = "Stopped"
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
