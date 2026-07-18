package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bus_diagnostic_logs")
data class BusDiagnosticLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val busType: String,       // "CAN" or "PCI"
    val messageId: String,     // Hex ID (e.g., "0x18DAF110" or "0x68")
    val sourceAddress: String, // Hex Address
    val targetAddress: String, // Hex Address
    val payloadHex: String,    // Data bytes representation
    val isError: Boolean,      // Flag indicating error frame
    val description: String,   // Explanation of packet/error
    val severity: String,      // "INFO", "WARNING", "CRITICAL"
    val troubleshooting: String // Autel-style repair guidance
)
