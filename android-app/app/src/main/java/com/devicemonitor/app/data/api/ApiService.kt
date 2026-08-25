package com.devicemonitor.app.data.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("devices/register")
    suspend fun registerDevice(@Body request: DeviceRegisterRequest): Response<DeviceResponse>

    @POST("devices/{deviceId}/location")
    suspend fun postLocation(
        @Path("deviceId") deviceId: String,
        @Body request: LocationRequest
    ): Response<ApiMessage>

    @POST("devices/{deviceId}/status")
    suspend fun postStatus(
        @Path("deviceId") deviceId: String,
        @Body request: StatusRequest
    ): Response<ApiMessage>

    @POST("devices/{deviceId}/battery")
    suspend fun postBattery(
        @Path("deviceId") deviceId: String,
        @Body request: BatteryRequest
    ): Response<ApiMessage>
}
