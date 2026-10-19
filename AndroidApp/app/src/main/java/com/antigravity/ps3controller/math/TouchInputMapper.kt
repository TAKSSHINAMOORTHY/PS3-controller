package com.antigravity.ps3controller.math

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign

object TouchInputMapper {

    data class StickResult(
        val x: Float,
        val y: Float
    )

    /**
     * Map a raw touch delta (dx, dy) within a bounding radius to normalized [-1.0, 1.0] joystick coordinates.
     *
     * @param dx Raw touch horizontal displacement from center in pixels.
     * @param dy Raw touch vertical displacement from center in pixels.
     * @param maxRadius Maximum visual travel radius of the joystick in pixels.
     * @param deadZone Normalized dead zone threshold [0.0, 0.5] below which input is 0.
     * @param sensitivity Response curve exponent (1.0 = linear, >1.0 = finer center control).
     */
    fun mapStick(
        dx: Float,
        dy: Float,
        maxRadius: Float,
        deadZone: Float = 0.15f,
        sensitivity: Float = 1.0f
    ): StickResult {
        if (maxRadius <= 0f) return StickResult(0f, 0f)

        // Raw normalized radius in [0, inf)
        val rawDist = hypot(dx, dy)
        if (rawDist <= 0.00001f) return StickResult(0f, 0f)

        val normalizedDist = min(rawDist / maxRadius, 1.0f)
        val effectiveDeadZone = deadZone.coerceIn(0.0f, 0.95f)

        if (normalizedDist <= effectiveDeadZone) {
            return StickResult(0f, 0f)
        }

        // Rescale range (effectiveDeadZone..1.0] -> (0.0..1.0]
        val adjustedDist = ((normalizedDist - effectiveDeadZone) / (1.0f - effectiveDeadZone)).coerceIn(0.0f, 1.0f)
        if (adjustedDist <= 0.001f) return StickResult(0f, 0f)

        val effectiveSens = if (sensitivity <= 0f) 1.0f else sensitivity
        val curvedDist = if (effectiveSens == 1.0f) adjustedDist else adjustedDist.pow(effectiveSens)

        // Unit vector along drag direction
        val unitX = dx / rawDist
        val unitY = dy / rawDist

        val rawX = unitX * curvedDist
        val rawY = unitY * curvedDist

        val mappedX = if (kotlin.math.abs(rawX) < 0.015f) 0f else rawX.coerceIn(-1.0f, 1.0f)
        val mappedY = if (kotlin.math.abs(rawY) < 0.015f) 0f else rawY.coerceIn(-1.0f, 1.0f)

        return StickResult(mappedX, mappedY)
    }

    /**
     * Clamps a trigger value to [0.0, 1.0] scaled by sensitivity.
     */
    fun mapTrigger(value: Float, sensitivity: Float = 1.0f): Float {
        val effectiveSensitivity = if (sensitivity <= 0f) 1.0f else sensitivity
        return (value * effectiveSensitivity).coerceIn(0f, 1f)
    }
}
