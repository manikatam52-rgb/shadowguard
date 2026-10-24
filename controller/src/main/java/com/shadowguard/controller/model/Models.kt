package com.shadowguard.controller.model

import com.google.gson.annotations.SerializedName

data class AuthRequest(
    val username: String,
    val passwordHash: String
)

data class AuthResponse(
    val token: String,
    val controllerId: String,
    val username: String
)

data class GenerateTokenRequest(
    val expiresInMinutes: Int = 15
)

data class PairingTokenResponse(
    val token: String,
    val qrCodePayload: String,
    val expiresAt: String
)

data class Device(
    val id: String,
    val deviceName: String,
    val deviceModel: String,
    val androidVersion: String,
    val status: String, // "ONLINE", "OFFLINE", "PAUSED", "REVOKED"
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val batteryHealth: String = "Good",
    val totalStorageGb: Float = 0f,
    val availableStorageGb: Float = 0f,
    val totalRamGb: Float = 0f,
    val availableRamGb: Float = 0f,
    val lastSeenAt: String? = null,
    val createdAt: String? = null,
    val lastTelemetry: TelemetryData? = null
)

data class TelemetryData(
    val deviceId: String,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val batteryHealth: String,
    val batteryTemperature: Float,
    val availableStorageGb: Float,
    val totalStorageGb: Float,
    val availableRamGb: Float,
    val totalRamGb: Float,
    val networkType: String,
    val location: LocationData?,
    val timestamp: String
)

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val altitude: Double = 0.0,
    val speed: Float = 0f,
    val timestamp: String
)

data class DeviceCommandRequest(
    val commandType: String, // "PING", "REFRESH_LOCATION", "SOUND_ALARM", "PAUSE_MONITORING", "REVOKE_ACCESS"
    val payload: String? = null
)

data class DeviceCommand(
    val id: String,
    val deviceId: String,
    val commandType: String,
    val status: String, // "PENDING", "SENT", "EXECUTED", "FAILED"
    val payload: String?,
    val result: String?,
    val createdAt: String,
    val executedAt: String?
)

data class AuditLog(
    val id: String,
    val action: String,
    val targetDeviceId: String?,
    val details: String?,
    val timestamp: String
)

data class ApiResponse<T>(
    val success: Boolean,
    val message: String?,
    val data: T?
)
