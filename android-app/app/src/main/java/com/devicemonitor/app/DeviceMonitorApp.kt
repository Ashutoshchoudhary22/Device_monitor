package com.devicemonitor.app

import android.app.Application
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.devicemonitor.app.data.prefs.TokenManager
import com.devicemonitor.app.service.LocationForegroundService
import com.devicemonitor.app.util.PermissionHelper
import com.devicemonitor.app.worker.SyncWorker
import java.util.concurrent.TimeUnit

class DeviceMonitorApp : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        WorkManager.initialize(this, workManagerConfiguration)
        resumeTrackingIfNeeded()
        scheduleSyncWorker()
    }

    private fun resumeTrackingIfNeeded() {
        val tokenManager = TokenManager(this)
        if (!tokenManager.isTrackingEnabled() || tokenManager.getToken() == null) return
        if (!PermissionHelper.hasLocationPermissions(this)) return
        if (!PermissionHelper.hasBackgroundLocation(this)) return

        // Do NOT check isRunning() here — SharedPreferences flag may be stale if the
        // process was killed. Always attempt startForegroundService; the service itself
        // handles being called while already running (onStartCommand guard).
        LocationForegroundService.start(this)
    }

    private fun scheduleSyncWorker() {
        // 15 minutes is Android's minimum for PeriodicWorkRequest.
        // This acts as the 3rd-layer safety net: even if JobScheduler and AlarmManager
        // are both suppressed, WorkManager will restart the service every 15 minutes.
        val work = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "sync_locations",
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
