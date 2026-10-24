package com.shadowguard.companion.data

import android.content.Context
import android.content.SharedPreferences

class CompanionPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("shadowguard_companion_prefs", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString("server_url", "http://10.0.2.2:3000/") ?: "http://10.0.2.2:3000/"
        set(value) {
            var url = value.trim()
            if (!url.endsWith("/")) {
                url += "/"
            }
            prefs.edit().putString("server_url", url).apply()
        }

    var deviceId: String?
        get() = prefs.getString("device_id", null)
        set(value) = prefs.edit().putString("device_id", value).apply()

    var deviceToken: String?
        get() = prefs.getString("device_token", null)
        set(value) = prefs.edit().putString("device_token", value).apply()

    var controllerId: String?
        get() = prefs.getString("controller_id", null)
        set(value) = prefs.edit().putString("controller_id", value).apply()

    var isMonitoringPaused: Boolean
        get() = prefs.getBoolean("is_monitoring_paused", false)
        set(value) = prefs.edit().putBoolean("is_monitoring_paused", value).apply()

    fun isPaired(): Boolean = !deviceId.isNullOrEmpty() && !deviceToken.isNullOrEmpty()

    fun unpair() {
        prefs.edit()
            .remove("device_id")
            .remove("device_token")
            .remove("controller_id")
            .remove("is_monitoring_paused")
            .apply()
    }
}
