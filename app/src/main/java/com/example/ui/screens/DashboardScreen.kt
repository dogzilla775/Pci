package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.AppViewModel
import com.example.communication.ObdMetrics
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonGreen

@Composable
fun DashboardScreen(viewModel: AppViewModel) {
    val metrics by viewModel.pciBusManager.metrics.collectAsState()
    val currentVehicle by viewModel.selectedVehicle.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        VehicleDiagramCard(metrics, currentVehicle)
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                GaugeWidget("RPM", metrics.rpm, 8000f, NeonCyan, "rev/min")
            }
            item {
                GaugeWidget("Speed", metrics.speed, 200f, NeonPurple, "km/h")
            }
            item {
                GaugeWidget("Coolant", metrics.coolantTemp, 150f, NeonGreen, "°C")
            }
            item {
                GaugeWidget("Engine Load", metrics.engineLoad, 100f, Color(0xFFFF3366), "%")
            }
            item {
                GaugeWidget("Trans Temp", metrics.transTemp, 150f, Color(0xFFFF9933), "°C")
            }
        }
    }
}

@Composable
fun GaugeWidget(title: String, value: Float, max: Float, color: Color, unit: String) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.aspectRatio(1f)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)) {
                val strokeWidth = 12.dp.toPx()
                drawArc(
                    color = Color.DarkGray,
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round),
                    size = Size(size.width, size.height)
                )
                
                val progress = (value / max).coerceIn(0f, 1f)
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = 270f * progress,
                    useCenter = false,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round),
                    size = Size(size.width, size.height)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    String.format("%.1f", value), 
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    color = color
                )
                Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
