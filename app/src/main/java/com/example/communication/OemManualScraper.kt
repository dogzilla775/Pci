package com.example.communication

import kotlinx.coroutines.delay

data class ManualSection(
    val title: String,
    val textContent: String,
    val diagramUrl: String? = null
)

class OemManualScraper {
    suspend fun searchManual(vehicle: String, query: String): List<ManualSection> {
        // Relies on WebSocket stream / hardware backend
        return emptyList()
    }
}
