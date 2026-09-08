package com.devicemonitor.app.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.ComponentName
import com.devicemonitor.app.data.prefs.TokenManager
import com.devicemonitor.app.util.PermissionHelper

/**
 * JobScheduler-based watchdog.
 *
 * Scheduled from LocationForegroundService every time the service starts.
 * setPersisted(true) means it survives reboots without needing BootReceiver.
 * After each execution it reschedules itself so the watchdog keeps running
 * as long as tracking is enabled — even if the app process never restarts.
 */
class RestartJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        val ctx = applicationContext
        val tokenManager = TokenManager(ctx)

        if (tokenManager.isTrackingEnabled() &&
            tokenManager.getToken() != null &&
            PermissionHelper.hasLocationPermissions(ctx) &&
            PermissionHelper.hasBackgroundLocation(ctx)
        ) {
            if (!LocationForegroundService.isRunning(ctx)) {
                LocationForegroundService.start(ctx)
            }
            // Reschedule next watchdog job
            scheduleNext()
        }

        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = true  // retry if stopped

    private fun scheduleNext() {
        try {
            val js = getSystemService(JOB_SCHEDULER_SERVICE) as android.app.job.JobScheduler
            js.schedule(
                JobInfo.Builder(
                    JOB_ID,
                    ComponentName(applicationContext, RestartJobService::class.java)
                )
                    .setMinimumLatency(INTERVAL_MS)
                    .setOverrideDeadline(INTERVAL_MS * 3)
                    .setPersisted(true)
                    .build()
            )
        } catch (_: Exception) {}
    }

    companion object {
        const val JOB_ID = 42001
        const val INTERVAL_MS = 60_000L
    }
}
