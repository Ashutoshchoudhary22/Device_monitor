package com.devicemonitor.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.devicemonitor.app.R
import com.devicemonitor.app.data.prefs.TokenManager
import com.devicemonitor.app.data.repository.DeviceRepository
import com.devicemonitor.app.ui.MainActivity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LocationForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var statusJob: Job? = null
    private lateinit var repository: DeviceRepository
    private lateinit var tokenManager: TokenManager
    private lateinit var fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        repository = DeviceRepository(this)
        tokenManager = TokenManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                return START_NOT_STICKY
            }
            else -> startTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        tokenManager.setTrackingEnabled(true)
        startForeground(NOTIFICATION_ID, buildNotification())
        startLocationUpdates()
        startPeriodicStatusUpdates()
        serviceScope.launch {
            repository.sendStatus(true)
            repository.sendBattery()
            repository.syncQueuedLocations()
        }
    }

    private fun stopTracking() {
        tokenManager.setTrackingEnabled(false)
        stopLocationUpdates()
        statusJob?.cancel()
        serviceScope.launch { repository.sendStatus(false) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startLocationUpdates() {
        val intervalSeconds = tokenManager.getUpdateInterval()
        val intervalMs = (intervalSeconds * 1000).toLong()

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs)
            .setMaxUpdateDelayMillis(intervalMs * 2)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation
                if (location != null) {
                    handleLocation(location)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            stopTracking()
        }
    }

    private fun handleLocation(location: Location) {
        serviceScope.launch {
            repository.sendLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = if (location.hasAccuracy()) location.accuracy else null,
                altitude = if (location.hasAltitude()) location.altitude else null,
                speed = if (location.hasSpeed()) location.speed else null,
                timestamp = repository.createLocationTimestamp()
            )
            repository.sendBattery()
        }
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
    }

    private fun startPeriodicStatusUpdates() {
        statusJob?.cancel()
        statusJob = serviceScope.launch {
            while (isActive) {
                repository.sendStatus(true)
                repository.sendBattery()
                repository.syncQueuedLocations()
                delay(60_000)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.location_notification_title),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.location_notification_text)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LocationForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.location_notification_title))
            .setContentText(getString(R.string.location_notification_text))
            .setSmallIcon(R.drawable.ic_location)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.stop_tracking), stopIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        statusJob?.cancel()
        stopLocationUpdates()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "location_tracking"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.devicemonitor.app.ACTION_STOP_TRACKING"

        fun start(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun isRunning(context: Context): Boolean {
            return TokenManager(context).isTrackingEnabled()
        }
    }
}
