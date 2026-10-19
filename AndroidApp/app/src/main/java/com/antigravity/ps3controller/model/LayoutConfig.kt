package com.antigravity.ps3controller.model

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    val dpad: ElementLayout = ElementLayout(),
    val leftStick: ElementLayout = ElementLayout(),
    val actionButtons: ElementLayout = ElementLayout(),
    val rightStick: ElementLayout = ElementLayout(),
    val leftShoulders: ElementLayout = ElementLayout(),
    val rightShoulders: ElementLayout = ElementLayout(),
    val centerControls: ElementLayout = ElementLayout(),
    val globalButtonSensitivity: Float = 1.0f
) {
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
