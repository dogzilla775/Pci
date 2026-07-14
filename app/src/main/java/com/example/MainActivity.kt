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
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.room.Room
import com.example.db.ObdDatabase
import com.example.db.ObdLog
import com.patrykandpatrick.vico.core.entry.FloatEntry

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val selectedVehicle = kotlinx.coroutines.flow.MutableStateFlow("Chrysler 2007 Town & Country")
    val selectedAdapter = kotlinx.coroutines.flow.MutableStateFlow("ELM327 (Standard)")
    val pciBusManager = PciBusManager()
    val fordCanProtocolHandler = com.example.communication.FordCanProtocolHandler(pciBusManager)
    val webSocketService = ObdWebSocketService(pciBusManager)
    val pciSniffer = PciSniffer(pciBusManager)
    val diagnosticMonitor = DiagnosticMonitorService(pciBusManager)
    val geminiChatbot = GeminiChatbot()
    val oemScraper = com.example.communication.OemManualScraper()

    private val db = Room.databaseBuilder(
        application,
        ObdDatabase::class.java, "obd-database"
    ).build()

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
    val currentVehicle by viewModel.selectedVehicle.collectAsState()
    
    val connectionState by viewModel.webSocketService.connectionState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ProDiag Elite") },
                actions = {
                    Box {
                        TextButton(onClick = { vehicleMenuExpanded = true }) {
                            Text(currentVehicle.take(15) + "...", color = MaterialTheme.colorScheme.onSurface)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Vehicle", tint = MaterialTheme.colorScheme.onSurface)
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
                    PciBusIndicators(viewModel)
                    IconButton(onClick = { selectedTab = 4 }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Speed, contentDescription = "Dashboard") },
                    label = { Text("Dash") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Timeline, contentDescription = "Scope") },
                    label = { Text("Scope") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI") },
                    label = { Text("AI Diag") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Build, contentDescription = "Service") },
                    label = { Text("Service") },
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Search, contentDescription = "Sniffer") },
                    label = { Text("Sniffer") },
                    selected = selectedTab == 5,
                    onClick = { selectedTab = 5 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.MenuBook, contentDescription = "Manuals") },
                    label = { Text("Manuals") },
                    selected = selectedTab == 6,
                    onClick = { selectedTab = 6 }
                )
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
                            text = "Hardware Adapter Disconnected. Please connect in Settings.",
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
            DiagnosticLogTab()
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
fun DiagnosticLogTab() {
    val errorLogs = emptyList<Pair<String, String>>()
    
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Captured Error Frames & Troubleshooting", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 300.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            gridItems(errorLogs) { (errorTitle, fix) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(errorTitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.height(8.dp))
                        Text("Suggested Fix:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(fix, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
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
