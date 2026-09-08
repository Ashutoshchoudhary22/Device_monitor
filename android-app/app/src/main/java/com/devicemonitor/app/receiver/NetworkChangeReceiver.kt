package com.devicemonitor.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.devicemonitor.app.data.prefs.TokenManager
import com.devicemonitor.app.service.LocationForegroundService
import com.devicemonitor.app.util.PermissionHelper

/**
 * Restarts LocationForegroundService when network becomes available after the
 * service was killed by the OS (e.g. vivo/MIUI aggressive kill from recents).
 *
 * This is a safety net on top of the AlarmManager restart already in the service.
 * Two independent restart mechanisms = much harder for the OS to suppress both.
 */
class NetworkChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (!isNetworkAvailable(context)) return

        val tokenManager = TokenManager(context)
        if (!tokenManager.isTrackingEnabled() || tokenManager.getToken() == null) return
        if (!PermissionHelper.hasLocationPermissions(context)) return
        if (!PermissionHelper.hasBackgroundLocation(context)) return

        // Service already running — nothing to do
        if (LocationForegroundService.isRunning(context)) return

        LocationForegroundService.start(context)
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
