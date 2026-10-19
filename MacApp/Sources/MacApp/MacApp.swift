import SwiftUI
import MacGamepadCore

struct MacGamepadApp: App {
    @StateObject private var model = AppViewModel()

    var body: some Scene {
        WindowGroup("PS3 Gamepad Bridge") {
            ContentView(model: model)
                .frame(minWidth: 720, minHeight: 650)
        }
    }
}

@main
struct Launcher {
    static func main() {
        if CommandLine.arguments.contains("--headless") || CommandLine.arguments.contains("--cli") {
            runHeadless()
        } else {
            MacGamepadApp.main()
        }
    }

    static func runHeadless() {
        var port: UInt16 = 6000
        if let portIndex = CommandLine.arguments.firstIndex(of: "--port"),
           portIndex + 1 < CommandLine.arguments.count,
           let customPort = UInt16(CommandLine.arguments[portIndex + 1]) {
            port = customPort
        }
        print("[PS3-Companion] Starting in headless mode on port \(port)...")
        let adapter = RPCS3DirectInputAdapter()
        let server = ControllerServer(port: port, backend: adapter)

        server.onStatusChanged = { status in
            print("[PS3-Companion] Server status: \(status)")
        }
        server.onError = { error in
            print("[PS3-Companion] Error: \(error)")
        }

        Task {
            do {
                try await adapter.connect()
                print("[PS3-Companion] Connected to \(adapter.deviceName)")
            } catch {
                print("[PS3-Companion] Backend connection warning: \(error.localizedDescription)")
            }
        }

        do {
            try server.start()
            print("[PS3-Companion] Ready. Listening for Android connections on port 6000. Press Ctrl+C to exit.")
            RunLoop.main.run()
        } catch {
            print("[PS3-Companion] Failed to bind server: \(error)")
        }
    }
}
