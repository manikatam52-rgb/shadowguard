package com.shadowguard.companion.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shadowguard.companion.api.CompanionApiClient
import com.shadowguard.companion.data.CompanionPreferences
import com.shadowguard.companion.model.LocationData
import com.shadowguard.companion.model.PairingRequest
import com.shadowguard.companion.service.MonitoringForegroundService
import com.shadowguard.companion.util.DeviceStatusCollector
import com.shadowguard.companion.util.LocationHelper
import com.shadowguard.companion.worker.StatusSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PermissionsState(
    val hasFineLocation: Boolean = false,
    val hasBackgroundLocation: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val isIgnoringBatteryOptimizations: Boolean = false
)

data class CompanionUiState(
    val serverUrl: String = "http://10.0.2.2:3000/",
    val isPaired: Boolean = false,
    val deviceId: String? = null,
    val isMonitoringPaused: Boolean = false,
    val batteryInfo: DeviceStatusCollector.BatteryInfo? = null,
    val storageInfo: DeviceStatusCollector.StorageInfo? = null,
    val memoryInfo: DeviceStatusCollector.MemoryInfoData? = null,
    val networkType: String = "Unknown",
    val locationData: LocationData? = null,
    val permissions: PermissionsState = PermissionsState(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class CompanionViewModel(application: Application) : AndroidViewModel(application) {

    val prefs = CompanionPreferences(application)
    private val locationHelper = LocationHelper(application)

    private val _uiState = MutableStateFlow(
        CompanionUiState(
            serverUrl = prefs.serverUrl,
            isPaired = prefs.isPaired(),
            deviceId = prefs.deviceId,
            isMonitoringPaused = prefs.isMonitoringPaused
        )
    )
    val uiState: StateFlow<CompanionUiState> = _uiState.asStateFlow()

    init {
        refreshDeviceStatus()
        checkPermissions()

        if (prefs.isPaired() && !prefs.isMonitoringPaused) {
            MonitoringForegroundService.startService(getApplication())
            StatusSyncWorker.schedulePeriodicSync(getApplication())
        }
    }

    fun updateServerUrl(url: String) {
        prefs.serverUrl = url
        CompanionApiClient.resetClient()
        _uiState.update { it.copy(serverUrl = prefs.serverUrl) }
    }

    fun refreshDeviceStatus() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val battery = DeviceStatusCollector.getBatteryInfo(context)
            val storage = DeviceStatusCollector.getStorageInfo()
            val memory = DeviceStatusCollector.getMemoryInfo(context)
            val network = DeviceStatusCollector.getNetworkType(context)
            val loc = locationHelper.getCurrentLocation()

            _uiState.update {
                it.copy(
                    batteryInfo = battery,
                    storageInfo = storage,
                    memoryInfo = memory,
                    networkType = network,
                    locationData = loc
                )
            }
        }
    }

    fun checkPermissions() {
        val context = getApplication<Application>()
        val fineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val bgLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val postNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val ignoringBattery = pm.isIgnoringBatteryOptimizations(context.packageName)

        _uiState.update {
            it.copy(
                permissions = PermissionsState(
                    hasFineLocation = fineLocation,
                    hasBackgroundLocation = bgLocation,
                    hasNotificationPermission = postNotif,
                    isIgnoringBatteryOptimizations = ignoringBattery
                )
            )
        }
    }

    fun pairWithToken(token: String) {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid pairing code") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val api = CompanionApiClient.getService(prefs)
                val request = PairingRequest(
                    token = cleanToken,
                    deviceName = DeviceStatusCollector.getDeviceModel(),
                    deviceModel = DeviceStatusCollector.getDeviceModel(),
                    androidVersion = DeviceStatusCollector.getAndroidVersion()
                )

                val response = api.pairDevice(request)
                if (response.isSuccessful && response.body()?.success == true) {
                    val pairData = response.body()?.data
                    if (pairData != null) {
                        prefs.deviceId = pairData.deviceId
                        prefs.deviceToken = pairData.deviceToken
                        prefs.controllerId = pairData.controllerId
                        prefs.isMonitoringPaused = false

                        _uiState.update {
                            it.copy(
                                isPaired = true,
                                deviceId = pairData.deviceId,
                                isMonitoringPaused = false,
                                isLoading = false,
                                successMessage = "Successfully paired with Controller!"
                            )
                        }

                        MonitoringForegroundService.startService(getApplication())
                        StatusSyncWorker.schedulePeriodicSync(getApplication())
                    } else {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Pairing data missing") }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = response.body()?.message ?: "Pairing failed (${response.code()})"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Connection error: ${e.localizedMessage}") }
            }
        }
    }

    fun togglePauseMonitoring() {
        val newPaused = !prefs.isMonitoringPaused
        prefs.isMonitoringPaused = newPaused
        _uiState.update { it.copy(isMonitoringPaused = newPaused) }

        if (newPaused) {
            MonitoringForegroundService.stopService(getApplication())
            StatusSyncWorker.cancelPeriodicSync(getApplication())
            _uiState.update { it.copy(successMessage = "Monitoring paused locally") }
        } else {
            MonitoringForegroundService.startService(getApplication())
            StatusSyncWorker.schedulePeriodicSync(getApplication())
            _uiState.update { it.copy(successMessage = "Monitoring resumed") }
        }
    }

    fun revokeAccessAndUnpair() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val deviceId = prefs.deviceId
            val deviceToken = prefs.deviceToken

            if (deviceId != null && deviceToken != null) {
                try {
                    val api = CompanionApiClient.getService(prefs)
                    api.revokeAccess("Bearer $deviceToken", deviceId)
                } catch (_: Exception) {}
            }

            prefs.unpair()
            MonitoringForegroundService.stopService(getApplication())
            StatusSyncWorker.cancelPeriodicSync(getApplication())

            _uiState.update {
                it.copy(
                    isPaired = false,
                    deviceId = null,
                    isLoading = false,
                    successMessage = "Access revoked and device unpaired."
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
