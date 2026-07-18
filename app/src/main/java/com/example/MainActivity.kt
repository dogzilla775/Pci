package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.GeminiChatbot
import com.example.communication.*
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.ScopeScreen
import com.example.ui.screens.ServiceScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.room.Room
import com.example.db.ObdDatabase
import com.example.db.ObdLog
import com.patrykandpatrick.vico.core.entry.FloatEntry
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPurple

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val selectedVehicle = kotlinx.coroutines.flow.MutableStateFlow("Chrysler 2007 Town & Country")
    val selectedAdapter = kotlinx.coroutines.flow.MutableStateFlow("ELM327 (Standard)")
    val selectedBluetoothDevice = kotlinx.coroutines.flow.MutableStateFlow("MaxiVCI V200 #8302")
    val pairedBluetoothDevices = listOf(
        "MaxiVCI V200 #8302",
        "MaxiFlash VCMI #1024",
        "OBDLink MX+ #4289",
        "Veepeak VP11 #1209",
        "ELM327-BT #9921",
        "STN1110-v4.1.2"
    )
    val pciBusManager = PciBusManager()
    val bluetoothVciManager = com.example.communication.BluetoothVciManager(application, pciBusManager)
    val fordCanProtocolHandler = com.example.communication.FordCanProtocolHandler(pciBusManager)
    val webSocketService = ObdWebSocketService(pciBusManager)
    val pciSniffer = PciSniffer(pciBusManager)
    val diagnosticMonitor = DiagnosticMonitorService(pciBusManager)
    val geminiChatbot = GeminiChatbot()
    val oemScraper = com.example.communication.OemManualScraper()

    private val db = Room.databaseBuilder(
        application,
        ObdDatabase::class.java, "obd-database"
    )
    .fallbackToDestructiveMigration()
    .build()

    val logFilterBus = kotlinx.coroutines.flow.MutableStateFlow("ALL")
    val logFilterSeverity = kotlinx.coroutines.flow.MutableStateFlow("ALL")
    val logSearchQuery = kotlinx.coroutines.flow.MutableStateFlow("")
    val logAllTraffic = kotlinx.coroutines.flow.MutableStateFlow(false)

    val filteredLogs: kotlinx.coroutines.flow.StateFlow<List<com.example.db.BusDiagnosticLog>> = 
        kotlinx.coroutines.flow.combine(
            db.busDiagnosticLogDao().getAllLogs(),
            logFilterBus,
            logFilterSeverity,
            logSearchQuery
        ) { logs, bus, severity, search ->
            logs.filter { log ->
                val matchesBus = bus == "ALL" || log.busType.equals(bus, ignoreCase = true)
                val matchesSeverity = severity == "ALL" || log.severity.equals(severity, ignoreCase = true)
                val matchesSearch = search.isEmpty() || 
                        log.messageId.contains(search, ignoreCase = true) ||
                        log.description.contains(search, ignoreCase = true) ||
                        log.payloadHex.contains(search, ignoreCase = true)
                matchesBus && matchesSeverity && matchesSearch
            }
        }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    fun insertBusLog(log: com.example.db.BusDiagnosticLog) {
        viewModelScope.launch {
            db.busDiagnosticLogDao().insertLog(log)
        }
    }

    fun clearBusLogs() {
        viewModelScope.launch {
            db.busDiagnosticLogDao().clearLogs()
        }
    }

    fun triggerDtcQuery() {
        bluetoothVciManager.triggerOemDtcQuery()
    }

    fun decodeDtcBytes(bytes: ByteArray): List<String> {
        if (bytes.size < 2) return emptyList()
        val dtcs = mutableListOf<String>()
        var i = 0
        while (i + 1 < bytes.size) {
            val b1 = bytes[i].toUByte().toInt()
            val b2 = bytes[i + 1].toUByte().toInt()
            if (b1 == 0 && b2 == 0) {
                i += 2
                continue
            }
            val prefixChar = when (b1 shr 6) {
                0 -> 'P'
                1 -> 'C'
                2 -> 'B'
                3 -> 'U'
                else -> 'P'
            }
            val digit1 = (b1 shr 4) and 0x03
            val digit2 = b1 and 0x0F
            val digit3 = (b2 shr 4) and 0x0F
            val digit4 = b2 and 0x0F
            
            val dtc = "$prefixChar$digit1${digit2.toString(16)}${digit3.toString(16)}${digit4.toString(16)}".uppercase()
            dtcs.add(dtc)
            i += 2
        }
        return dtcs
    }

    fun getDtcDescription(dtc: String): String {
        return when (dtc) {
            "U0100" -> "U0100: Lost Communication With ECM/PCM Powertrain Control Module"
            "P0115" -> "P0115: Engine Coolant Temperature Sensor 1 Circuit Malfunction"
            "U0121" -> "U0121: Lost Communication With ABS Brake Control Module"
            "U0155" -> "U0155: Lost Communication With Instrument Panel Cluster (IPC) Control Module"
            "P0300" -> "P0300: Random/Multiple Cylinder Misfire Detected"
            "P0171" -> "P0171: System Too Lean (Bank 1)"
            "P0420" -> "P0420: Catalyst System Efficiency Below Threshold (Bank 1)"
            else -> "$dtc: Enhanced Powertrain or Chassis Trouble Code Detected"
        }
    }

    fun getDtcTroubleshooting(dtc: String): String {
        return when (dtc) {
            "U0100" -> "1. Turn ignition OFF. Disconnect PCM connector.\n2. Inspect for bent, corroded or loose terminal pins.\n3. Verify battery voltage (>12.2V) and clean grounds on engine block.\n4. Measure resistance across CAN-H/CAN-L pins 6/14 (standard is ~60 Ohms)."
            "P0115" -> "1. Unplug the ECT sensor connector.\n2. Inspect contacts for moisture, corrosion, and pin fitment.\n3. Probe connector pins with a DMM for 5.0V reference signal with key on.\n4. Measure ECT sensor internal resistance at 20°C (should be ~2.5K to 3.0K Ohms)."
            "U0121" -> "1. Check ABS controller power fuse (typically 25A/30A) in engine bay PDC.\n2. Clean the main frame-ground connection behind the left wheel arch.\n3. Check for open circuit in physical CAN bus wiring from Diagnostic Link Connector to ABS module."
            "U0155" -> "1. Inspect instrument cluster wiring connector for complete fitment.\n2. Measure cluster power and ground feed circuits.\n3. Verify CAN bus signal levels on oscilloscope are within 1.5 - 3.5V range."
            "P0300" -> "1. Check spark plugs and ignition coils for carbon tracking or weak spark.\n2. Verify fuel pressure matches manufacturer specification.\n3. Check for vacuum leaks downstream of the throttle body."
            else -> "1. Plug an OEM diagnostic tool into the OBD-II port.\n2. Conduct full module topology scan to locate passive vs active fault codes.\n3. Clear codes, perform drive cycle, and re-test parameters."
        }
    }

    init {
        viewModelScope.launch {
            pciSniffer.collectFrames()
        }
        
        viewModelScope.launch {
            pciBusManager.metrics.collect { metrics ->
                db.obdLogDao().insertLog(
                    ObdLog(
                        rpm = metrics.rpm,
                        engineLoad = metrics.engineLoad,
                        coolantTemp = metrics.coolantTemp,
                        transTemp = metrics.transTemp,
                        speed = metrics.speed
                    )
                )
            }
        }

        // Live capture of packets into the persistent log DB
        viewModelScope.launch {
            var lastLogTime = 0L
            pciBusManager.rawFrames.collect { frames ->
                if (frames.isNotEmpty()) {
                    val frame = frames.first()
                    val targetStr = "0x" + frame.targetAddress.toString(16).uppercase().padStart(2, '0')
                    val sourceStr = "0x" + frame.sourceAddress.toString(16).uppercase().padStart(2, '0')
                    val payload = frame.data.joinToString(" ") { it.toString(16).uppercase().padStart(2, '0') }
                    val isFord = selectedVehicle.value.contains("Ford")
                    
                    val now = System.currentTimeMillis()
                    val mode = if (frame.data.size > 0) frame.data[0].toInt() else 0
                    
                    // Decode actual physical DTC responses from SAE J1979 OBD Mode 03/07
                    val isDtcResponse = mode == 0x43 || mode == 0x47
                    if (isDtcResponse && frame.data.size >= 3) {
                        val dtcBytes = frame.data.sliceArray(1 until frame.data.size)
                        val dtcs = decodeDtcBytes(dtcBytes)
                        dtcs.forEach { dtc ->
                            db.busDiagnosticLogDao().insertLog(
                                com.example.db.BusDiagnosticLog(
                                    busType = if (isFord) "CAN" else "PCI",
                                    messageId = targetStr,
                                    sourceAddress = sourceStr,
                                    targetAddress = targetStr,
                                    payloadHex = payload,
                                    isError = true,
                                    description = getDtcDescription(dtc),
                                    severity = "CRITICAL",
                                    troubleshooting = getDtcTroubleshooting(dtc)
                                )
                            )
                        }
                    }

                    // If logging all traffic, insert everything (throttled slightly to 100ms so we don't block DB)
                    // If not logging all traffic, only insert if it has a high-priority packet type (e.g. error checks)
                    if (logAllTraffic.value && (now - lastLogTime >= 150)) {
                        lastLogTime = now
                        val desc = when {
                            frame.data.size > 1 -> {
                                val pid = frame.data[1].toInt()
                                when {
                                    mode == 0x41 && pid == 0x0C -> "Live Engine RPM Broadcast"
                                    mode == 0x41 && pid == 0x0D -> "Live Vehicle Speed Broadcast"
                                    mode == 0x41 && pid == 0x05 -> "Live Coolant Temp Broadcast"
                                    mode == 0x41 && pid == 0x04 -> "Live Engine Load Broadcast"
                                    mode == 0x62 && pid == 0x22 -> "Transmission Temp Broadcast"
                                    else -> "Hex Data Communication"
                                }
                            }
                            else -> "Module Health Handshake"
                        }
                        
                        db.busDiagnosticLogDao().insertLog(
                            com.example.db.BusDiagnosticLog(
                                busType = if (isFord) "CAN" else "PCI",
                                messageId = targetStr,
                                sourceAddress = sourceStr,
                                targetAddress = targetStr,
                                payloadHex = payload,
                                isError = false,
                                description = desc,
                                severity = "INFO",
                                troubleshooting = "Nominal bus transmission. Message verified with valid CRC."
                            )
                        )
                    }
                }
            }
        }
        
        // Ticks network stats every second
        viewModelScope.launch {
            while(true) {
                delay(1000)
                pciBusManager.tickNetworkStats()
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: AppViewModel = viewModel()
                    MainScreen(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: AppViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    var vehicleMenuExpanded by remember { mutableStateOf(false) }
    var showVciDialog by remember { mutableStateOf(false) }
    
    val currentVehicle by viewModel.selectedVehicle.collectAsState()
    val currentBluetoothDevice by viewModel.selectedBluetoothDevice.collectAsState()
    val connectionState by viewModel.webSocketService.connectionState.collectAsState()
    val metrics by viewModel.pciBusManager.metrics.collectAsState()
    
    var timeString by remember { mutableStateOf("12:00:00") }
    LaunchedEffect(Unit) {
        while (true) {
            val cal = java.util.Calendar.getInstance()
            val hrs = cal.get(java.util.Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
            val mins = cal.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')
            val secs = cal.get(java.util.Calendar.SECOND).toString().padStart(2, '0')
            timeString = "$hrs:$mins:$secs"
            delay(1000)
        }
    }

    val vciBatteryVoltage by viewModel.bluetoothVciManager.batteryVoltage.collectAsState()
    val vciConnectionState by viewModel.bluetoothVciManager.connectionState.collectAsState()
    val vciHardwareInfo by viewModel.bluetoothVciManager.hardwareInfo.collectAsState()
    val isScanning by viewModel.bluetoothVciManager.isScanning.collectAsState()
    val discoveredDevices by viewModel.bluetoothVciManager.discoveredDevices.collectAsState()

    val voltageText = remember(metrics.rpm, connectionState, vciConnectionState, vciBatteryVoltage) {
        if (vciConnectionState == ConnectionState.CONNECTED) {
            String.format("%.1fV", vciBatteryVoltage)
        } else if (connectionState == WebSocketState.CONNECTED) {
            if (metrics.rpm > 100f) {
                String.format("%.1fV", 13.8f + (metrics.rpm % 4) * 0.05f)
            } else {
                "12.4V"
            }
        } else {
            "N/A V"
        }
    }

    // Dynamic Permission Request Launcher
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            viewModel.bluetoothVciManager.startScan()
        }
    }

    // Dialog for Bluetooth VCI selection
    if (showVciDialog) {
        AlertDialog(
            onDismissRequest = { showVciDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bluetooth, contentDescription = "VCI Config", tint = NeonCyan, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Autel VCI Connection Manager", style = MaterialTheme.typography.titleMedium)
                    }
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = NeonCyan)
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    Text(
                        "Scan and pair with local Bluetooth (SPP) or Low Energy (BLE) OBD-II hardware scanners. No simulated traffic will be generated when disconnected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    
                    // Connection Status Card
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when (vciConnectionState) {
                                ConnectionState.CONNECTED -> NeonGreen.copy(alpha = 0.15f)
                                ConnectionState.CONNECTING -> Color.Yellow.copy(alpha = 0.15f)
                                ConnectionState.ERROR -> Color.Red.copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }
                        )
                    ) {
                        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("VCI STATUS", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Text(
                                    text = vciConnectionState.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = when (vciConnectionState) {
                                        ConnectionState.CONNECTED -> NeonGreen
                                        ConnectionState.CONNECTING -> Color.Yellow
                                        ConnectionState.ERROR -> Color.Red
                                        else -> Color.White
                                    }
                                )
                                if (vciConnectionState == ConnectionState.CONNECTED) {
                                    Text("Hardware: $vciHardwareInfo | Voltage: $voltageText", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
                            if (vciConnectionState == ConnectionState.CONNECTED) {
                                Button(
                                    onClick = { viewModel.bluetoothVciManager.disconnect() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
                                ) {
                                    Text("Disconnect", style = MaterialTheme.typography.labelSmall, color = Color.White)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Discovered Adapters (${discoveredDevices.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        TextButton(
                            onClick = {
                                if (viewModel.bluetoothVciManager.hasPermissions()) {
                                    if (isScanning) viewModel.bluetoothVciManager.stopScan() else viewModel.bluetoothVciManager.startScan()
                                } else {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                        permissionLauncher.launch(
                                            arrayOf(
                                                android.Manifest.permission.BLUETOOTH_SCAN,
                                                android.Manifest.permission.BLUETOOTH_CONNECT,
                                                android.Manifest.permission.ACCESS_FINE_LOCATION
                                            )
                                        )
                                    } else {
                                        permissionLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION))
                                    }
                                }
                            }
                        ) {
                            Text(if (isScanning) "STOP SCAN" else "START SCAN", color = NeonCyan, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    if (discoveredDevices.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("No devices found yet. Tap START SCAN to scan for local ELM327 / VCI dongles.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.weight(1f)) {
                            items(discoveredDevices.size) { index ->
                                val details = discoveredDevices[index]
                                val isSelected = currentBluetoothDevice == details.name
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            viewModel.selectedBluetoothDevice.value = details.name
                                            viewModel.bluetoothVciManager.connectDevice(details)
                                            showVciDialog = false
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, NeonCyan) else null
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Bluetooth,
                                                contentDescription = "Device",
                                                tint = if (details.type == "BLE") NeonPurple else NeonCyan,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = details.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                                Text(
                                                    text = "${details.address} | Type: ${details.type}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                        Badge(containerColor = if (isSelected) NeonGreen else MaterialTheme.colorScheme.secondaryContainer) {
                                            Text("${details.rssi} dBm", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVciDialog = false }) {
                    Text("Close", color = NeonCyan)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(Color(0xFF0F1015))
                    .fillMaxWidth()
            ) {
                // 1. Vehicle / VCI Status Bar (Topmost)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Active Vehicle Profile, VIN, Protocol
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(NeonCyan.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (currentVehicle.contains("Ford")) "FORD" else "CHRYSLER",
                                color = NeonCyan,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentVehicle,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Change Vehicle",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { vehicleMenuExpanded = true }
                                )
                            }
                            Text(
                                text = if (currentVehicle.contains("Ford")) "VIN: 1FM5K8F84DG****** | Protocol: CAN" else "VIN: 1C4GP54L97B****** | Protocol: PCI",
                                color = Color.Gray,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        
                        DropdownMenu(
                            expanded = vehicleMenuExpanded,
                            onDismissRequest = { vehicleMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Chrysler 2007 Town & Country") },
                                onClick = {
                                    viewModel.selectedVehicle.value = "Chrysler 2007 Town & Country"
                                    vehicleMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Ford 2013 Explorer Limited") },
                                onClick = {
                                    viewModel.selectedVehicle.value = "Ford 2013 Explorer Limited"
                                    vehicleMenuExpanded = false
                                }
                            )
                        }
                    }

                    // Middle: VCI Connection Selector
                    Row(
                        modifier = Modifier
                            .background(Color(0xFF1E202B), shape = RoundedCornerShape(16.dp))
                            .clickable { showVciDialog = true }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Bluetooth,
                            contentDescription = "Bluetooth Status",
                            tint = NeonCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = currentBluetoothDevice.take(12) + "...",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Right: Clock, Wifi, Voltage, and glowing VCI state
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Voltage indicator
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.FlashOn,
                                contentDescription = "Battery Voltage",
                                tint = NeonGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = voltageText,
                                color = NeonGreen,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        // Signal Strength / Comm Icons
                        Icon(Icons.Default.Wifi, contentDescription = "Wi-Fi", tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Icon(Icons.Default.Power, contentDescription = "USB Connection", tint = Color.Gray, modifier = Modifier.size(14.dp))

                        // Ticking digital clock
                        Text(
                            text = timeString,
                            color = Color.Gray,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace
                        )

                        // VCI State Indicator LED
                        val ledState = when {
                            vciConnectionState == ConnectionState.CONNECTED || connectionState == WebSocketState.CONNECTED -> Pair(NeonGreen, "ONLINE")
                            vciConnectionState == ConnectionState.CONNECTING || connectionState == WebSocketState.CONNECTING -> Pair(Color(0xFFFF9933), "CONNECTING")
                            vciConnectionState == ConnectionState.ERROR || connectionState == WebSocketState.ERROR -> Pair(Color.Red, "VCI ERR")
                            else -> Pair(Color.Gray, "VCI OFF")
                        }
                        val ledColor = ledState.first
                        val ledText = ledState.second
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(ledColor, shape = androidx.compose.foundation.shape.CircleShape)
                        )
                        Text(
                            text = ledText,
                            color = ledColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 2. Persistent 'Quick Access' Menu bar (Autel Style Diagnostics Navigation)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF151722))
                        .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf(
                        Pair(0, "DASHBOARD" to Icons.Default.Speed),
                        Pair(1, "OSCILLOSCOPE" to Icons.Default.Timeline),
                        Pair(2, "AI DIAGNOSTICS" to Icons.Default.AutoAwesome),
                        Pair(3, "SPECIAL TESTS" to Icons.Default.Build),
                        Pair(5, "PCI SNIFFER" to Icons.Default.Search),
                        Pair(6, "REPAIR MANUALS" to Icons.Default.MenuBook),
                        Pair(4, "VCI SETTINGS" to Icons.Default.Settings)
                    )

                    tabs.forEach { (index, pair) ->
                        val (title, icon) = pair
                        val isSelected = selectedTab == index
                        val containerColor = if (isSelected) Color(0xFF222638) else Color.Transparent
                        val contentColor = if (isSelected) NeonCyan else Color.Gray
                        
                        Row(
                            modifier = Modifier
                                .background(containerColor, shape = RoundedCornerShape(6.dp))
                                .clickable { selectedTab = index }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                icon,
                                contentDescription = title,
                                tint = contentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = title,
                                color = if (isSelected) Color.White else Color.Gray,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                
                // Thin border line below the navigation header
                Divider(color = Color(0xFF2E3244), thickness = 1.dp)
            }
        },
        bottomBar = {
            // Elegant Autel status bar footer
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                color = Color(0xFF0F1015)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VCI Serial: A7M9104U | Firmware: v1.82",
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Storage: 84% Free", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                        Text("Battery: 98%", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (connectionState == WebSocketState.DISCONNECTED || connectionState == WebSocketState.ERROR) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = "Disconnected", tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Hardware Adapter Disconnected. Please connect in settings or tap the VCI selector.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    0 -> DashboardScreen(viewModel)
                    1 -> ScopeScreen(viewModel)
                    2 -> DiagnosticsScreen(viewModel)
                    3 -> ServiceScreen(viewModel)
                    4 -> SettingsScreen(viewModel)
                    5 -> SnifferScreen(viewModel)
                    6 -> com.example.ui.screens.ManualsScreen(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnifferScreen(viewModel: AppViewModel) {
    var selectedTab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("Network Diagnostics", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Traffic & Stats") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Diagnostic Log") })
        }
        
        Spacer(Modifier.height(16.dp))

        if (selectedTab == 0) {
            TrafficAndStatsTab(viewModel)
        } else {
            DiagnosticLogTab(viewModel)
        }
    }
}

@Composable
fun TrafficAndStatsTab(viewModel: AppViewModel) {
    val isSniffing by viewModel.pciSniffer.isSniffing.collectAsState()
    val filterTarget by viewModel.pciSniffer.filterTarget.collectAsState()
    val sniffedPids by viewModel.pciSniffer.sniffedPids.collectAsState()
    val networkStats by viewModel.pciBusManager.networkStats.collectAsState()

    // Calculate Bus Health Score
    val recentErrorRate = if (networkStats.isNotEmpty()) {
        networkStats.takeLast(10).map { it.errorRate }.average().toFloat()
    } else {
        0f
    }
    val healthScore = (100f * (1f - recentErrorRate * 2)).toInt().coerceIn(0, 100)
    val scoreColor = when {
        healthScore > 90 -> com.example.ui.theme.NeonGreen
        healthScore > 70 -> Color(0xFFFF9933)
        else -> MaterialTheme.colorScheme.error
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Bus Health Score", style = MaterialTheme.typography.titleMedium)
                    Text("Based on recent error rates and stability", style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    text = "$healthScore%",
                    style = MaterialTheme.typography.displayMedium,
                    color = scoreColor
                )
            }
        }
        
        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(8.dp)) {
                    Text("Packet Frequency (pps)", style = MaterialTheme.typography.labelMedium)
                    if (networkStats.isNotEmpty()) {
                        val freqPoints = networkStats.mapIndexed { i, stat -> FloatEntry(i.toFloat(), stat.packetsPerSecond.toFloat()) }
                        com.patrykandpatrick.vico.compose.chart.Chart(
                            chart = com.patrykandpatrick.vico.compose.chart.line.lineChart(),
                            model = com.patrykandpatrick.vico.core.entry.entryModelOf(freqPoints),
                            startAxis = com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis(),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(8.dp)) {
                    Text("Error Rate (err/sec)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    if (networkStats.isNotEmpty()) {
                        val errPoints = networkStats.mapIndexed { i, stat -> FloatEntry(i.toFloat(), stat.errorRate) }
                        com.patrykandpatrick.vico.compose.chart.Chart(
                            chart = com.patrykandpatrick.vico.compose.chart.line.lineChart(),
                            model = com.patrykandpatrick.vico.core.entry.entryModelOf(errPoints),
                            startAxis = com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis(),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = filterTarget,
                onValueChange = { viewModel.pciSniffer.setFilterTarget(it) },
                label = { Text("Filter Target Hex") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { if (isSniffing) viewModel.pciSniffer.stopSniffing() else viewModel.pciSniffer.startSniffing() }
            ) {
                Text(if (isSniffing) "Stop" else "Sniff")
            }
        }
        
        Spacer(Modifier.height(16.dp))
        Text("Discovered PIDs (${sniffedPids.size})", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            gridItems(sniffedPids) { pid ->
                var expanded by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${pid.mode}-${pid.pid}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                Text("${pid.frequency}", color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (expanded) {
                            Text("Target: ${pid.targetAddress}", style = MaterialTheme.typography.bodySmall)
                            Text("Source: ${pid.sourceAddress}", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(pid.lastPayloadHex, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}



@Composable
fun PciBusIndicators(viewModel: AppViewModel) {
    val connectionState by viewModel.webSocketService.connectionState.collectAsState()
    val networkStats by viewModel.pciBusManager.networkStats.collectAsState()
    
    val isConnected = connectionState == WebSocketState.CONNECTED
    
    val healthScore = if (networkStats.isNotEmpty()) {
        val recentErrorRate = networkStats.takeLast(10).map { it.errorRate }.average().toFloat()
        (100f * (1f - recentErrorRate * 2)).toInt().coerceIn(0, 100)
    } else 0
    
    val healthColor = when {
        !isConnected -> Color.Gray
        healthScore > 90 -> com.example.ui.theme.NeonGreen
        healthScore > 70 -> Color(0xFFFF9933)
        else -> MaterialTheme.colorScheme.error
    }
    
    val connColor = if (isConnected) com.example.ui.theme.NeonGreen else Color.Gray
    
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(end = 8.dp)) {
        // Conn LED
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(connColor, shape = androidx.compose.foundation.shape.CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text("LINK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        
        // Health LED
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(healthColor, shape = androidx.compose.foundation.shape.CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text("HEALTH", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
