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

        val prefs = getSharedPreferences("ps3_controller_prefs", Context.MODE_PRIVATE)

        setContent {
            var host by remember {
                val intentHost = intent?.getStringExtra("host")
                val defaultHost = intentHost ?: prefs.getString("host", "127.0.0.1") ?: "127.0.0.1"
                if (intentHost != null) {
                    prefs.edit().putString("host", intentHost).apply()
                }
                mutableStateOf(defaultHost)
            }
            var port by remember {
                val intentPort = intent?.getIntExtra("port", 0)?.takeIf { it > 0 }
                mutableIntStateOf(intentPort ?: prefs.getInt("port", 6000))
            }
            var deadZone by remember { mutableFloatStateOf(prefs.getFloat("deadzone", 0.05f)) }
            var sensitivity by remember { mutableFloatStateOf(prefs.getFloat("sensitivity", 1.0f)) }
            var haptics by remember { mutableStateOf(prefs.getBoolean("haptics", true)) }

            var isSettingsOpen by remember { mutableStateOf(false) }

            val activeClient = remember(host, port) {
                client?.disconnect()
                GamepadClient(host, port).also {
                    client = it
                }
            }

            // Auto-connect on startup
            LaunchedEffect(activeClient) {
                activeClient.connect()
            }

            ControllerScreen(
                client = activeClient,
                host = host,
                port = port,
                deadZone = deadZone,
                sensitivity = sensitivity,
                hapticsEnabled = haptics,
                onOpenSettings = { isSettingsOpen = true }
            )

            if (isSettingsOpen) {
                SettingsDialog(
                    initialHost = host,
                    initialPort = port,
                    initialDeadZone = deadZone,
                    initialSensitivity = sensitivity,
                    initialHaptics = haptics,
                    onDismiss = { isSettingsOpen = false },
                    onSave = { newHost, newPort, newDeadZone, newSensitivity, newHaptics ->
                        host = newHost
                        port = newPort
                        deadZone = newDeadZone
                        sensitivity = newSensitivity
                        haptics = newHaptics

                        prefs.edit()
                            .putString("host", newHost)
                            .putInt("port", newPort)
                            .putFloat("deadzone", newDeadZone)
                            .putFloat("sensitivity", newSensitivity)
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
