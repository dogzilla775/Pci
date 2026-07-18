package com.example.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ObdLog::class, BusDiagnosticLog::class], version = 2, exportSchema = false)
abstract class ObdDatabase : RoomDatabase() {
    abstract fun obdLogDao(): ObdLogDao
    abstract fun busDiagnosticLogDao(): BusDiagnosticLogDao
}
