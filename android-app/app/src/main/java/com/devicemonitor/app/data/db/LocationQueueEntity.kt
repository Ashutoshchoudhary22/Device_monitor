package com.devicemonitor.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_queue")
data class LocationQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    val altitude: Double?,
    val speed: Float?,
    val timestamp: String,
    val retryCount: Int = 0
)
