package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "obd_logs")
data class ObdLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val rpm: Float,
    val engineLoad: Float,
    val coolantTemp: Float,
    val transTemp: Float,
    val speed: Float
)
