package com.shadowguard.companion.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shadowguard.companion.data.CompanionPreferences
import com.shadowguard.companion.service.MonitoringForegroundService
import com.shadowguard.companion.worker.StatusSyncWorker

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefs = CompanionPreferences(context)
            if (prefs.isPaired() && !prefs.isMonitoringPaused) {
                MonitoringForegroundService.startService(context)
                StatusSyncWorker.schedulePeriodicSync(context)
            }
        }
    }
}
