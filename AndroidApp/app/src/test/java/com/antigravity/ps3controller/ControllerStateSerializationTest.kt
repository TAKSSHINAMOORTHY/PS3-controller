package com.antigravity.ps3controller

import com.antigravity.ps3controller.model.ControllerAxes
import com.antigravity.ps3controller.model.ControllerButtons
import com.antigravity.ps3controller.model.ControllerDPad
import com.antigravity.ps3controller.model.ControllerState
import com.antigravity.ps3controller.model.ControllerTriggers
import com.antigravity.ps3controller.model.HeartbeatMessage
import com.antigravity.ps3controller.model.PairRequest
import com.antigravity.ps3controller.model.PairResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerStateSerializationTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun testControllerStateSerializationKeys() {
        val state = ControllerState(
            sessionId = "sess-12345",
            seq = 77,
            timestamp = 1700000000000L,
            buttons = ControllerButtons(cross = true, triangle = true),
            dpad = ControllerDPad(up = true),
            axes = ControllerAxes(lx = -0.5f, ly = 0.5f, rx = 1.0f, ry = -1.0f),
            triggers = ControllerTriggers(l2 = 0.8f, r2 = 0.0f)
        )

        val jsonString = json.encodeToString(state)
        val obj = json.parseToJsonElement(jsonString).jsonObject

        assertEquals("state", obj["type"]?.jsonPrimitive?.content)
        assertEquals("sess-12345", obj["session_id"]?.jsonPrimitive?.content)
        assertEquals(77L, obj["seq"]?.jsonPrimitive?.long)
        assertEquals(1700000000000L, obj["timestamp"]?.jsonPrimitive?.long)

        val buttonsObj = obj["buttons"]?.jsonObject!!
        assertTrue(buttonsObj["cross"]?.jsonPrimitive?.boolean == true)
        assertTrue(buttonsObj["triangle"]?.jsonPrimitive?.boolean == true)
        assertFalse(buttonsObj["circle"]?.jsonPrimitive?.boolean == true)
        assertFalse(buttonsObj["square"]?.jsonPrimitive?.boolean == true)

        val dpadObj = obj["dpad"]?.jsonObject!!
        assertTrue(dpadObj["up"]?.jsonPrimitive?.boolean == true)
        assertFalse(dpadObj["down"]?.jsonPrimitive?.boolean == true)

        val axesObj = obj["axes"]?.jsonObject!!
        assertEquals(-0.5, axesObj["lx"]?.jsonPrimitive?.double ?: 0.0, 0.001)
        assertEquals(0.5, axesObj["ly"]?.jsonPrimitive?.double ?: 0.0, 0.001)
        assertEquals(1.0, axesObj["rx"]?.jsonPrimitive?.double ?: 0.0, 0.001)
        assertEquals(-1.0, axesObj["ry"]?.jsonPrimitive?.double ?: 0.0, 0.001)

        val triggersObj = obj["triggers"]?.jsonObject!!
        assertEquals(0.8, triggersObj["l2"]?.jsonPrimitive?.double ?: 0.0, 0.001)
        assertEquals(0.0, triggersObj["r2"]?.jsonPrimitive?.double ?: 0.0, 0.001)
    }

    @Test
    fun testPairRequestAndResponseSerialization() {
        val req = PairRequest(
            clientName = "Google Pixel",
            clientId = "android-99",
            protocolVersion = 1,
            pairingCode = "1234"
        )
        val reqStr = json.encodeToString(req)
        val reqObj = json.parseToJsonElement(reqStr).jsonObject
        assertEquals("pair_request", reqObj["type"]?.jsonPrimitive?.content)
        assertEquals("Google Pixel", reqObj["client_name"]?.jsonPrimitive?.content)
        assertEquals("android-99", reqObj["client_id"]?.jsonPrimitive?.content)
        assertEquals(1, reqObj["protocol_version"]?.jsonPrimitive?.int)
        assertEquals("1234", reqObj["pairing_code"]?.jsonPrimitive?.content)

        val respJson = """
            {
                "type": "pair_response",
                "status": "accepted",
                "session_id": "session-xyz",
                "heartbeat_interval_ms": 1000,
                "max_rate_hz": 60
            }
        """.trimIndent()

        val resp = json.decodeFromString<PairResponse>(respJson)
        assertEquals("pair_response", resp.type)
        assertEquals("accepted", resp.status)
        assertEquals("session-xyz", resp.sessionId)
        assertEquals(1000, resp.heartbeatIntervalMs)
        assertEquals(60, resp.maxRateHz)
    }

    @Test
    fun testHeartbeatSerialization() {
        val hb = HeartbeatMessage(sessionId = "sess-hb", seq = 10, timestamp = 987654321L)
        val hbStr = json.encodeToString(hb)
        val hbObj = json.parseToJsonElement(hbStr).jsonObject
        assertEquals("heartbeat", hbObj["type"]?.jsonPrimitive?.content)
        assertEquals("sess-hb", hbObj["session_id"]?.jsonPrimitive?.content)
        assertEquals(10L, hbObj["seq"]?.jsonPrimitive?.long)
        assertEquals(987654321L, hbObj["timestamp"]?.jsonPrimitive?.long)
    }
}
