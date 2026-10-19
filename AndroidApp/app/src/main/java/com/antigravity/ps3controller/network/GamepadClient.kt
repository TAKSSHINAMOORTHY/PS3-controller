package com.antigravity.ps3controller.network

import com.antigravity.ps3controller.model.ControllerState
import com.antigravity.ps3controller.model.HeartbeatMessage
import com.antigravity.ps3controller.model.PairRequest
import com.antigravity.ps3controller.model.PairResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val wifiHost: String = "192.168.1.20",
    val serverPort: Int = 6000,
    val transportPreference: TransportPreference = TransportPreference.AUTO_PREFER_USB,
    private val clientName: String = "Android PS3 Gamepad"
) {
    // Backwards-compatible constructor for single host
    constructor(serverHost: String, serverPort: Int, clientName: String = "Android PS3 Gamepad") : this(
        usbHost = if (serverHost == "127.0.0.1") "127.0.0.1" else serverHost,
        wifiHost = if (serverHost != "127.0.0.1") serverHost else "192.168.1.20",
        serverPort = serverPort,
        transportPreference = if (serverHost == "127.0.0.1") TransportPreference.AUTO_PREFER_USB else TransportPreference.FORCE_WIFI,
        clientName = clientName
    )

    val serverHost: String get() = _activeHost.value

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val scope = CoroutineScope(Dispatchers.IO + Job())

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
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private suspend fun resolveTargetHost(): Pair<String, String> = withContext(Dispatchers.IO) {
        when (transportPreference) {
            TransportPreference.FORCE_USB -> Pair(usbHost, "USB")
            TransportPreference.FORCE_WIFI -> Pair(wifiHost, "Wi-Fi")
            TransportPreference.AUTO_PREFER_USB -> {
                // Probe USB (127.0.0.1:serverPort) first with quick 300ms socket probe
                val isUsbAvailable = try {
                    java.net.Socket().use { s ->
                        s.connect(java.net.InetSocketAddress(usbHost, serverPort), 300)
                        true
                    }
                } catch (_: Exception) {
                    false
                }

                if (isUsbAvailable) {
                    Pair(usbHost, "USB")
                } else {
                    Pair(wifiHost, "Wi-Fi")
                }
            }
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

    private var pendingState: ControllerState? = null
    private var trailingSendJob: Job? = null

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

    fun sendState(state: ControllerState) {
        val ws = webSocket ?: return
        val session = activeSessionId ?: return
        if (_status.value != ConnectionStatus.CONNECTED) return

        val now = System.currentTimeMillis()
        val timeSinceLast = now - lastSentTime
        val isNeutral = isNeutralState(state)

        // Neutral / release states must never be dropped to avoid stuck inputs.
        if (isNeutral || timeSinceLast >= 16) {
            trailingSendJob?.cancel()
            trailingSendJob = null
            pendingState = null
            lastSentTime = now

            val seq = sequenceNumber.incrementAndGet()
            val outbound = state.copy(sessionId = session, seq = seq, timestamp = now)
            try {
                val payload = json.encodeToString(outbound)
                ws.send(payload)
            } catch (_: Exception) {}
        } else {
            // Buffer latest motion state so trailing final position is never lost
            pendingState = state
            if (trailingSendJob == null) {
                val delayMs = (16 - timeSinceLast).coerceIn(1, 16)
                trailingSendJob = scope.launch {
                    delay(delayMs)
                    val toSend = pendingState
                    pendingState = null
                    trailingSendJob = null
                    if (toSend != null) {
                        sendState(toSend)
                    }
                }
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && _status.value == ConnectionStatus.CONNECTED) {
                delay(500)
                val session = activeSessionId ?: continue
                val ws = webSocket ?: continue
                val now = System.currentTimeMillis()
                if (now - lastSentTime >= 400) {
                    val seq = sequenceNumber.incrementAndGet()
                    val hb = HeartbeatMessage(sessionId = session, seq = seq, timestamp = now)
                    try {
                        ws.send(json.encodeToString(hb))
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private var isExplicitDisconnect = false

    private fun handleDisconnect(reason: String) {
        heartbeatJob?.cancel()
        heartbeatJob = null
        activeSessionId = null
        _status.value = ConnectionStatus.DISCONNECTED
        _errorMessage.value = reason
        webSocket = null

        if (!isExplicitDisconnect) {
            scope.launch {
                delay(1500)
                if (_status.value == ConnectionStatus.DISCONNECTED && !isExplicitDisconnect) {
                    Log.i("GamepadClient", "Auto-reconnecting to ws://$serverHost:$serverPort...")
                    connect()
                }
            }
        }
    }

    fun disconnect() {
        isExplicitDisconnect = true
        heartbeatJob?.cancel()
        heartbeatJob = null

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
