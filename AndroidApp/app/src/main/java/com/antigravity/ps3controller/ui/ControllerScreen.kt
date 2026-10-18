package com.antigravity.ps3controller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.ps3controller.model.ControllerAxes
import com.antigravity.ps3controller.model.ControllerButtons
import com.antigravity.ps3controller.model.ControllerDPad
import com.antigravity.ps3controller.model.ControllerState
import com.antigravity.ps3controller.model.ControllerTriggers
import com.antigravity.ps3controller.network.ConnectionStatus
import com.antigravity.ps3controller.network.GamepadClient
import com.antigravity.ps3controller.ui.components.ActionButtonsPad
import com.antigravity.ps3controller.ui.components.DPadPad
import com.antigravity.ps3controller.ui.components.JoystickStick
import com.antigravity.ps3controller.ui.components.ShoulderButton
import com.antigravity.ps3controller.ui.components.SystemPillButton

@Composable
fun ControllerScreen(
    client: GamepadClient,
    host: String,
    port: Int,
    deadZone: Float,
    sensitivity: Float,
    hapticsEnabled: Boolean,
    onOpenSettings: () -> Unit
) {
    val connectionStatus by client.status.collectAsState()
    val errorMessage by client.errorMessage.collectAsState()

    var buttons by remember { mutableStateOf(ControllerButtons()) }
    var dpad by remember { mutableStateOf(ControllerDPad()) }
    var axes by remember { mutableStateOf(ControllerAxes()) }
    var triggers by remember { mutableStateOf(ControllerTriggers()) }

    fun updateAndSend(
        newButtons: ControllerButtons = buttons,
        newDpad: ControllerDPad = dpad,
        newAxes: ControllerAxes = axes,
        newTriggers: ControllerTriggers = triggers
    ) {
        buttons = newButtons
        dpad = newDpad
        axes = newAxes
        triggers = newTriggers

        val state = ControllerState(
            sessionId = "",
            seq = 0,
            buttons = newButtons,
            dpad = newDpad,
            axes = newAxes,
            triggers = newTriggers
        )
        client.sendState(state)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF14171D), Color(0xFF0A0C0E))
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TOP BAR
            TopStatusBar(
                status = connectionStatus,
                host = host,
                port = port,
                error = errorMessage,
                onConnect = { client.connect() },
                onDisconnect = { client.disconnect() },
                onOpenSettings = onOpenSettings
            )

            // CONTROLLER BODY
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT CLUSTER: Shoulders, D-Pad, Left Stick
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.Start
                ) {
                    // Left Shoulders
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShoulderButton(
                            label = "L2",
                            width = 68.dp,
                            height = 36.dp,
                            onPressChanged = { isPressed ->
                                updateAndSend(
                                    newButtons = buttons.copy(l2 = isPressed),
                                    newTriggers = triggers.copy(l2 = if (isPressed) 1f else 0f)
                                )
                            }
                        )
                        ShoulderButton(
                            label = "L1",
                            width = 68.dp,
                            height = 36.dp,
                            onPressChanged = { isPressed ->
                                updateAndSend(newButtons = buttons.copy(l1 = isPressed))
                            }
                        )
                    }

                    // D-Pad and Left Stick
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DPadPad(
                            onUp = { updateAndSend(newDpad = dpad.copy(up = it)) },
                            onDown = { updateAndSend(newDpad = dpad.copy(down = it)) },
                            onLeft = { updateAndSend(newDpad = dpad.copy(left = it)) },
                            onRight = { updateAndSend(newDpad = dpad.copy(right = it)) }
                        )

                        JoystickStick(
                            size = 136.dp,
                            deadZone = deadZone,
                            sensitivity = sensitivity,
                            onMove = { x, y ->
                                updateAndSend(newAxes = axes.copy(lx = x, ly = y))
                            }
                        )
                    }
                }

                // CENTER CLUSTER: System buttons (Select, PS, Start) & L3/R3
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceAround
                ) {
                    // L3 / R3 Stick Clicks
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        SystemPillButton(
                            label = "L3",
                            onPressChanged = { updateAndSend(newButtons = buttons.copy(l3 = it)) }
                        )
                        SystemPillButton(
                            label = "R3",
                            onPressChanged = { updateAndSend(newButtons = buttons.copy(r3 = it)) }
                        )
                    }

                    // Select, PS, Start
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SystemPillButton(
                            label = "SELECT",
                            onPressChanged = { updateAndSend(newButtons = buttons.copy(select = it)) }
                        )
                        SystemPillButton(
                            label = "PS",
                            isSpecial = true,
                            onPressChanged = { updateAndSend(newButtons = buttons.copy(ps = it)) }
                        )
                        SystemPillButton(
                            label = "START",
                            onPressChanged = { updateAndSend(newButtons = buttons.copy(start = it)) }
                        )
                    }
                }

                // RIGHT CLUSTER: Shoulders, Action Buttons, Right Stick
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    // Right Shoulders
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShoulderButton(
                            label = "R1",
                            width = 68.dp,
                            height = 36.dp,
                            onPressChanged = { isPressed ->
                                updateAndSend(newButtons = buttons.copy(r1 = isPressed))
                            }
                        )
                        ShoulderButton(
                            label = "R2",
                            width = 68.dp,
                            height = 36.dp,
                            onPressChanged = { isPressed ->
                                updateAndSend(
                                    newButtons = buttons.copy(r2 = isPressed),
                                    newTriggers = triggers.copy(r2 = if (isPressed) 1f else 0f)
                                )
                            }
                        )
                    }

                    // Right Stick and Action Buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        JoystickStick(
                            size = 136.dp,
                            deadZone = deadZone,
                            sensitivity = sensitivity,
                            onMove = { x, y ->
                                updateAndSend(newAxes = axes.copy(rx = x, ry = y))
                            }
                        )

                        ActionButtonsPad(
                            onTriangle = { updateAndSend(newButtons = buttons.copy(triangle = it)) },
                            onCircle = { updateAndSend(newButtons = buttons.copy(circle = it)) },
                            onCross = { updateAndSend(newButtons = buttons.copy(cross = it)) },
                            onSquare = { updateAndSend(newButtons = buttons.copy(square = it)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopStatusBar(
    status: ConnectionStatus,
    host: String,
    port: Int,
    error: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val statusColor = when (status) {
        ConnectionStatus.CONNECTED -> Color(0xFF10B981)
        ConnectionStatus.CONNECTING, ConnectionStatus.PAIRING -> Color(0xFFF59E0B)
        ConnectionStatus.DISCONNECTED -> Color(0xFF6B7280)
        ConnectionStatus.ERROR -> Color(0xFFEF4444)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Color(0xFF0F1216))
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator & host
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Text(
                text = "$status: $host:$port",
                color = Color(0xFFCBD5E1),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            if (error != null && status == ConnectionStatus.ERROR) {
                Text(
                    text = "($error)",
                    color = Color(0xFFF87171),
                    fontSize = 11.sp
                )
            }
        }

        // Actions
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (status == ConnectionStatus.CONNECTED) {
                TextButton(onClick = onDisconnect) {
                    Text("Disconnect", color = Color(0xFFF87171), fontSize = 12.sp)
                }
            } else {
                TextButton(onClick = onConnect) {
                    Text("Connect", color = Color(0xFF60A5FA), fontSize = 12.sp)
                }
            }

            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
