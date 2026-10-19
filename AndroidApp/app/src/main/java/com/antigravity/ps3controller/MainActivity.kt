package com.antigravity.ps3controller

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.antigravity.ps3controller.network.GamepadClient
import com.antigravity.ps3controller.ui.ControllerScreen
import com.antigravity.ps3controller.ui.SettingsDialog

class MainActivity : ComponentActivity() {

    private var client: GamepadClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val prefs = getSharedPreferences("ps3_controller_prefs", Context.MODE_PRIVATE)

        setContent {
            var wifiHost by remember {
                val intentHost = intent?.getStringExtra("wifi_host") ?: intent?.getStringExtra("host")
                val defaultHost = intentHost ?: prefs.getString("wifi_host", "192.168.1.24") ?: "192.168.1.24"
                mutableStateOf(defaultHost)
            }
            var usbHost by remember {
                mutableStateOf(prefs.getString("usb_host", "127.0.0.1") ?: "127.0.0.1")
            }
            var transportPrefName by remember {
                mutableStateOf(prefs.getString("transport_pref", "AUTO") ?: "AUTO")
            }
            var port by remember {
                val intentPort = intent?.getIntExtra("port", 0)?.takeIf { it > 0 }
                mutableIntStateOf(intentPort ?: prefs.getInt("port", 6000))
            }
            var deadZone by remember { mutableFloatStateOf(prefs.getFloat("deadzone", 0.05f)) }
            var sensitivity by remember { mutableFloatStateOf(prefs.getFloat("sensitivity", 1.0f)) }
            var buttonSensitivity by remember { mutableFloatStateOf(prefs.getFloat("button_sensitivity", 1.0f)) }
            var haptics by remember { mutableStateOf(prefs.getBoolean("haptics", true)) }

            var layoutConfig by remember {
                mutableStateOf(com.antigravity.ps3controller.model.ControllerLayoutConfig.loadFromPrefs(prefs))
            }
            var isEditMode by remember { mutableStateOf(false) }
            var isSettingsOpen by remember { mutableStateOf(false) }

            val transportPref = when (transportPrefName) {
                "FORCE_USB" -> com.antigravity.ps3controller.network.TransportPreference.FORCE_USB
                "FORCE_WIFI" -> com.antigravity.ps3controller.network.TransportPreference.FORCE_WIFI
                else -> com.antigravity.ps3controller.network.TransportPreference.AUTO_PREFER_USB
            }

            val activeClient = remember(usbHost, wifiHost, port, transportPref) {
                client?.disconnect()
                GamepadClient(
                    usbHost = usbHost,
                    wifiHost = wifiHost,
                    serverPort = port,
                    transportPreference = transportPref
                ).also {
                    client = it
                }
            }

            // Auto-connect on startup
            LaunchedEffect(activeClient) {
                activeClient.connect()
            }

            ControllerScreen(
                client = activeClient,
                host = activeClient.serverHost,
                port = port,
                deadZone = deadZone,
                sensitivity = sensitivity,
                buttonSensitivity = buttonSensitivity,
                hapticsEnabled = haptics,
                layoutConfig = layoutConfig,
                isEditMode = isEditMode,
                onEditModeChanged = { isEditMode = it },
                onUpdateLayoutConfig = { newConfig ->
                    layoutConfig = newConfig
                    com.antigravity.ps3controller.model.ControllerLayoutConfig.saveToPrefs(prefs, newConfig)
                },
                onOpenSettings = { isSettingsOpen = true }
            )

            if (isSettingsOpen) {
                SettingsDialog(
                    initialHost = wifiHost,
                    initialPort = port,
                    initialDeadZone = deadZone,
                    initialSensitivity = sensitivity,
                    initialButtonSensitivity = buttonSensitivity,
                    initialHaptics = haptics,
                    initialTransportPref = transportPrefName,
                    initialWifiHost = wifiHost,
                    onDismiss = { isSettingsOpen = false },
                    onEnterEditMode = {
                        isEditMode = true
                    },
                    onSave = { newWifiHost, newTransportPref, newPort, newDeadZone, newSensitivity, newButtonSensitivity, newHaptics ->
                        wifiHost = newWifiHost
                        transportPrefName = newTransportPref
                        port = newPort
                        deadZone = newDeadZone
                        sensitivity = newSensitivity
                        buttonSensitivity = newButtonSensitivity
                        haptics = newHaptics

                        prefs.edit()
                            .putString("wifi_host", newWifiHost)
                            .putString("transport_pref", newTransportPref)
                            .putInt("port", newPort)
                            .putFloat("deadzone", newDeadZone)
                            .putFloat("sensitivity", newSensitivity)
                            .putFloat("button_sensitivity", newButtonSensitivity)
                            .putBoolean("haptics", newHaptics)
                            .apply()

                        activeClient.connect()
                    }
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun hideSystemBars() {
        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }

    override fun onStop() {
        super.onStop()
        // Neutralize and disconnect to prevent sticking inputs
        client?.disconnect()
    }
}
