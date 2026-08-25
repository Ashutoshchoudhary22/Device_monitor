package com.devicemonitor.app.data.api

import com.google.gson.annotations.SerializedName

data class LoginRequest(val email: String, val password: String)

data class AuthResponse(
    val token: String,
    val user: UserDto
)

data class UserDto(
    val id: String,
    val email: String,
    val name: String
)

data class DeviceRegisterRequest(
    val deviceId: String,
    val deviceName: String,
    val androidVersion: String,
    val manufacturer: String,
    val model: String,
    val appVersion: String,
    val updateIntervalSeconds: Int
)

data class DeviceResponse(val device: DeviceDto, val message: String)

data class DeviceDto(
    val deviceId: String,
    val deviceName: String
)

data class LocationRequest(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    val altitude: Double?,
    val speed: Float?,
    val timestamp: String
)

data class StatusRequest(
    val isOnline: Boolean,
    val networkType: String,
    val wifiAvailable: Boolean,
    val mobileDataAvailable: Boolean
)

data class BatteryRequest(
    val batteryPercentage: Int,
    val isCharging: Boolean,
    val batteryTemperature: Float?,
    val batteryHealth: String?
)

data class ApiMessage(val message: String)
