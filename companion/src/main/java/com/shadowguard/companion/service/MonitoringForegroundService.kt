package com.shadowguard.companion.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.shadowguard.companion.MainActivity
import com.shadowguard.companion.R
import com.shadowguard.companion.api.CompanionApiClient
import com.shadowguard.companion.api.RealtimeCompanionWebSocketClient
import com.shadowguard.companion.data.CompanionPreferences
import com.shadowguard.companion.model.CommandAckRequest
import com.shadowguard.companion.model.DeviceCommand
import com.shadowguard.companion.model.TelemetryPayload
import com.shadowguard.companion.util.DeviceStatusCollector
import com.shadowguard.companion.util.LocationHelper
import kotlinx.coroutines.*

class MonitoringForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var preferences: CompanionPreferences
    private lateinit var locationHelper: LocationHelper

    private var realTimeWsClient: RealtimeCompanionWebSocketClient? = null
    private var syncJob: Job? = null

    companion object {
        const val CHANNEL_ID = "shadowguard_monitoring_channel"
        const val NOTIFICATION_ID = 1001

        fun startService(context: Context) {
            val intent = Intent(context, MonitoringForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MonitoringForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferences = CompanionPreferences(applicationContext)
        locationHelper = LocationHelper(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Monitoring Active", "ShadowGuard companion device status and real-time location sync active.")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            startForeground(NOTIFICATION_ID, notification, foregroundServiceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        initWebSocket()
        startTelemetryLoop()

        return START_STICKY
    }

    private fun initWebSocket() {
        realTimeWsClient?.disconnect()
        realTimeWsClient = RealtimeCompanionWebSocketClient(
            preferences = preferences,
            onCommandReceived = { command ->
                serviceScope.launch {
                    processCommand(command)
                }
            }
        )
        realTimeWsClient?.connect()
    }

    private fun startTelemetryLoop() {
        syncJob?.cancel()
        syncJob = serviceScope.launch {
            while (isActive) {
                if (preferences.isPaired() && !preferences.isMonitoringPaused) {
                    performTelemetrySync()
                    pollAndProcessCommands()
                } else if (!preferences.isPaired()) {
                    stopSelf()
                    break
                }
                delay(30_000)
            }
        }
    }

    private suspend fun performTelemetrySync() {
        val deviceId = preferences.deviceId ?: return
        val deviceToken = preferences.deviceToken ?: return

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

        try {
            val api = CompanionApiClient.getService(preferences)
            api.sendTelemetry("Bearer $deviceToken", deviceId, payload)
        } catch (_: Exception) {}
    }

    private suspend fun pollAndProcessCommands() {
        val deviceId = preferences.deviceId ?: return
        val deviceToken = preferences.deviceToken ?: return

        try {
            val api = CompanionApiClient.getService(preferences)
            val response = api.getPendingCommands("Bearer $deviceToken", deviceId)

            if (response.isSuccessful && response.body()?.success == true) {
                val commands = response.body()?.data ?: emptyList()
                for (cmd in commands) {
                    processCommand(cmd)
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun processCommand(cmd: DeviceCommand) {
        val deviceId = preferences.deviceId ?: return
        val deviceToken = preferences.deviceToken ?: return

        var resultMsg = "Command executed"
        when (cmd.commandType) {
            "PING" -> {
                performTelemetrySync()
                resultMsg = "Pong - Telemetry refreshed live"
            }
            "REFRESH_LOCATION" -> {
                val loc = locationHelper.getCurrentLocation()
                performTelemetrySync()
                resultMsg = if (loc != null) "Location updated (${loc.latitude}, ${loc.longitude})" else "Location fix unavailable"
            }
            "SOUND_ALARM" -> {
                playAlarmSound()
                resultMsg = "Alarm tone sounded on companion device"
            }
            "PAUSE_MONITORING" -> {
                preferences.isMonitoringPaused = true
                resultMsg = "Monitoring paused by controller"
            }
            "REVOKE_ACCESS" -> {
                preferences.unpair()
                resultMsg = "Access revoked & unpaired"
                stopSelf()
            }
        }

        try {
            val api = CompanionApiClient.getService(preferences)
            api.acknowledgeCommand(
                authHeader = "Bearer $deviceToken",
                deviceId = deviceId,
                commandId = cmd.id,
                request = CommandAckRequest(result = resultMsg, status = "EXECUTED")
            )
        } catch (_: Exception) {}
    }

    private fun playAlarmSound() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
            ringtone.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ShadowGuard Companion Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification required for persistent companion monitoring service"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        realTimeWsClient?.disconnect()
        realTimeWsClient = null
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
