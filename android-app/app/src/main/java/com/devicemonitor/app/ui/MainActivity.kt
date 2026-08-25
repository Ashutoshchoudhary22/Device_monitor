package com.devicemonitor.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.devicemonitor.app.data.repository.DeviceRepository
import com.devicemonitor.app.databinding.ActivityMainBinding
import com.devicemonitor.app.service.LocationForegroundService
import com.devicemonitor.app.util.BatteryMonitor
import com.devicemonitor.app.util.NetworkMonitor
import com.devicemonitor.app.util.PermissionHelper
import com.devicemonitor.app.worker.SyncWorker
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: DeviceRepository
    private val batteryMonitor = BatteryMonitor(this)
    private val networkMonitor = NetworkMonitor(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = DeviceRepository(this)
        if (repository.getToken() == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUi()
        registerDevice()
        scheduleSyncWorker()
        updateStatusUi()
    }

    private fun setupUi() {
        binding.deviceIdText.text = "Device ID: ${repository.getDeviceId()}"

        val interval = repository.getUpdateInterval()
        binding.intervalSlider.value = interval.toFloat()
        binding.intervalValueText.text = "$interval seconds"

        binding.intervalSlider.addOnChangeListener { _, value, _ ->
            val seconds = value.toInt()
            binding.intervalValueText.text = "$seconds seconds"
            repository.setUpdateInterval(seconds)
        }

        binding.toggleTrackingButton.setOnClickListener {
            if (LocationForegroundService.isRunning(this)) {
                LocationForegroundService.stop(this)
                updateStatusUi()
            } else {
                requestPermissionsAndStart()
            }
        }

        binding.logoutButton.setOnClickListener {
            LocationForegroundService.stop(this)
            repository.clearAuth()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        binding.openSettingsButton.setOnClickListener {
            PermissionHelper.openAppSettings(this)
        }
    }

    private fun registerDevice() {
        lifecycleScope.launch {
            repository.registerDevice().onFailure { e ->
                Toast.makeText(this@MainActivity, "Registration: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun scheduleSyncWorker() {
        val work = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "sync_locations",
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )
    }

    private fun requestPermissionsAndStart() {
        if (!PermissionHelper.hasNotificationPermission(this)) {
            PermissionHelper.requestNotificationPermission(this)
        }

        if (!PermissionHelper.hasLocationPermissions(this)) {
            AlertDialog.Builder(this)
                .setTitle("Location Permission")
                .setMessage(getString(com.devicemonitor.app.R.string.permission_location_rationale))
                .setPositiveButton("Grant") { _, _ ->
                    PermissionHelper.requestLocationPermissions(this)
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        if (!PermissionHelper.hasBackgroundLocation(this)) {
            AlertDialog.Builder(this)
                .setTitle("Background Location")
                .setMessage(getString(com.devicemonitor.app.R.string.permission_background_rationale))
                .setPositiveButton("Grant") { _, _ ->
                    PermissionHelper.requestBackgroundLocation(this)
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        LocationForegroundService.start(this)
        updateStatusUi()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            PermissionHelper.REQUEST_LOCATION -> {
                if (PermissionHelper.hasLocationPermissions(this)) {
                    if (!PermissionHelper.hasBackgroundLocation(this)) {
                        PermissionHelper.requestBackgroundLocation(this)
                    } else {
                        LocationForegroundService.start(this)
                        updateStatusUi()
                    }
                } else {
                    showPermissionDenied()
                }
            }
            PermissionHelper.REQUEST_BACKGROUND -> {
                if (PermissionHelper.hasBackgroundLocation(this)) {
                    LocationForegroundService.start(this)
                    updateStatusUi()
                } else {
                    showPermissionDenied()
                }
            }
        }
    }

    private fun showPermissionDenied() {
        binding.permissionText.text = getString(com.devicemonitor.app.R.string.permission_denied)
        binding.permissionText.visibility = View.VISIBLE
        binding.openSettingsButton.visibility = View.VISIBLE
    }

    private fun updateStatusUi() {
        val tracking = LocationForegroundService.isRunning(this)
        binding.trackingStatusText.text = if (tracking) {
            getString(com.devicemonitor.app.R.string.tracking_active)
        } else {
            getString(com.devicemonitor.app.R.string.tracking_stopped)
        }
        binding.toggleTrackingButton.text = if (tracking) {
            getString(com.devicemonitor.app.R.string.stop_tracking)
        } else {
            getString(com.devicemonitor.app.R.string.start_tracking)
        }

        val battery = batteryMonitor.getBatteryInfo()
        binding.batteryText.text = "Battery: ${battery.percentage}% " +
            (if (battery.isCharging) "(charging)" else "(not charging)") +
            (battery.temperature?.let { " · ${it}°C" } ?: "")

        val network = networkMonitor.getNetworkInfo()
        binding.networkText.text = "Network: ${network.networkType}" +
            (if (network.wifiAvailable) " · WiFi" else "") +
            (if (network.mobileDataAvailable) " · Mobile" else "")
    }

    override fun onResume() {
        super.onResume()
        updateStatusUi()
    }
}
