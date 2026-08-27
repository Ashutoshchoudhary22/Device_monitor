package com.devicemonitor.app.data.repository

import android.content.Context
import android.os.Build
import com.devicemonitor.app.BuildConfig
import com.devicemonitor.app.data.api.DeviceRegisterRequest
import com.devicemonitor.app.data.api.LocationRequest
import com.devicemonitor.app.data.api.RetrofitClient
import com.devicemonitor.app.data.db.AppDatabase
import com.devicemonitor.app.data.db.LocationQueueEntity
import com.devicemonitor.app.data.prefs.TokenManager
import com.devicemonitor.app.data.socket.SocketManager
import com.devicemonitor.app.util.BatteryMonitor
import com.devicemonitor.app.util.DeviceIdManager
import com.devicemonitor.app.util.NetworkMonitor
import org.json.JSONObject
import java.time.Instant

class DeviceRepository(private val context: Context) {
    private val tokenManager = TokenManager(context)
    private val socketManager = SocketManager(tokenManager)
    private val api = RetrofitClient.create(tokenManager)
    private val deviceIdManager = DeviceIdManager(context)
    private val db = AppDatabase.getInstance(context)
    private val queueDao = db.locationQueueDao()
    private val batteryMonitor = BatteryMonitor(context)
    private val networkMonitor = NetworkMonitor(context)

    fun getToken(): String? = tokenManager.getToken()
    fun clearAuth() {
        socketManager.disconnect()
        tokenManager.clearToken()
    }

    fun getDeviceId(): String = deviceIdManager.getDeviceId()
    fun getUpdateInterval(): Int = tokenManager.getUpdateInterval()
    fun setUpdateInterval(seconds: Int) = tokenManager.saveUpdateInterval(seconds)

    fun connectSocket(onReconnect: (() -> Unit)? = null) {
        if (tokenManager.getToken() == null) return
        socketManager.setOnReconnectListener(onReconnect)
        socketManager.connect()
    }

    fun disconnectSocket() {
        socketManager.disconnect()
    }

    fun isSocketConnected(): Boolean = socketManager.isConnected()

    suspend fun login(email: String, password: String): Result<LoginResult> {
        return try {
            val payload = JSONObject()
                .put("email", email)
                .put("password", password)

            val response = socketManager.emitAck("auth:login", payload, guest = true)
            if (!response.optBoolean("ok", false)) {
                return Result.failure(Exception(response.optString("error", "Login failed")))
            }

            val token = response.getString("token")
            tokenManager.saveToken(token)
            socketManager.disconnect()
            socketManager.connect()

            val userObj = response.getJSONObject("user")
            Result.success(
                LoginResult(
                    token = token,
                    userId = userObj.getString("id"),
                    email = userObj.getString("email"),
                    name = userObj.getString("name")
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerDevice(): Result<String> {
        val request = DeviceRegisterRequest(
            deviceId = getDeviceId(),
            deviceName = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            appVersion = BuildConfig.VERSION_NAME,
            updateIntervalSeconds = getUpdateInterval()
        )

        return try {
            val restResponse = api.registerDevice(request)
            if (restResponse.isSuccessful) {
                return Result.success(restResponse.body()?.message ?: "Device registered")
            }

            val payload = JSONObject()
                .put("deviceId", request.deviceId)
                .put("deviceName", request.deviceName)
                .put("androidVersion", request.androidVersion)
                .put("manufacturer", request.manufacturer)
                .put("model", request.model)
                .put("appVersion", request.appVersion)
                .put("updateIntervalSeconds", request.updateIntervalSeconds)

            val response = socketManager.emitAck("device:register", payload)
            if (!response.optBoolean("ok", false)) {
                return Result.failure(Exception(response.optString("error", "Registration failed")))
            }

            Result.success(response.optString("message", "Device registered"))
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
        while (attempt < maxRetries) {
            if (sendLocationViaRest(request)) return true
            if (sendLocationViaSocket(request)) return true
            attempt++
            kotlinx.coroutines.delay(1000L * attempt)
        }
        queueLocation(request)
        return false
    }

    private suspend fun sendLocationViaSocket(request: LocationRequest): Boolean {
        return try {
            val payload = JSONObject()
                .put("deviceId", getDeviceId())
                .put("latitude", request.latitude)
                .put("longitude", request.longitude)
                .put("timestamp", request.timestamp)

            request.accuracy?.let { payload.put("accuracy", it) }
            request.altitude?.let { payload.put("altitude", it) }
            request.speed?.let { payload.put("speed", it) }

            val response = socketManager.emitAck("device:location", payload)
            if (response.optBoolean("ok", false)) return true

            val error = response.optString("error", "")
            if (error.contains("401") || error.contains("Authentication")) {
                tokenManager.clearToken()
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun sendLocationViaRest(request: LocationRequest): Boolean {
        return try {
            val response = api.postLocation(getDeviceId(), request)
            if (response.isSuccessful) return true
            if (response.code() == 401) {
                tokenManager.clearToken()
            }
            false
        } catch (_: Exception) {
            false
        }
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
            val success = sendLocationWithRetry(request, maxRetries = 1)
            if (success) {
                queueDao.deleteById(item.id)
                synced++
            } else if (item.retryCount >= 5) {
                queueDao.deleteById(item.id)
            } else {
                queueDao.incrementRetry(item.id)
            }
        }
        return synced
    }

    suspend fun sendStatus(isOnline: Boolean): Boolean {
        val info = networkMonitor.getNetworkInfo()
        val payload = JSONObject()
            .put("deviceId", getDeviceId())
            .put("isOnline", isOnline)
            .put("networkType", info.networkType)
            .put("wifiAvailable", info.wifiAvailable)
            .put("mobileDataAvailable", info.mobileDataAvailable)

        if (sendStatusViaRest(isOnline, info)) return true
        return sendStatusViaSocket(payload)
    }

    private suspend fun sendStatusViaSocket(payload: JSONObject): Boolean {
        return try {
            val response = socketManager.emitAck("device:status", payload)
            response.optBoolean("ok", false)
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun sendStatusViaRest(
        isOnline: Boolean,
        info: com.devicemonitor.app.util.NetworkMonitor.NetworkInfo
    ): Boolean {
        return try {
            val response = api.postStatus(
                getDeviceId(),
                com.devicemonitor.app.data.api.StatusRequest(
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
        val payload = JSONObject()
            .put("deviceId", getDeviceId())
            .put("batteryPercentage", info.percentage)
            .put("isCharging", info.isCharging)

        info.temperature?.let { payload.put("batteryTemperature", it) }
        info.health?.let { payload.put("batteryHealth", it) }

        if (sendBatteryViaRest(info)) return true
        return sendBatteryViaSocket(payload)
    }

    private suspend fun sendBatteryViaSocket(payload: JSONObject): Boolean {
        return try {
            val response = socketManager.emitAck("device:battery", payload)
            response.optBoolean("ok", false)
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun sendBatteryViaRest(info: com.devicemonitor.app.util.BatteryMonitor.BatteryInfo): Boolean {
        return try {
            val response = api.postBattery(
                getDeviceId(),
                com.devicemonitor.app.data.api.BatteryRequest(
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

    data class LoginResult(
        val token: String,
        val userId: String,
        val email: String,
        val name: String
    )
}
