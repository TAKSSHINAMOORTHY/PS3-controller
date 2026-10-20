package com.antigravity.ps3controller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.antigravity.ps3controller.network.ConnectionStatus
import com.antigravity.ps3controller.network.GamepadClient
import com.antigravity.ps3controller.ui.ControllerScreen
import com.antigravity.ps3controller.ui.SettingsDialog

class MainActivity : ComponentActivity() {

    private var client: GamepadClient? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == "android.hardware.usb.action.USB_STATE") {
                val isConnected = intent.getBooleanExtra("connected", false)
                client?.onUsbStateChanged(isConnected)
            } else if (action == Intent.ACTION_POWER_CONNECTED) {
                client?.onUsbStateChanged(true)
            } else if (action == Intent.ACTION_POWER_DISCONNECTED) {
                client?.onUsbStateChanged(false)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // On Android 10+ (API 29+), exclude the left and right screen edges from system back gestures and sidebars
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.decorView.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                val width = right - left
                val height = bottom - top
                if (width > 0 && height > 0) {
                    val exclusionWidth = (width * 0.25f).toInt().coerceAtMost(350)
                    val leftRect = android.graphics.Rect(0, 0, exclusionWidth, height)
                    val rightRect = android.graphics.Rect(width - exclusionWidth, 0, width, height)
                    try {
                        window.decorView.systemGestureExclusionRects = listOf(leftRect, rightRect)
                    } catch (_: Exception) {}
                }
            }
        }

        // Automatically re-hide transient system bars if revealed by edge swipes
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insets ->
            val isVisible = insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            if (isVisible) {
                window.decorView.postDelayed({
                    hideSystemBars()
                }, 1000)
            }
            insets
        }

        // Keep CPU and Wi-Fi radio at high performance so cutscenes never drop connectivity
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            wakeLock = powerManager?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "PS3Controller::WakeLock")?.apply {
                setReferenceCounted(false)
                acquire(60 * 60 * 1000L) // 1 hour lease
            }

            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                wifiLock = wifiManager?.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "PS3Controller::WifiLock")
            } else {
                @Suppress("DEPRECATION")
                wifiLock = wifiManager?.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PS3Controller::WifiLock")
            }
            wifiLock?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {}

        try {
            val filter = IntentFilter().apply {
                addAction("android.hardware.usb.action.USB_STATE")
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(usbReceiver, filter)
            }
        } catch (_: Exception) {}

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
            var deadZone by remember { mutableFloatStateOf(prefs.getFloat("deadzone", 0.15f)) }
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

    override fun onStart() {
        super.onStart()
        // Auto-reconnect when activity becomes visible
        client?.connect()
    }

    override fun onResume() {
        super.onResume()
        if (client?.status?.value == ConnectionStatus.DISCONNECTED) {
            client?.connect()
        }
    }

    override fun onStop() {
        super.onStop()
        // Neutralize and disconnect to prevent sticking inputs
        client?.disconnect()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(usbReceiver)
        } catch (_: Exception) {}
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Exception) {}
    }
}
