package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.communication.ObdMetrics
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDiagramCard(metrics: ObdMetrics, currentVehicle: String) {
    var selectedSystem by remember { mutableStateOf<String?>(null) }
    val isFord = currentVehicle.contains("Ford")

    if (selectedSystem != null) {
        ModalBottomSheet(onDismissRequest = { selectedSystem = null }) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text(selectedSystem ?: "", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                when (selectedSystem) {
                    "Engine" -> {
                        Text("Live Data:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text("RPM: ${metrics.rpm} rev/min")
                        Text("Coolant: ${metrics.coolantTemp} °C")
                        Text("Engine Load: ${metrics.engineLoad} %")
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Diagnostic Tests:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        if (isFord) {
                            Button(onClick = { /* trigger */ }, modifier = Modifier.fillMaxWidth()) { Text("Fuel Injector Disable") }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { /* trigger */ }, modifier = Modifier.fillMaxWidth()) { Text("VVT Solenoid Test") }
                        } else {
                            Button(onClick = { /* trigger */ }, modifier = Modifier.fillMaxWidth()) { Text("Test Cooling Fan Relay") }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { /* trigger */ }, modifier = Modifier.fillMaxWidth()) { Text("Test EGR Solenoid") }
                        }
                    }
                    "Transmission" -> {
                        Text("Live Data:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Trans Temp: ${metrics.transTemp} °C")
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Diagnostic Tests:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Button(onClick = { /* trigger */ }, modifier = Modifier.fillMaxWidth()) { Text("Quick Learn Procedure") }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { /* trigger */ }, modifier = Modifier.fillMaxWidth()) { Text("Reset Shift Adaptives") }
                    }
                    "Exhaust/Body" -> {
                        Text("Live Data:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Speed: ${metrics.speed} km/h")
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Diagnostic Tests:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Button(onClick = { /* trigger */ }, modifier = Modifier.fillMaxWidth()) { Text("EVAP Purge Valve Test") }
                    }
                    "BCM (Body)" -> {
                        Text("Diagnostic Tests:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Button(onClick = { /* trigger */ }, modifier = Modifier.fillMaxWidth()) { Text("Cycle Door Locks") }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { /* trigger */ }, modifier = Modifier.fillMaxWidth()) { Text("Wiper Motor On") }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { /* trigger */ }, modifier = Modifier.fillMaxWidth()) { Text("Headlamp High Beam") }
                    }
                    "APIM (SYNC)" -> {
                        Text("Diagnostic Tests:", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Button(onClick = { /* trigger */ }, modifier = Modifier.fillMaxWidth()) { Text("Radio Screen Test Pattern") }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { /* trigger */ }, modifier = Modifier.fillMaxWidth()) { Text("Audio Speaker Walk") }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            var engineCenter by remember { mutableStateOf(Offset.Zero) }
            var transCenter by remember { mutableStateOf(Offset.Zero) }
            var exhaustCenter by remember { mutableStateOf(Offset.Zero) }
            
            var bcmCenter by remember { mutableStateOf(Offset.Zero) }
            var apimCenter by remember { mutableStateOf(Offset.Zero) }
            
            Canvas(modifier = Modifier.fillMaxSize().padding(16.dp).pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    if ((tapOffset - engineCenter).getDistance() < 100f) {
                        selectedSystem = "Engine"
                    } else if (!isFord && (tapOffset - transCenter).getDistance() < 100f) {
                        selectedSystem = "Transmission"
                    } else if (!isFord && (tapOffset - exhaustCenter).getDistance() < 100f) {
                        selectedSystem = "Exhaust/Body"
                    } else if (isFord && (tapOffset - bcmCenter).getDistance() < 100f) {
                        selectedSystem = "BCM (Body)"
                    } else if (isFord && (tapOffset - apimCenter).getDistance() < 100f) {
                        selectedSystem = "APIM (SYNC)"
                    }
                }
            }) {
                val carWidth = size.width * 0.7f
                val carHeight = size.height * 0.4f
                val startX = (size.width - carWidth) / 2
                val startY = (size.height - carHeight) / 2
                
                // Body
                drawRoundRect(
                    color = Color.DarkGray,
                    topLeft = Offset(startX, startY),
                    size = Size(carWidth, carHeight),
                    style = Stroke(width = 4.dp.toPx())
                )
                // Cabin
                drawRoundRect(
                    color = Color.DarkGray,
                    topLeft = Offset(startX + carWidth * 0.2f, startY - carHeight * 0.5f),
                    size = Size(carWidth * 0.5f, carHeight * 0.5f),
                    style = Stroke(width = 4.dp.toPx())
                )
                // Wheels
                drawCircle(color = Color.DarkGray, radius = 20f, center = Offset(startX + carWidth * 0.2f, startY + carHeight))
                drawCircle(color = Color.DarkGray, radius = 20f, center = Offset(startX + carWidth * 0.8f, startY + carHeight))
                
                // Engine (Front)
                engineCenter = Offset(startX + carWidth * 0.1f, startY + carHeight * 0.5f)
                val engineColor = if (metrics.coolantTemp > 105f) Color.Red else NeonGreen
                drawCircle(color = engineColor, radius = 25f, center = engineCenter)
                
                if (isFord) {
                    // APIM (Cabin)
                    apimCenter = Offset(startX + carWidth * 0.45f, startY - carHeight * 0.1f)
                    drawCircle(color = Color(0xFFFF9933), radius = 25f, center = apimCenter)
                    
                    // BCM (Rear/Cabin)
                    bcmCenter = Offset(startX + carWidth * 0.8f, startY + carHeight * 0.5f)
                    drawCircle(color = NeonCyan, radius = 25f, center = bcmCenter)
                } else {
                    // Transmission (Mid)
                    transCenter = Offset(startX + carWidth * 0.4f, startY + carHeight * 0.6f)
                    val transColor = if (metrics.transTemp > 120f) Color.Red else NeonGreen
                    drawCircle(color = transColor, radius = 25f, center = transCenter)
                    
                    // Exhaust (Rear)
                    exhaustCenter = Offset(startX + carWidth * 0.9f, startY + carHeight * 0.8f)
                    drawCircle(color = NeonCyan, radius = 25f, center = exhaustCenter)
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(currentVehicle, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                Text("Interactive Schematic (Tap nodes)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
