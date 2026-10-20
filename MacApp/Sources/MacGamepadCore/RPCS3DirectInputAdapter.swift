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

    private var lastStickActivity: Date = .distantPast
    private var lastStateActivity: Date = .distantPast
    private var lastProcessedSeq: UInt32 = 0
    private var lastButtonPressTime: [CGKeyCode: Date] = [:]
    private var pendingReleases: [CGKeyCode: DispatchWorkItem] = [:]
    private let minimumButtonHoldDuration: TimeInterval = 0.035 // 35ms guarantees recognition across 30 FPS / 60 FPS game frames without swallowing rapid clicks
    private var stickWatchdogTimer: DispatchSourceTimer?
    private let watchdogQueue = DispatchQueue(label: "com.antigravity.watchdog", qos: .userInteractive)

    private let stickKeys: Set<CGKeyCode> = [
        13, 1, 0, 2,        // Left Stick (W, S, A, D)
        115, 119, 117, 121, // Right Stick Default.yml (Home, End, Delete, PageDown)
        34, 40, 38, 37      // Right Stick Alphanumeric (I, K, J, L)
    ]

    public func connect() async throws {
        lock.withLock {
            isConnected = true
            refreshRPCS3Pids()
            startWatchdog()
        }
        if !Self.isAccessibilityPermissionGranted {
            print("[RPCS3Adapter] ⚠️ WARNING: Accessibility permission is NOT granted. CGEvent taps require Accessibility in System Settings > Privacy & Security > Accessibility.")
        } else {
            print("[RPCS3Adapter] ✅ Accessibility permission verified.")
        }
    }

    public func disconnect() async {
        stopWatchdog()
        await releaseAll()
        lock.withLock {
            isConnected = false
            cachedPids.removeAll()
        }
    }

    public func releaseAll() async {
        lock.withLock {
            for (_, workItem) in pendingReleases {
                workItem.cancel()
            }
            pendingReleases.removeAll()
            for key in activeKeys {
                postRawKeyEvent(code: key, down: false)
            }
            activeKeys.removeAll()
            lastButtonPressTime.removeAll()
            lastProcessedSeq = 0
        }
    }

    public func updateState(_ state: ControllerState) async throws {
        lock.withLock {
            if state.seq > 0 && state.seq < lastProcessedSeq {
                // Drop stale out-of-order sequence packet
                return
            }
            if state.seq > 0 {
                lastProcessedSeq = state.seq
            }

            refreshRPCS3PidsIfNeeded()
            let now = Date()
            lastStateActivity = now

            let isStickActive = abs(state.axes.lx) > 0.15 || abs(state.axes.ly) > 0.15 ||
                                abs(state.axes.rx) > 0.15 || abs(state.axes.ry) > 0.15
            if isStickActive {
                lastStickActivity = now
            }

            let desired = calculateDesiredKeys(for: state)

            // Rapid-Click Edge Restoration:
            // If a key is desired (finger down), but was waiting in pendingReleases (user clicked and immediately clicked again):
            // Cancel the delayed workItem, immediately post KeyUp so RPCS3 registers the release edge,
            // and remove from activeKeys so that it re-presses below as an independent click!
            for key in desired {
                if let workItem = pendingReleases[key] {
                    workItem.cancel()
                    pendingReleases.removeValue(forKey: key)
                    postRawKeyEvent(code: key, down: false)
                    activeKeys.remove(key)
                }
            }

            // Keys to press: desired but not in activeKeys
            let toPress = desired.subtracting(activeKeys)

            // Keys to release: activeKeys not in desired, and not already scheduled for pending release
            let candidatesToRelease = activeKeys.subtracting(desired)
            let toRelease = candidatesToRelease.filter { pendingReleases[$0] == nil }

            for key in toPress {
                lastButtonPressTime[key] = now
                postRawKeyEvent(code: key, down: true)
                activeKeys.insert(key)
            }

            for key in toRelease {
                if stickKeys.contains(key) {
                    // Sticks release immediately with 0ms delay for crisp centering
                    postRawKeyEvent(code: key, down: false)
                    activeKeys.remove(key)
                } else {
                    // Buttons enforce minimum hold duration (35ms) so 30 FPS / 60 FPS game engines never miss quick taps
                    let pressTime = lastButtonPressTime[key] ?? .distantPast
                    let elapsed = now.timeIntervalSince(pressTime)

                    if elapsed < minimumButtonHoldDuration {
                        let remaining = minimumButtonHoldDuration - elapsed
                        let workItem = DispatchWorkItem { [weak self] in
                            guard let self = self else { return }
                            self.lock.withLock {
                                guard self.pendingReleases[key] != nil else { return }
                                self.pendingReleases.removeValue(forKey: key)
                                self.postRawKeyEvent(code: key, down: false)
                                self.activeKeys.remove(key)
                            }
                        }
                        pendingReleases[key] = workItem
                        watchdogQueue.asyncAfter(deadline: .now() + remaining, execute: workItem)
                    } else {
                        pendingReleases[key]?.cancel()
                        pendingReleases.removeValue(forKey: key)
                        postRawKeyEvent(code: key, down: false)
                        activeKeys.remove(key)
                    }
                }
            }
        }
    }

    private func startWatchdog() {
        stopWatchdog()
        let timer = DispatchSource.makeTimerSource(queue: watchdogQueue)
        timer.schedule(deadline: .now() + 0.1, repeating: 0.1)
        timer.setEventHandler { [weak self] in
            self?.checkWatchdog()
        }
        self.stickWatchdogTimer = timer
        timer.resume()
    }

    private func stopWatchdog() {
        stickWatchdogTimer?.cancel()
        stickWatchdogTimer = nil
    }

    private func checkWatchdog() {
        lock.withLock {
            let now = Date()
            // 1. Stick Watchdog: If stick has been silent / inactive for > 1000ms (1.0s), auto-center and release any stuck stick keys
            if now.timeIntervalSince(lastStickActivity) > 1.0 {
                let stuckStickKeys = activeKeys.intersection(stickKeys)
                if !stuckStickKeys.isEmpty {
                    for key in stuckStickKeys {
                        postRawKeyEvent(code: key, down: false)
                        activeKeys.remove(key)
                    }
                }
            }

            // 2. Button & Connection Watchdog: If input stream has been silent for > 2000ms (2.0s), cleanly flush pending releases and all stuck keys
            if now.timeIntervalSince(lastStateActivity) > 2.0 {
                for (key, workItem) in pendingReleases {
                    workItem.cancel()
                    postRawKeyEvent(code: key, down: false)
                    activeKeys.remove(key)
                }
                pendingReleases.removeAll()

                if !activeKeys.isEmpty {
                    for key in activeKeys {
                        postRawKeyEvent(code: key, down: false)
                    }
                    activeKeys.removeAll()
                }
            }
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
        // PS Button: Escape (53) & Backspace (51)
        if state.buttons.ps {
            keys.insert(53) // Escape
            keys.insert(51) // Backspace (RPCS3 default: ⌫)
        }

        // D-Pad (Default: Arrow keys)
        if state.dpad.up { keys.insert(126) }    // Up Arrow
        if state.dpad.down { keys.insert(125) }  // Down Arrow
        if state.dpad.left { keys.insert(123) }  // Left Arrow
        if state.dpad.right { keys.insert(124) } // Right Arrow

        // Left Analog Stick -> WASD
        let stickThreshold: Float = 0.20
        if state.axes.ly < -stickThreshold { keys.insert(13) } // W (Up)
        if state.axes.ly > stickThreshold { keys.insert(1) }   // S (Down)
        if state.axes.lx < -stickThreshold { keys.insert(0) }   // A (Left)
        if state.axes.lx > stickThreshold { keys.insert(2) }   // D (Right)

        // Right Analog Stick -> Dual-mapped to RPCS3 Default.yml (Home: 115, End: 119, Forward Delete: 117, Page Down: 121)
        // AND clean alphanumeric keys (I: 34, K: 40, J: 38, L: 37).
        // This enables in-game dodging/evasion in God of War III while preserving alphanumeric compatibility.
        if state.axes.ry < -stickThreshold {
            keys.insert(115) // Home (RPCS3 native Default Right Stick Up: ↖)
            keys.insert(34)  // I
        }
        if state.axes.ry > stickThreshold {
            keys.insert(119) // End (RPCS3 native Default Right Stick Down: ↘)
            keys.insert(40)  // K
        }
        if state.axes.rx < -stickThreshold {
            keys.insert(117) // Forward Delete (RPCS3 native Default Right Stick Left: ⌦)
            keys.insert(38)  // J
        }
        if state.axes.rx > stickThreshold {
            keys.insert(121) // Page Down (RPCS3 native Default Right Stick Right: ⇟)
            keys.insert(37)  // L
        }

        return keys
    }

    private func refreshRPCS3Pids() {
        var pids = Set<pid_t>()

        let targetApps = NSRunningApplication.runningApplications(withBundleIdentifier: "net.rpcs3.rpcs3")
        for app in targetApps {
            pids.insert(app.processIdentifier)
        }

        let allApps = NSWorkspace.shared.runningApplications
        for app in allApps {
            guard let bundleId = app.bundleIdentifier, !bundleId.hasPrefix("com.apple.") else { continue }
            let isRPCS3 = bundleId.lowercased() == "net.rpcs3.rpcs3" ||
                          app.executableURL?.lastPathComponent.lowercased() == "rpcs3" ||
                          app.localizedName?.lowercased() == "rpcs3"
            if isRPCS3 {
                pids.insert(app.processIdentifier)
            }
        }

        self.cachedPids = Array(pids)
        self.lastPidScan = Date()
    }

    private func refreshRPCS3PidsIfNeeded() {
        if cachedPids.isEmpty || Date().timeIntervalSince(lastPidScan) > 2.0 {
            refreshRPCS3Pids()
        }
    }

    private func postRawKeyEvent(code: CGKeyCode, down: Bool) {
        guard let event = CGEvent(keyboardEventSource: eventSource, virtualKey: code, keyDown: down) else { return }

        // Sanitize event flags: game key presses should NEVER trigger macOS system gestures/shortcuts
        // such as Fn+C (Control Center/Menu Bar), Cmd+C, Ctrl+C, or character accent menus.
        if code == 56 { // Left Shift (L3 alias)
            event.flags = down ? .maskShift : []
        } else {
            event.flags = []
        }

        // Strict process isolation: deliver key events EXCLUSIVELY to RPCS3's process event queue.
        // Never post to the global macOS system event tap (.cghidEventTap), which would leak
        // keystrokes into macOS WindowServer and trigger system sidebars, docks, and menu bars.
        refreshRPCS3PidsIfNeeded()
        for pid in cachedPids {
            event.postToPid(pid)
        }
    }
}
