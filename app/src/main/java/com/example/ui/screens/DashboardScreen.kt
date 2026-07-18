package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.AppViewModel
import com.example.communication.ConnectionState
import com.example.communication.ObdMetrics
import com.example.communication.WebSocketState
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.NeonGreen
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun DashboardScreen(viewModel: AppViewModel) {
    val metrics by viewModel.pciBusManager.metrics.collectAsState()
    val currentVehicle by viewModel.selectedVehicle.collectAsState()
    val connectionState by viewModel.webSocketService.connectionState.collectAsState()
    val vciConnectionState by viewModel.bluetoothVciManager.connectionState.collectAsState()
    val networkStats by viewModel.pciBusManager.networkStats.collectAsState()
    
    val currentStat = networkStats.lastOrNull()
    val pps = currentStat?.packetsPerSecond ?: 0
    val isFord = currentVehicle.contains("Ford")

    // State buffers for real-time oscilloscope signal traces
    var pointsH by remember { mutableStateOf(FloatArray(85) { 2.5f }) }
    var pointsL by remember { mutableStateOf(FloatArray(85) { 2.5f }) }
    var pointsPci by remember { mutableStateOf(FloatArray(85) { 0.1f }) }

    LaunchedEffect(pps, connectionState, vciConnectionState, isFord) {
        val random = Random(System.currentTimeMillis())
        while (true) {
            delay(40) // ~25 fps for ultra-smooth rendering with low overhead
            
            val isConnected = connectionState == WebSocketState.CONNECTED || vciConnectionState == ConnectionState.CONNECTED
            val nextH: Float
            val nextL: Float
            val nextPci: Float
            
            val baseNoise = (random.nextFloat() - 0.5f) * 0.04f
            
            if (isConnected && pps > 0) {
                // Highly realistic bus traffic representation based on active packet throughput
                // When a digital bit is dominant, CAN-High goes up to 3.5V and CAN-Low goes down to 1.5V
                // PCI bus transitions from 0V (recessive) to 7V (dominant)
                val isDominant = random.nextFloat() < (0.25f + (pps.coerceAtMost(60) / 120f))
                if (isDominant) {
                    nextH = 3.5f + baseNoise
                    nextL = 1.5f + baseNoise
                    nextPci = 7.0f + (random.nextFloat() - 0.5f) * 0.12f
                } else {
                    nextH = 2.5f + baseNoise
                    nextL = 2.5f + baseNoise
                    nextPci = 0.1f + baseNoise.coerceAtLeast(0f)
                }
            } else {
                // Bus is idle, show standard flatline recessive voltages with subtle electrical wire noise
                nextH = 2.5f + baseNoise
                nextL = 2.5f + baseNoise
                nextPci = 0.04f + baseNoise.coerceAtLeast(0f)
            }
            
            pointsH = pointsH.sliceArray(1 until 85) + nextH
            pointsL = pointsL.sliceArray(1 until 85) + nextL
            pointsPci = pointsPci.sliceArray(1 until 85) + nextPci
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 1. Vehicle Interactive Schematic Map
        VehicleDiagramCard(metrics, currentVehicle)
        Spacer(modifier = Modifier.height(8.dp))

        // 2. High-Tech Real-time Signal Oscilloscope (Recharts alternative in Jetpack Compose)
        CanBusRealtimeScopeCard(
            isFord = isFord,
            pps = pps,
            connectionState = if (connectionState == WebSocketState.CONNECTED || vciConnectionState == ConnectionState.CONNECTED) WebSocketState.CONNECTED else WebSocketState.DISCONNECTED,
            pointsH = pointsH,
            pointsL = pointsL,
            pointsPci = pointsPci
        )
        Spacer(modifier = Modifier.height(16.dp))

        // 3. Grid Gauges laid out in highly responsive, elegant rows
        Text("Standard Live Sensors", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                GaugeWidget("RPM", metrics.rpm, 8000f, NeonCyan, "rev/min")
            }
            Box(modifier = Modifier.weight(1f)) {
                GaugeWidget("Speed", metrics.speed, 200f, NeonPurple, "km/h")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                GaugeWidget("Coolant", metrics.coolantTemp, 150f, NeonGreen, "°C")
            }
            Box(modifier = Modifier.weight(1f)) {
                GaugeWidget("Engine Load", metrics.engineLoad, 100f, Color(0xFFFF3366), "%")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                GaugeWidget("Trans Temp", metrics.transTemp, 150f, Color(0xFFFF9933), "°C")
            }
            Box(modifier = Modifier.weight(1f)) {
                // Balance space
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    modifier = Modifier.aspectRatio(1f)
                ) {}
            }
        }
    }
}

@Composable
fun CanBusRealtimeScopeCard(
    isFord: Boolean,
    pps: Int,
    connectionState: WebSocketState,
    pointsH: FloatArray,
    pointsL: FloatArray,
    pointsPci: FloatArray
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF11121C)),
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (connectionState == WebSocketState.CONNECTED && pps > 0) NeonGreen else Color.Gray,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isFord) "Live CAN Bus Signal Oscilloscope" else "Live J1850 PCI Bus Signal Oscilloscope",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = if (connectionState == WebSocketState.CONNECTED) "Packet Rate: $pps pps" else "BUS UNPOWERED",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (connectionState == WebSocketState.CONNECTED && pps > 0) NeonCyan else Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Scope Screen Screen
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    
                    // 1. Draw Grid Lines (Neon digital styling)
                    val gridColor = Color(0xFF1E2135)
                    val numVerticalDivisions = 10
                    val numHorizontalDivisions = 5
                    
                    for (i in 1 until numVerticalDivisions) {
                        val x = (width / numVerticalDivisions) * i
                        drawLine(color = gridColor, start = Offset(x, 0f), end = Offset(x, height), strokeWidth = 1f)
                    }
                    for (i in 1 until numHorizontalDivisions) {
                        val y = (height / numHorizontalDivisions) * i
                        drawLine(color = gridColor, start = Offset(0f, y), end = Offset(width, y), strokeWidth = 1f)
                    }
                    
                    // 2. Draw Waveforms
                    if (isFord) {
                        // CAN Bus dual-trace map (0V bottom to 5V top of canvas)
                        fun mapVoltage(v: Float): Float {
                            val clamped = v.coerceIn(0f, 5f)
                            return height - (clamped * (height / 5f))
                        }
                        
                        val pathH = Path()
                        val pathL = Path()
                        
                        pointsH.forEachIndexed { idx, v ->
                            val x = (width / (pointsH.size - 1)) * idx
                            val y = mapVoltage(v)
                            if (idx == 0) pathH.moveTo(x, y) else pathH.lineTo(x, y)
                        }
                        
                        pointsL.forEachIndexed { idx, v ->
                            val x = (width / (pointsL.size - 1)) * idx
                            val y = mapVoltage(v)
                            if (idx == 0) pathL.moveTo(x, y) else pathL.lineTo(x, y)
                        }
                        
                        // Dual traces shadow glow
                        drawPath(pathH, color = NeonCyan.copy(alpha = 0.25f), style = Stroke(width = 6f, join = StrokeJoin.Round))
                        drawPath(pathL, color = NeonPurple.copy(alpha = 0.25f), style = Stroke(width = 6f, join = StrokeJoin.Round))
                        
                        // Main lines
                        drawPath(pathH, color = NeonCyan, style = Stroke(width = 2.5f, join = StrokeJoin.Round))
                        drawPath(pathL, color = NeonPurple, style = Stroke(width = 2.5f, join = StrokeJoin.Round))
                    } else {
                        // J1850 VPW single wire PCI Bus trace (0V bottom to 8V top of canvas)
                        fun mapVoltagePci(v: Float): Float {
                            val clamped = v.coerceIn(0f, 8f)
                            return height - (clamped * (height / 8f))
                        }
                        
                        val pathPci = Path()
                        pointsPci.forEachIndexed { idx, v ->
                            val x = (width / (pointsPci.size - 1)) * idx
                            val y = mapVoltagePci(v)
                            if (idx == 0) pathPci.moveTo(x, y) else pathPci.lineTo(x, y)
                        }
                        
                        // PCI trace shadow glow and core
                        drawPath(pathPci, color = NeonGreen.copy(alpha = 0.25f), style = Stroke(width = 6f, join = StrokeJoin.Round))
                        drawPath(pathPci, color = NeonGreen, style = Stroke(width = 2.5f, join = StrokeJoin.Round))
                    }
                }
                
                // Absolute Voltage scale markings
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .align(Alignment.CenterStart)
                        .padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = if (isFord) "5.0V" else "8.0V", color = Color.Gray, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                    Text(text = if (isFord) "2.5V" else "4.0V", color = Color.Gray, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                    Text(text = "0.0V", color = Color.Gray, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                }
                
                // Trace indicator box
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isFord) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(7.dp).background(NeonCyan, shape = RoundedCornerShape(1.5.dp)))
                            Spacer(Modifier.width(3.dp))
                            Text("CAN-H", color = Color.White, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(7.dp).background(NeonPurple, shape = RoundedCornerShape(1.5.dp)))
                            Spacer(Modifier.width(3.dp))
                            Text("CAN-L", color = Color.White, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(7.dp).background(NeonGreen, shape = RoundedCornerShape(1.5.dp)))
                            Spacer(Modifier.width(3.dp))
                            Text("SAE J1850 VPW", color = Color.White, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
                        }
                    }
                }
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
