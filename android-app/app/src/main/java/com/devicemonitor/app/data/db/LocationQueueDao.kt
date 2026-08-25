package com.devicemonitor.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LocationQueueDao {
    @Insert
    suspend fun insert(entity: LocationQueueEntity): Long

    @Query("SELECT * FROM location_queue ORDER BY id ASC LIMIT :limit")
    suspend fun getPending(limit: Int = 100): List<LocationQueueEntity>

    @Query("DELETE FROM location_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE location_queue SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetry(id: Long)

    @Query("SELECT COUNT(*) FROM location_queue")
    suspend fun count(): Int
}
