package com.shadowguard.companion.model

data class PairingRequest(
    val token: String,
    val deviceName: String,
    val deviceModel: String,
    val androidVersion: String,
    val fcmToken: String? = null
)

data class PairingResponse(
    val deviceId: String,
    val controllerId: String,
    val deviceToken: String,
    val message: String
)

data class TelemetryPayload(
    val batteryLevel: Int,
    val isCharging: Boolean,
    val batteryHealth: String,
    val batteryTemperature: Float,
    val availableStorageGb: Float,
    val totalStorageGb: Float,
    val availableRamGb: Float,
    val totalRamGb: Float,
    val networkType: String,
    val location: LocationData?
)

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val altitude: Double = 0.0,
    val speed: Float = 0f,
    val timestamp: String
)

data class DeviceCommand(
    val id: String,
    val deviceId: String,
    val commandType: String,
    val payload: String?,
    val status: String,
    val createdAt: String
)

data class CommandAckRequest(
    val result: String,
    val status: String = "EXECUTED" // EXECUTED, FAILED
)

data class ApiResponse<T>(
    val success: Boolean,
    val message: String?,
    val data: T?
)
