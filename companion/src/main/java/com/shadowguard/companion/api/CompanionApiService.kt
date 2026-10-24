package com.shadowguard.companion.api

import com.shadowguard.companion.model.*
import retrofit2.Response
import retrofit2.http.*

interface CompanionApiService {

    @POST("api/pairing/pair-device")
    suspend fun pairDevice(
        @Body request: PairingRequest
    ): Response<ApiResponse<PairingResponse>>

    @POST("api/devices/{deviceId}/telemetry")
    suspend fun sendTelemetry(
        @Header("Authorization") authHeader: String,
        @Path("deviceId") deviceId: String,
        @Body payload: TelemetryPayload
    ): Response<ApiResponse<Boolean>>

    @GET("api/devices/{deviceId}/pending-commands")
    suspend fun getPendingCommands(
        @Header("Authorization") authHeader: String,
        @Path("deviceId") deviceId: String
    ): Response<ApiResponse<List<DeviceCommand>>>

    @POST("api/devices/{deviceId}/commands/{commandId}/ack")
    suspend fun acknowledgeCommand(
        @Header("Authorization") authHeader: String,
        @Path("deviceId") deviceId: String,
        @Path("commandId") commandId: String,
        @Body request: CommandAckRequest
    ): Response<ApiResponse<Boolean>>

    @POST("api/devices/{deviceId}/revoke")
    suspend fun revokeAccess(
        @Header("Authorization") authHeader: String,
        @Path("deviceId") deviceId: String
    ): Response<ApiResponse<Boolean>>
}
