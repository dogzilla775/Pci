package com.example

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.communication.PdfReportGenerator
import com.example.db.BusDiagnosticLog
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonGreen
import com.example.ui.theme.NeonPurple
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticLogTab(viewModel: AppViewModel) {
    val context = LocalContext.current
    val logs by viewModel.filteredLogs.collectAsState()
    
    val selectedBus by viewModel.logFilterBus.collectAsState()
    val selectedSeverity by viewModel.logFilterSeverity.collectAsState()
    val searchQuery by viewModel.logSearchQuery.collectAsState()
    val recordLiveTraffic by viewModel.logAllTraffic.collectAsState()
    
    var selectedLogDetail by remember { mutableStateOf<BusDiagnosticLog?>(null) }
    
    val sdf = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // --- 1. Autel Elite Diagnostic Control Bar ---
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Row 1: Search & Live Capture Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.logSearchQuery.value = it },
                        placeholder = { Text("Search by Hex ID, Data or Component...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Live Capture Switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.background(
                            color = if (recordLiveTraffic) NeonGreen.copy(alpha = 0.15f) else Color.Transparent,
                            shape = RoundedCornerShape(20.dp)
                        ).border(
                            width = 1.dp,
                            color = if (recordLiveTraffic) NeonGreen.copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(20.dp)
                        ).padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "REC TRAFFIC",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (recordLiveTraffic) NeonGreen else Color.Gray
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = recordLiveTraffic,
                            onCheckedChange = { viewModel.logAllTraffic.value = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NeonGreen,
                                checkedTrackColor = NeonGreen.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Row 2: Selectable filters for Bus and Severity
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Bus Filters (ALL, CAN, PCI)
                    Text(
                        "BUS:", 
                        style = MaterialTheme.typography.labelMedium, 
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterVertically).padding(end = 4.dp),
                        color = Color.Gray
                    )
                    listOf("ALL", "CAN", "PCI").forEach { bus ->
                        val isSelected = selectedBus == bus
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.logFilterBus.value = bus },
                            label = { Text(bus) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                                selectedLabelColor = NeonCyan,
                                selectedLeadingIconColor = NeonCyan
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // Severity Filters (ALL, INFO, WARNING, CRITICAL)
                    Text(
                        "SEV:", 
                        style = MaterialTheme.typography.labelMedium, 
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterVertically).padding(end = 4.dp),
                        color = Color.Gray
                    )
                    listOf("ALL", "INFO", "WARNING", "CRITICAL").forEach { sev ->
                        val isSelected = selectedSeverity == sev
                        val activeColor = when (sev) {
                            "CRITICAL" -> Color.Red
                            "WARNING" -> Color(0xFFFF9933)
                            else -> NeonCyan
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.logFilterSeverity.value = sev },
                            label = { Text(sev) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = activeColor.copy(alpha = 0.15f),
                                selectedLabelColor = activeColor
                            )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Row 3: Command Actions (Fault Simulator, Export, Clear)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.triggerDtcQuery()
                            Toast.makeText(context, "Mode 03 OBD-II DTC query command sent over physical VCI", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text("Read Vehicle DTCs", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    
                    Button(
                        onClick = {
                            if (logs.isEmpty()) {
                                Toast.makeText(context, "No logs available to export.", Toast.LENGTH_SHORT).show()
                            } else {
                                val recentDtcList = logs.filter { it.isError }.map { "${it.busType} Fault: ${it.description}" }
                                val liveMetrics = "Logs Total: ${logs.size}\nErrors: ${logs.count { it.isError }}\nCaptured: ${recordLiveTraffic}"
                                val reportFile = PdfReportGenerator.generateModuleReport(
                                    context = context,
                                    moduleName = "Active Bus Diagnostics",
                                    vehicleName = viewModel.selectedVehicle.value,
                                    dtcs = recentDtcList,
                                    liveData = liveMetrics,
                                    repairProcedures = logs.firstOrNull { it.isError }?.troubleshooting ?: "Continuous bus monitoring successful. No faults active."
                                )
                                if (reportFile != null) {
                                    Toast.makeText(context, "Report saved: ${reportFile.name}", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Failed to write PDF report.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Export PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = { viewModel.clearBusLogs() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = borderStrokeHelper(),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.LightGray)
                        Spacer(Modifier.width(4.dp))
                        Text("Clear Logs", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
                    }
                }
            }
        }
        
        // --- 2. Live Rolling Log Board ---
        Text(
            text = "PERSISTENT LOG ARCHIVE (${logs.size} Frames)", 
            style = MaterialTheme.typography.labelSmall, 
            color = Color.Gray,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No messages in archive.", color = Color.Gray)
                    Text("Enable REC TRAFFIC or trigger the simulator.", style = MaterialTheme.typography.bodySmall, color = Color.Gray.copy(alpha = 0.8f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    LogCard(log = log, sdf = sdf, onSelect = { selectedLogDetail = log })
                }
            }
        }
    }
    
    // --- 3. Autel OEM Step-by-Step Diagnostic troubleshooting dialog ---
    selectedLogDetail?.let { log ->
        AlertDialog(
            onDismissRequest = { selectedLogDetail = null },
            icon = {
                Icon(
                    imageVector = if (log.isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (log.isError) {
                        if (log.severity == "CRITICAL") Color.Red else Color(0xFFFF9933)
                    } else NeonGreen,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = if (log.isError) "Diagnostic Trouble Code (DTC)" else "Message ID Detail",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = log.description, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("BUS PROTOCOL", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text(log.busType, fontWeight = FontWeight.Bold, color = if (log.busType == "CAN") NeonCyan else NeonPurple)
                        }
                        Column {
                            Text("SEVERITY", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text(
                                log.severity, 
                                fontWeight = FontWeight.Bold, 
                                color = when(log.severity) {
                                    "CRITICAL" -> Color.Red
                                    "WARNING" -> Color(0xFFFF9933)
                                    else -> NeonGreen
                                }
                            )
                        }
                        Column {
                            Text("ID", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text(log.messageId, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("SOURCE ADDRESS", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text(log.sourceAddress, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Column {
                            Text("TARGET ADDRESS", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text(log.targetAddress, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(modifier = Modifier.width(40.dp))
                    }
                    
                    Column {
                        Text("DATA PAYLOAD (HEX)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Text(
                            text = log.payloadHex,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            fontSize = 14.sp
                        )
                    }
                    
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
                    
                    Text("AUTEL OEM GUIDES & REPAIR FLOWCHART", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = NeonCyan)
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = borderStrokeHelper(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            log.troubleshooting.split("\n").forEach { line ->
                                Text(line, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedLogDetail = null },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black)
                ) {
                    Text("Acknowledge Diagnostic")
                }
            }
        )
    }
}

@Composable
fun LogCard(log: BusDiagnosticLog, sdf: SimpleDateFormat, onSelect: () -> Unit) {
    val dateStr = remember(log.timestamp) { sdf.format(Date(log.timestamp)) }
    
    val cardBorderColor = when {
        log.isError && log.severity == "CRITICAL" -> Color.Red
        log.isError -> Color(0xFFFF9933)
        log.busType == "CAN" -> NeonCyan.copy(alpha = 0.4f)
        else -> NeonPurple.copy(alpha = 0.4f)
    }
    
    val indicatorColor = when {
        log.isError && log.severity == "CRITICAL" -> Color.Red
        log.isError -> Color(0xFFFF9933)
        log.busType == "CAN" -> NeonCyan
        else -> NeonPurple
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (log.isError) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            }
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, cardBorderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left margin status indicator
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .background(indicatorColor, shape = RoundedCornerShape(2.dp))
            )
            
            Spacer(modifier = Modifier.width(10.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                // Time & Bus & ID header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = dateStr, 
                            style = MaterialTheme.typography.labelSmall, 
                            fontFamily = FontFamily.Monospace,
                            color = Color.LightGray
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(indicatorColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = log.busType, 
                                style = MaterialTheme.typography.labelSmall, 
                                color = indicatorColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp
                            )
                        }
                    }
                    Text(
                        text = "ID: ${log.messageId}", 
                        style = MaterialTheme.typography.labelMedium, 
                        fontFamily = FontFamily.Monospace, 
                        fontWeight = FontWeight.Bold,
                        color = indicatorColor
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Description
                Text(
                    text = log.description, 
                    style = MaterialTheme.typography.bodySmall, 
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                // Payload Hex
                Row {
                    Text(
                        text = "DATA: ", 
                        style = MaterialTheme.typography.bodySmall, 
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                    Text(
                        text = log.payloadHex, 
                        style = MaterialTheme.typography.bodySmall, 
                        color = if (log.isError) Color.White else NeonCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(6.dp))
            
            // Expand Chevron
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun borderStrokeHelper() = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
