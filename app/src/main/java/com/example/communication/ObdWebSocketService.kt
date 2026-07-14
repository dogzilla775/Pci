package com.example.communication

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

enum class WebSocketState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

class ObdWebSocketService(
    private val pciBusManager: PciBusManager
) : WebSocketListener() {

    private val client = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    private val _connectionState = MutableStateFlow(WebSocketState.DISCONNECTED)
    val connectionState: StateFlow<WebSocketState> = _connectionState.asStateFlow()

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    fun connect(url: String) {
        if (_connectionState.value == WebSocketState.CONNECTING || _connectionState.value == WebSocketState.CONNECTED) {
            return
        }
        
        _connectionState.value = WebSocketState.CONNECTING
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, this)
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = WebSocketState.DISCONNECTED
    }

    fun sendCommand(command: String) {
        webSocket?.send("$command\r")
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        super.onOpen(webSocket, response)
        _connectionState.value = WebSocketState.CONNECTED
        Log.d(TAG, "WebSocket connected")
        
        // Initialize ELM327 adapter for J1850 VPW (Chrysler PCI Bus)
        serviceScope.launch {
            Log.d(TAG, "Sending ELM327 init sequence...")
            
            sendCommand("AT Z") // Reset
            delay(500)
            
            sendCommand("AT E0") // Echo off
            delay(100)
            
            sendCommand("AT L0") // Linefeeds off
            delay(100)
            
            sendCommand("AT S0") // Spaces off
            delay(100)
            
            sendCommand("AT H1") // Headers on (Important for raw PCI Bus frames)
            delay(100)
            
            sendCommand("AT SP 2") // Set Protocol to 2: SAE J1850 VPW (10.4 kbaud) - used in '07 Town & Country
            delay(500)
            
            Log.d(TAG, "Initialization sequence complete")
        }
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        super.onMessage(webSocket, text)
        Log.d(TAG, "Received message: $text")
        
        // ELM327 often sends prompt '>' when ready, remove it for processing
        val cleanedText = text.replace(">", "").trim()
        if (cleanedText.isNotEmpty()) {
            val bytes = hexStringToByteArray(cleanedText)
            if (bytes.isNotEmpty()) {
                pciBusManager.processRawBytes(bytes)
            }
        }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        super.onMessage(webSocket, bytes)
        Log.d(TAG, "Received bytes: ${bytes.hex()}")
        pciBusManager.processRawBytes(bytes.toByteArray())
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosing(webSocket, code, reason)
        Log.d(TAG, "WebSocket closing: $reason")
        webSocket.close(1000, null)
        _connectionState.value = WebSocketState.DISCONNECTED
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        super.onFailure(webSocket, t, response)
        Log.e(TAG, "WebSocket error: ${t.message}")
        _connectionState.value = WebSocketState.ERROR
        this.webSocket = null
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        if (len % 2 != 0 || len == 0) return ByteArray(0)
        
        return try {
            val data = ByteArray(len / 2)
            for (i in 0 until len step 2) {
                data[i / 2] = ((Character.digit(s[i], 16) shl 4) +
                        Character.digit(s[i + 1], 16)).toByte()
            }
            data
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing hex string: $s", e)
            ByteArray(0)
        }
    }

    companion object {
        private const val TAG = "ObdWebSocketService"
    }
}
