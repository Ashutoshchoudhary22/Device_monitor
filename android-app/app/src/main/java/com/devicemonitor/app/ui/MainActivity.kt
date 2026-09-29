package com.devicemonitor.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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

    private enum class PermStep { NOTIFICATION, LOCATION, BACKGROUND, BATTERY, EXACT_ALARM, AUTOSTART }

    // Each step is prompted at most once per flow so a denial can't cause an endless popup loop.
    private val attemptedSteps = mutableSetOf<PermStep>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        runPermissionFlow()
        updateStatusUi()
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        runPermissionFlow()
        updateStatusUi()
    }

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

        startPermissionFlow()
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
                startPermissionFlow()
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
            startPermissionFlow()
        }
    }

    private fun registerDevice() {
        lifecycleScope.launch {
            repository.registerDevice().onFailure { e ->
                Toast.makeText(this@MainActivity, "Registration: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startPermissionFlow() {
        attemptedSteps.clear()
        runPermissionFlow()
    }

    /** Prompts the next missing permission; starts tracking once nothing is left to ask. */
    private fun runPermissionFlow() {
        if (isFinishing || isDestroyed) return
        val step = nextMissingStep()
        if (step == null) {
            if (PermissionHelper.hasLocationPermissions(this)) {
                LocationForegroundService.start(this)
            }
            updateStatusUi()
            return
        }
        attemptedSteps.add(step)
        when (step) {
            PermStep.NOTIFICATION -> requestRuntime(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                "Notification Permission",
                "Notifications are needed to show that tracking is running."
            )

            PermStep.LOCATION -> requestRuntime(
                PermissionHelper.LOCATION_PERMISSIONS,
                "Location Permission",
                "Location access is required to track this device."
            )

            PermStep.BACKGROUND -> showStepDialog(
                title = "Allow Location All the Time",
                message = "On the next screen choose \"Allow all the time\" so location keeps " +
                    "updating even when the app is closed.",
                onContinue = {
                    requestRuntime(
                        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                        "Allow Location All the Time",
                        "Background location is required so tracking continues when the app is closed."
                    )
                }
            )

            PermStep.BATTERY -> {
                // ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is itself a system popup.
                val launched = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    tryLaunchSettings(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:$packageName"))
                    )
                } else false
                if (!launched) runPermissionFlow()
            }

            PermStep.EXACT_ALARM -> showStepDialog(
                title = "Allow Alarms & Reminders",
                message = "Enable \"Alarms & reminders\" so tracking can restart automatically " +
                    "after the app is closed or the phone restarts.",
                onContinue = {
                    val launched = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        tryLaunchSettings(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(Uri.parse("package:$packageName"))
                        )
                    } else false
                    if (!launched) runPermissionFlow()
                }
            )

            PermStep.AUTOSTART -> {
                markOemTipShown()
                showStepDialog(
                    title = "Allow Background / Autostart",
                    message = oemAutostartMessage(),
                    onContinue = {
                        if (!tryLaunchSettings(PermissionHelper.getAutostartIntent(this))) {
                            runPermissionFlow()
                        }
                    }
                )
            }
        }
    }

    private fun nextMissingStep(): PermStep? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionHelper.hasNotificationPermission(this) &&
            PermStep.NOTIFICATION !in attemptedSteps
        ) return PermStep.NOTIFICATION

        if (!PermissionHelper.hasLocationPermissions(this) &&
            PermStep.LOCATION !in attemptedSteps
        ) return PermStep.LOCATION

        // Background location can only be requested after foreground location is granted.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            PermissionHelper.hasLocationPermissions(this) &&
            !PermissionHelper.hasBackgroundLocation(this) &&
            PermStep.BACKGROUND !in attemptedSteps
        ) return PermStep.BACKGROUND

        if (!PermissionHelper.isBatteryOptimizationIgnored(this) &&
            PermStep.BATTERY !in attemptedSteps
        ) return PermStep.BATTERY

        if (!PermissionHelper.canScheduleExactAlarms(this) &&
            PermStep.EXACT_ALARM !in attemptedSteps
        ) return PermStep.EXACT_ALARM

        if (DeviceUtils.isAggressiveOem() && !hasShownOemTip() &&
            PermStep.AUTOSTART !in attemptedSteps
        ) return PermStep.AUTOSTART

        return null
    }

    /**
     * Shows the system permission popup. Once the user has denied twice, Android stops
     * showing the popup and silently returns "denied", so send them to app settings instead.
     */
    private fun requestRuntime(permissions: Array<String>, title: String, message: String) {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val askedBefore = permissions.any { prefs.getBoolean("asked_$it", false) }
        val popupAllowed = permissions.any { shouldShowRequestPermissionRationale(it) }
        if (askedBefore && !popupAllowed) {
            showStepDialog(
                title = title,
                message = "$message\n\nThis permission was denied earlier. On the next screen " +
                    "open Permissions and allow it.",
                onContinue = {
                    if (!tryLaunchSettings(PermissionHelper.appDetailsIntent(this))) runPermissionFlow()
                }
            )
            return
        }
        prefs.edit().apply { permissions.forEach { putBoolean("asked_$it", true) } }.apply()
        permissionLauncher.launch(permissions)
    }

    private fun showStepDialog(title: String, message: String, onContinue: () -> Unit) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Continue") { _, _ -> onContinue() }
            .setNegativeButton("Skip") { _, _ -> runPermissionFlow() }
            .setCancelable(false)
            .show()
    }

    private fun tryLaunchSettings(intent: Intent): Boolean {
        return try {
            settingsLauncher.launch(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun oemAutostartMessage(): String = when {
        DeviceUtils.isVivo() ->
            "Your vivo device stops background apps. On the next screen enable autostart / " +
                "background running for Device Monitor, and set Battery to \"No restrictions\"."
        DeviceUtils.isMiui() ->
            "Your Xiaomi device stops background apps. On the next screen enable Autostart for " +
                "Device Monitor, and set Battery Saver to \"No restrictions\"."
        else ->
            "Your device stops background apps. On the next screen allow Device Monitor to run in " +
                "the background / autostart, and remove any battery restrictions."
    }

    override fun onResume() {
        super.onResume()
        // Keeps tracking alive whenever the app is opened, without re-prompting steps the
        // user already dismissed this session and without undoing an explicit "Stop".
        if (LocationForegroundService.isTrackingEnabled(this)) {
            LocationForegroundService.start(this)
        }
        updateStatusUi()
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

        val missing = missingPermissionLabels()
        if (missing.isNotEmpty()) {
            binding.permissionText.text =
                "Background tracking may stop. Missing:\n• " + missing.joinToString("\n• ")
            binding.permissionText.visibility = View.VISIBLE
            binding.openSettingsButton.text = "Allow Permissions"
            binding.openSettingsButton.visibility = View.VISIBLE
        } else {
            binding.permissionText.visibility = View.GONE
            binding.openSettingsButton.visibility = View.GONE
        }
    }

    private fun missingPermissionLabels(): List<String> = buildList {
        if (!PermissionHelper.hasNotificationPermission(this@MainActivity)) add("Notifications")
        if (!PermissionHelper.hasLocationPermissions(this@MainActivity)) add("Location")
        if (!PermissionHelper.hasBackgroundLocation(this@MainActivity)) add("Location: Allow all the time")
        if (!PermissionHelper.isBatteryOptimizationIgnored(this@MainActivity)) add("Battery: Don't optimize")
        if (!PermissionHelper.canScheduleExactAlarms(this@MainActivity)) add("Alarms & reminders")
    }

    // --- OEM tip: show setup dialog only once per install ---
    private fun hasShownOemTip(): Boolean =
        getSharedPreferences("app_prefs", MODE_PRIVATE).getBoolean("oem_tip_shown", false)

    private fun markOemTipShown() =
        getSharedPreferences("app_prefs", MODE_PRIVATE).edit().putBoolean("oem_tip_shown", true).apply()
}
