package com.example.communication

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class ThresholdAlert(
    val component: String,
    val message: String,
    val severity: AlertSeverity
)

enum class AlertSeverity { WARNING, CRITICAL }

class DiagnosticMonitorService(private val busManager: PciBusManager) {
    private val _alerts = MutableSharedFlow<ThresholdAlert>(replay = 50)
    val alerts: SharedFlow<ThresholdAlert> = _alerts.asSharedFlow()
    
    private val scope = CoroutineScope(Dispatchers.Default)
    
    init {
        scope.launch {
            busManager.metrics.collect { metrics ->
                checkThresholds(metrics)
            }
        }
    }
    
    private suspend fun checkThresholds(metrics: ObdMetrics) {
        if (metrics.coolantTemp > 110f) {
            _alerts.emit(ThresholdAlert("Engine Coolant", "High coolant temperature: ${metrics.coolantTemp}°C", AlertSeverity.CRITICAL))
        }
        if (metrics.transTemp > 135f) {
            _alerts.emit(ThresholdAlert("Transmission", "High transmission temperature: ${metrics.transTemp}°C", AlertSeverity.CRITICAL))
        }
        if (metrics.rpm > 6000f) {
            _alerts.emit(ThresholdAlert("Engine RPM", "Engine over-rev detected: ${metrics.rpm} RPM", AlertSeverity.WARNING))
        }
    }
}
