package com.shadowguard.controller.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shadowguard.controller.api.ApiClient
import com.shadowguard.controller.api.RealtimeWebSocketClient
import com.shadowguard.controller.api.WsConnectionState
import com.shadowguard.controller.data.PreferencesManager
import com.shadowguard.controller.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ControllerUiState(
    val serverUrl: String = "http://10.0.2.2:3000/",
    val isLoggedIn: Boolean = false,
    val username: String = "",
    val devices: List<Device> = emptyList(),
    val selectedDevice: Device? = null,
    val telemetryHistory: List<TelemetryData> = emptyList(),
    val pairingToken: PairingTokenResponse? = null,
    val auditLogs: List<AuditLog> = emptyList(),
    val wsState: WsConnectionState = WsConnectionState.DISCONNECTED,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class ControllerViewModel(application: Application) : AndroidViewModel(application) {

    val prefs = PreferencesManager(application)

    private var realTimeWsClient: RealtimeWebSocketClient? = null

    private val _uiState = MutableStateFlow(
        ControllerUiState(
            serverUrl = prefs.serverUrl,
            isLoggedIn = prefs.isLoggedIn(),
            username = prefs.username ?: ""
        )
    )
    val uiState: StateFlow<ControllerUiState> = _uiState.asStateFlow()

    init {
        if (prefs.isLoggedIn()) {
            loadDevices()
            initWebSocket()
        }
    }

    private fun initWebSocket() {
        realTimeWsClient?.disconnect()
        realTimeWsClient = RealtimeWebSocketClient(
            preferencesManager = prefs,
            onTelemetryReceived = { deviceId, telemetry ->
                handleRealtimeTelemetry(deviceId, telemetry)
            },
            onCommandAckReceived = { deviceId, resultMsg ->
                _uiState.update {
                    it.copy(successMessage = "Device Command Result: $resultMsg")
                }
                loadDevices()
                if (_uiState.value.selectedDevice?.id == deviceId) {
                    selectDevice(deviceId)
                }
            }
        )

        viewModelScope.launch {
            realTimeWsClient?.connectionState?.collect { state ->
                _uiState.update { it.copy(wsState = state) }
            }
        }

        realTimeWsClient?.connect()
    }

    private fun handleRealtimeTelemetry(deviceId: String, telemetry: TelemetryData) {
        _uiState.update { currentState ->
            val updatedDevices = currentState.devices.map { dev ->
                if (dev.id == deviceId) {
                    dev.copy(
                        batteryLevel = telemetry.batteryLevel,
                        isCharging = telemetry.isCharging,
                        batteryHealth = telemetry.batteryHealth,
                        totalStorageGb = telemetry.totalStorageGb,
                        availableStorageGb = telemetry.availableStorageGb,
                        totalRamGb = telemetry.totalRamGb,
                        availableRamGb = telemetry.availableRamGb,
                        status = "ONLINE",
                        lastSeenAt = telemetry.timestamp,
                        lastTelemetry = telemetry
                    )
                } else dev
            }

            val updatedSelectedDevice = if (currentState.selectedDevice?.id == deviceId) {
                currentState.selectedDevice.copy(
                    batteryLevel = telemetry.batteryLevel,
                    isCharging = telemetry.isCharging,
                    batteryHealth = telemetry.batteryHealth,
                    totalStorageGb = telemetry.totalStorageGb,
                    availableStorageGb = telemetry.availableStorageGb,
                    totalRamGb = telemetry.totalRamGb,
                    availableRamGb = telemetry.availableRamGb,
                    status = "ONLINE",
                    lastSeenAt = telemetry.timestamp,
                    lastTelemetry = telemetry
                )
            } else currentState.selectedDevice

            val updatedHistory = if (currentState.selectedDevice?.id == deviceId) {
                listOf(telemetry) + currentState.telemetryHistory.take(19)
            } else currentState.telemetryHistory

            currentState.copy(
                devices = updatedDevices,
                selectedDevice = updatedSelectedDevice,
                telemetryHistory = updatedHistory
            )
        }
    }

    fun updateServerUrl(url: String) {
        prefs.serverUrl = url
        ApiClient.resetClient()
        _uiState.update { it.copy(serverUrl = prefs.serverUrl) }
        if (prefs.isLoggedIn()) {
            initWebSocket()
        }
    }

    fun login(usernameInput: String, passwordInput: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val api = ApiClient.getService(prefs)
                val response = api.login(AuthRequest(usernameInput, passwordInput))
                if (response.isSuccessful && response.body()?.success == true) {
                    val authData = response.body()?.data
                    if (authData != null) {
                        prefs.authToken = authData.token
                        prefs.username = authData.username
                        _uiState.update {
                            it.copy(
                                isLoggedIn = true,
                                username = authData.username,
                                isLoading = false,
                                successMessage = "Logged in successfully"
                            )
                        }
                        loadDevices()
                        initWebSocket()
                    } else {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Invalid response") }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = response.body()?.message ?: "Login failed (${response.code()})"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Connection error: ${e.localizedMessage}") }
            }
        }
    }

    fun register(usernameInput: String, passwordInput: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val api = ApiClient.getService(prefs)
                val response = api.register(AuthRequest(usernameInput, passwordInput))
                if (response.isSuccessful && response.body()?.success == true) {
                    val authData = response.body()?.data
                    if (authData != null) {
                        prefs.authToken = authData.token
                        prefs.username = authData.username
                        _uiState.update {
                            it.copy(
                                isLoggedIn = true,
                                username = authData.username,
                                isLoading = false,
                                successMessage = "Registered & Logged in"
                            )
                        }
                        loadDevices()
                        initWebSocket()
                    } else {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Invalid response") }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = response.body()?.message ?: "Registration failed (${response.code()})"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Connection error: ${e.localizedMessage}") }
            }
        }
    }

    fun logout() {
        realTimeWsClient?.disconnect()
        realTimeWsClient = null
        prefs.logout()
        _uiState.update {
            it.copy(
                isLoggedIn = false,
                username = "",
                devices = emptyList(),
                selectedDevice = null,
                pairingToken = null,
                telemetryHistory = emptyList(),
                wsState = WsConnectionState.DISCONNECTED
            )
        }
    }

    fun loadDevices() {
        viewModelScope.launch {
            val token = prefs.authToken ?: return@launch
            _uiState.update { it.copy(isLoading = true) }
            try {
                val api = ApiClient.getService(prefs)
                val response = api.getDevices("Bearer $token")
                if (response.isSuccessful && response.body()?.success == true) {
                    val deviceList = response.body()?.data ?: emptyList()
                    _uiState.update { it.copy(devices = deviceList, isLoading = false) }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = response.body()?.message) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to fetch devices: ${e.localizedMessage}") }
            }
        }
    }

    fun selectDevice(deviceId: String) {
        viewModelScope.launch {
            val token = prefs.authToken ?: return@launch
            _uiState.update { it.copy(isLoading = true) }
            try {
                val api = ApiClient.getService(prefs)
                val response = api.getDeviceDetails("Bearer $token", deviceId)
                if (response.isSuccessful && response.body()?.success == true) {
                    val device = response.body()?.data
                    _uiState.update { it.copy(selectedDevice = device, isLoading = false) }
                    loadTelemetryHistory(deviceId)
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = response.body()?.message) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Error: ${e.localizedMessage}") }
            }
        }
    }

    fun loadTelemetryHistory(deviceId: String) {
        viewModelScope.launch {
            val token = prefs.authToken ?: return@launch
            try {
                val api = ApiClient.getService(prefs)
                val response = api.getDeviceTelemetryHistory("Bearer $token", deviceId)
                if (response.isSuccessful && response.body()?.success == true) {
                    _uiState.update { it.copy(telemetryHistory = response.body()?.data ?: emptyList()) }
                }
            } catch (_: Exception) {}
        }
    }

    fun generatePairingToken() {
        viewModelScope.launch {
            val token = prefs.authToken ?: return@launch
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val api = ApiClient.getService(prefs)
                val response = api.generatePairingToken("Bearer $token")
                if (response.isSuccessful && response.body()?.success == true) {
                    _uiState.update {
                        it.copy(
                            pairingToken = response.body()?.data,
                            isLoading = false,
                            successMessage = "Pairing token generated"
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = response.body()?.message ?: "Token generation failed") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Error: ${e.localizedMessage}") }
            }
        }
    }

    fun sendCommand(deviceId: String, commandType: String, payload: String? = null) {
        viewModelScope.launch {
            val token = prefs.authToken ?: return@launch
            _uiState.update { it.copy(isLoading = true) }
            try {
                val api = ApiClient.getService(prefs)
                val response = api.sendCommand("Bearer $token", deviceId, DeviceCommandRequest(commandType, payload))
                if (response.isSuccessful && response.body()?.success == true) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "Command '$commandType' issued to device"
                        )
                    }
                    selectDevice(deviceId)
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = response.body()?.message ?: "Command failed") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Error sending command: ${e.localizedMessage}") }
            }
        }
    }

    fun revokeDeviceAccess(deviceId: String) {
        viewModelScope.launch {
            val token = prefs.authToken ?: return@launch
            _uiState.update { it.copy(isLoading = true) }
            try {
                val api = ApiClient.getService(prefs)
                val response = api.revokeDeviceAccess("Bearer $token", deviceId)
                if (response.isSuccessful && response.body()?.success == true) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            selectedDevice = null,
                            successMessage = "Device access revoked successfully"
                        )
                    }
                    loadDevices()
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = response.body()?.message ?: "Revocation failed") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Error revoking device: ${e.localizedMessage}") }
            }
        }
    }

    fun loadAuditLogs() {
        viewModelScope.launch {
            val token = prefs.authToken ?: return@launch
            try {
                val api = ApiClient.getService(prefs)
                val response = api.getAuditLogs("Bearer $token")
                if (response.isSuccessful && response.body()?.success == true) {
                    _uiState.update { it.copy(auditLogs = response.body()?.data ?: emptyList()) }
                }
            } catch (_: Exception) {}
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        realTimeWsClient?.disconnect()
    }
}
