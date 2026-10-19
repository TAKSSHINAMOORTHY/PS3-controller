package com.antigravity.ps3controller.model

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class ControlId(val displayName: String) {
    // Face buttons
    CROSS("Cross (✕)"),
    CIRCLE("Circle (○)"),
    SQUARE("Square (□)"),
    TRIANGLE("Triangle (△)"),

    // D-Pad
    DPAD_UP("D-Pad Up (▲)"),
    DPAD_DOWN("D-Pad Down (▼)"),
    DPAD_LEFT("D-Pad Left (◀)"),
    DPAD_RIGHT("D-Pad Right (▶)"),

    // Shoulders & Triggers
    L1("L1 Bumper"),
    R1("R1 Bumper"),
    L2("L2 Trigger"),
    R2("R2 Trigger"),

    // Analog Sticks & Clicks
    LEFT_STICK("Left Analog Stick"),
    RIGHT_STICK("Right Analog Stick"),
    L3("L3 Stick Click"),
    R3("R3 Stick Click"),

    // System Buttons
    START("Start Button"),
    SELECT("Select Button"),
    PS("PS Button")
}

@Serializable
data class ControlLayout(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1.0f,
    val opacity: Float = 1.0f,
    val isVisible: Boolean = true,
    val sensitivity: Float = 1.0f
)

// Legacy compatibility models
@Serializable
data class ElementLayout(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1.0f,
    val sensitivity: Float = 1.0f
)

enum class LayoutElementType(val displayName: String) {
    DPAD("D-Pad"),
    LEFT_STICK("Left Stick"),
    ACTION_BUTTONS("Action Buttons (△ ○ ✕ □)"),
    RIGHT_STICK("Right Stick"),
    LEFT_SHOULDERS("Left Shoulders (L1/L2)"),
    RIGHT_SHOULDERS("Right Shoulders (R1/R2)"),
    CENTER_CONTROLS("System Controls (Select/PS/Start)")
}

@Serializable
data class ControllerLayoutConfig(
    val controls: Map<String, ControlLayout> = emptyMap(),
    val globalButtonSensitivity: Float = 1.0f,

    // Deprecated cluster fields retained for serialization compatibility & migration
    val dpad: ElementLayout = ElementLayout(),
    val leftStick: ElementLayout = ElementLayout(),
    val actionButtons: ElementLayout = ElementLayout(),
    val rightStick: ElementLayout = ElementLayout(),
    val leftShoulders: ElementLayout = ElementLayout(),
    val rightShoulders: ElementLayout = ElementLayout(),
    val centerControls: ElementLayout = ElementLayout()
) {
    /**
     * Get independent layout for a specific control with automatic migration from legacy clusters.
     */
    fun getControl(id: ControlId): ControlLayout {
        controls[id.name]?.let { return it }

        // Fallback to legacy cluster defaults if present
        return when (id) {
            ControlId.DPAD_UP, ControlId.DPAD_DOWN, ControlId.DPAD_LEFT, ControlId.DPAD_RIGHT ->
                ControlLayout(offsetX = dpad.offsetX, offsetY = dpad.offsetY, scale = dpad.scale, sensitivity = dpad.sensitivity)
            ControlId.LEFT_STICK ->
                ControlLayout(offsetX = leftStick.offsetX, offsetY = leftStick.offsetY, scale = leftStick.scale, sensitivity = leftStick.sensitivity)
            ControlId.CROSS, ControlId.CIRCLE, ControlId.SQUARE, ControlId.TRIANGLE ->
                ControlLayout(offsetX = actionButtons.offsetX, offsetY = actionButtons.offsetY, scale = actionButtons.scale, sensitivity = actionButtons.sensitivity)
            ControlId.RIGHT_STICK ->
                ControlLayout(offsetX = rightStick.offsetX, offsetY = rightStick.offsetY, scale = rightStick.scale, sensitivity = rightStick.sensitivity)
            ControlId.L1, ControlId.L2 ->
                ControlLayout(offsetX = leftShoulders.offsetX, offsetY = leftShoulders.offsetY, scale = leftShoulders.scale, sensitivity = leftShoulders.sensitivity)
            ControlId.R1, ControlId.R2 ->
                ControlLayout(offsetX = rightShoulders.offsetX, offsetY = rightShoulders.offsetY, scale = rightShoulders.scale, sensitivity = rightShoulders.sensitivity)
            ControlId.START, ControlId.SELECT, ControlId.PS, ControlId.L3, ControlId.R3 ->
                ControlLayout(offsetX = centerControls.offsetX, offsetY = centerControls.offsetY, scale = centerControls.scale, sensitivity = centerControls.sensitivity)
        }
    }

    /**
     * Update an individual control's layout independently.
     */
    fun updateControl(id: ControlId, layout: ControlLayout): ControllerLayoutConfig {
        val updated = controls.toMutableMap()
        updated[id.name] = layout
        return copy(controls = updated)
    }

    /**
     * Reset a single control to defaults.
     */
    fun resetControl(id: ControlId): ControllerLayoutConfig {
        val updated = controls.toMutableMap()
        updated.remove(id.name)
        return copy(controls = updated)
    }

    /**
     * Reset all controls to factory default.
     */
    fun resetAll(): ControllerLayoutConfig {
        return ControllerLayoutConfig()
    }

    // Deprecated cluster methods for backward compatibility
    fun getElement(type: LayoutElementType): ElementLayout = when (type) {
        LayoutElementType.DPAD -> dpad
        LayoutElementType.LEFT_STICK -> leftStick
        LayoutElementType.ACTION_BUTTONS -> actionButtons
        LayoutElementType.RIGHT_STICK -> rightStick
        LayoutElementType.LEFT_SHOULDERS -> leftShoulders
        LayoutElementType.RIGHT_SHOULDERS -> rightShoulders
        LayoutElementType.CENTER_CONTROLS -> centerControls
    }

    fun updateElement(type: LayoutElementType, element: ElementLayout): ControllerLayoutConfig = when (type) {
        LayoutElementType.DPAD -> copy(dpad = element)
        LayoutElementType.LEFT_STICK -> copy(leftStick = element)
        LayoutElementType.ACTION_BUTTONS -> copy(actionButtons = element)
        LayoutElementType.RIGHT_STICK -> copy(rightStick = element)
        LayoutElementType.LEFT_SHOULDERS -> copy(leftShoulders = element)
        LayoutElementType.RIGHT_SHOULDERS -> copy(rightShoulders = element)
        LayoutElementType.CENTER_CONTROLS -> copy(centerControls = element)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
        private const val PREF_KEY = "controller_layout_config"

        fun loadFromPrefs(prefs: SharedPreferences): ControllerLayoutConfig {
            val raw = prefs.getString(PREF_KEY, null) ?: return ControllerLayoutConfig()
            return try {
                json.decodeFromString<ControllerLayoutConfig>(raw)
            } catch (_: Exception) {
                ControllerLayoutConfig()
            }
        }

        fun saveToPrefs(prefs: SharedPreferences, config: ControllerLayoutConfig) {
            try {
                val raw = json.encodeToString(config)
                prefs.edit().putString(PREF_KEY, raw).apply()
            } catch (_: Exception) {}
        }
    }
}
