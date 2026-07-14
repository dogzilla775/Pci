package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SettingsBluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.AppViewModel
import com.example.ui.theme.NeonCyan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    var wsUrl by remember { mutableStateOf("ws://192.168.0.10:35000") }
    val currentVehicle by viewModel.selectedVehicle.collectAsState()
    val currentAdapter by viewModel.selectedAdapter.collectAsState()
    
    var adapterMenuExpanded by remember { mutableStateOf(false) }
    
    val adapters = listOf(
        "ELM327 (Standard)",
        "ELM327 with MS-CAN Switch (Ford/Mazda)",
        "J2534 Pass-Thru (Professional)"
    )

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Settings & Configuration", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        item {
            Text("Hardware Adapter", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            
            ExposedDropdownMenuBox(
                expanded = adapterMenuExpanded,
                onExpandedChange = { adapterMenuExpanded = !adapterMenuExpanded }
            ) {
                OutlinedTextField(
                    value = currentAdapter,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Diagnostic Adapter") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = adapterMenuExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                
                ExposedDropdownMenu(
                    expanded = adapterMenuExpanded,
                    onDismissRequest = { adapterMenuExpanded = false }
                ) {
                    adapters.forEach { adapter ->
                        DropdownMenuItem(
                            text = { Text(adapter) },
                            onClick = {
                                viewModel.selectedAdapter.value = adapter
                                adapterMenuExpanded = false
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Adapter Capabilities:", style = MaterialTheme.typography.labelLarge, color = NeonCyan)
                    Spacer(modifier = Modifier.height(4.dp))
                    when (currentAdapter) {
                        "ELM327 (Standard)" -> {
                            Text("• Basic OBD-II Support", style = MaterialTheme.typography.bodyMedium)
                            Text("• Single CAN Bus (HS-CAN)", style = MaterialTheme.typography.bodyMedium)
                            Text("• J1850 VPW (Chrysler PCI)", style = MaterialTheme.typography.bodyMedium)
                            Text("• NO Medium Speed CAN support", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        }
                        "ELM327 with MS-CAN Switch (Ford/Mazda)" -> {
                            Text("• Basic OBD-II Support", style = MaterialTheme.typography.bodyMedium)
                            Text("• HS-CAN Support (Pins 6, 14)", style = MaterialTheme.typography.bodyMedium)
                            Text("• MS-CAN Support via toggle switch (Pins 3, 11)", style = MaterialTheme.typography.bodyMedium)
                            Text("• J1850 VPW (Chrysler PCI)", style = MaterialTheme.typography.bodyMedium)
                        }
                        "J2534 Pass-Thru (Professional)" -> {
                            Text("• Full OEM Level Capabilities", style = MaterialTheme.typography.bodyMedium)
                            Text("• Simultaneous Multi-CAN Network routing", style = MaterialTheme.typography.bodyMedium)
                            Text("• Module Programming & Firmware Flashing", style = MaterialTheme.typography.bodyMedium)
                            Text("• Supports all UDS Actuator Tests", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = wsUrl,
                onValueChange = { wsUrl = it },
                label = { Text("WebSocket / IP Address") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { viewModel.webSocketService.connect(wsUrl) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.SettingsBluetooth, contentDescription = "Connect")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connect to Adapter")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        item {
            Text("Active Vehicle Profile", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = "Info")
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Current Vehicle:", style = MaterialTheme.typography.labelMedium)
                        Text(currentVehicle, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        if (currentVehicle.contains("Ford")) {
                            Text("Protocol: HS-CAN / MS-CAN (UDS)", style = MaterialTheme.typography.bodySmall)
                            Text("Requires: MS-CAN switch or J2534 to access APIM/BCM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Protocol: SAE J1850 VPW (PCI Bus)", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
