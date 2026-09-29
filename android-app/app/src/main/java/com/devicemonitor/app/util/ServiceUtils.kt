package com.devicemonitor.app.util

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks service running state in memory only.
 *
 * Must NOT be persisted: when the OS / OEM kills the process (swipe from recents,
 * low memory, vivo/MIUI killers) onDestroy never runs, so a persisted "running=true"
 * flag would stay stale forever and every watchdog would skip the restart.
 * An in-memory flag resets automatically with the process, which is exactly right
 * as long as the service lives in the same process as the watchdogs.
 */
object ServiceUtils {

    private val running = ConcurrentHashMap.newKeySet<String>()

    @Suppress("UNUSED_PARAMETER")
    fun markServiceRunning(context: Context, serviceClass: Class<*>) {
        running.add(serviceClass.name)
    }

    @Suppress("UNUSED_PARAMETER")
    fun markServiceStopped(context: Context, serviceClass: Class<*>) {
        running.remove(serviceClass.name)
    }

    @Suppress("UNUSED_PARAMETER")
    fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        return running.contains(serviceClass.name)
    }
}
