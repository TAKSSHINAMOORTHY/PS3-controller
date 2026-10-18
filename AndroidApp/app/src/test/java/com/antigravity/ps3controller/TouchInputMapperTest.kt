package com.antigravity.ps3controller

import com.antigravity.ps3controller.math.TouchInputMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class TouchInputMapperTest {

    @Test
    fun testCenterIsZero() {
        val result = TouchInputMapper.mapStick(
            dx = 0f,
            dy = 0f,
            maxRadius = 100f,
            deadZone = 0.05f
        )
        assertEquals(0f, result.x, 0.001f)
        assertEquals(0f, result.y, 0.001f)
    }

    @Test
    fun testDeadZoneFiltersSmallMovement() {
        // Radius is 100f, so 4f displacement is 0.04 (below 0.05 deadzone)
        val result = TouchInputMapper.mapStick(
            dx = 4f,
            dy = 0f,
            maxRadius = 100f,
            deadZone = 0.05f
        )
        assertEquals(0f, result.x, 0.001f)
        assertEquals(0f, result.y, 0.001f)
    }

    @Test
    fun testMaxDisplacementClampedToUnitCircle() {
        // Displace 200px right on a 100px maxRadius stick
        val result = TouchInputMapper.mapStick(
            dx = 200f,
            dy = 0f,
            maxRadius = 100f,
            deadZone = 0.05f
        )
        assertEquals(1.0f, result.x, 0.001f)
        assertEquals(0.0f, result.y, 0.001f)
    }

    @Test
    fun testNegativeDisplacement() {
        // Displace 100px left
        val result = TouchInputMapper.mapStick(
            dx = -100f,
            dy = 0f,
            maxRadius = 100f,
            deadZone = 0.05f
        )
        assertEquals(-1.0f, result.x, 0.001f)
        assertEquals(0.0f, result.y, 0.001f)
    }

    @Test
    fun testDiagonalDisplacementPreservesAngle() {
        // Displace diagonally by (100, 100)
        val result = TouchInputMapper.mapStick(
            dx = 100f,
            dy = 100f,
            maxRadius = 100f,
            deadZone = 0.0f
        )
        // Since magnitude exceeds maxRadius, length must be 1.0f and x == y
        val mag = hypot(result.x, result.y)
        assertEquals(1.0f, mag, 0.001f)
        assertEquals(result.x, result.y, 0.001f)
        assertTrue(result.x > 0.70f)
    }

    @Test
    fun testTriggerClamping() {
        assertEquals(0.0f, TouchInputMapper.mapTrigger(-0.5f), 0.001f)
        assertEquals(0.75f, TouchInputMapper.mapTrigger(0.75f), 0.001f)
        assertEquals(1.0f, TouchInputMapper.mapTrigger(1.5f), 0.001f)
    }
}
