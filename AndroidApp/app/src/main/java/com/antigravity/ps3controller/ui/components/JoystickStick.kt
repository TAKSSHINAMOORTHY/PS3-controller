package com.antigravity.ps3controller.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antigravity.ps3controller.math.TouchInputMapper
import kotlin.math.hypot
import kotlin.math.min

@Composable
fun JoystickStick(
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    deadZone: Float = 0.05f,
    sensitivity: Float = 1.0f,
    onMove: (Float, Float) -> Unit
) {
    var knobOffsetX by remember { mutableFloatStateOf(0f) }
    var knobOffsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .pointerInput(deadZone, sensitivity, size) {
                val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
                val maxRadius = (size.toPx() / 2f) * 0.7f

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id
                    isDragging = true

                    fun updateStickPosition(touchPos: Offset) {
                        val dx = touchPos.x - center.x
                        val dy = touchPos.y - center.y
                        val dist = hypot(dx, dy)
                        val clampedDist = min(dist, maxRadius)
                        val angle = if (dist > 0f) (dx / dist) to (dy / dist) else 0f to 0f

                        knobOffsetX = angle.first * clampedDist
                        knobOffsetY = angle.second * clampedDist

                        val result = TouchInputMapper.mapStick(dx, dy, maxRadius, deadZone, sensitivity)
                        onMove(result.x, result.y)
                    }

                    down.consume()
                    updateStickPosition(down.position)

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                            if (change == null || !change.pressed) {
                                break
                            }
                            change.consume()
                            updateStickPosition(change.position)
                        }
                    } finally {
                        isDragging = false
                        knobOffsetX = 0f
                        knobOffsetY = 0f
                        onMove(0f, 0f)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val outerRadius = this.size.width / 2f
            val knobRadius = outerRadius * 0.42f

            // Base outer well gradient
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF22252A),
                        Color(0xFF141619),
                        Color(0xFF0D0E10)
                    ),
                    center = center,
                    radius = outerRadius
                ),
                radius = outerRadius,
                center = center
            )

            // Outer border ring
            drawCircle(
                color = Color(0xFF383C44),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )

            // Subtle crosshair guides
            val crosshairColor = Color(0x33FFFFFF)
            val crosshairLen = outerRadius * 0.85f
            drawLine(
                color = crosshairColor,
                start = Offset(center.x - crosshairLen, center.y),
                end = Offset(center.x + crosshairLen, center.y),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = crosshairColor,
                start = Offset(center.x, center.y - crosshairLen),
                end = Offset(center.x, center.y + crosshairLen),
                strokeWidth = 1.dp.toPx()
            )

            // Joystick Knob
            val knobCenter = Offset(center.x + knobOffsetX, center.y + knobOffsetY)

            // Knob shadow
            drawCircle(
                color = Color(0x66000000),
                radius = knobRadius + 4.dp.toPx(),
                center = knobCenter
            )

            // Knob body gradient
            val knobColors = if (isDragging) {
                listOf(Color(0xFF3A4B62), Color(0xFF253040), Color(0xFF181F2A))
            } else {
                listOf(Color(0xFF32363D), Color(0xFF24272C), Color(0xFF17191C))
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = knobColors,
                    center = knobCenter,
                    radius = knobRadius
                ),
                radius = knobRadius,
                center = knobCenter
            )

            // Inner thumb grip ring
            drawCircle(
                color = if (isDragging) Color(0xFF3B82F6) else Color(0xFF4B5563),
                radius = knobRadius * 0.55f,
                center = knobCenter,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
