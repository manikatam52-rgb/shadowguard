package com.shadowguard.companion.worker

import android.content.Context
import androidx.work.*
import com.shadowguard.companion.api.CompanionApiClient
import com.shadowguard.companion.data.CompanionPreferences
import com.shadowguard.companion.model.TelemetryPayload
import com.shadowguard.companion.util.DeviceStatusCollector
import com.shadowguard.companion.util.LocationHelper
import java.util.concurrent.TimeUnit

class StatusSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = CompanionPreferences(applicationContext)

        if (!prefs.isPaired() || prefs.isMonitoringPaused) {
            return Result.success()
        }

        val deviceId = prefs.deviceId ?: return Result.success()
        val deviceToken = prefs.deviceToken ?: return Result.success()

        val locationHelper = LocationHelper(applicationContext)

        val battery = DeviceStatusCollector.getBatteryInfo(applicationContext)
        val storage = DeviceStatusCollector.getStorageInfo()
        val memory = DeviceStatusCollector.getMemoryInfo(applicationContext)
        val networkType = DeviceStatusCollector.getNetworkType(applicationContext)
        val location = locationHelper.getCurrentLocation()

        val payload = TelemetryPayload(
            batteryLevel = battery.level,
            isCharging = battery.isCharging,
            batteryHealth = battery.health,
            batteryTemperature = battery.temperature,
            availableStorageGb = storage.availableGb,
            totalStorageGb = storage.totalGb,
            availableRamGb = memory.availableGb,
            totalRamGb = memory.totalGb,
            networkType = networkType,
            location = location
        )

        return try {
            val api = CompanionApiClient.getService(prefs)
            val response = api.sendTelemetry("Bearer $deviceToken", deviceId, payload)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "shadowguard_companion_status_sync"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<StatusSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        }

        fun cancelPeriodicSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
