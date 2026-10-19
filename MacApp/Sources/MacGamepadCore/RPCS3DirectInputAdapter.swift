import Foundation
import CoreGraphics
import AppKit
import ApplicationServices

public final class RPCS3DirectInputAdapter: GamepadBackend, @unchecked Sendable {
    public private(set) var isConnected: Bool = false
    public let deviceName: String = "RPCS3 Direct Virtual Gamepad (Native)"

    private var activeKeys = Set<CGKeyCode>()
    private let lock = NSLock()
    private var lastPidScan: Date = .distantPast
    private var cachedPids: [pid_t] = []
    private let eventSource: CGEventSource?

    public static var isAccessibilityPermissionGranted: Bool {
        return AXIsProcessTrusted()
    }

    @discardableResult
    public static func requestAccessibilityPrompt() -> Bool {
        let promptKey = "AXTrustedCheckOptionPrompt" as CFString
        let options = [promptKey: true] as CFDictionary
        return AXIsProcessTrustedWithOptions(options)
    }

    public init() {
        self.eventSource = CGEventSource(stateID: .hidSystemState)
    }

    public func connect() async throws {
        lock.withLock {
            isConnected = true
            refreshRPCS3Pids()
        }
        if !Self.isAccessibilityPermissionGranted {
            print("[RPCS3Adapter] ⚠️ WARNING: Accessibility permission is NOT granted. CGEvent taps require Accessibility in System Settings > Privacy & Security > Accessibility.")
        } else {
            print("[RPCS3Adapter] ✅ Accessibility permission verified.")
        }
    }

    public func disconnect() async {
        await releaseAll()
        lock.withLock {
            isConnected = false
            cachedPids.removeAll()
        }
    }

    public func releaseAll() async {
        lock.withLock {
            for key in activeKeys {
                postRawKeyEvent(code: key, down: false)
            }
            activeKeys.removeAll()
        }
    }

    public func updateState(_ state: ControllerState) async throws {
        lock.withLock {
            refreshRPCS3PidsIfNeeded()
            let desired = calculateDesiredKeys(for: state)

            let toPress = desired.subtracting(activeKeys)
            let toRelease = activeKeys.subtracting(desired)

            for key in toPress {
                postRawKeyEvent(code: key, down: true)
            }
            for key in toRelease {
                postRawKeyEvent(code: key, down: false)
            }

            activeKeys = desired
        }
    }

    private func calculateDesiredKeys(for state: ControllerState) -> Set<CGKeyCode> {
        var keys = Set<CGKeyCode>()

        // Face Buttons (Default: Z, X, C, V)
        if state.buttons.cross { keys.insert(7) }      // X
        if state.buttons.circle { keys.insert(8) }     // C
        if state.buttons.square { keys.insert(6) }     // Z
        if state.buttons.triangle { keys.insert(9) }   // V

        // Bumpers (Default: Q, E)
        if state.buttons.l1 { keys.insert(12) }        // Q
        if state.buttons.r1 { keys.insert(14) }        // E

        // Triggers (Default: R, T)
        if state.triggers.l2 > 0.20 || state.buttons.l2 { keys.insert(15) } // R
        if state.triggers.r2 > 0.20 || state.buttons.r2 { keys.insert(17) } // T

        // Sticks Clicks (Default: L3 = F / keycode 3, R3 = G / keycode 5; plus Shift / 56 as alias)
        if state.buttons.l3 {
            keys.insert(3)  // F (RPCS3 default)
            keys.insert(56) // Left Shift
        }
        if state.buttons.r3 {
            keys.insert(5)  // G (RPCS3 default)
        }

        // System Buttons
        // Start: Return (36)
        if state.buttons.start { keys.insert(36) }
        // Select: Space (49)
        if state.buttons.select { keys.insert(49) }
        // PS Button: Backspace (51 in RPCS3 Default.yml) & Escape (53)
        if state.buttons.ps {
            keys.insert(51) // Backspace
            keys.insert(53) // Escape
        }

        // D-Pad (Default: Arrow keys)
        if state.dpad.up { keys.insert(126) }    // Up Arrow
        if state.dpad.down { keys.insert(125) }  // Down Arrow
        if state.dpad.left { keys.insert(123) }  // Left Arrow
        if state.dpad.right { keys.insert(124) } // Right Arrow

        // Left Analog Stick -> WASD
        let stickThreshold: Float = 0.25
        if state.axes.ly < -stickThreshold { keys.insert(13) } // W (Up)
        if state.axes.ly > stickThreshold { keys.insert(1) }   // S (Down)
        if state.axes.lx < -stickThreshold { keys.insert(0) }   // A (Left)
        if state.axes.lx > stickThreshold { keys.insert(2) }   // D (Right)

        // Right Analog Stick:
        // RPCS3 Default binds:
        // Up: ↖ (Home, 115)
        // Down: ↘ (End, 119)
        // Left: ⌦ (Forward Delete, 117)
        // Right: ⇟ (Page Down, 121)
        // Also include standard IKJL keys (34, 40, 38, 37) so both setups work seamlessly
        if state.axes.ry < -stickThreshold {
            keys.insert(115) // Home (RPCS3 Default Up)
            keys.insert(34)  // I
        }
        if state.axes.ry > stickThreshold {
            keys.insert(119) // End (RPCS3 Default Down)
            keys.insert(40)  // K
        }
        if state.axes.rx < -stickThreshold {
            keys.insert(117) // Forward Delete (RPCS3 Default Left)
            keys.insert(38)  // J
        }
        if state.axes.rx > stickThreshold {
            keys.insert(121) // Page Down (RPCS3 Default Right)
            keys.insert(37)  // L
        }

        return keys
    }

    private func refreshRPCS3Pids() {
        let apps = NSWorkspace.shared.runningApplications
        let pids = apps.compactMap { app -> pid_t? in
            if app.localizedName?.lowercased().contains("rpcs3") == true ||
               app.bundleIdentifier?.lowercased().contains("rpcs3") == true ||
               app.executableURL?.lastPathComponent.lowercased().contains("rpcs3") == true {
                return app.processIdentifier
            }
            return nil
        }
        self.cachedPids = pids
        self.lastPidScan = Date()
    }

    private func refreshRPCS3PidsIfNeeded() {
        if Date().timeIntervalSince(lastPidScan) > 2.0 {
            refreshRPCS3Pids()
        }
    }

    private func postRawKeyEvent(code: CGKeyCode, down: Bool) {
        guard let event = CGEvent(keyboardEventSource: eventSource, virtualKey: code, keyDown: down) else { return }

        // Post to the system HID event tap
        event.post(tap: .cghidEventTap)

        // If RPCS3 is running and not frontmost, also route to its PID
        let rpcs3App = NSWorkspace.shared.runningApplications.first(where: {
            $0.localizedName?.lowercased().contains("rpcs3") == true
        })
        if let rpcs3 = rpcs3App, !rpcs3.isActive {
            for pid in cachedPids {
                event.postToPid(pid)
            }
        }
    }
}
