package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.AppViewModel
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.patrykandpatrick.vico.compose.axis.axisGuidelineComponent
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.chart.line.LineChart
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.entry.entryModelOf
import kotlinx.coroutines.delay
import kotlin.random.Random

data class BusAnomaly(val id: Int, val time: Float, val highV: Float, val lowV: Float, val message: String)

@Composable
fun ScopeScreen(viewModel: AppViewModel) {
    var canHigh by remember { mutableStateOf(listOf<FloatEntry>()) }
    var canLow by remember { mutableStateOf(listOf<FloatEntry>()) }
    var anomalies by remember { mutableStateOf(listOf<BusAnomaly>()) }
    var isPaused by remember { mutableStateOf(false) }
    var highlightedAnomaly by remember { mutableStateOf<BusAnomaly?>(null) }
    var showGrid by remember { mutableStateOf(true) }
    
    val currentVehicle by viewModel.selectedVehicle.collectAsState()
    
    // Simulated data generation removed per security policy
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text("CAN Bus Oscilloscope", style = MaterialTheme.typography.titleLarge)
                Text(currentVehicle, style = MaterialTheme.typography.labelMedium)
                Text("Live Waveform & Anomaly Sync", style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = { 
                    isPaused = !isPaused
                    if (!isPaused) highlightedAnomaly = null 
                }) {
                    Text(if (isPaused) "Resume" else "Pause")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Grid Overlay", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(checked = showGrid, onCheckedChange = { showGrid = it })
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (canHigh.isNotEmpty() && canLow.isNotEmpty()) {
                    val chartEntryModel = entryModelOf(canHigh, canLow)
                    val guideline = if (showGrid) axisGuidelineComponent() else null
                    Chart(
                        chart = lineChart(),
                        model = chartEntryModel,
                        startAxis = rememberStartAxis(title = "Voltage (V)", guideline = guideline),
                        bottomAxis = rememberBottomAxis(title = "Time (ms)", guideline = guideline),
                        modifier = Modifier.fillMaxSize().padding(16.dp)
                    )
                } else {
                    Text("Waiting for signals...", modifier = Modifier.padding(16.dp))
                }
                
                // Highlight Overlay
                highlightedAnomaly?.let { anomaly ->
                    Surface(
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = "Inspecting: ${anomaly.message}\nCAN-H: ${"%.2f".format(anomaly.highV)}V | CAN-L: ${"%.2f".format(anomaly.lowV)}V\nAt time index: ${anomaly.time}",
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Diagnostic Log Viewer", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(anomalies) { anomaly ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { 
                            isPaused = true
                            highlightedAnomaly = anomaly
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (highlightedAnomaly?.id == anomaly.id) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(anomaly.message, style = MaterialTheme.typography.bodyMedium, color = if (highlightedAnomaly?.id == anomaly.id) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Time: ${anomaly.time} | Tap to inspect on graph", style = MaterialTheme.typography.labelSmall, color = if (highlightedAnomaly?.id == anomaly.id) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
