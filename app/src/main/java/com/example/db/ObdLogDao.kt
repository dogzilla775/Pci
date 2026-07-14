package com.example.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ObdLogDao {
    @Query("SELECT * FROM obd_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentLogs(): Flow<List<ObdLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ObdLog)
    
    @Query("DELETE FROM obd_logs")
    suspend fun clearLogs()
}
