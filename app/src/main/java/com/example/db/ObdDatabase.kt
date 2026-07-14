package com.example.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ObdLog::class], version = 1, exportSchema = false)
abstract class ObdDatabase : RoomDatabase() {
    abstract fun obdLogDao(): ObdLogDao
}
