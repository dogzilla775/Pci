package com.example.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BusDiagnosticLogDao {
    @Query("SELECT * FROM bus_diagnostic_logs ORDER BY timestamp DESC LIMIT 200")
    fun getAllLogs(): Flow<List<BusDiagnosticLog>>

    @Query("SELECT * FROM bus_diagnostic_logs WHERE busType = :busType ORDER BY timestamp DESC LIMIT 200")
    fun getLogsByBus(busType: String): Flow<List<BusDiagnosticLog>>

    @Query("SELECT * FROM bus_diagnostic_logs WHERE severity = :severity ORDER BY timestamp DESC LIMIT 200")
    fun getLogsBySeverity(severity: String): Flow<List<BusDiagnosticLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: BusDiagnosticLog)

    @Query("DELETE FROM bus_diagnostic_logs")
    suspend fun clearLogs()
}
