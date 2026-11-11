package com.adblocker.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QueryLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: QueryLogEntity)

    @Query("SELECT * FROM query_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 200): Flow<List<QueryLogEntity>>

    @Query("DELETE FROM query_logs")
    suspend fun clearAll()

    @Query("DELETE FROM query_logs WHERE id NOT IN (SELECT id FROM query_logs ORDER BY timestamp DESC LIMIT 1000)")
    suspend fun trimOldLogs()
}
