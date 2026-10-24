package com.shadowguard.controller.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("shadowguard_controller_prefs", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString("server_url", "http://10.0.2.2:3000/") ?: "http://10.0.2.2:3000/"
        set(value) {
            var url = value.trim()
            if (!url.endsWith("/")) {
                url += "/"
            }
            prefs.edit().putString("server_url", url).apply()
        }

    var authToken: String?
        get() = prefs.getString("auth_token", null)
        set(value) = prefs.edit().putString("auth_token", value).apply()

    var username: String?
        get() = prefs.getString("username", null)
        set(value) = prefs.edit().putString("username", value).apply()

    fun isLoggedIn(): Boolean = !authToken.isNullOrEmpty()

    fun logout() {
        prefs.edit().remove("auth_token").remove("username").apply()
    }
}
