package com.devicemonitor.app.data.repository

import android.content.Context
import android.os.Build
import com.devicemonitor.app.BuildConfig
import com.devicemonitor.app.data.api.*
import com.devicemonitor.app.data.db.AppDatabase
import com.devicemonitor.app.data.db.LocationQueueEntity
import com.devicemonitor.app.data.prefs.TokenManager
import com.devicemonitor.app.util.BatteryMonitor
import com.devicemonitor.app.util.DeviceIdManager
import com.devicemonitor.app.util.NetworkMonitor
import kotlinx.coroutines.delay
import java.time.Instant

class DeviceRepository(private val context: Context) {
    private val tokenManager = TokenManager(context)
    private val api = RetrofitClient.create(tokenManager)
    private val deviceIdManager = DeviceIdManager(context)
    private val db = AppDatabase.getInstance(context)
    private val queueDao = db.locationQueueDao()
    private val batteryMonitor = BatteryMonitor(context)
    private val networkMonitor = NetworkMonitor(context)

    fun getToken(): String? = tokenManager.getToken()
    fun clearAuth() = tokenManager.clearToken()
    fun getDeviceId(): String = deviceIdManager.getDeviceId()
    fun getUpdateInterval(): Int = tokenManager.getUpdateInterval()
    fun setUpdateInterval(seconds: Int) = tokenManager.saveUpdateInterval(seconds)

    suspend fun login(email: String, password: String): Result<AuthResponse> {
        return try {
            val response = api.login(LoginRequest(email, password))
            if (response.isSuccessful && response.body() != null) {
                tokenManager.saveToken(response.body()!!.token)
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Login failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerDevice(): Result<DeviceResponse> {
        return try {
            val request = DeviceRegisterRequest(
                deviceId = getDeviceId(),
                deviceName = Build.MODEL,
                androidVersion = Build.VERSION.RELEASE,
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                appVersion = BuildConfig.VERSION_NAME,
                updateIntervalSeconds = getUpdateInterval()
            )
            val response = api.registerDevice(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Registration failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendLocation(
        latitude: Double,
        longitude: Double,
        accuracy: Float?,
        altitude: Double?,
        speed: Float?,
        timestamp: String
    ): Boolean {
        val request = LocationRequest(latitude, longitude, accuracy, altitude, speed, timestamp)
        return sendLocationWithRetry(request)
    }

    private suspend fun sendLocationWithRetry(request: LocationRequest, maxRetries: Int = 3): Boolean {
        var attempt = 0
        var delayMs = 1000L
        while (attempt < maxRetries) {
            try {
                val response = api.postLocation(getDeviceId(), request)
                if (response.isSuccessful) return true
                if (response.code() == 401) {
                    tokenManager.clearToken()
                    return false
                }
            } catch (_: Exception) {
                // retry
            }
            attempt++
            delay(delayMs)
            delayMs *= 2
        }
        queueLocation(request)
        return false
    }

    private suspend fun queueLocation(request: LocationRequest) {
        queueDao.insert(
            LocationQueueEntity(
                latitude = request.latitude,
                longitude = request.longitude,
                accuracy = request.accuracy,
                altitude = request.altitude,
                speed = request.speed,
                timestamp = request.timestamp
            )
        )
    }

    suspend fun syncQueuedLocations(): Int {
        val pending = queueDao.getPending()
        var synced = 0
        for (item in pending) {
            val request = LocationRequest(
                item.latitude,
                item.longitude,
                item.accuracy,
                item.altitude,
                item.speed,
                item.timestamp
            )
            try {
                val response = api.postLocation(getDeviceId(), request)
                if (response.isSuccessful) {
                    queueDao.deleteById(item.id)
                    synced++
                } else if (item.retryCount >= 5) {
                    queueDao.deleteById(item.id)
                } else {
                    queueDao.incrementRetry(item.id)
                }
            } catch (_: Exception) {
                if (item.retryCount >= 5) {
                    queueDao.deleteById(item.id)
                } else {
                    queueDao.incrementRetry(item.id)
                }
            }
        }
        return synced
    }

    suspend fun sendStatus(isOnline: Boolean): Boolean {
        val info = networkMonitor.getNetworkInfo()
        return try {
            val response = api.postStatus(
                getDeviceId(),
                StatusRequest(
                    isOnline = isOnline,
                    networkType = info.networkType,
                    wifiAvailable = info.wifiAvailable,
                    mobileDataAvailable = info.mobileDataAvailable
                )
            )
            response.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    suspend fun sendBattery(): Boolean {
        val info = batteryMonitor.getBatteryInfo()
        return try {
            val response = api.postBattery(
                getDeviceId(),
                BatteryRequest(
                    batteryPercentage = info.percentage,
                    isCharging = info.isCharging,
                    batteryTemperature = info.temperature,
                    batteryHealth = info.health
                )
            )
            response.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    fun createLocationTimestamp(): String = Instant.now().toString()
}
