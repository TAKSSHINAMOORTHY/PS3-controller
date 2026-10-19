package com.antigravity.ps3controller

import com.antigravity.ps3controller.model.ControlId
import com.antigravity.ps3controller.model.ControlLayout
import com.antigravity.ps3controller.model.ControllerLayoutConfig
import com.antigravity.ps3controller.model.ElementLayout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IndependentControlsLayoutTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    @Test
    fun testAll19ControlsHaveUniqueIds() {
        val all = ControlId.values()
        assertEquals("Expected exactly 19 unique controls", 19, all.size)
        val uniqueNames = all.map { it.name }.toSet()
        assertEquals(19, uniqueNames.size)
    }

    @Test
    fun testIndependentMovementDoesNotCoupleNeighbors() {
        var config = ControllerLayoutConfig()

        // Move only DPAD_UP
        val originalDown = config.getControl(ControlId.DPAD_DOWN)
        val originalLeft = config.getControl(ControlId.DPAD_LEFT)
        val originalRight = config.getControl(ControlId.DPAD_RIGHT)

        val newUp = ControlLayout(offsetX = 15f, offsetY = -30f, scale = 1.2f)
        config = config.updateControl(ControlId.DPAD_UP, newUp)

        assertEquals(15f, config.getControl(ControlId.DPAD_UP).offsetX, 0.001f)
        assertEquals(-30f, config.getControl(ControlId.DPAD_UP).offsetY, 0.001f)
        assertEquals(1.2f, config.getControl(ControlId.DPAD_UP).scale, 0.001f)

        // Neighbors must be completely unchanged
        assertEquals(originalDown.offsetX, config.getControl(ControlId.DPAD_DOWN).offsetX, 0.001f)
        assertEquals(originalDown.offsetY, config.getControl(ControlId.DPAD_DOWN).offsetY, 0.001f)
        assertEquals(originalDown.scale, config.getControl(ControlId.DPAD_DOWN).scale, 0.001f)

        assertEquals(originalLeft.offsetX, config.getControl(ControlId.DPAD_LEFT).offsetX, 0.001f)
        assertEquals(originalRight.offsetX, config.getControl(ControlId.DPAD_RIGHT).offsetX, 0.001f)
    }

    @Test
    fun testLeftStickAndRightStickAreIndependent() {
        var config = ControllerLayoutConfig()

        val newLeft = ControlLayout(offsetX = -25f, offsetY = 10f, scale = 1.4f, sensitivity = 1.5f)
        config = config.updateControl(ControlId.LEFT_STICK, newLeft)

        val right = config.getControl(ControlId.RIGHT_STICK)
        assertEquals(0f, right.offsetX, 0.001f)
        assertEquals(0f, right.offsetY, 0.001f)
        assertEquals(1.0f, right.scale, 0.001f)
        assertEquals(1.0f, right.sensitivity, 0.001f)

        assertEquals(-25f, config.getControl(ControlId.LEFT_STICK).offsetX, 0.001f)
        assertEquals(10f, config.getControl(ControlId.LEFT_STICK).offsetY, 0.001f)
        assertEquals(1.4f, config.getControl(ControlId.LEFT_STICK).scale, 0.001f)
    }

    @Test
    fun testResizingFaceButtonDoesNotResizeOtherButtons() {
        var config = ControllerLayoutConfig()

        val newCross = ControlLayout(scale = 1.8f)
        config = config.updateControl(ControlId.CROSS, newCross)

        assertEquals(1.8f, config.getControl(ControlId.CROSS).scale, 0.001f)
        assertEquals(1.0f, config.getControl(ControlId.SQUARE).scale, 0.001f)
        assertEquals(1.0f, config.getControl(ControlId.CIRCLE).scale, 0.001f)
        assertEquals(1.0f, config.getControl(ControlId.TRIANGLE).scale, 0.001f)
    }

    @Test
    fun testResetIndividualControlAndResetAll() {
        var config = ControllerLayoutConfig()
        config = config.updateControl(ControlId.L1, ControlLayout(offsetX = 50f, scale = 1.5f))
        config = config.updateControl(ControlId.R1, ControlLayout(offsetX = -50f, scale = 1.3f))

        assertEquals(50f, config.getControl(ControlId.L1).offsetX, 0.001f)
        assertEquals(-50f, config.getControl(ControlId.R1).offsetX, 0.001f)

        // Reset only L1
        config = config.resetControl(ControlId.L1)
        assertEquals(0f, config.getControl(ControlId.L1).offsetX, 0.001f)
        assertEquals(1.0f, config.getControl(ControlId.L1).scale, 0.001f)

        // R1 must still be customized
        assertEquals(-50f, config.getControl(ControlId.R1).offsetX, 0.001f)
        assertEquals(1.3f, config.getControl(ControlId.R1).scale, 0.001f)

        // Reset all
        config = config.resetAll()
        assertEquals(0f, config.getControl(ControlId.R1).offsetX, 0.001f)
        assertEquals(1.0f, config.getControl(ControlId.R1).scale, 0.001f)
    }

    @Test
    fun testSerializationAndDeserialization() {
        var config = ControllerLayoutConfig()
        config = config.updateControl(ControlId.SELECT, ControlLayout(offsetX = 12f, offsetY = 8f, scale = 1.1f))
        config = config.updateControl(ControlId.PS, ControlLayout(scale = 1.25f, sensitivity = 1.2f))

        val encoded = json.encodeToString(config)
        val decoded = json.decodeFromString<ControllerLayoutConfig>(encoded)

        assertEquals(12f, decoded.getControl(ControlId.SELECT).offsetX, 0.001f)
        assertEquals(8f, decoded.getControl(ControlId.SELECT).offsetY, 0.001f)
        assertEquals(1.1f, decoded.getControl(ControlId.SELECT).scale, 0.001f)

        assertEquals(1.25f, decoded.getControl(ControlId.PS).scale, 0.001f)
        assertEquals(1.2f, decoded.getControl(ControlId.PS).sensitivity, 0.001f)
    }

    @Test
    fun testLegacyMigrationFallback() {
        // Construct legacy config with cluster offsets
        val legacy = ControllerLayoutConfig(
            actionButtons = ElementLayout(offsetX = 30f, offsetY = 40f, scale = 1.2f, sensitivity = 1.1f),
            dpad = ElementLayout(offsetX = -20f, offsetY = 10f, scale = 0.9f)
        )

        // In absence of explicit control entries, child controls should inherit legacy cluster values
        assertEquals(30f, legacy.getControl(ControlId.CROSS).offsetX, 0.001f)
        assertEquals(40f, legacy.getControl(ControlId.CROSS).offsetY, 0.001f)
        assertEquals(1.2f, legacy.getControl(ControlId.CROSS).scale, 0.001f)

        assertEquals(-20f, legacy.getControl(ControlId.DPAD_UP).offsetX, 0.001f)
        assertEquals(0.9f, legacy.getControl(ControlId.DPAD_UP).scale, 0.001f)
    }
}
