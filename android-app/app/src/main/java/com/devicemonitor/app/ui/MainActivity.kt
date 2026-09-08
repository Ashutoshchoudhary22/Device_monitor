package com.devicemonitor.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.devicemonitor.app.data.repository.DeviceRepository
import com.devicemonitor.app.databinding.ActivityMainBinding
import com.devicemonitor.app.service.LocationForegroundService
import com.devicemonitor.app.util.BatteryMonitor
import com.devicemonitor.app.util.NetworkMonitor
import com.devicemonitor.app.util.DeviceUtils
import com.devicemonitor.app.util.PermissionHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: DeviceRepository
    private val batteryMonitor by lazy { BatteryMonitor(this) }
    private val networkMonitor by lazy { NetworkMonitor(this) }

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

        // Always ensure tracking is running when MainActivity opens.
        ensureTrackingRunning()
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

        // Toggle button: only shown/used when tracking is explicitly stopped by the user.
        // Normal flow = tracking is always on after login.
        binding.toggleTrackingButton.setOnClickListener {
            if (LocationForegroundService.isTrackingEnabled(this)) {
                // User wants to stop — confirm first
                AlertDialog.Builder(this)
                    .setTitle("Stop Tracking")
                    .setMessage("Are you sure you want to stop location tracking?")
                    .setPositiveButton("Stop") { _, _ ->
                        LocationForegroundService.stop(this)
                        updateStatusUi()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                // Tracking was manually stopped — restart it
                requestPermissionsAndStart()
            }
        }

        binding.logoutButton.setOnClickListener {
            LocationForegroundService.stop(this)
            repository.disconnectSocket()
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

    private fun ensureTrackingRunning() {
        if (!PermissionHelper.hasNotificationPermission(this)) {
            PermissionHelper.requestNotificationPermission(this)
            return
        }
        if (!PermissionHelper.hasLocationPermissions(this)) {
            PermissionHelper.requestLocationPermissions(this)
            return
        }
        if (!PermissionHelper.hasBackgroundLocation(this)) {
            PermissionHelper.requestBackgroundLocation(this)
            return
        }

        // Battery optimization — ask every launch until granted.
        // This is the #1 reason background services are killed on vivo/OEM devices.
        if (!PermissionHelper.isBatteryOptimizationIgnored(this)) {
            showBatteryOptimizationDialog()
            return
        }

        // Vivo/aggressive OEM: show setup guide once
        if (DeviceUtils.isAggressiveOem() && !hasShownOemTip()) {
            markOemTipShown()
            showOemSetupDialog()
        }

        LocationForegroundService.start(this)
    }

    private fun showBatteryOptimizationDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Battery Restriction")
            .setMessage(
                "Device Monitor needs to be excluded from battery optimization to keep " +
                "tracking running when the app is closed.\n\n" +
                "Tap 'Allow' and select 'Don't optimize' or 'No restrictions'."
            )
            .setPositiveButton("Allow") { _, _ ->
                PermissionHelper.requestIgnoreBatteryOptimizations(this)
            }
            .setNegativeButton("Skip") { _, _ ->
                LocationForegroundService.start(this)
            }
            .setCancelable(false)
            .show()
    }

    private fun showOemSetupDialog() {
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        val steps = when {
            manufacturer.contains("vivo") ->
                "1. Go to Settings → Apps → Device Monitor\n" +
                "2. Tap Battery → select 'No restrictions'\n" +
                "3. Also enable: Settings → Battery → Background power consumption → Device Monitor → Allow"
            DeviceUtils.isMiui() ->
                "1. Settings → Apps → Device Monitor → Battery Saver → No restrictions\n" +
                "2. Settings → Apps → Device Monitor → Autostart → Enable"
            else ->
                "Go to Settings → Apps → Device Monitor → Battery and disable restrictions."
        }

        AlertDialog.Builder(this)
            .setTitle("Allow Background Access")
            .setMessage("Your device restricts background apps. To keep tracking running:\n\n$steps")
            .setPositiveButton("Open App Settings") { _, _ ->
                PermissionHelper.openAppSettings(this)
            }
            .setNegativeButton("Skip", null)
            .show()
    }

    private fun requestPermissionsAndStart() {
        if (!PermissionHelper.hasNotificationPermission(this)) {
            AlertDialog.Builder(this)
                .setTitle("Notification Permission")
                .setMessage(getString(com.devicemonitor.app.R.string.notification_permission_rationale))
                .setPositiveButton("Allow") { _, _ -> PermissionHelper.requestNotificationPermission(this) }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        if (!PermissionHelper.hasLocationPermissions(this)) {
            AlertDialog.Builder(this)
                .setTitle("Location Permission")
                .setMessage(getString(com.devicemonitor.app.R.string.permission_location_rationale))
                .setPositiveButton("Grant") { _, _ -> PermissionHelper.requestLocationPermissions(this) }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        if (!PermissionHelper.hasBackgroundLocation(this)) {
            AlertDialog.Builder(this)
                .setTitle("Background Location")
                .setMessage(getString(com.devicemonitor.app.R.string.permission_background_rationale))
                .setPositiveButton("Grant") { _, _ -> PermissionHelper.requestBackgroundLocation(this) }
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
        // After any permission result, retry starting tracking
        ensureTrackingRunning()
        updateStatusUi()
    }

    override fun onResume() {
        super.onResume()
        // Re-check every time app comes to foreground — covers the case where
        // user enabled battery optimization exemption or autostart in settings and came back.
        ensureTrackingRunning()
        updateStatusUi()
    }

    private fun showPermissionDenied() {
        binding.permissionText.text = getString(com.devicemonitor.app.R.string.permission_denied)
        binding.permissionText.visibility = View.VISIBLE
        binding.openSettingsButton.visibility = View.VISIBLE
    }

    private fun updateStatusUi() {
        val trackingEnabled = LocationForegroundService.isTrackingEnabled(this)
        val serviceRunning = LocationForegroundService.isRunning(this)

        binding.trackingStatusText.text = when {
            serviceRunning -> getString(com.devicemonitor.app.R.string.tracking_active)
            trackingEnabled -> "Tracking enabled — restarting..."
            else -> getString(com.devicemonitor.app.R.string.tracking_stopped)
        }

        // Show Stop button only when service is actively running
        binding.toggleTrackingButton.text = if (trackingEnabled) {
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

        // Show permission warning if missing
        if (!PermissionHelper.hasLocationPermissions(this) ||
            !PermissionHelper.hasBackgroundLocation(this)
        ) {
            showPermissionDenied()
        } else {
            binding.permissionText.visibility = View.GONE
            binding.openSettingsButton.visibility = View.GONE
        }
    }

    // --- OEM tip: show setup dialog only once per install ---
    private fun hasShownOemTip(): Boolean =
        getSharedPreferences("app_prefs", MODE_PRIVATE).getBoolean("oem_tip_shown", false)

    private fun markOemTipShown() =
        getSharedPreferences("app_prefs", MODE_PRIVATE).edit().putBoolean("oem_tip_shown", true).apply()

    private fun hasShownMiuiTip(): Boolean = hasShownOemTip()
    private fun markMiuiTipShown() = markOemTipShown()
}
