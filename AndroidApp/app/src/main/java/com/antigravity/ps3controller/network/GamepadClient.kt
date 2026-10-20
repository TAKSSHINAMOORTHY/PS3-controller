package com.antigravity.ps3controller.network

import com.antigravity.ps3controller.model.ControllerState
import com.antigravity.ps3controller.model.ControllerButtons
import com.antigravity.ps3controller.model.ControllerDPad
import com.antigravity.ps3controller.model.ControllerAxes
import com.antigravity.ps3controller.model.HeartbeatMessage
import com.antigravity.ps3controller.model.PairRequest
import com.antigravity.ps3controller.model.PairResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    PAIRING,
    CONNECTED,
    ERROR
}

enum class TransportPreference {
    AUTO_PREFER_USB,
    FORCE_USB,
    FORCE_WIFI
}

class GamepadClient(
    val usbHost: String = "127.0.0.1",
    val wifiHost: String = "192.168.1.24",
    val serverPort: Int = 6000,
    val transportPreference: TransportPreference = TransportPreference.AUTO_PREFER_USB,
    private val clientName: String = "Android PS3 Gamepad"
) {
    // Backwards-compatible constructor for single host
    constructor(serverHost: String, serverPort: Int, clientName: String = "Android PS3 Gamepad") : this(
        usbHost = if (serverHost == "127.0.0.1") "127.0.0.1" else serverHost,
        wifiHost = if (serverHost != "127.0.0.1") serverHost else "192.168.1.24",
        serverPort = serverPort,
        transportPreference = if (serverHost == "127.0.0.1") TransportPreference.AUTO_PREFER_USB else TransportPreference.FORCE_WIFI,
        clientName = clientName
    )

    val serverHost: String get() = _activeHost.value

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _status = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private val _activeTransport = MutableStateFlow("DISCONNECTED")
    val activeTransport: StateFlow<String> = _activeTransport.asStateFlow()

    private val _activeHost = MutableStateFlow(usbHost)
    val activeHost: StateFlow<String> = _activeHost.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var activeSessionId: String? = null
    private val sequenceNumber = AtomicLong(0)
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var lastSentTime = 0L

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private suspend fun resolveTargetHost(): Pair<String, String> = withContext(Dispatchers.IO) {
        when (transportPreference) {
            TransportPreference.FORCE_USB -> Pair(usbHost, "USB")
            TransportPreference.FORCE_WIFI -> Pair(resolveWifiHost(), "Wi-Fi")
            TransportPreference.AUTO_PREFER_USB -> {
                // Probe USB (127.0.0.1:serverPort) first with reliable 250ms socket probe
                val isUsbAvailable = try {
                    java.net.Socket().use { s ->
                        s.connect(java.net.InetSocketAddress(usbHost, serverPort), 250)
                        true
                    }
                } catch (_: Exception) {
                    false
                }

                if (isUsbAvailable) {
                    Pair(usbHost, "USB")
                } else {
                    Pair(resolveWifiHost(), "Wi-Fi")
                }
            }
        }
    }

    private fun resolveWifiHost(): String {
        // 1. Probe currently configured wifiHost (e.g. 192.168.1.24)
        if (isHostReachable(wifiHost, serverPort, 400)) {
            return wifiHost
        }

        // 2. Auto-discover Mac companion via UDP broadcast on port 6001
        val discovered = discoverHostViaUDP(6001, 600)
        if (discovered != null) {
            Log.i("GamepadClient", "Auto-discovered Mac bridge via UDP: $discovered")
            return discovered
        }

        // 3. Fallback to configured host
        return wifiHost
    }

    private fun isHostReachable(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            java.net.Socket().use { s ->
                s.connect(java.net.InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun discoverHostViaUDP(discoveryPort: Int, timeoutMs: Int): String? {
        return try {
            java.net.DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = timeoutMs
                val msg = "DISCOVER_PS3_BRIDGE".toByteArray()
                val packet = java.net.DatagramPacket(
                    msg, msg.size,
                    java.net.InetAddress.getByName("255.255.255.255"),
                    discoveryPort
                )
                socket.send(packet)

                val buf = ByteArray(1024)
                val respPacket = java.net.DatagramPacket(buf, buf.size)
                socket.receive(respPacket)
                val str = String(respPacket.data, 0, respPacket.length)
                val regex = "\"ip\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                val match = regex.find(str)
                match?.groupValues?.get(1) ?: respPacket.address.hostAddress
            }
        } catch (_: Exception) {
            null
        }
    }

    fun connect() {
        isExplicitDisconnect = false
        if (_status.value == ConnectionStatus.CONNECTING || _status.value == ConnectionStatus.CONNECTED) {
            return
        }

        _status.value = ConnectionStatus.CONNECTING
        _errorMessage.value = null

        scope.launch {
            val (resolvedHost, transportName) = resolveTargetHost()
            connectToHost(resolvedHost, transportName)
        }
    }

    private fun connectToHost(resolvedHost: String, transportName: String) {
        _activeHost.value = resolvedHost
        _activeTransport.value = transportName

        val url = "ws://$resolvedHost:$serverPort"
        Log.i("GamepadClient", "Connecting via $transportName to $url...")
        val request = Request.Builder().url(url).build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i("GamepadClient", "WebSocket onOpen via $transportName! Sending PairRequest...")
                _status.value = ConnectionStatus.PAIRING
                // Send pair request
                val pairReq = PairRequest(
                    clientName = clientName,
                    clientId = "android-${System.currentTimeMillis() % 10000}",
                    protocolVersion = 1,
                    pairingCode = "1234"
                )
                webSocket.send(json.encodeToString(pairReq))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.i("GamepadClient", "onMessage: $text")
                handleIncomingMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w("GamepadClient", "onClosed: $reason ($code)")
                handleDisconnect("Closed: $reason ($code)")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("GamepadClient", "onFailure: ${t.localizedMessage}", t)
                handleDisconnect("Failed: ${t.localizedMessage ?: "Unknown error"}")
            }
        })
    }

    private fun disconnectForTransportSwitch() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        activeStateStreamJob?.cancel()
        activeStateStreamJob = null
        val ws = webSocket
        webSocket = null
        activeSessionId = null
        try {
            ws?.close(1000, "Transport switch")
        } catch (_: Exception) {}
    }

    fun onUsbStateChanged(isAttached: Boolean) {
        Log.i("GamepadClient", "onUsbStateChanged: isAttached=$isAttached, pref=$transportPreference, activeTransport=${_activeTransport.value}")
        if (transportPreference == TransportPreference.FORCE_WIFI) {
            return
        }

        if (isAttached) {
            // USB plugged in!
            scope.launch {
                var usbAvailable = false
                // Check if ADB reverse port forwarding is ready (give Mac up to 2.4s to configure)
                for (attempt in 1..8) {
                    delay(300)
                    if (isHostReachable(usbHost, serverPort, 200)) {
                        usbAvailable = true
                        break
                    }
                }

                if (usbAvailable) {
                    Log.i("GamepadClient", "USB loopback confirmed reachable after plug event!")
                    if (_activeTransport.value != "USB" || _status.value != ConnectionStatus.CONNECTED) {
                        Log.i("GamepadClient", "Switching to low-latency USB transport now...")
                        disconnectForTransportSwitch()
                        connectToHost(usbHost, "USB")
                    }
                }
            }
        } else {
            // USB unplugged!
            if (_activeTransport.value == "USB") {
                Log.i("GamepadClient", "USB physically unplugged while on USB transport!")
                scope.launch {
                    disconnectForTransportSwitch()
                    if (transportPreference == TransportPreference.AUTO_PREFER_USB) {
                        Log.i("GamepadClient", "Falling back to Wi-Fi after USB unplug...")
                        connect()
                    } else {
                        _status.value = ConnectionStatus.DISCONNECTED
                        _errorMessage.value = "USB cable unplugged"
                    }
                }
            }
        }
    }

    private fun handleIncomingMessage(text: String) {
        try {
            if (text.contains("pair_response")) {
                val response = json.decodeFromString<PairResponse>(text)
                if (response.status == "accepted" && response.sessionId != null) {
                    activeSessionId = response.sessionId
                    _status.value = ConnectionStatus.CONNECTED
                    startHeartbeat()
                } else {
                    _status.value = ConnectionStatus.ERROR
                    _errorMessage.value = response.error ?: "Pairing rejected by host"
                    webSocket?.close(1000, "Rejected")
                }
            }
        } catch (e: Exception) {
            _errorMessage.value = "Parse error: ${e.message}"
        }
    }

    private val stateLock = Any()
    private var currentState = ControllerState.neutral()
    private var activeStateStreamJob: Job? = null
    private var pendingState: ControllerState? = null
    private var trailingSendJob: Job? = null
    private var lastSentButtons = ControllerButtons()
    private var lastSentDpad = ControllerDPad()
    private var lastSentAxes = ControllerAxes()

    fun updateButtons(updater: (ControllerButtons) -> ControllerButtons) {
        val stateToSend: ControllerState
        synchronized(stateLock) {
            val updated = updater(currentState.buttons)
            currentState = currentState.copy(buttons = updated)
            stateToSend = currentState
        }
        dispatchState(stateToSend, forceImmediate = true)
        checkAndMaintainActiveStreaming()
    }

    fun updateDpad(updater: (ControllerDPad) -> ControllerDPad) {
        val stateToSend: ControllerState
        synchronized(stateLock) {
            val updated = updater(currentState.dpad)
            currentState = currentState.copy(dpad = updated)
            stateToSend = currentState
        }
        dispatchState(stateToSend, forceImmediate = true)
        checkAndMaintainActiveStreaming()
    }

    fun updateStick(isLeft: Boolean, x: Float, y: Float) {
        val stateToSend: ControllerState
        val isCenter: Boolean
        val significantChange: Boolean
        synchronized(stateLock) {
            val oldAxes = currentState.axes
            val newAxes = if (isLeft) {
                currentState.axes.copy(lx = x, ly = y)
            } else {
                currentState.axes.copy(rx = x, ry = y)
            }
            currentState = currentState.copy(axes = newAxes)
            stateToSend = currentState
            isCenter = (x == 0f && y == 0f)
            val dx = if (isLeft) kotlin.math.abs(newAxes.lx - oldAxes.lx) else kotlin.math.abs(newAxes.rx - oldAxes.rx)
            val dy = if (isLeft) kotlin.math.abs(newAxes.ly - oldAxes.ly) else kotlin.math.abs(newAxes.ry - oldAxes.ry)
            significantChange = (dx > 0.02f || dy > 0.02f)
        }
        dispatchState(stateToSend, forceImmediate = isCenter || significantChange)
        checkAndMaintainActiveStreaming()
    }

    fun updateTrigger(l2: Float? = null, r2: Float? = null, isL2Pressed: Boolean? = null, isR2Pressed: Boolean? = null) {
        val stateToSend: ControllerState
        synchronized(stateLock) {
            val newL2 = l2 ?: currentState.triggers.l2
            val newR2 = r2 ?: currentState.triggers.r2
            val newTriggers = currentState.triggers.copy(l2 = newL2, r2 = newR2)
            val btnL2 = isL2Pressed ?: (newL2 > 0.20f)
            val btnR2 = isR2Pressed ?: (newR2 > 0.20f)
            val newButtons = currentState.buttons.copy(l2 = btnL2, r2 = btnR2)
            currentState = currentState.copy(triggers = newTriggers, buttons = newButtons)
            stateToSend = currentState
        }
        dispatchState(stateToSend, forceImmediate = true)
        checkAndMaintainActiveStreaming()
    }

    private fun checkAndMaintainActiveStreaming() {
        synchronized(stateLock) {
            val nonNeutral = !isNeutralState(currentState)
            if (nonNeutral) {
                if (activeStateStreamJob == null) {
                    activeStateStreamJob = scope.launch {
                        while (isActive && _status.value == ConnectionStatus.CONNECTED) {
                            delay(25) // Stream held state at 40Hz (every 25ms) to keep server watchdog refreshed
                            val stateToSend = synchronized(stateLock) {
                                if (isNeutralState(currentState)) null else currentState
                            }
                            if (stateToSend != null) {
                                sendRawStatePacket(stateToSend)
                            } else {
                                break
                            }
                        }
                        activeStateStreamJob = null
                    }
                }
            } else {
                activeStateStreamJob?.cancel()
                activeStateStreamJob = null
            }
        }
    }

    private fun isNeutralState(state: ControllerState): Boolean {
        val b = state.buttons
        val d = state.dpad
        val a = state.axes
        val t = state.triggers
        return !b.cross && !b.circle && !b.square && !b.triangle &&
               !b.l1 && !b.r1 && !b.l2 && !b.r2 && !b.l3 && !b.r3 &&
               !b.start && !b.select && !b.ps &&
               !d.up && !d.down && !d.left && !d.right &&
               a.lx == 0f && a.ly == 0f && a.rx == 0f && a.ry == 0f &&
               t.l2 == 0f && t.r2 == 0f
    }

    private fun sendRawStatePacket(state: ControllerState) {
        val ws = webSocket ?: return
        val session = activeSessionId ?: return
        if (_status.value != ConnectionStatus.CONNECTED) return

        val now = System.currentTimeMillis()
        val seq = sequenceNumber.incrementAndGet()
        val outbound = state.copy(sessionId = session, seq = seq, timestamp = now)
        try {
            val payload = json.encodeToString(outbound)
            ws.send(payload)
            lastSentTime = now
        } catch (_: Exception) {}
    }

    fun dispatchState(state: ControllerState, forceImmediate: Boolean = false) {
        val ws = webSocket ?: return
        val session = activeSessionId ?: return
        if (_status.value != ConnectionStatus.CONNECTED) return

        val now = System.currentTimeMillis()
        val timeSinceLast = now - lastSentTime
        val isNeutral = isNeutralState(state)
        val buttonsChanged = state.buttons != lastSentButtons || state.dpad != lastSentDpad
        val isZeroAxes = state.axes.lx == 0f && state.axes.ly == 0f && state.axes.rx == 0f && state.axes.ry == 0f
        val wasZeroAxes = lastSentAxes.lx == 0f && lastSentAxes.ly == 0f && lastSentAxes.rx == 0f && lastSentAxes.ry == 0f
        val stickReleasedToCenter = isZeroAxes && !wasZeroAxes

        // Button transitions, rapid taps, neutral releases, or stick center releases transmit immediately (0ms latency).
        // Analog stick motion updates are throttled to 8ms (~120Hz) for ultra-fluid stick response.
        if (forceImmediate || buttonsChanged || stickReleasedToCenter || isNeutral || timeSinceLast >= 8) {
            trailingSendJob?.cancel()
            trailingSendJob = null
            pendingState = null
            lastSentButtons = state.buttons
            lastSentDpad = state.dpad
            lastSentAxes = state.axes

            sendRawStatePacket(state)

            // Send secondary redundant center confirmation after 30ms to guarantee zero stick drift under packet loss
            if (stickReleasedToCenter) {
                scope.launch {
                    delay(30)
                    val s = activeSessionId
                    val w = webSocket
                    if (s != null && w != null && _status.value == ConnectionStatus.CONNECTED) {
                        val q = sequenceNumber.incrementAndGet()
                        val confirm = state.copy(sessionId = s, seq = q, timestamp = System.currentTimeMillis())
                        try { w.send(json.encodeToString(confirm)) } catch (_: Exception) {}
                    }
                }
            }
        } else {
            // Buffer latest motion state so trailing final position is never lost
            pendingState = state
            if (trailingSendJob == null) {
                val delayMs = (8 - timeSinceLast).coerceIn(1, 8)
                trailingSendJob = scope.launch {
                    delay(delayMs)
                    val toSend = pendingState
                    pendingState = null
                    trailingSendJob = null
                    if (toSend != null) {
                        dispatchState(toSend, forceImmediate = false)
                    }
                }
            }
        }
    }

    fun sendState(state: ControllerState) {
        synchronized(stateLock) {
            currentState = state
        }
        dispatchState(state, forceImmediate = false)
        checkAndMaintainActiveStreaming()
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            var probeUsbCounter = 0
            while (isActive && _status.value == ConnectionStatus.CONNECTED) {
                delay(1000)
                val session = activeSessionId ?: continue
                val ws = webSocket ?: continue
                val now = System.currentTimeMillis()
                val seq = sequenceNumber.incrementAndGet()
                val hb = HeartbeatMessage(sessionId = session, seq = seq, timestamp = now)
                try {
                    ws.send(json.encodeToString(hb))
                    lastSentTime = now
                } catch (_: Exception) {}

                // If currently connected via Wi-Fi and AUTO_PREFER_USB is active:
                // Periodically probe USB loopback to seamlessly promote to USB when plugged in
                if (_activeTransport.value == "Wi-Fi" && transportPreference == TransportPreference.AUTO_PREFER_USB) {
                    probeUsbCounter++
                    if (probeUsbCounter >= 2) {
                        probeUsbCounter = 0
                        if (isHostReachable(usbHost, serverPort, 150)) {
                            Log.i("GamepadClient", "USB loopback detected while on Wi-Fi! Auto-switching to USB...")
                            disconnectForTransportSwitch()
                            connectToHost(usbHost, "USB")
                            break
                        }
                    }
                }
            }
        }
    }

    private var isExplicitDisconnect = false

    private fun handleDisconnect(reason: String) {
        heartbeatJob?.cancel()
        heartbeatJob = null
        activeStateStreamJob?.cancel()
        activeStateStreamJob = null
        activeSessionId = null
        _status.value = ConnectionStatus.DISCONNECTED
        _errorMessage.value = reason
        webSocket = null

        if (!isExplicitDisconnect) {
            scope.launch {
                delay(400)
                if (_status.value == ConnectionStatus.DISCONNECTED && !isExplicitDisconnect) {
                    Log.i("GamepadClient", "Auto-reconnecting to server...")
                    connect()
                }
            }
        }
    }

    fun disconnect() {
        isExplicitDisconnect = true
        heartbeatJob?.cancel()
        heartbeatJob = null
        activeStateStreamJob?.cancel()
        activeStateStreamJob = null

        // Send a neutral state packet before closing
        val session = activeSessionId
        val ws = webSocket
        if (session != null && ws != null) {
            val seq = sequenceNumber.incrementAndGet()
            val neutral = ControllerState.neutral(sessionId = session, seq = seq)
            try {
                ws.send(json.encodeToString(neutral))
            } catch (_: Exception) {}
        }

        ws?.close(1000, "User disconnected")
        handleDisconnect("User initiated disconnect")
    }
}
