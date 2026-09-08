package com.devicemonitor.app.util

import android.content.Context

/**
 * ActivityManager.getRunningServices() is deprecated and unreliable on Android 8+
 * (returns only own services), and completely broken on Android 10+ for third-party apps.
 * We track service state via a static in-process flag instead.
 */
object ServiceUtils {
    // Tracks running service classes by their canonical name
    private val runningServices = mutableSetOf<String>()

    fun markServiceRunning(serviceClass: Class<*>) {
        runningServices.add(serviceClass.name)
    }

    fun markServiceStopped(serviceClass: Class<*>) {
        runningServices.remove(serviceClass.name)
    }

    @Suppress("UNUSED_PARAMETER")
    fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        return runningServices.contains(serviceClass.name)
    }
}
