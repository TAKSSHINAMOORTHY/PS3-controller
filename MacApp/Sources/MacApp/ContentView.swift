import SwiftUI
import MacGamepadCore

public struct ContentView: View {
    @ObservedObject var model: AppViewModel

    public init(model: AppViewModel) {
        self.model = model
    }

    public var body: some View {
        VStack(spacing: 0) {
            headerBar
            Divider()
            ScrollView {
                VStack(spacing: 16) {
                    connectionCard
                    controllerVisualizerCard
                    diagnosticsCard
                    logsCard
                }
                .padding(16)
            }
        }
        .frame(minWidth: 700, minHeight: 640)
        .background(Color(NSColor.windowBackgroundColor))
        .onAppear {
            model.startServices()
        }
    }

    // MARK: - Header Bar
    private var headerBar: some View {
        HStack {
            Image(systemName: "gamecontroller.fill")
                .font(.system(size: 24))
                .foregroundColor(.blue)

            VStack(alignment: .leading, spacing: 2) {
                Text("PS3 Virtual Gamepad Bridge")
                    .font(.headline)
                Text("RPCS3 Native macOS Companion")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            Spacer()

            HStack(spacing: 8) {
                if !model.isAccessibilityGranted {
                    Button(action: { model.openAccessibilitySettings() }) {
                        HStack(spacing: 4) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundColor(.yellow)
                            Text("Grant Accessibility")
                                .font(.caption)
                        }
                    }
                    .buttonStyle(.bordered)
                }

                statusPill(
                    title: "RPCS3 Bridge",
                    isActive: model.isBackendConnected,
                    activeColor: .green,
                    inactiveColor: .orange
                )

                statusPill(
                    title: "USB: \(model.usbStatusText)",
                    isActive: model.isUsbAdbConfigured,
                    activeColor: .green,
                    inactiveColor: .secondary
                )

                statusPill(
                    title: model.isClientConnected ? "Android Paired" : (model.isListening ? "Listening" : "Offline"),
                    isActive: model.isListening,
                    activeColor: model.isClientConnected ? .blue : .green,
                    inactiveColor: .red
                )
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color(NSColor.controlBackgroundColor))
    }

    private func statusPill(title: String, isActive: Bool, activeColor: Color, inactiveColor: Color) -> some View {
        HStack(spacing: 6) {
            Circle()
                .fill(isActive ? activeColor : inactiveColor)
                .frame(width: 8, height: 8)
            Text(title)
                .font(.caption)
                .fontWeight(.medium)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
        .background(RoundedRectangle(cornerRadius: 12).fill(Color.secondary.opacity(0.15)))
    }

    // MARK: - Connection Card
    private var connectionCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Network & Pairing")
                    .font(.subheadline)
                    .fontWeight(.bold)
                    .foregroundColor(.secondary)

                Spacer()

                HStack(spacing: 6) {
                    Circle()
                        .fill(model.isListening ? Color.green : (model.serverStatusText.contains("Error") ? Color.red : Color.gray))
                        .frame(width: 8, height: 8)
                    Text("Server: \(model.serverStatusText)")
                        .font(.caption)
                        .fontWeight(.semibold)
                        .foregroundColor(model.isListening ? .green : (model.serverStatusText.contains("Error") ? .red : .secondary))
                }
            }

            HStack(alignment: .top, spacing: 24) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Connection Endpoints:")
                        .font(.caption)
                        .foregroundColor(.secondary)

                    // USB Endpoint
                    HStack {
                        Image(systemName: "cable.connector")
                            .foregroundColor(.green)
                            .frame(width: 16)
                        Text("ws://127.0.0.1:\(model.portString) (USB)")
                            .font(.system(.body, design: .monospaced))
                            .textSelection(.enabled)

                        HStack(spacing: 4) {
                            Circle()
                                .fill(model.isUsbAdbConfigured ? Color.green : Color.orange)
                                .frame(width: 6, height: 6)
                            Text(model.usbStatusText)
                                .font(.caption2)
                                .foregroundColor(.secondary)
                        }

                        Button("Setup USB") {
                            model.configureUsbAdb()
                        }
                        .buttonStyle(.borderless)
                        .font(.caption2)
                        .foregroundColor(.blue)
                    }

                    // Wi-Fi Endpoints
                    ForEach(model.localIPs, id: \.self) { ip in
                        HStack {
                            Image(systemName: "wifi")
                                .foregroundColor(.blue)
                                .frame(width: 16)
                            Text("ws://\(ip):\(model.portString) (Wi-Fi)")
                                .font(.system(.body, design: .monospaced))
                                .textSelection(.enabled)
                        }
                    }
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 8) {
                    Text("Client: \(model.clientEndpoint)")
                        .font(.caption)
                        .foregroundColor(.secondary)

                    HStack(spacing: 8) {
                        if model.isListening {
                            Button("Stop Server") {
                                model.stopServices()
                            }
                            Button("Restart") {
                                model.restartServices()
                            }
                        } else {
                            Button("Start Server") {
                                model.startServices()
                            }
                            .buttonStyle(.borderedProminent)

                            if model.serverStatusText.contains("Error") {
                                Button("Free Port & Retry") {
                                    model.restartServices()
                                }
                                .buttonStyle(.bordered)
                            }
                        }
                    }
                }
            }
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 10).fill(Color(NSColor.controlBackgroundColor)))
    }

    // MARK: - Controller Visualizer Card
    private var controllerVisualizerCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Live Gamepad State")
                .font(.subheadline)
                .fontWeight(.bold)
                .foregroundColor(.secondary)

            HStack(alignment: .center, spacing: 20) {
                // Left Side: D-Pad & Left Stick
                VStack(spacing: 12) {
                    Text("D-Pad")
                        .font(.caption)
                        .foregroundColor(.secondary)

                    dpadView(model.currentState.dpad)

                    Text("Left Stick (LX/LY)")
                        .font(.caption)
                        .foregroundColor(.secondary)

                    stickCanvas(x: model.currentState.axes.lx, y: model.currentState.axes.ly)
                }
                .frame(maxWidth: .infinity)

                Divider()

                // Center: Triggers, Shoulders, System Buttons
                VStack(spacing: 12) {
                    HStack(spacing: 20) {
                        triggerIndicator(name: "L2", val: model.currentState.triggers.l2)
                        triggerIndicator(name: "R2", val: model.currentState.triggers.r2)
                    }

                    HStack(spacing: 12) {
                        buttonPill("L1", active: model.currentState.buttons.l1)
                        buttonPill("R1", active: model.currentState.buttons.r1)
                    }

                    HStack(spacing: 8) {
                        buttonPill("SELECT", active: model.currentState.buttons.select)
                        buttonPill("PS", active: model.currentState.buttons.ps, color: .orange)
                        buttonPill("START", active: model.currentState.buttons.start)
                    }

                    HStack(spacing: 12) {
                        buttonPill("L3", active: model.currentState.buttons.l3)
                        buttonPill("R3", active: model.currentState.buttons.r3)
                    }

                    Text("Seq: \(model.currentState.seq)")
                        .font(.system(.caption, design: .monospaced))
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity)

                Divider()

                // Right Side: Action Buttons & Right Stick
                VStack(spacing: 12) {
                    Text("Action Buttons")
                        .font(.caption)
                        .foregroundColor(.secondary)

                    actionButtonsView(model.currentState.buttons)

                    Text("Right Stick (RX/RY)")
                        .font(.caption)
                        .foregroundColor(.secondary)

                    stickCanvas(x: model.currentState.axes.rx, y: model.currentState.axes.ry)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 10).fill(Color(NSColor.controlBackgroundColor)))
    }

    // MARK: - Stick Canvas
    private func stickCanvas(x: Float, y: Float) -> some View {
        ZStack {
            Circle()
                .stroke(Color.secondary.opacity(0.3), lineWidth: 2)
                .background(Circle().fill(Color.secondary.opacity(0.08)))
                .frame(width: 80, height: 80)

            Path { path in
                path.move(to: CGPoint(x: 40, y: 0))
                path.addLine(to: CGPoint(x: 40, y: 80))
                path.move(to: CGPoint(x: 0, y: 40))
                path.addLine(to: CGPoint(x: 80, y: 40))
            }
            .stroke(Color.secondary.opacity(0.2), lineWidth: 1)
            .frame(width: 80, height: 80)

            // Stick knob
            Circle()
                .fill(Color.blue)
                .frame(width: 20, height: 20)
                .shadow(radius: 2)
                .offset(
                    x: CGFloat(x) * 30.0,
                    y: CGFloat(y) * 30.0
                )
        }
        .frame(width: 80, height: 80)
    }

    // MARK: - D-Pad View
    private func dpadView(_ dpad: ControllerDPad) -> some View {
        VStack(spacing: 2) {
            dpadDirection("▲", active: dpad.up)
            HStack(spacing: 12) {
                dpadDirection("◀", active: dpad.left)
                dpadDirection("▶", active: dpad.right)
            }
            dpadDirection("▼", active: dpad.down)
        }
        .frame(width: 80, height: 80)
    }

    private func dpadDirection(_ symbol: String, active: Bool) -> some View {
        Text(symbol)
            .font(.caption)
            .frame(width: 24, height: 24)
            .background(RoundedRectangle(cornerRadius: 4).fill(active ? Color.blue : Color.secondary.opacity(0.2)))
            .foregroundColor(active ? .white : .primary)
    }

    // MARK: - Action Buttons View
    private func actionButtonsView(_ buttons: ControllerButtons) -> some View {
        VStack(spacing: 2) {
            actionButton("△", active: buttons.triangle, color: .green)
            HStack(spacing: 12) {
                actionButton("□", active: buttons.square, color: .pink)
                actionButton("○", active: buttons.circle, color: .red)
            }
            actionButton("✕", active: buttons.cross, color: .blue)
        }
        .frame(width: 80, height: 80)
    }

    private func actionButton(_ label: String, active: Bool, color: Color) -> some View {
        Text(label)
            .font(.system(size: 14, weight: .bold))
            .frame(width: 26, height: 26)
            .background(Circle().fill(active ? color : Color.secondary.opacity(0.2)))
            .foregroundColor(active ? .white : .primary)
    }

    private func buttonPill(_ label: String, active: Bool, color: Color = .blue) -> some View {
        Text(label)
            .font(.system(size: 10, weight: .bold))
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(RoundedRectangle(cornerRadius: 6).fill(active ? color : Color.secondary.opacity(0.18)))
            .foregroundColor(active ? .white : .secondary)
    }

    private func triggerIndicator(name: String, val: Float) -> some View {
        VStack(spacing: 4) {
            Text("\(name): \(Int(val * 100))%")
                .font(.caption2)
                .foregroundColor(.secondary)
            ProgressView(value: Double(val), total: 1.0)
                .frame(width: 60)
        }
    }

    // MARK: - Diagnostics Card
    private var diagnosticsCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Diagnostics & Test Injection")
                .font(.subheadline)
                .fontWeight(.bold)
                .foregroundColor(.secondary)

            HStack(spacing: 12) {
                Button("Inject CROSS") {
                    model.injectTestButton(name: "CROSS")
                }

                Button("Inject CIRCLE") {
                    model.injectTestButton(name: "CIRCLE")
                }

                Button("Inject TRIANGLE") {
                    model.injectTestButton(name: "TRIANGLE")
                }

                Button("Inject SQUARE") {
                    model.injectTestButton(name: "SQUARE")
                }

                Spacer()

                Button("Release All") {
                    model.releaseAll()
                }
            }
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 10).fill(Color(NSColor.controlBackgroundColor)))
    }

    // MARK: - Logs Card
    private var logsCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Activity Log")
                .font(.subheadline)
                .fontWeight(.bold)
                .foregroundColor(.secondary)

            ScrollView {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(model.logs, id: \.self) { log in
                        Text(log)
                            .font(.system(.caption, design: .monospaced))
                            .foregroundColor(.secondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .frame(height: 100)
            .padding(8)
            .background(RoundedRectangle(cornerRadius: 6).fill(Color.black.opacity(0.15)))
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: 10).fill(Color(NSColor.controlBackgroundColor)))
    }
}
