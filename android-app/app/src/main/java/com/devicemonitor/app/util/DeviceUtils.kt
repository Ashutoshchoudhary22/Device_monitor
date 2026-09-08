package com.devicemonitor.app.util

import android.os.Build

object DeviceUtils {
    fun isMiui(): Boolean {
        return Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
            Build.MANUFACTURER.equals("Redmi", ignoreCase = true) ||
            Build.MANUFACTURER.equals("POCO", ignoreCase = true) ||
            !getSystemProperty("ro.miui.ui.version.name").isNullOrBlank()
    }

    fun isVivo(): Boolean {
        return Build.MANUFACTURER.equals("vivo", ignoreCase = true) ||
            !getSystemProperty("ro.vivo.os.version").isNullOrBlank()
    }

    fun isAggressiveOem(): Boolean = isMiui() || isVivo() ||
        Build.MANUFACTURER.equals("Huawei", ignoreCase = true) ||
        Build.MANUFACTURER.equals("Honor", ignoreCase = true) ||
        Build.MANUFACTURER.equals("OnePlus", ignoreCase = true) ||
        Build.MANUFACTURER.equals("Oppo", ignoreCase = true) ||
        Build.MANUFACTURER.equals("Realme", ignoreCase = true) ||
        Build.MANUFACTURER.equals("Samsung", ignoreCase = true)

    private fun getSystemProperty(key: String): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            method.invoke(null, key) as? String
        } catch (_: Exception) {
            null
        }
    }
}
