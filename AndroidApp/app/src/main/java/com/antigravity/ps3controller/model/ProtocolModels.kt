package com.antigravity.ps3controller.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ControllerButtons(
    val cross: Boolean = false,
    val circle: Boolean = false,
    val square: Boolean = false,
    val triangle: Boolean = false,
    val l1: Boolean = false,
    val r1: Boolean = false,
    val l2: Boolean = false,
    val r2: Boolean = false,
    val l3: Boolean = false,
    val r3: Boolean = false,
    val start: Boolean = false,
    val select: Boolean = false,
    val ps: Boolean = false
)

@Serializable
data class ControllerDPad(
    val up: Boolean = false,
    val down: Boolean = false,
    val left: Boolean = false,
    val right: Boolean = false
)

@Serializable
data class ControllerAxes(
    val lx: Float = 0f,
    val ly: Float = 0f,
    val rx: Float = 0f,
    val ry: Float = 0f
)

@Serializable
data class ControllerTriggers(
    val l2: Float = 0f,
    val r2: Float = 0f
)

@Serializable
data class ControllerState(
    val type: String = "state",
    @SerialName("session_id") val sessionId: String,
    val seq: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val buttons: ControllerButtons = ControllerButtons(),
    val dpad: ControllerDPad = ControllerDPad(),
    val axes: ControllerAxes = ControllerAxes(),
    val triggers: ControllerTriggers = ControllerTriggers()
) {
    companion object {
        fun neutral(sessionId: String = "", seq: Long = 0): ControllerState {
            return ControllerState(
                sessionId = sessionId,
                seq = seq,
                timestamp = System.currentTimeMillis(),
                buttons = ControllerButtons(),
                dpad = ControllerDPad(),
                axes = ControllerAxes(),
                triggers = ControllerTriggers()
            )
        }
    }
}

@Serializable
data class PairRequest(
    val type: String = "pair_request",
    @SerialName("client_name") val clientName: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("protocol_version") val protocolVersion: Int = 1,
    @SerialName("pairing_code") val pairingCode: String = "1234"
)

@Serializable
data class PairResponse(
    val type: String = "pair_response",
    val status: String,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("heartbeat_interval_ms") val heartbeatIntervalMs: Int? = 1000,
    @SerialName("max_rate_hz") val maxRateHz: Int? = 60,
    val error: String? = null
)

@Serializable
data class HeartbeatMessage(
    val type: String = "heartbeat",
    @SerialName("session_id") val sessionId: String,
    val seq: Long,
    val timestamp: Long = System.currentTimeMillis()
)
