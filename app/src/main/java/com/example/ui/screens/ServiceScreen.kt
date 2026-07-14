package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import com.example.ui.theme.NeonGreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.AppViewModel
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ServiceScreen(viewModel: AppViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    val currentVehicle by viewModel.selectedVehicle.collectAsState()
    val isFord = currentVehicle.contains("Ford")
    
    val chryslerModules = listOf(
        "BCM (Body Control Module)" to listOf("Courtesy Light Timeout", "Door Lock Behavior", "DRL Activation", "Wiper Delay Calibration"),
        "TCM (Transmission Control Module)" to listOf("Quick Learn Procedure", "Reset Shift Adaptives", "Read Clutch Volumes"),
        "ECM (Engine Control Module)" to listOf("Throttle Body Alignment", "Injector Kill Test", "Reset KAM (Keep Alive Memory)")
    )
    
    val fordModules = listOf(
        "BCM (Body Control Module)" to listOf("BMS Reset", "Door Keypad Code Read/Write", "Global Window Open/Close", "Lighting Self-Test"),
        "PCM (Powertrain Control Module)" to listOf("Misfire Profile Correction", "KAM Reset", "Idle Speed Adjustment", "Throttle Body Alignment"),
        "APIM (SYNC Module)" to listOf("Module Reset", "Screen Calibration", "Audio output test"),
        "ACM (Audio Control Module)" to listOf("Speaker Walk Test", "Radio Antenna Self-Test", "Satellite Radio Activation"),
        "FCIM (Front Controls Interface)" to listOf("HVAC Actuator Calibration", "Button Self-Test"),
        "IPC (Instrument Panel Cluster)" to listOf("Gauge Sweep", "Chime Test", "Indicator LED Test")
    )
    
    val modules = if (isFord) fordModules else chryslerModules
    
    val chryslerTests = listOf(
        "Cooling Fan Relay (Low/High)",
        "EGR Solenoid Control",
        "EVAP Purge Valve",
        "Fuel Pump Relay",
        "All Doors Lock/Unlock",
        "Horn Activation",
        "Instrument Cluster Sweep"
    )
    
    val fordTests = listOf(
        "Cooling Fan Relay (Low/High/Max)",
        "Fuel Injector Disable (1-6)",
        "VVT Solenoid Test",
        "EVAP Purge/Vent Valve",
        "All Doors Lock/Unlock (BCM)",
        "Horn Activation",
        "Wiper Motor (Low/High/Washer)",
        "Radio Display Test Pattern",
        "Audio Speaker Test (FL, FR, RL, RR, Sub)"
    )
    
    val activeTests = if (isFord) fordTests else chryslerTests

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Service & Active Tests", style = MaterialTheme.typography.titleLarge, color = NeonPurple)
        Text(if (isFord) "Ford OEM Special Functions" else "Chrysler OEM Special Functions", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(16.dp))
        
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 8.dp) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Active Tests") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Service Resets") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text(if (isFord) "Ford Pro Tests" else "VPW Commands") })
            if (isFord) {
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Wiring Library") })
            }
        }
        
        Spacer(Modifier.height(16.dp))

        when (selectedTab) {
            0 -> {
                LazyColumn {
                    items(activeTests) { test ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { /* Trigger test */ },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PowerSettingsNew, contentDescription = "Actuator", tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(16.dp))
                                Text(test, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
            1 -> {
                LazyColumn {
                    items(modules) { (moduleName, functions) ->
                        Text(moduleName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        functions.forEach { func ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { /* trigger command for func */ },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Build, contentDescription = "Tool", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(16.dp))
                                    Text(func, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
            2 -> {
                CommandInterface(isFord, viewModel)
            }
            3 -> {
                if (isFord) {
                    WiringLibraryView()
                }
            }
        }
    }
}

@Composable
fun CommandInterface(isFord: Boolean, viewModel: AppViewModel) {
    val coroutineScope = rememberCoroutineScope()
    var commandLog by remember { mutableStateOf(listOf<String>()) }
    var isSending by remember { mutableStateOf(false) }

    var isQuickScanning by remember { mutableStateOf(false) }
    var apimStatus by remember { mutableStateOf("Unknown") }
    var bcmStatus by remember { mutableStateOf("Unknown") }
    var pcmStatus by remember { mutableStateOf("Unknown") }
    
    var reportModule by remember { mutableStateOf<String?>(null) }
    var reportMessage by remember { mutableStateOf<String?>(null) }
    val currentVehicle by viewModel.selectedVehicle.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val chryslerCommands = listOf(
        "Cycle Door Locks" to "8C 1A 2B 3C",
        "Toggle Interior Lighting" to "8C 4D 5E 6F",
        "Activate Horn Relay" to "8C 7A 8B 9C",
        "Wiper Motor (Low Speed)" to "8C 11 22 33",
        "Headlamp Relay (High Beam)" to "8C 44 55 66"
    )
    
    val fordCommands = listOf(
        "Radio Screen Test (APIM)" to "7D0 2F 01 02 03",
        "Audio Speaker Walk (ACM)" to "727 2F 02 01 04",
        "Instrument Sweep (IPC)" to "720 2F 01 01",
        "Cycle Door Locks (BCM)" to "726 2F 01 05",
        "Headlamp High Beam (BCM)" to "726 2F 01 08",
        "Wiper Motor On (BCM)" to "726 2F 02 01",
        "HVAC Blower Max (FCIM)" to "7A7 2F 01 09",
        "Fuel Injector Disable Cyl 1 (PCM)" to "7E0 2F 02 01 03",
        "VVT Solenoid Test (PCM)" to "7E0 2F 03 04 01"
    )
    
    val commands = if (isFord) fordCommands else chryslerCommands

    Column(modifier = Modifier.fillMaxSize()) {
        Text(if (isFord) "J2534 UDS CAN Actuator Commands (HS-CAN / MS-CAN)" else "J1850 VPW Actuator Commands", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
        Spacer(modifier = Modifier.height(8.dp))

        if (isFord) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Module Network Status", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Button(
                            onClick = {
                                if (!isQuickScanning) {
                                    isQuickScanning = true
                                    apimStatus = "Error: Offline"
                                    bcmStatus = "Error: Offline"
                                    pcmStatus = "Error: Offline"
                                    coroutineScope.launch {
                                        isQuickScanning = false
                                        commandLog = (commandLog + "Quick Scan failed: Hardware Not Connected").takeLast(10)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                            enabled = !isQuickScanning
                        ) {
                            if (isQuickScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scanning")
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Scan", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Quick Scan")
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ModuleStatusIcon("PCM", pcmStatus) { reportModule = "PCM" }
                        ModuleStatusIcon("BCM", bcmStatus) { reportModule = "BCM" }
                        ModuleStatusIcon("APIM", apimStatus) { reportModule = "APIM" }
                    }
                }
            }
        }
        
        if (reportModule != null) {
            AlertDialog(
                onDismissRequest = { reportModule = null },
                title = { Text("Generate Diagnostic Report") },
                text = { Text("Do you want to generate a PDF diagnostic report for the $reportModule module?") },
                confirmButton = {
                    TextButton(onClick = {
                        val dtcs = if (reportModule == "BCM") listOf("U0422 - Invalid Data Received From Body Control Module") else emptyList()
                        val repair = if (reportModule == "BCM") "1. Check BCM connectors for corrosion.\n2. Perform MS-CAN network test.\n3. Flash latest BCM calibration." else "No recommended repairs at this time."
                        val liveData = "Battery Voltage: 12.4V\nModule State: Active\nNetwork Ping: 12ms"
                        val file = com.example.communication.PdfReportGenerator.generateModuleReport(
                            context = context,
                            moduleName = reportModule!!,
                            vehicleName = currentVehicle,
                            dtcs = dtcs,
                            liveData = liveData,
                            repairProcedures = repair
                        )
                        reportMessage = if (file != null) "Report saved to ${file.name}" else "Failed to generate report."
                        commandLog = (commandLog + (reportMessage ?: "")).takeLast(10)
                        reportModule = null
                    }) {
                        Text("Generate PDF")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { reportModule = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
        
        if (reportMessage != null) {
            Snackbar(
                modifier = Modifier.padding(8.dp),
                action = {
                    TextButton(onClick = { reportMessage = null }) {
                        Text("Dismiss", color = NeonCyan)
                    }
                }
            ) {
                Text(reportMessage!!)
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(commands) { (name, hexCommand) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text("TX: $hexCommand", style = MaterialTheme.typography.labelMedium, color = NeonCyan)
                        }
                        Button(
                            onClick = {
                                if (!isSending) {
                                    isSending = true
                                    commandLog = (commandLog + "Sending: $hexCommand -> $name").takeLast(10)
                                    coroutineScope.launch {
                                        // Simulated data removed
                                        commandLog = (commandLog + "Error: Hardware Adapter Not Connected").takeLast(10)
                                        isSending = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                            enabled = !isSending
                        ) {
                            Text("Send")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Command Log Terminal
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Color.Black, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Column {
                Text("Transmission Log", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn {
                    items(commandLog) { logEntry ->
                        Text(
                            text = "> $logEntry",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (logEntry.startsWith("Success")) NeonCyan else Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModuleStatusIcon(name: String, status: String, onClick: () -> Unit = {}) {
    val color = when {
        status == "Unknown" -> Color.Gray
        status == "Pinging..." -> Color(0xFFFF9933)
        status.contains("DTC") && status.contains("0") -> NeonGreen
        status.contains("DTC") -> MaterialTheme.colorScheme.error
        else -> Color.Gray
    }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(enabled = status.contains("DTC")) { onClick() }) {
        Icon(
            imageVector = Icons.Default.Memory,
            contentDescription = name,
            tint = color,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(status, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WiringLibraryView() {
    var selectedModule by remember { mutableStateOf("PCM") }
    var expanded by remember { mutableStateOf(false) }
    
    val modules = listOf("PCM", "BCM", "APIM")
    
    Column(modifier = Modifier.fillMaxSize()) {
        Text("2013 Ford Explorer Wiring Library", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
        Spacer(modifier = Modifier.height(16.dp))
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = "$selectedModule (Powertrain Control Module)",
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Module") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                modules.forEach { module ->
                    DropdownMenuItem(
                        text = { Text(module) },
                        onClick = {
                            selectedModule = module
                            expanded = false
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("$selectedModule Pinout & Wiring Diagram", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                
                // Drawing a connector schematic
                Box(modifier = Modifier.fillMaxWidth().height(120.dp).background(Color.Black, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        
                        // Draw connector outline
                        drawRoundRect(
                            color = Color.DarkGray,
                            size = androidx.compose.ui.geometry.Size(canvasWidth, canvasHeight),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                        )
                        
                        // Draw pins
                        val rows = 3
                        val cols = 12
                        val pinSpacingX = canvasWidth / (cols + 1)
                        val pinSpacingY = canvasHeight / (rows + 1)
                        
                        for (r in 1..rows) {
                            for (c in 1..cols) {
                                val isPopulated = Math.random() > 0.3
                                drawCircle(
                                    color = if (isPopulated) NeonGreen else Color.Gray.copy(alpha = 0.3f),
                                    radius = 4.dp.toPx(),
                                    center = androidx.compose.ui.geometry.Offset(c * pinSpacingX, r * pinSpacingY)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Pin Assignments", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    val pins = when (selectedModule) {
                        "PCM" -> listOf(
                            "Pin 1 - Power (12V) - Red/White",
                            "Pin 2 - Ground - Black",
                            "Pin 3 - HS-CAN High - Grey/Orange",
                            "Pin 4 - HS-CAN Low - Violet/Orange",
                            "Pin 5 - Injector 1 Control - Blue",
                            "Pin 6 - VVT Solenoid Control - Green"
                        )
                        "BCM" -> listOf(
                            "Pin 1 - Battery Power - Red",
                            "Pin 2 - MS-CAN High - Grey/Blue",
                            "Pin 3 - MS-CAN Low - Violet/Blue",
                            "Pin 4 - Headlamp Relay Control - Yellow/Blue",
                            "Pin 5 - Door Lock Actuator - Brown",
                            "Pin 6 - Horn Relay - Dark Green"
                        )
                        "APIM" -> listOf(
                            "Pin 1 - Ignition Power - Yellow",
                            "Pin 2 - MS-CAN High - Grey/Blue",
                            "Pin 3 - MS-CAN Low - Violet/Blue",
                            "Pin 4 - Microphone + - White",
                            "Pin 5 - Microphone - - Black",
                            "Pin 6 - Audio Output FL - Blue/White"
                        )
                        else -> emptyList()
                    }
                    
                    items(pins.size) { index ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp), tint = NeonCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(pins[index], style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}
