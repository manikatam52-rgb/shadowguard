package com.shadowguard.controller.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.shadowguard.controller.data.PreferencesManager
import com.shadowguard.controller.model.TelemetryData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

enum class WsConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

class RealtimeWebSocketClient(
    private val preferencesManager: PreferencesManager,
    private val onTelemetryReceived: (String, TelemetryData) -> Unit,
    private val onCommandAckReceived: (String, String) -> Unit
) {
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var webSocket: WebSocket? = null
    private var isClosedManually = false

    private val _connectionState = MutableStateFlow(WsConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WsConnectionState> = _connectionState.asStateFlow()

    fun connect() {
        if (_connectionState.value == WsConnectionState.CONNECTED) return
        isClosedManually = false

        val serverUrl = preferencesManager.serverUrl
        val token = preferencesManager.authToken ?: return

        val wsUrl = serverUrl.replace("http://", "ws://").replace("https://", "wss://") + "ws"

        val client = OkHttpClient.Builder()
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(wsUrl).build()

        _connectionState.value = WsConnectionState.CONNECTING

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = WsConnectionState.CONNECTED

                // Authenticate Controller
                val authPayload = JsonObject().apply {
                    addProperty("type", "AUTH_CONTROLLER")
                    addProperty("token", token)
                }
                webSocket.send(gson.toJson(authPayload))

                // Start periodic ping keep-alive
                startPingKeepAlive(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val jsonObj = gson.fromJson(text, JsonObject::class.java)
                    val type = jsonObj.get("type")?.asString ?: return

                    when (type) {
                        "TELEMETRY_UPDATED" -> {
                            val deviceId = jsonObj.get("deviceId")?.asString ?: ""
                            val telemetryObj = jsonObj.getAsJsonObject("telemetry")
                            if (telemetryObj != null) {
                                val telemetryData = gson.fromJson(telemetryObj, TelemetryData::class.java)
                                onTelemetryReceived(deviceId, telemetryData)
                            }
                        }
                        "COMMAND_ACKNOWLEDGED" -> {
                            val deviceId = jsonObj.get("deviceId")?.asString ?: ""
                            val ackObj = jsonObj.getAsJsonObject("ack")
                            val result = ackObj?.get("result")?.asString ?: "Command executed"
                            onCommandAckReceived(deviceId, result)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = WsConnectionState.DISCONNECTED
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = WsConnectionState.DISCONNECTED
                if (!isClosedManually) {
                    scheduleReconnect()
                }
            }
        })
    }

    private fun startPingKeepAlive(ws: WebSocket) {
        scope.launch {
            while (isActive && _connectionState.value == WsConnectionState.CONNECTED) {
                delay(20_000)
                val pingObj = JsonObject().apply { addProperty("type", "PING") }
                ws.send(gson.toJson(pingObj))
            }
        }
    }

    private fun scheduleReconnect() {
        scope.launch {
            delay(5000)
            if (!isClosedManually && _connectionState.value == WsConnectionState.DISCONNECTED) {
                connect()
            }
        }
    }

    fun disconnect() {
        isClosedManually = true
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = WsConnectionState.DISCONNECTED
    }
}
