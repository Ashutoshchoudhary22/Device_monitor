package com.devicemonitor.app.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.devicemonitor.app.data.prefs.TokenManager
import com.devicemonitor.app.service.LocationForegroundService
import com.devicemonitor.app.util.PermissionHelper

class TrackingRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        val tokenManager = TokenManager(appContext)

        if (!tokenManager.isTrackingEnabled() || tokenManager.getToken() == null) return
        if (!PermissionHelper.hasLocationPermissions(appContext)) return
        if (!PermissionHelper.hasBackgroundLocation(appContext)) return

        // Restart service if not running
        if (!LocationForegroundService.isRunning(appContext)) {
            LocationForegroundService.start(appContext)
        }

        // Chain the next exact alarm manually (for devices that support exact alarms).
        // This keeps the watchdog alive even after a SIGKILL because the alarm was
        // already registered in the system alarm table before the process died.
        rescheduleIfExact(appContext)
    }

    private fun rescheduleIfExact(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) return

        val tokenManager = TokenManager(context)
        if (!tokenManager.isTrackingEnabled()) return

        val pi = PendingIntent.getBroadcast(
            context,
            WATCHDOG_REQUEST_CODE,
            Intent(context, TrackingRestartReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        try {
            am.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MS,
                pi
            )
        } catch (_: SecurityException) {}
    }

    companion object {
        private const val WATCHDOG_REQUEST_CODE = 3001
        private const val WATCHDOG_INTERVAL_MS = 60_000L
    }
}
