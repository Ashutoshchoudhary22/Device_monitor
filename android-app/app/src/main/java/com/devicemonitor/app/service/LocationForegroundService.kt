package com.devicemonitor.app.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.devicemonitor.app.R
import com.devicemonitor.app.data.prefs.TokenManager
import com.devicemonitor.app.data.repository.DeviceRepository
import com.devicemonitor.app.ui.MainActivity
import com.devicemonitor.app.util.PermissionHelper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
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
                stopTracking(userInitiated = true)
                return START_NOT_STICKY
            }
            else -> {
                promoteToForeground()
                startTracking()
            }
        }
        return START_STICKY
    }

    private fun promoteToForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startTracking() {
        tokenManager.setTrackingEnabled(true)
        updateNotification()
        repository.connectSocket {
            serviceScope.launch {
                repository.sendStatus(true)
                repository.sendBattery()
            }
        }
        startLocationUpdates()
        fetchImmediateLocation()
        startPeriodicStatusUpdates()
        serviceScope.launch {
            repository.registerDevice()
            repository.sendStatus(true)
            repository.sendBattery()
            repository.syncQueuedLocations()
        }
    }

    private fun stopTracking(userInitiated: Boolean) {
        if (userInitiated) {
            tokenManager.setTrackingEnabled(false)
            serviceScope.launch { repository.sendStatus(false) }
            repository.disconnectSocket()
        }
        stopLocationUpdates()
        statusJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startLocationUpdates() {
        val intervalSeconds = tokenManager.getUpdateInterval().coerceAtLeast(5)
        val intervalMs = intervalSeconds * 1000L

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setMaxUpdateDelayMillis(intervalMs * 2)
            .setWaitForAccurateLocation(false)
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
        } catch (_: SecurityException) {
            // Keep tracking preference — user can reopen app to grant permissions
            stopTracking(userInitiated = false)
        }
    }

    private fun fetchImmediateLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    handleLocation(location)
                }
            }

            val cancellationToken = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            ).addOnSuccessListener { location ->
                if (location != null) {
                    handleLocation(location)
                }
            }
        } catch (_: SecurityException) {
            // handled in startLocationUpdates
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
            updateNotification(
                "Tracking · ${String.format("%.5f", location.latitude)}, ${String.format("%.5f", location.longitude)}"
            )
        }
    }

    private fun updateNotification(subtitle: String? = null) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(subtitle))
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
                delay(30_000)
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!tokenManager.isTrackingEnabled()) return

        val restartIntent = Intent(applicationContext, LocationForegroundService::class.java)
        val pendingIntent = PendingIntent.getService(
            applicationContext,
            RESTART_REQUEST_CODE,
            restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 1_000,
            pendingIntent
        )
    }

    override fun onDestroy() {
        statusJob?.cancel()
        stopLocationUpdates()
        serviceScope.cancel()

        if (tokenManager.isTrackingEnabled()) {
            scheduleRestart()
        }

        super.onDestroy()
    }

    private fun scheduleRestart() {
        val restartIntent = Intent(applicationContext, LocationForegroundService::class.java)
        val pendingIntent = PendingIntent.getService(
            applicationContext,
            RESTART_REQUEST_CODE + 1,
            restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 2_000,
            pendingIntent
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(CHANNEL_ID_LEGACY)

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.location_notification_title),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.location_notification_text)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(subtitle: String? = null): Notification {
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
            .setContentText(subtitle ?: getString(R.string.location_notification_text))
            .setSmallIcon(R.drawable.ic_location)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.stop_tracking), stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "location_tracking_v2"
        private const val CHANNEL_ID_LEGACY = "location_tracking"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.devicemonitor.app.ACTION_STOP_TRACKING"
        private const val RESTART_REQUEST_CODE = 2001

        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !PermissionHelper.hasNotificationPermission(context)
            ) {
                return
            }
            val intent = Intent(context, LocationForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun isTrackingEnabled(context: Context): Boolean {
            return TokenManager(context).isTrackingEnabled()
        }

        fun isRunning(context: Context): Boolean {
            return com.devicemonitor.app.util.ServiceUtils.isServiceRunning(
                context,
                LocationForegroundService::class.java
            )
        }
    }
}
