package com.antigravity.ps3controller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.ps3controller.math.TouchInputMapper
import com.antigravity.ps3controller.model.ControllerAxes
import com.antigravity.ps3controller.model.ControllerButtons
import com.antigravity.ps3controller.model.ControllerDPad
import com.antigravity.ps3controller.model.ControllerLayoutConfig
import com.antigravity.ps3controller.model.ControllerState
import com.antigravity.ps3controller.model.ControllerTriggers
import com.antigravity.ps3controller.model.ElementLayout
import com.antigravity.ps3controller.model.LayoutElementType
import com.antigravity.ps3controller.network.ConnectionStatus
import com.antigravity.ps3controller.network.GamepadClient
import com.antigravity.ps3controller.ui.components.ActionButtonsPad
import com.antigravity.ps3controller.ui.components.DPadPad
import com.antigravity.ps3controller.ui.components.JoystickStick
import com.antigravity.ps3controller.ui.components.ShoulderButton
import com.antigravity.ps3controller.ui.components.SystemPillButton
import kotlin.math.roundToInt

@Composable
fun ControllerScreen(
    client: GamepadClient,
    host: String,
    port: Int,
    deadZone: Float,
    sensitivity: Float,
    buttonSensitivity: Float,
    hapticsEnabled: Boolean,
    layoutConfig: ControllerLayoutConfig,
    isEditMode: Boolean,
    onEditModeChanged: (Boolean) -> Unit,
    onUpdateLayoutConfig: (ControllerLayoutConfig) -> Unit,
    onOpenSettings: () -> Unit
) {
    val connectionStatus by client.status.collectAsState()
    val errorMessage by client.errorMessage.collectAsState()

    var buttons by remember { mutableStateOf(ControllerButtons()) }
    var dpad by remember { mutableStateOf(ControllerDPad()) }
    var axes by remember { mutableStateOf(ControllerAxes()) }
    var triggers by remember { mutableStateOf(ControllerTriggers()) }

    var selectedElement by remember { mutableStateOf<LayoutElementType?>(LayoutElementType.ACTION_BUTTONS) }

    fun updateAndSend(
        newButtons: ControllerButtons = buttons,
        newDpad: ControllerDPad = dpad,
        newAxes: ControllerAxes = axes,
        newTriggers: ControllerTriggers = triggers
    ) {
        if (isEditMode) return // Don't fire inputs while customizing layout
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
                isEditMode = isEditMode,
                onToggleEditMode = { onEditModeChanged(!isEditMode) },
                onConnect = { client.connect() },
                onDisconnect = { client.disconnect() },
                onOpenSettings = onOpenSettings
            )

            // CONTROLLER BODY
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
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
                        val leftShouldersLayout = layoutConfig.leftShoulders
                        EditableCluster(
                            type = LayoutElementType.LEFT_SHOULDERS,
                            layout = leftShouldersLayout,
                            isEditMode = isEditMode,
                            isSelected = selectedElement == LayoutElementType.LEFT_SHOULDERS,
                            onSelect = { selectedElement = LayoutElementType.LEFT_SHOULDERS },
                            onDragDelta = { dx, dy ->
                                val updated = leftShouldersLayout.copy(
                                    offsetX = leftShouldersLayout.offsetX + dx,
                                    offsetY = leftShouldersLayout.offsetY + dy
                                )
                                onUpdateLayoutConfig(layoutConfig.updateElement(LayoutElementType.LEFT_SHOULDERS, updated))
                            }
                        ) {
                            val effSens = leftShouldersLayout.sensitivity * buttonSensitivity
                            Row(horizontalArrangement = Arrangement.spacedBy((8 * leftShouldersLayout.scale).dp)) {
                                ShoulderButton(
                                    label = "L2",
                                    width = 68.dp,
                                    height = 36.dp,
                                    scale = leftShouldersLayout.scale,
                                    sensitivity = effSens,
                                    onPressChanged = { isPressed ->
                                        val trigVal = TouchInputMapper.mapTrigger(if (isPressed) 1f else 0f, effSens)
                                        updateAndSend(
                                            newButtons = buttons.copy(l2 = isPressed),
                                            newTriggers = triggers.copy(l2 = trigVal)
                                        )
                                    }
                                )
                                ShoulderButton(
                                    label = "L1",
                                    width = 68.dp,
                                    height = 36.dp,
                                    scale = leftShouldersLayout.scale,
                                    sensitivity = effSens,
                                    onPressChanged = { isPressed ->
                                        updateAndSend(newButtons = buttons.copy(l1 = isPressed))
                                    }
                                )
                            }
                        }

                        // D-Pad and Left Stick
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val dpadLayout = layoutConfig.dpad
                            EditableCluster(
                                type = LayoutElementType.DPAD,
                                layout = dpadLayout,
                                isEditMode = isEditMode,
                                isSelected = selectedElement == LayoutElementType.DPAD,
                                onSelect = { selectedElement = LayoutElementType.DPAD },
                                onDragDelta = { dx, dy ->
                                    val updated = dpadLayout.copy(
                                        offsetX = dpadLayout.offsetX + dx,
                                        offsetY = dpadLayout.offsetY + dy
                                    )
                                    onUpdateLayoutConfig(layoutConfig.updateElement(LayoutElementType.DPAD, updated))
                                }
                            ) {
                                val effSens = dpadLayout.sensitivity * buttonSensitivity
                                DPadPad(
                                    scale = dpadLayout.scale,
                                    sensitivity = effSens,
                                    onUp = { updateAndSend(newDpad = dpad.copy(up = it)) },
                                    onDown = { updateAndSend(newDpad = dpad.copy(down = it)) },
                                    onLeft = { updateAndSend(newDpad = dpad.copy(left = it)) },
                                    onRight = { updateAndSend(newDpad = dpad.copy(right = it)) }
                                )
                            }

                            val leftStickLayout = layoutConfig.leftStick
                            EditableCluster(
                                type = LayoutElementType.LEFT_STICK,
                                layout = leftStickLayout,
                                isEditMode = isEditMode,
                                isSelected = selectedElement == LayoutElementType.LEFT_STICK,
                                onSelect = { selectedElement = LayoutElementType.LEFT_STICK },
                                onDragDelta = { dx, dy ->
                                    val updated = leftStickLayout.copy(
                                        offsetX = leftStickLayout.offsetX + dx,
                                        offsetY = leftStickLayout.offsetY + dy
                                    )
                                    onUpdateLayoutConfig(layoutConfig.updateElement(LayoutElementType.LEFT_STICK, updated))
                                }
                            ) {
                                JoystickStick(
                                    size = (136 * leftStickLayout.scale).dp,
                                    deadZone = deadZone,
                                    sensitivity = sensitivity * leftStickLayout.sensitivity,
                                    onMove = { x, y ->
                                        updateAndSend(newAxes = axes.copy(lx = x, ly = y))
                                    }
                                )
                            }
                        }
                    }

                    // CENTER CLUSTER: System buttons (Select, PS, Start) & L3/R3
                    val centerLayout = layoutConfig.centerControls
                    EditableCluster(
                        type = LayoutElementType.CENTER_CONTROLS,
                        layout = centerLayout,
                        isEditMode = isEditMode,
                        isSelected = selectedElement == LayoutElementType.CENTER_CONTROLS,
                        onSelect = { selectedElement = LayoutElementType.CENTER_CONTROLS },
                        onDragDelta = { dx, dy ->
                            val updated = centerLayout.copy(
                                offsetX = centerLayout.offsetX + dx,
                                offsetY = centerLayout.offsetY + dy
                            )
                            onUpdateLayoutConfig(layoutConfig.updateElement(LayoutElementType.CENTER_CONTROLS, updated))
                        }
                    ) {
                        val effSens = centerLayout.sensitivity * buttonSensitivity
                        Column(
                            modifier = Modifier.fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceAround
                        ) {
                            // L3 / R3 Stick Clicks
                            Row(horizontalArrangement = Arrangement.spacedBy((16 * centerLayout.scale).dp)) {
                                SystemPillButton(
                                    label = "L3",
                                    scale = centerLayout.scale,
                                    sensitivity = effSens,
                                    onPressChanged = { updateAndSend(newButtons = buttons.copy(l3 = it)) }
                                )
                                SystemPillButton(
                                    label = "R3",
                                    scale = centerLayout.scale,
                                    sensitivity = effSens,
                                    onPressChanged = { updateAndSend(newButtons = buttons.copy(r3 = it)) }
                                )
                            }

                            // Select, PS, Start
                            Row(
                                horizontalArrangement = Arrangement.spacedBy((12 * centerLayout.scale).dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SystemPillButton(
                                    label = "SELECT",
                                    scale = centerLayout.scale,
                                    sensitivity = effSens,
                                    onPressChanged = { updateAndSend(newButtons = buttons.copy(select = it)) }
                                )
                                SystemPillButton(
                                    label = "PS",
                                    isSpecial = true,
                                    scale = centerLayout.scale,
                                    sensitivity = effSens,
                                    onPressChanged = { updateAndSend(newButtons = buttons.copy(ps = it)) }
                                )
                                SystemPillButton(
                                    label = "START",
                                    scale = centerLayout.scale,
                                    sensitivity = effSens,
                                    onPressChanged = { updateAndSend(newButtons = buttons.copy(start = it)) }
                                )
                            }
                        }
                    }

                    // RIGHT CLUSTER: Shoulders, Action Buttons, Right Stick
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {
                        // Right Shoulders
                        val rightShouldersLayout = layoutConfig.rightShoulders
                        EditableCluster(
                            type = LayoutElementType.RIGHT_SHOULDERS,
                            layout = rightShouldersLayout,
                            isEditMode = isEditMode,
                            isSelected = selectedElement == LayoutElementType.RIGHT_SHOULDERS,
                            onSelect = { selectedElement = LayoutElementType.RIGHT_SHOULDERS },
                            onDragDelta = { dx, dy ->
                                val updated = rightShouldersLayout.copy(
                                    offsetX = rightShouldersLayout.offsetX + dx,
                                    offsetY = rightShouldersLayout.offsetY + dy
                                )
                                onUpdateLayoutConfig(layoutConfig.updateElement(LayoutElementType.RIGHT_SHOULDERS, updated))
                            }
                        ) {
                            val effSens = rightShouldersLayout.sensitivity * buttonSensitivity
                            Row(horizontalArrangement = Arrangement.spacedBy((8 * rightShouldersLayout.scale).dp)) {
                                ShoulderButton(
                                    label = "R1",
                                    width = 68.dp,
                                    height = 36.dp,
                                    scale = rightShouldersLayout.scale,
                                    sensitivity = effSens,
                                    onPressChanged = { isPressed ->
                                        updateAndSend(newButtons = buttons.copy(r1 = isPressed))
                                    }
                                )
                                ShoulderButton(
                                    label = "R2",
                                    width = 68.dp,
                                    height = 36.dp,
                                    scale = rightShouldersLayout.scale,
                                    sensitivity = effSens,
                                    onPressChanged = { isPressed ->
                                        val trigVal = TouchInputMapper.mapTrigger(if (isPressed) 1f else 0f, effSens)
                                        updateAndSend(
                                            newButtons = buttons.copy(r2 = isPressed),
                                            newTriggers = triggers.copy(r2 = trigVal)
                                        )
                                    }
                                )
                            }
                        }

                        // Right Stick and Action Buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val rightStickLayout = layoutConfig.rightStick
                            EditableCluster(
                                type = LayoutElementType.RIGHT_STICK,
                                layout = rightStickLayout,
                                isEditMode = isEditMode,
                                isSelected = selectedElement == LayoutElementType.RIGHT_STICK,
                                onSelect = { selectedElement = LayoutElementType.RIGHT_STICK },
                                onDragDelta = { dx, dy ->
                                    val updated = rightStickLayout.copy(
                                        offsetX = rightStickLayout.offsetX + dx,
                                        offsetY = rightStickLayout.offsetY + dy
                                    )
                                    onUpdateLayoutConfig(layoutConfig.updateElement(LayoutElementType.RIGHT_STICK, updated))
                                }
                            ) {
                                JoystickStick(
                                    size = (136 * rightStickLayout.scale).dp,
                                    deadZone = deadZone,
                                    sensitivity = sensitivity * rightStickLayout.sensitivity,
                                    onMove = { x, y ->
                                        updateAndSend(newAxes = axes.copy(rx = x, ry = y))
                                    }
                                )
                            }

                            val actionButtonsLayout = layoutConfig.actionButtons
                            EditableCluster(
                                type = LayoutElementType.ACTION_BUTTONS,
                                layout = actionButtonsLayout,
                                isEditMode = isEditMode,
                                isSelected = selectedElement == LayoutElementType.ACTION_BUTTONS,
                                onSelect = { selectedElement = LayoutElementType.ACTION_BUTTONS },
                                onDragDelta = { dx, dy ->
                                    val updated = actionButtonsLayout.copy(
                                        offsetX = actionButtonsLayout.offsetX + dx,
                                        offsetY = actionButtonsLayout.offsetY + dy
                                    )
                                    onUpdateLayoutConfig(layoutConfig.updateElement(LayoutElementType.ACTION_BUTTONS, updated))
                                }
                            ) {
                                val effSens = actionButtonsLayout.sensitivity * buttonSensitivity
                                ActionButtonsPad(
                                    scale = actionButtonsLayout.scale,
                                    sensitivity = effSens,
                                    onTriangle = { updateAndSend(newButtons = buttons.copy(triangle = it)) },
                                    onCircle = { updateAndSend(newButtons = buttons.copy(circle = it)) },
                                    onCross = { updateAndSend(newButtons = buttons.copy(cross = it)) },
                                    onSquare = { updateAndSend(newButtons = buttons.copy(square = it)) }
                                )
                            }
                        }
                    }
                }

                // EDIT MODE FLOATING INSPECTOR TOOLBAR
                if (isEditMode) {
                    val selType = selectedElement ?: LayoutElementType.ACTION_BUTTONS
                    val currentElemLayout = layoutConfig.getElement(selType)

                    EditInspectorToolbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(0.85f),
                        selectedType = selType,
                        elementLayout = currentElemLayout,
                        onUpdate = { newElem ->
                            onUpdateLayoutConfig(layoutConfig.updateElement(selType, newElem))
                        },
                        onResetCurrent = {
                            onUpdateLayoutConfig(layoutConfig.updateElement(selType, ElementLayout()))
                        },
                        onResetAll = {
                            onUpdateLayoutConfig(ControllerLayoutConfig())
                        },
                        onDone = {
                            onEditModeChanged(false)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EditableCluster(
    type: LayoutElementType,
    layout: ElementLayout,
    isEditMode: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDragDelta: (Float, Float) -> Unit,
    content: @Composable () -> Unit
) {
    val borderModifier = if (isEditMode) {
        Modifier
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFF6366F1) else Color(0x5594A3B8),
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                if (isSelected) Color(0x226366F1) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .padding(4.dp)
            .pointerInput(type) {
                detectDragGestures(
                    onDragStart = { onSelect() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount.x, dragAmount.y)
                    }
                )
            }
            .clickable { onSelect() }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(layout.offsetX.roundToInt(), layout.offsetY.roundToInt()) }
            .then(borderModifier),
        contentAlignment = Alignment.Center
    ) {
        content()
        if (isEditMode && isSelected) {
            Text(
                text = type.displayName,
                color = Color(0xFFA5B4FC),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .background(Color(0xDD1E1B4B), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun EditInspectorToolbar(
    modifier: Modifier = Modifier,
    selectedType: LayoutElementType,
    elementLayout: ElementLayout,
    onUpdate: (ElementLayout) -> Unit,
    onResetCurrent: () -> Unit,
    onResetAll: () -> Unit,
    onDone: () -> Unit
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(16.dp)),
        color = Color(0xF0181B20),
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF374151))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Header: Element Title and Done Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("✏️ Layout Editor: ", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Text(
                        selectedType.displayName,
                        color = Color(0xFF60A5FA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onResetCurrent) {
                        Text("Reset This", color = Color(0xFFF87171), fontSize = 11.sp)
                    }
                    TextButton(onClick = onResetAll) {
                        Text("Reset All", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                    Button(
                        onClick = onDone,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Done", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Done", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Controls Row: Size Scale, Sensitivity, Nudge Arrows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Size Scale Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1.1f)
                ) {
                    Text("Size:", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                    IconButton(
                        onClick = { onUpdate(elementLayout.copy(scale = (elementLayout.scale - 0.05f).coerceIn(0.6f, 1.8f))) },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Smaller", tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                    Text("${(elementLayout.scale * 100).toInt()}%", color = Color(0xFF60A5FA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    IconButton(
                        onClick = { onUpdate(elementLayout.copy(scale = (elementLayout.scale + 0.05f).coerceIn(0.6f, 1.8f))) },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Larger", tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                    Slider(
                        value = elementLayout.scale,
                        onValueChange = { onUpdate(elementLayout.copy(scale = it)) },
                        valueRange = 0.6f..1.8f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF60A5FA),
                            activeTrackColor = Color(0xFF60A5FA),
                            inactiveTrackColor = Color(0xFF374151)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Sensitivity Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1.1f)
                ) {
                    Text("Sens:", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                    IconButton(
                        onClick = { onUpdate(elementLayout.copy(sensitivity = (elementLayout.sensitivity - 0.1f).coerceIn(0.5f, 2.5f))) },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Less", tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                    Text(String.format("%.1fx", elementLayout.sensitivity), color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    IconButton(
                        onClick = { onUpdate(elementLayout.copy(sensitivity = (elementLayout.sensitivity + 0.1f).coerceIn(0.5f, 2.5f))) },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "More", tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                    Slider(
                        value = elementLayout.sensitivity,
                        onValueChange = { onUpdate(elementLayout.copy(sensitivity = it)) },
                        valueRange = 0.5f..2.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF10B981),
                            activeTrackColor = Color(0xFF10B981),
                            inactiveTrackColor = Color(0xFF374151)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Position Nudge Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("Nudge:", color = Color(0xFF94A3B8), fontSize = 10.sp)
                    IconButton(
                        onClick = { onUpdate(elementLayout.copy(offsetX = elementLayout.offsetX - 6f)) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Left", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                    IconButton(
                        onClick = { onUpdate(elementLayout.copy(offsetY = elementLayout.offsetY - 6f)) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Up", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                    IconButton(
                        onClick = { onUpdate(elementLayout.copy(offsetY = elementLayout.offsetY + 6f)) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Down", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                    IconButton(
                        onClick = { onUpdate(elementLayout.copy(offsetX = elementLayout.offsetX + 6f)) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Right", tint = Color.White, modifier = Modifier.size(14.dp))
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
    isEditMode: Boolean,
    onToggleEditMode: () -> Unit,
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
            // Edit Buttons Toggle
            Button(
                onClick = onToggleEditMode,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEditMode) Color(0xFF10B981) else Color(0xFF374151)
                ),
                modifier = Modifier.height(28.dp)
            ) {
                Icon(
                    imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = "Edit Layout",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isEditMode) "Done" else "Edit Buttons", fontSize = 11.sp, color = Color.White)
            }

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
