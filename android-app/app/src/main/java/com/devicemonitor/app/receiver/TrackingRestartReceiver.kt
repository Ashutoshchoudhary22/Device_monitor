package com.devicemonitor.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

        LocationForegroundService.start(appContext)
    }
}
