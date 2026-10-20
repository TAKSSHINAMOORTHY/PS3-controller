package com.antigravity.ps3controller.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.ps3controller.math.TouchInputMapper
import com.antigravity.ps3controller.model.ControlId
import com.antigravity.ps3controller.model.ControlLayout
import com.antigravity.ps3controller.model.ControllerAxes
import com.antigravity.ps3controller.model.ControllerButtons
import com.antigravity.ps3controller.model.ControllerDPad
import com.antigravity.ps3controller.model.ControllerLayoutConfig
import com.antigravity.ps3controller.model.ControllerState
import com.antigravity.ps3controller.model.ControllerTriggers
import com.antigravity.ps3controller.network.ConnectionStatus
import com.antigravity.ps3controller.network.GamepadClient
import com.antigravity.ps3controller.ui.components.JoystickStick
import com.antigravity.ps3controller.ui.components.PressablePadButton
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
    val activeTransport by client.activeTransport.collectAsState()
    val activeHost by client.activeHost.collectAsState()
    val errorMessage by client.errorMessage.collectAsState()

    var selectedControl by remember { mutableStateOf(ControlId.CROSS) }

    fun onDragControl(id: ControlId, dx: Float, dy: Float) {
        val current = layoutConfig.getControl(id)
        onUpdateLayoutConfig(
            layoutConfig.updateControl(
                id,
                current.copy(offsetX = current.offsetX + dx, offsetY = current.offsetY + dy)
            )
        )
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
            // TOP STATUS & ACTION BAR
            TopStatusBar(
                status = connectionStatus,
                transport = activeTransport,
                host = activeHost,
                port = port,
                error = errorMessage,
                isEditMode = isEditMode,
                onToggleEditMode = { onEditModeChanged(!isEditMode) },
                onConnect = { client.connect() },
                onDisconnect = { client.disconnect() },
                onOpenSettings = onOpenSettings
            )

            // FULL-CANVAS CONTROLLER BODY
            // Every control is a direct child of the full screen bounds, allowing unrestricted free selection
            // and full-range drag movement anywhere on screen with zero hit-testing clipping or ghost boxes.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                val dpadCenterX = 96.dp
                val dpadCenterY = (maxHeight / 2) + 26.dp

                val actionCenterX = maxWidth - 96.dp
                val actionCenterY = dpadCenterY

                val stickLeftX = dpadCenterX + 124.dp
                val stickLeftY = dpadCenterY - 68.dp

                val stickRightX = actionCenterX - 124.dp - 136.dp
                val stickRightY = dpadCenterY - 68.dp

                // 1. LEFT SHOULDERS (L2, L1)
                val l2Layout = layoutConfig.getControl(ControlId.L2)
                EditableControl(
                    id = ControlId.L2,
                    layout = l2Layout,
                    isEditMode = isEditMode,
                    isSelected = selectedControl == ControlId.L2,
                    baseX = 0.dp,
                    baseY = 6.dp,
                    onSelect = { selectedControl = ControlId.L2 },
                    onDragDelta = { dx, dy -> onDragControl(ControlId.L2, dx, dy) }
                ) {
                    val effSens = l2Layout.sensitivity * buttonSensitivity
                    ShoulderButton(
                        label = "L2",
                        width = 68.dp,
                        height = 36.dp,
                        scale = l2Layout.scale,
                        sensitivity = effSens,
                        enabled = !isEditMode,
                        onPressChanged = { isPressed ->
                            val trigVal = TouchInputMapper.mapTrigger(if (isPressed) 1f else 0f, effSens)
                            client.updateTrigger(l2 = trigVal, isL2Pressed = isPressed)
                        }
                    )
                }

                val l1Layout = layoutConfig.getControl(ControlId.L1)
                EditableControl(
                    id = ControlId.L1,
                    layout = l1Layout,
                    isEditMode = isEditMode,
                    isSelected = selectedControl == ControlId.L1,
                    baseX = 76.dp,
                    baseY = 6.dp,
                    onSelect = { selectedControl = ControlId.L1 },
                    onDragDelta = { dx, dy -> onDragControl(ControlId.L1, dx, dy) }
                ) {
                    val effSens = l1Layout.sensitivity * buttonSensitivity
                    ShoulderButton(
                        label = "L1",
                        width = 68.dp,
                        height = 36.dp,
                        scale = l1Layout.scale,
                        sensitivity = effSens,
                        enabled = !isEditMode,
                        onPressChanged = { isPressed ->
                            client.updateButtons { it.copy(l1 = isPressed) }
                        }
                    )
                }

                // 2. D-PAD DIRECTIONAL BUTTONS (UP, LEFT, RIGHT, DOWN)
                val upLayout = layoutConfig.getControl(ControlId.DPAD_UP)
                EditableControl(
                    id = ControlId.DPAD_UP,
                    layout = upLayout,
                    isEditMode = isEditMode,
                    isSelected = selectedControl == ControlId.DPAD_UP,
                    baseX = dpadCenterX - 26.dp,
                    baseY = dpadCenterY - 80.dp,
                    onSelect = { selectedControl = ControlId.DPAD_UP },
                    onDragDelta = { dx, dy -> onDragControl(ControlId.DPAD_UP, dx, dy) }
                ) {
                    PressablePadButton(
                        label = "▲",
                        accentColor = Color(0xFF94A3B8),
                        isRound = false,
                        size = 52.dp,
                        scale = upLayout.scale,
                        sensitivity = upLayout.sensitivity * buttonSensitivity,
                        fontSize = 18,
                        enabled = !isEditMode,
                        onPressChanged = { isPressed -> client.updateDpad { it.copy(up = isPressed) } }
                    )
                }

                val leftLayout = layoutConfig.getControl(ControlId.DPAD_LEFT)
                EditableControl(
                    id = ControlId.DPAD_LEFT,
                    layout = leftLayout,
                    isEditMode = isEditMode,
                    isSelected = selectedControl == ControlId.DPAD_LEFT,
                    baseX = dpadCenterX - 80.dp,
                    baseY = dpadCenterY - 26.dp,
                    onSelect = { selectedControl = ControlId.DPAD_LEFT },
                    onDragDelta = { dx, dy -> onDragControl(ControlId.DPAD_LEFT, dx, dy) }
                ) {
                    PressablePadButton(
                        label = "◀",
                        accentColor = Color(0xFF94A3B8),
                        isRound = false,
                        size = 52.dp,
                        scale = leftLayout.scale,
                        sensitivity = leftLayout.sensitivity * buttonSensitivity,
                        fontSize = 18,
                        enabled = !isEditMode,
                        onPressChanged = { isPressed -> client.updateDpad { it.copy(left = isPressed) } }
                    )
                }

                val rightLayout = layoutConfig.getControl(ControlId.DPAD_RIGHT)
                EditableControl(
                    id = ControlId.DPAD_RIGHT,
                    layout = rightLayout,
                    isEditMode = isEditMode,
                    isSelected = selectedControl == ControlId.DPAD_RIGHT,
                    baseX = dpadCenterX + 28.dp,
                    baseY = dpadCenterY - 26.dp,
                    onSelect = { selectedControl = ControlId.DPAD_RIGHT },
                    onDragDelta = { dx, dy -> onDragControl(ControlId.DPAD_RIGHT, dx, dy) }
                ) {
                    PressablePadButton(
                        label = "▶",
                        accentColor = Color(0xFF94A3B8),
                        isRound = false,
                        size = 52.dp,
                        scale = rightLayout.scale,
                        sensitivity = rightLayout.sensitivity * buttonSensitivity,
                        fontSize = 18,
                        enabled = !isEditMode,
                        onPressChanged = { isPressed -> client.updateDpad { it.copy(right = isPressed) } }
                    )
                }

                val downLayout = layoutConfig.getControl(ControlId.DPAD_DOWN)
                EditableControl(
                    id = ControlId.DPAD_DOWN,
                    layout = downLayout,
                    isEditMode = isEditMode,
                    isSelected = selectedControl == ControlId.DPAD_DOWN,
                    baseX = dpadCenterX - 26.dp,
                    baseY = dpadCenterY + 28.dp,
                    onSelect = { selectedControl = ControlId.DPAD_DOWN },
                    onDragDelta = { dx, dy -> onDragControl(ControlId.DPAD_DOWN, dx, dy) }
                ) {
                    PressablePadButton(
                        label = "▼",
                        accentColor = Color(0xFF94A3B8),
                        isRound = false,
                        size = 52.dp,
                        scale = downLayout.scale,
                        sensitivity = downLayout.sensitivity * buttonSensitivity,
                        fontSize = 18,
                        enabled = !isEditMode,
                        onPressChanged = { isPressed -> client.updateDpad { it.copy(down = isPressed) } }
                    )
                }

                // 3. LEFT ANALOG STICK
                val leftStickLayout = layoutConfig.getControl(ControlId.LEFT_STICK)
                EditableControl(
                    id = ControlId.LEFT_STICK,
                    layout = leftStickLayout,
                    isEditMode = isEditMode,
                    isSelected = selectedControl == ControlId.LEFT_STICK,
                    baseX = stickLeftX,
                    baseY = stickLeftY,
                    onSelect = { selectedControl = ControlId.LEFT_STICK },
                    onDragDelta = { dx, dy -> onDragControl(ControlId.LEFT_STICK, dx, dy) }
                ) {
                    JoystickStick(
                        size = (136 * leftStickLayout.scale).dp,
                        deadZone = deadZone,
                        sensitivity = sensitivity * leftStickLayout.sensitivity,
                        enabled = !isEditMode,
                        onMove = { x, y ->
                            client.updateStick(isLeft = true, x = x, y = y)
                        }
                    )
                }

                // 4. CENTER CONTROLS (L3, R3, SELECT, PS, START)
                val l3Layout = layoutConfig.getControl(ControlId.L3)
                EditableControl(
                    id = ControlId.L3,
                    layout = l3Layout,
                    isEditMode = isEditMode,
                    isSelected = selectedControl == ControlId.L3,
                    baseX = (maxWidth / 2) - 64.dp,
                    baseY = 12.dp,
                    onSelect = { selectedControl = ControlId.L3 },
                    onDragDelta = { dx, dy -> onDragControl(ControlId.L3, dx, dy) }
                ) {
                    SystemPillButton(
                        label = "L3",
                        scale = l3Layout.scale,
                        sensitivity = l3Layout.sensitivity * buttonSensitivity,
                        enabled = !isEditMode,
                        onPressChanged = { isPressed -> client.updateButtons { it.copy(l3 = isPressed) } }
                    )
                }

                val r3Layout = layoutConfig.getControl(ControlId.R3)
                EditableControl(
                    id = ControlId.R3,
                    layout = r3Layout,
                    isEditMode = isEditMode,
                    isSelected = selectedControl == ControlId.R3,
                    baseX = (maxWidth / 2) + 8.dp,
                    baseY = 12.dp,
                    onSelect = { selectedControl = ControlId.R3 },
                    onDragDelta = { dx, dy -> onDragControl(ControlId.R3, dx, dy) }
                ) {
                    SystemPillButton(
                        label = "R3",
                        scale = r3Layout.scale,
                        sensitivity = r3Layout.sensitivity * buttonSensitivity,
                        enabled = !isEditMode,
                        onPressChanged = { isPressed -> client.updateButtons { it.copy(r3 = isPressed) } }
                    )
                }

                val selectLayout = layoutConfig.getControl(ControlId.SELECT)
                EditableControl(
                    id = ControlId.SELECT,
                    layout = selectLayout,
                    isEditMode = isEditMode,
                    isSelected = selectedControl == ControlId.SELECT,
                    baseX = (maxWidth / 2) - 96.dp,
                    baseY = maxHeight - 56.dp,
                    onSelect = { selectedControl = ControlId.SELECT },
                    onDragDelta = { dx, dy -> onDragControl(ControlId.SELECT, dx, dy) }
                ) {
                    SystemPillButton(
                        label = "SELECT",
                        scale = selectLayout.scale,
                        sensitivity = selectLayout.sensitivity * buttonSensitivity,
                        enabled = !isEditMode,
                        onPressChanged = { isPressed -> client.updateButtons { it.copy(select = isPressed) } }
                    )
                }

                val psLayout = layoutConfig.getControl(ControlId.PS)
                EditableControl(
                    id = ControlId.PS,
                    layout = psLayout,
                    isEditMode = isEditMode,
                    isSelected = selectedControl == ControlId.PS,
                    baseX = (maxWidth / 2) - 22.dp,
                    baseY = maxHeight - 62.dp,
                    onSelect = { selectedControl = ControlId.PS },
                    onDragDelta = { dx, dy -> onDragControl(ControlId.PS, dx, dy) }
                ) {
                    SystemPillButton(
                        label = "PS",
                        isSpecial = true,
                        scale = psLayout.scale,
                        sensitivity = psLayout.sensitivity * buttonSensitivity,
                        enabled = !isEditMode,
                        onPressChanged = { isPressed -> client.updateButtons { it.copy(ps = isPressed) } }
                    )
                }

                val startLayout = layoutConfig.getControl(ControlId.START)
                EditableControl(
                    id = ControlId.START,
                    layout = startLayout,
                    isEditMode = isEditMode,
                    isSelected = selectedControl == ControlId.START,
                    baseX = (maxWidth / 2) + 40.dp,
                    baseY = maxHeight - 56.dp,
                    onSelect = { selectedControl = ControlId.START },
                    onDragDelta = { dx, dy -> onDragControl(ControlId.START, dx, dy) }
                ) {
                    SystemPillButton(
                        label = "START",
                        scale = startLayout.scale,
                        sensitivity = startLayout.sensitivity * buttonSensitivity,
                        enabled = !isEditMode,
                        onPressChanged = { isPressed -> client.updateButtons { it.copy(start = isPressed) } }
                    )
                }

                // 5. RIGHT SHOULDERS (R1, R2)
                val r1Layout = layoutConfig.getControl(ControlId.R1)
                EditableControl(
                    id = ControlId.R1,
                    layout = r1Layout,
                    isEditMode = isEditMode,
                    isSelected = selectedControl == ControlId.R1,
                    baseX = maxWidth - 144.dp,
                    baseY = 6.dp,
                    onSelect = { selectedControl = ControlId.R1 },
                    onDragDelta = { dx, dy -> onDragControl(ControlId.R1, dx, dy) }
                ) {
                    val effSens = r1Layout.sensitivity * buttonSensitivity
                    ShoulderButton(
                        label = "R1",
                        width = 68.dp,
                        height = 36.dp,
                        scale = r1Layout.scale,
                        sensitivity = effSens,
                        enabled = !isEditMode,
                        onPressChanged = { isPressed ->
                            client.updateButtons { it.copy(r1 = isPressed) }
                        }
                    )
                }

                val r2Layout = layoutConfig.getControl(ControlId.R2)
                EditableControl(
                    id = ControlId.R2,
                    layout = r2Layout,
                    isEditMode = isEditMode,
                    isSelected = selectedControl == ControlId.R2,
                    baseX = maxWidth - 68.dp,
                    baseY = 6.dp,
                    onSelect = { selectedControl = ControlId.R2 },
                    onDragDelta = { dx, dy -> onDragControl(ControlId.R2, dx, dy) }
                ) {
                    val effSens = r2Layout.sensitivity * buttonSensitivity
                    ShoulderButton(
                        label = "R2",
                        width = 68.dp,
                        height = 36.dp,
                        scale = r2Layout.scale,
                        sensitivity = effSens,
                        enabled = !isEditMode,
                        onPressChanged = { isPressed ->
                            val trigVal = TouchInputMapper.mapTrigger(if (isPressed) 1f else 0f, effSens)
                            client.updateTrigger(r2 = trigVal, isR2Pressed = isPressed)
                        }
                    )
                }

                // 6. RIGHT ANALOG STICK
                val rightStickLayout = layoutConfig.getControl(ControlId.RIGHT_STICK)
                EditableControl(
                    id = ControlId.RIGHT_STICK,
                    layout = rightStickLayout,
                    isEditMode = isEditMode,
                    isSelected = selectedControl == ControlId.RIGHT_STICK,
                    baseX = stickRightX,
                    baseY = stickRightY,
                    onSelect = { selectedControl = ControlId.RIGHT_STICK },
                    onDragDelta = { dx, dy -> onDragControl(ControlId.RIGHT_STICK, dx, dy) }
                ) {
                    JoystickStick(
                        size = (136 * rightStickLayout.scale).dp,
                        deadZone = deadZone,
                        sensitivity = sensitivity * rightStickLayout.sensitivity,
                        enabled = !isEditMode,
                        onMove = { x, y ->
                            client.updateStick(isLeft = false, x = x, y = y)
                        }
                    )
                }

                // 7. ACTION BUTTONS (TRIANGLE, SQUARE, CIRCLE, CROSS)
                val triLayout = layoutConfig.getControl(ControlId.TRIANGLE)
                EditableControl(
                    id = ControlId.TRIANGLE,
                    layout = triLayout,
                    isEditMode = isEditMode,
                    isSelected = selectedControl == ControlId.TRIANGLE,
                    baseX = actionCenterX - 28.dp,
                    baseY = actionCenterY - 84.dp,
                    onSelect = { selectedControl = ControlId.TRIANGLE },
                    onDragDelta = { dx, dy -> onDragControl(ControlId.TRIANGLE, dx, dy) }
                ) {
                    PressablePadButton(
                        label = "△",
                        accentColor = Color(0xFF10B981),
                        size = 56.dp,
                        scale = triLayout.scale,
                        sensitivity = triLayout.sensitivity * buttonSensitivity,
                        fontSize = 22,
                        enabled = !isEditMode,
                        onPressChanged = { isPressed -> client.updateButtons { it.copy(triangle = isPressed) } }
                    )
                }

                val sqLayout = layoutConfig.getControl(ControlId.SQUARE)
                EditableControl(
                    id = ControlId.SQUARE,
                    layout = sqLayout,
                    isEditMode = isEditMode,
                    isSelected = selectedControl == ControlId.SQUARE,
                    baseX = actionCenterX - 84.dp,
                    baseY = actionCenterY - 28.dp,
                    onSelect = { selectedControl = ControlId.SQUARE },
                    onDragDelta = { dx, dy -> onDragControl(ControlId.SQUARE, dx, dy) }
                ) {
                    PressablePadButton(
                        label = "□",
                        accentColor = Color(0xFFEC4899),
                        size = 56.dp,
                        scale = sqLayout.scale,
                        sensitivity = sqLayout.sensitivity * buttonSensitivity,
                        fontSize = 22,
                        enabled = !isEditMode,
                        onPressChanged = { isPressed -> client.updateButtons { it.copy(square = isPressed) } }
                    )
                }

                val circLayout = layoutConfig.getControl(ControlId.CIRCLE)
                EditableControl(
                    id = ControlId.CIRCLE,
                    layout = circLayout,
                    isEditMode = isEditMode,
                    isSelected = selectedControl == ControlId.CIRCLE,
                    baseX = actionCenterX + 28.dp,
                    baseY = actionCenterY - 28.dp,
                    onSelect = { selectedControl = ControlId.CIRCLE },
                    onDragDelta = { dx, dy -> onDragControl(ControlId.CIRCLE, dx, dy) }
                ) {
                    PressablePadButton(
                        label = "○",
                        accentColor = Color(0xFFEF4444),
                        size = 56.dp,
                        scale = circLayout.scale,
                        sensitivity = circLayout.sensitivity * buttonSensitivity,
                        fontSize = 22,
                        enabled = !isEditMode,
                        onPressChanged = { isPressed -> client.updateButtons { it.copy(circle = isPressed) } }
                    )
                }

                val crossLayout = layoutConfig.getControl(ControlId.CROSS)
                EditableControl(
                    id = ControlId.CROSS,
                    layout = crossLayout,
                    isEditMode = isEditMode,
                    isSelected = selectedControl == ControlId.CROSS,
                    baseX = actionCenterX - 28.dp,
                    baseY = actionCenterY + 28.dp,
                    onSelect = { selectedControl = ControlId.CROSS },
                    onDragDelta = { dx, dy -> onDragControl(ControlId.CROSS, dx, dy) }
                ) {
                    PressablePadButton(
                        label = "✕",
                        accentColor = Color(0xFF3B82F6),
                        size = 56.dp,
                        scale = crossLayout.scale,
                        sensitivity = crossLayout.sensitivity * buttonSensitivity,
                        fontSize = 20,
                        enabled = !isEditMode,
                        onPressChanged = { isPressed -> client.updateButtons { it.copy(cross = isPressed) } }
                    )
                }

                // EDIT MODE FLOATING INSPECTOR TOOLBAR
                if (isEditMode) {
                    val currentControlLayout = layoutConfig.getControl(selectedControl)

                    EditInspectorToolbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(0.92f),
                        selectedControl = selectedControl,
                        controlLayout = currentControlLayout,
                        onSelectControl = { selectedControl = it },
                        onUpdate = { newLayout ->
                            onUpdateLayoutConfig(layoutConfig.updateControl(selectedControl, newLayout))
                        },
                        onResetCurrent = {
                            onUpdateLayoutConfig(layoutConfig.resetControl(selectedControl))
                        },
                        onResetAll = {
                            onUpdateLayoutConfig(layoutConfig.resetAll())
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
private fun EditableControl(
    id: ControlId,
    layout: ControlLayout,
    isEditMode: Boolean,
    isSelected: Boolean,
    baseX: Dp,
    baseY: Dp,
    onSelect: () -> Unit,
    onDragDelta: (Float, Float) -> Unit,
    content: @Composable () -> Unit
) {
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnDragDelta by rememberUpdatedState(onDragDelta)

    val offsetModifier = Modifier.offset {
        IntOffset(
            (baseX.toPx() + layout.offsetX).roundToInt(),
            (baseY.toPx() + layout.offsetY).roundToInt()
        )
    }
    val alphaModifier = Modifier.alpha(layout.opacity.coerceIn(0.15f, 1.0f))

    Box(
        modifier = Modifier
            .then(offsetModifier)
            .then(alphaModifier),
        contentAlignment = Alignment.Center
    ) {
        content()

        if (isEditMode) {
            // Touch interception overlay on top of the control in Edit Mode
            // Provides instant selection on touch down and smooth 1:1 real-time drag tracking
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) Color(0xFF818CF8) else Color(0x6694A3B8),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .background(
                        if (isSelected) Color(0x336366F1) else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .pointerInput(id) {
                        detectTapGestures(
                            onTap = { currentOnSelect() }
                        )
                    }
                    .pointerInput(id) {
                        detectDragGestures(
                            onDragStart = { currentOnSelect() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                currentOnDragDelta(dragAmount.x, dragAmount.y)
                            }
                        )
                    }
            )

            if (isSelected) {
                Surface(
                    color = Color(0xEE1E1B4B),
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF818CF8)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-18).dp)
                ) {
                    Text(
                        text = id.displayName,
                        color = Color(0xFFC7D2FE),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EditInspectorToolbar(
    modifier: Modifier = Modifier,
    selectedControl: ControlId,
    controlLayout: ControlLayout,
    onSelectControl: (ControlId) -> Unit,
    onUpdate: (ControlLayout) -> Unit,
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
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Header Row: Control selector & Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Control Picker / Name
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val allControls = ControlId.values()
                    val currentIndex = allControls.indexOf(selectedControl)

                    IconButton(
                        onClick = {
                            val prevIndex = if (currentIndex > 0) currentIndex - 1 else allControls.size - 1
                            onSelectControl(allControls[prevIndex])
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Control", tint = Color.White, modifier = Modifier.size(14.dp))
                    }

                    Text("🎯 ", fontSize = 12.sp)
                    Text(
                        selectedControl.displayName,
                        color = Color(0xFF60A5FA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = {
                            val nextIndex = if (currentIndex < allControls.size - 1) currentIndex + 1 else 0
                            onSelectControl(allControls[nextIndex])
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Control", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }

                // Action buttons: Reset This, Reset All, Done
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onResetCurrent) {
                        Text("Reset Control", color = Color(0xFFF87171), fontSize = 11.sp)
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

            // Sliders Row: Size Scale, Sensitivity, Opacity & Nudge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Size Scale Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Size:", color = Color(0xFFCBD5E1), fontSize = 10.sp)
                    IconButton(
                        onClick = { onUpdate(controlLayout.copy(scale = (controlLayout.scale - 0.05f).coerceIn(0.5f, 2.0f))) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Smaller", tint = Color.White, modifier = Modifier.size(11.dp))
                    }
                    Text("${(controlLayout.scale * 100).toInt()}%", color = Color(0xFF60A5FA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    IconButton(
                        onClick = { onUpdate(controlLayout.copy(scale = (controlLayout.scale + 0.05f).coerceIn(0.5f, 2.0f))) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Larger", tint = Color.White, modifier = Modifier.size(11.dp))
                    }
                    Slider(
                        value = controlLayout.scale,
                        onValueChange = { onUpdate(controlLayout.copy(scale = it)) },
                        valueRange = 0.5f..2.0f,
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
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Sens:", color = Color(0xFFCBD5E1), fontSize = 10.sp)
                    IconButton(
                        onClick = { onUpdate(controlLayout.copy(sensitivity = (controlLayout.sensitivity - 0.1f).coerceIn(0.2f, 3.0f))) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Less", tint = Color.White, modifier = Modifier.size(11.dp))
                    }
                    Text(String.format("%.1fx", controlLayout.sensitivity), color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    IconButton(
                        onClick = { onUpdate(controlLayout.copy(sensitivity = (controlLayout.sensitivity + 0.1f).coerceIn(0.2f, 3.0f))) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "More", tint = Color.White, modifier = Modifier.size(11.dp))
                    }
                    Slider(
                        value = controlLayout.sensitivity,
                        onValueChange = { onUpdate(controlLayout.copy(sensitivity = it)) },
                        valueRange = 0.2f..3.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF10B981),
                            activeTrackColor = Color(0xFF10B981),
                            inactiveTrackColor = Color(0xFF374151)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Nudge Buttons (fine offset adjustment)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("Nudge:", color = Color(0xFF94A3B8), fontSize = 10.sp)
                    IconButton(
                        onClick = { onUpdate(controlLayout.copy(offsetX = controlLayout.offsetX - 5f)) },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Left", tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                    IconButton(
                        onClick = { onUpdate(controlLayout.copy(offsetY = controlLayout.offsetY - 5f)) },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Up", tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                    IconButton(
                        onClick = { onUpdate(controlLayout.copy(offsetY = controlLayout.offsetY + 5f)) },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Down", tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                    IconButton(
                        onClick = { onUpdate(controlLayout.copy(offsetX = controlLayout.offsetX + 5f)) },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Right", tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TopStatusBar(
    status: ConnectionStatus,
    transport: String,
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
        ConnectionStatus.CONNECTED -> if (transport == "USB") Color(0xFF10B981) else Color(0xFF0EA5E9)
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
            val transportLabel = if (status == ConnectionStatus.CONNECTED && transport != "DISCONNECTED") " ($transport)" else ""
            Text(
                text = "$status$transportLabel: $host:$port",
                color = if (status == ConnectionStatus.CONNECTED) (if (transport == "USB") Color(0xFF34D399) else Color(0xFF38BDF8)) else Color(0xFFCBD5E1),
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
