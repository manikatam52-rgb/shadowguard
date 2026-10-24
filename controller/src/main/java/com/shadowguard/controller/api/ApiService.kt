package com.shadowguard.controller.api

import com.shadowguard.controller.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body request: AuthRequest): Response<ApiResponse<AuthResponse>>

    @POST("api/auth/register")
    suspend fun register(@Body request: AuthRequest): Response<ApiResponse<AuthResponse>>

    @POST("api/pairing/generate-token")
    suspend fun generatePairingToken(
        @Header("Authorization") authHeader: String,
        @Body request: GenerateTokenRequest = GenerateTokenRequest()
    ): Response<ApiResponse<PairingTokenResponse>>

    @GET("api/devices")
    suspend fun getDevices(
        @Header("Authorization") authHeader: String
    ): Response<ApiResponse<List<Device>>>

    @GET("api/devices/{deviceId}")
    suspend fun getDeviceDetails(
        @Header("Authorization") authHeader: String,
        @Path("deviceId") deviceId: String
    ): Response<ApiResponse<Device>>

    @GET("api/devices/{deviceId}/telemetry/history")
    suspend fun getDeviceTelemetryHistory(
        @Header("Authorization") authHeader: String,
        @Path("deviceId") deviceId: String,
        @Query("limit") limit: Int = 20
    ): Response<ApiResponse<List<TelemetryData>>>

    @POST("api/devices/{deviceId}/commands")
    suspend fun sendCommand(
        @Header("Authorization") authHeader: String,
        @Path("deviceId") deviceId: String,
        @Body request: DeviceCommandRequest
    ): Response<ApiResponse<DeviceCommand>>

    @GET("api/devices/{deviceId}/commands")
    suspend fun getDeviceCommands(
        @Header("Authorization") authHeader: String,
        @Path("deviceId") deviceId: String
    ): Response<ApiResponse<List<DeviceCommand>>>

    @POST("api/devices/{deviceId}/revoke")
    suspend fun revokeDeviceAccess(
        @Header("Authorization") authHeader: String,
        @Path("deviceId") deviceId: String
    ): Response<ApiResponse<Boolean>>

    @GET("api/audit-logs")
    suspend fun getAuditLogs(
        @Header("Authorization") authHeader: String
    ): Response<ApiResponse<List<AuditLog>>>
}
