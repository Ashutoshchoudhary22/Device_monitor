package com.devicemonitor.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    fun saveUpdateInterval(seconds: Int) {
        prefs.edit().putInt(KEY_INTERVAL, seconds).apply()
    }

    fun getUpdateInterval(): Int = prefs.getInt(KEY_INTERVAL, 15)

    fun isTrackingEnabled(): Boolean = prefs.getBoolean(KEY_TRACKING, false)

    fun setTrackingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TRACKING, enabled).apply()
    }

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_INTERVAL = "update_interval"
        private const val KEY_TRACKING = "tracking_enabled"
    }
}
