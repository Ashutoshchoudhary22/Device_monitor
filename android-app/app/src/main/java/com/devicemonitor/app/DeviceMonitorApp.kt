package com.devicemonitor.app

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import com.devicemonitor.app.data.prefs.TokenManager
import com.devicemonitor.app.service.LocationForegroundService
import com.devicemonitor.app.util.PermissionHelper

class DeviceMonitorApp : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        WorkManager.initialize(this, workManagerConfiguration)
        resumeTrackingIfNeeded()
    }

    private fun resumeTrackingIfNeeded() {
        val tokenManager = TokenManager(this)
        if (!tokenManager.isTrackingEnabled() || tokenManager.getToken() == null) return
        if (!PermissionHelper.hasLocationPermissions(this)) return
        if (!PermissionHelper.hasBackgroundLocation(this)) return
        if (!PermissionHelper.hasNotificationPermission(this)) return

        if (LocationForegroundService.isRunning(this)) return
        LocationForegroundService.start(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
