package com.devicemonitor.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.devicemonitor.app.data.prefs.TokenManager
import com.devicemonitor.app.service.LocationForegroundService
import com.devicemonitor.app.util.PermissionHelper

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            return
        }

        val tokenManager = TokenManager(context)
        if (!tokenManager.isTrackingEnabled() || tokenManager.getToken() == null) {
            return
        }

        if (!PermissionHelper.hasLocationPermissions(context) ||
            !PermissionHelper.hasBackgroundLocation(context)
        ) {
            return
        }

        LocationForegroundService.start(context)
    }
}
