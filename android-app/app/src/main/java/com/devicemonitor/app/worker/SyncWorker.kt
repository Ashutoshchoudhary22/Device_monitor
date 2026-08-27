package com.devicemonitor.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.devicemonitor.app.data.prefs.TokenManager
import com.devicemonitor.app.data.repository.DeviceRepository
import com.devicemonitor.app.service.LocationForegroundService
import com.devicemonitor.app.util.PermissionHelper

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = DeviceRepository(applicationContext)
        val tokenManager = TokenManager(applicationContext)
        if (repository.getToken() == null) return Result.failure()

        if (tokenManager.isTrackingEnabled() &&
            PermissionHelper.hasLocationPermissions(applicationContext) &&
            PermissionHelper.hasBackgroundLocation(applicationContext) &&
            !LocationForegroundService.isRunning(applicationContext)
        ) {
            LocationForegroundService.start(applicationContext)
        }

        val synced = repository.syncQueuedLocations()
        repository.sendBattery()
        return if (synced >= 0) Result.success() else Result.retry()
    }
}
