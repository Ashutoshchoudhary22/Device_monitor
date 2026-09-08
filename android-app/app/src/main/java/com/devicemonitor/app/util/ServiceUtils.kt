package com.devicemonitor.app.util

import android.content.Context

/**
 * Tracks service running state in SharedPreferences so it survives process kills.
 *
 * In-memory Sets reset when the process is killed by the OS (e.g. vivo swipe-to-kill).
 * SharedPreferences persist across process restarts, so isRunning() returns the correct
 * value even after the app process is recreated by BootReceiver or JobScheduler.
 *
 * The flag is set to false in onDestroy/stopTracking and set to true in onCreate.
 * On a cold start the flag is false (correct), and after a kill-restart the flag
 * accurately reflects whether startForegroundService was called successfully.
 */
object ServiceUtils {

    private const val PREFS_NAME = "service_state"
    private const val KEY_PREFIX = "running_"

    fun markServiceRunning(context: Context, serviceClass: Class<*>) {
        prefs(context).edit().putBoolean(key(serviceClass), true).apply()
    }

    fun markServiceStopped(context: Context, serviceClass: Class<*>) {
        prefs(context).edit().putBoolean(key(serviceClass), false).apply()
    }

    @Suppress("UNUSED_PARAMETER")
    fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        return prefs(context).getBoolean(key(serviceClass), false)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(serviceClass: Class<*>) = KEY_PREFIX + serviceClass.name
}
