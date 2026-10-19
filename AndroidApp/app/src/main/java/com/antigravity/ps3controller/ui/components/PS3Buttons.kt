package com.antigravity.ps3controller.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A pressable button that fires state changes on touch-down and touch-up.
 */
@Composable
fun PressablePadButton(
    modifier: Modifier = Modifier,
    label: String,
    accentColor: Color = Color(0xFF3B82F6),
    isRound: Boolean = true,
    size: Dp = 56.dp,
    fontSize: Int = 20,
    scale: Float = 1.0f,
    sensitivity: Float = 1.0f,
    onPressChanged: (Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    val effectiveSize = size * scale
    val effectiveFontSize = (fontSize * scale).coerceAtLeast(10f).toInt()
    val shape = if (isRound) CircleShape else RoundedCornerShape((8 * scale).dp)
    val bgColor = if (isPressed) {
        accentColor.copy(alpha = (0.85f * sensitivity.coerceIn(0.5f, 1.2f)).coerceIn(0.5f, 1.0f))
    } else {
        Color(0xFF23272E)
    }
    val textColor = if (isPressed) Color.White else accentColor

    Box(
        modifier = modifier
            .size(effectiveSize)
            .shadow(if (isPressed) 2.dp else 4.dp, shape)
            .clip(shape)
            .background(bgColor)
            .border((2 * scale).dp.coerceAtLeast(1.dp), if (isPressed) accentColor else Color(0xFF383E4A), shape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isPressed = true
                    onPressChanged(true)

                    var isUp = false
                    while (!isUp) {
                        val event = awaitPointerEvent()
                        if (event.changes.all { !it.pressed }) {
                            isUp = true
                        }
                    }
                    isPressed = false
                    onPressChanged(false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = effectiveFontSize.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Action Buttons (Triangle, Circle, Cross, Square)
 */
@Composable
fun ActionButtonsPad(
    modifier: Modifier = Modifier,
    scale: Float = 1.0f,
    sensitivity: Float = 1.0f,
    onTriangle: (Boolean) -> Unit,
    onCircle: (Boolean) -> Unit,
    onCross: (Boolean) -> Unit,
    onSquare: (Boolean) -> Unit
) {
    val btnSize = (56 * scale).dp
    val spacing = (6 * scale).dp

    Box(modifier = modifier.size(btnSize * 3 + spacing * 2), contentAlignment = Alignment.Center) {
        // Triangle (Top) - Green
        Box(modifier = Modifier.align(Alignment.TopCenter)) {
            PressablePadButton(
                label = "△",
                accentColor = Color(0xFF10B981),
                size = 56.dp,
                scale = scale,
                sensitivity = sensitivity,
                fontSize = 22,
                onPressChanged = onTriangle
            )
        }

        // Square (Left) - Pink
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            PressablePadButton(
                label = "□",
                accentColor = Color(0xFFEC4899),
                size = 56.dp,
                scale = scale,
                sensitivity = sensitivity,
                fontSize = 22,
                onPressChanged = onSquare
            )
        }

        // Circle (Right) - Red
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            PressablePadButton(
                label = "○",
                accentColor = Color(0xFFEF4444),
                size = 56.dp,
                scale = scale,
                sensitivity = sensitivity,
                fontSize = 22,
                onPressChanged = onCircle
            )
        }

        // Cross (Bottom) - Blue
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            PressablePadButton(
                label = "✕",
                accentColor = Color(0xFF3B82F6),
                size = 56.dp,
                scale = scale,
                sensitivity = sensitivity,
                fontSize = 20,
                onPressChanged = onCross
            )
        }
    }
}

/**
 * Directional Pad (Up, Down, Left, Right)
 */
@Composable
fun DPadPad(
    modifier: Modifier = Modifier,
    scale: Float = 1.0f,
    sensitivity: Float = 1.0f,
    onUp: (Boolean) -> Unit,
    onDown: (Boolean) -> Unit,
    onLeft: (Boolean) -> Unit,
    onRight: (Boolean) -> Unit
) {
    val btnSize = (52 * scale).dp
    val spacing = (4 * scale).dp
    val dpadColor = Color(0xFF94A3B8)

    Box(modifier = modifier.size(btnSize * 3 + spacing * 2), contentAlignment = Alignment.Center) {
        // Up
        Box(modifier = Modifier.align(Alignment.TopCenter)) {
            PressablePadButton(
                label = "▲",
                accentColor = dpadColor,
                isRound = false,
                size = 52.dp,
                scale = scale,
                sensitivity = sensitivity,
                fontSize = 18,
                onPressChanged = onUp
            )
        }

        // Left
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            PressablePadButton(
                label = "◀",
                accentColor = dpadColor,
                isRound = false,
                size = 52.dp,
                scale = scale,
                sensitivity = sensitivity,
                fontSize = 18,
                onPressChanged = onLeft
            )
        }

        // Center hub
        Box(
            modifier = Modifier
                .size(btnSize)
                .background(Color(0xFF1E2229))
        )

        // Right
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            PressablePadButton(
                label = "▶",
                accentColor = dpadColor,
                isRound = false,
                size = 52.dp,
                scale = scale,
                sensitivity = sensitivity,
                fontSize = 18,
                onPressChanged = onRight
            )
        }

        // Down
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            PressablePadButton(
                label = "▼",
                accentColor = dpadColor,
                isRound = false,
                size = 52.dp,
                scale = scale,
                sensitivity = sensitivity,
                fontSize = 18,
                onPressChanged = onDown
            )
        }
    }
}

/**
 * Shoulder Trigger / Bumper Button
 */
@Composable
fun ShoulderButton(
    modifier: Modifier = Modifier,
    label: String,
    width: Dp = 80.dp,
    height: Dp = 38.dp,
    scale: Float = 1.0f,
    sensitivity: Float = 1.0f,
    onPressChanged: (Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape((6 * scale).dp)
    val effectiveWidth = width * scale
    val effectiveHeight = height * scale
    val activeColor = Color(0xFF3B82F6)

    Box(
        modifier = modifier
            .width(effectiveWidth)
            .height(effectiveHeight)
            .clip(shape)
            .background(if (isPressed) activeColor.copy(alpha = (0.9f * sensitivity.coerceIn(0.5f, 1.2f)).coerceIn(0.5f, 1.0f)) else Color(0xFF282D36))
            .border((1.5 * scale).dp.coerceAtLeast(1.dp), Color(0xFF475060), shape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isPressed = true
                    onPressChanged(true)

                    var isUp = false
                    while (!isUp) {
                        val event = awaitPointerEvent()
                        if (event.changes.all { !it.pressed }) {
                            isUp = true
                        }
                    }
                    isPressed = false
                    onPressChanged(false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isPressed) Color.White else Color(0xFFCBD5E1),
            fontSize = ((13 * scale).coerceAtLeast(9f)).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * System Pills (Select, Start, PS, L3, R3)
 */
@Composable
fun SystemPillButton(
    modifier: Modifier = Modifier,
    label: String,
    isSpecial: Boolean = false,
    scale: Float = 1.0f,
    sensitivity: Float = 1.0f,
    onPressChanged: (Boolean) -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val shape = if (isSpecial) CircleShape else RoundedCornerShape((12 * scale).dp)
    val effectiveWidth = (if (isSpecial) 44.dp else 56.dp) * scale
    val effectiveHeight = (if (isSpecial) 44.dp else 32.dp) * scale

    Box(
        modifier = modifier
            .size(width = effectiveWidth, height = effectiveHeight)
            .clip(shape)
            .background(
                if (isPressed) {
                    if (isSpecial) Color(0xFFF59E0B) else Color(0xFF3B82F6)
                } else {
                    Color(0xFF1E2229)
                }
            )
            .border((1 * scale).dp.coerceAtLeast(1.dp), Color(0xFF3F4654), shape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isPressed = true
                    onPressChanged(true)

                    var isUp = false
                    while (!isUp) {
                        val event = awaitPointerEvent()
                        if (event.changes.all { !it.pressed }) {
                            isUp = true
                        }
                    }
                    isPressed = false
                    onPressChanged(false)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isPressed) Color.White else (if (isSpecial) Color(0xFFF59E0B) else Color(0xFF94A3B8)),
            fontSize = (if (isSpecial) 14 * scale else 10 * scale).coerceAtLeast(8f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}
