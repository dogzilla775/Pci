package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.AppViewModel
import com.example.ui.theme.NeonCyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(viewModel: AppViewModel) {
    var selectedTab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Vehicle Diagnostics", style = MaterialTheme.typography.titleLarge, color = NeonCyan)
        Spacer(Modifier.height(16.dp))

        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Auto-Scan") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("AI Assistant") })
        }

        Spacer(Modifier.height(16.dp))

        if (selectedTab == 0) {
            AutoScanTab(viewModel)
        } else {
            AiAssistantTab(viewModel)
        }
    }
}

@Composable
fun AutoScanTab(viewModel: AppViewModel) {
    var isScanning by remember { mutableStateOf(false) }
    var scanComplete by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()
    val currentVehicle by viewModel.selectedVehicle.collectAsState()
    val isFord = currentVehicle.contains("Ford")
    
    // Simulated mock DTCs removed per security policy
    val scanText = if (isFord) "Querying PCM, APIM, BCM, ABS, RCM, IPC..." else "Querying ECM, TCM, BCM, ABS, SRS..."

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isScanning && !scanComplete) {
            Button(
                onClick = {
                    isScanning = true
                    scope.launch {
                        isScanning = false
                        scanComplete = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Start Full System Auto-Scan", style = MaterialTheme.typography.titleMedium)
            }
        }

        if (isScanning) {
            Text("Scanning Modules...", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(progress = { scanProgress }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Text(scanText, style = MaterialTheme.typography.bodySmall)
        }

        if (scanComplete) {
            Text("Scan Failed: Hardware Adapter Not Connected", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { scanComplete = false; scanProgress = 0f }, modifier = Modifier.fillMaxWidth()) {
                Text("Retry")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantTab(viewModel: AppViewModel) {
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val metrics by viewModel.pciBusManager.metrics.collectAsState()
    var isLoading by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Powered by Gemini", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), reverseLayout = true) {
            items(messages.reversed()) { msg ->
                ChatBubble(msg)
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Describe symptoms...") },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = NeonCyan,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (input.isNotBlank() && !isLoading) {
                        val prompt = input
                        input = ""
                        messages.add(ChatMessage(prompt, true))
                        isLoading = true
                        scope.launch {
                            val dataStr = "RPM: ${metrics.rpm}, Coolant: ${metrics.coolantTemp}C, Trans: ${metrics.transTemp}C, Load: ${metrics.engineLoad}%"
                            val response = viewModel.geminiChatbot.askQuestion(dataStr, prompt)
                            messages.add(ChatMessage(response, false))
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.background(NeonCyan, RoundedCornerShape(50))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.Black)
                }
            }
        }
    }
}

data class ChatMessage(val text: String, val isUser: Boolean)

@Composable
fun ChatBubble(msg: ChatMessage) {
    val alignment = if (msg.isUser) Alignment.End else Alignment.Start
    val color = if (msg.isUser) NeonCyan.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (msg.isUser) NeonCyan else MaterialTheme.colorScheme.onSurface

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = alignment) {
        Box(
            modifier = Modifier
                .background(color, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Text(msg.text, color = textColor)
        }
    }
}
