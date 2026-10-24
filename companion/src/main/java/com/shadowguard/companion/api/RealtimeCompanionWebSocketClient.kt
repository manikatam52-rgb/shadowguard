package com.shadowguard.companion.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.shadowguard.companion.data.CompanionPreferences
import com.shadowguard.companion.model.DeviceCommand
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit

enum class CompanionWsState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

class RealtimeCompanionWebSocketClient(
    private val preferences: CompanionPreferences,
    private val onCommandReceived: (DeviceCommand) -> Unit
) {
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var webSocket: WebSocket? = null
    private var isClosedManually = false

    private val _connectionState = MutableStateFlow(CompanionWsState.DISCONNECTED)
    val connectionState: StateFlow<CompanionWsState> = _connectionState.asStateFlow()

    fun connect() {
        if (_connectionState.value == CompanionWsState.CONNECTED) return
        isClosedManually = false

        val serverUrl = preferences.serverUrl
        val deviceToken = preferences.deviceToken ?: return

        val wsUrl = serverUrl.replace("http://", "ws://").replace("https://", "wss://") + "ws"

        val client = OkHttpClient.Builder()
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(wsUrl).build()

        _connectionState.value = CompanionWsState.CONNECTING

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connectionState.value = CompanionWsState.CONNECTED

                // Authenticate Companion Device
                val authPayload = JsonObject().apply {
                    addProperty("type", "AUTH_DEVICE")
                    addProperty("token", deviceToken)
                }
                webSocket.send(gson.toJson(authPayload))

                startPingKeepAlive(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val jsonObj = gson.fromJson(text, JsonObject::class.java)
                    val type = jsonObj.get("type")?.asString ?: return

                    if (type == "COMMAND_ISSUED") {
                        val commandObj = jsonObj.getAsJsonObject("command")
                        if (commandObj != null) {
                            val command = gson.fromJson(commandObj, DeviceCommand::class.java)
                            onCommandReceived(command)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connectionState.value = CompanionWsState.DISCONNECTED
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = CompanionWsState.DISCONNECTED
                if (!isClosedManually) {
                    scheduleReconnect()
                }
            }
        })
    }

    private fun startPingKeepAlive(ws: WebSocket) {
        scope.launch {
            while (isActive && _connectionState.value == CompanionWsState.CONNECTED) {
                delay(20_000)
                val pingObj = JsonObject().apply { addProperty("type", "PING") }
                ws.send(gson.toJson(pingObj))
            }
        }
    }

    private fun scheduleReconnect() {
        scope.launch {
            delay(5000)
            if (!isClosedManually && _connectionState.value == CompanionWsState.DISCONNECTED) {
                connect()
            }
        }
    }

    fun disconnect() {
        isClosedManually = true
        webSocket?.close(1000, "Service stopped")
        webSocket = null
        _connectionState.value = CompanionWsState.DISCONNECTED
    }
}
