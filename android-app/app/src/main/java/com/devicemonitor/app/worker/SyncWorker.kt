package com.devicemonitor.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.devicemonitor.app.data.repository.DeviceRepository

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = DeviceRepository(applicationContext)
        if (repository.getToken() == null) return Result.failure()

        val synced = repository.syncQueuedLocations()
        repository.sendBattery()
        return if (synced >= 0) Result.success() else Result.retry()
    }
}
