package com.example.communication

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Represents a raw frame from the Chrysler PCI Bus (typically J1850 VPW 10.4 kbps).
 */
data class PciFrame(
    val targetAddress: UByte,
    val sourceAddress: UByte,
    val length: UByte,
    val data: ByteArray,
    val crc: UByte
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PciFrame
        if (targetAddress != other.targetAddress) return false
        if (sourceAddress != other.sourceAddress) return false
        if (length != other.length) return false
        if (!data.contentEquals(other.data)) return false
        if (crc != other.crc) return false
        return true
    }

    override fun hashCode(): Int {
        var result = targetAddress.hashCode()
        result = 31 * result + sourceAddress.hashCode()
        result = 31 * result + length.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + crc.hashCode()
        return result
    }
}

/**
 * Utility for decoding diagnostic byte arrays into human-readable sensor values.
 */
object ObdDecoder {
    /**
     * Decodes standard OBD-II Service 01 (Current Data) responses.
     * Returns null if payload is invalid or the PID is unsupported here.
     */
    fun decodeService01(pid: Int, payload: ByteArray): Float? {
        if (payload.isEmpty()) return null
        return when (pid) {
            0x04 -> payload[0].toUByte().toFloat() * 100f / 255f // Calculated Engine Load (%)
            0x05 -> payload[0].toUByte().toFloat() - 40f         // Engine Coolant Temperature (°C)
            0x0C -> {
                if (payload.size >= 2) {
                    ((payload[0].toUByte().toInt() * 256) + payload[1].toUByte().toInt()) / 4f // Engine RPM
                } else null
            }
            0x0D -> payload[0].toUByte().toFloat()               // Vehicle Speed (km/h)
            0x0F -> payload[0].toUByte().toFloat() - 40f         // Intake Air Temperature (°C)
            0x11 -> payload[0].toUByte().toFloat() * 100f / 255f // Throttle Position (%)
            else -> null
        }
    }

    /**
     * Decodes Chrysler-specific enhanced PIDs.
     */
    fun decodeChryslerSpecific(pid: Int, payload: ByteArray): Float? {
        if (payload.isEmpty()) return null
        return when (pid) {
            // Chrysler PCI bus specific decoding logic (e.g., Transmission Temperature)
            0x22 -> payload[0].toUByte().toFloat() - 40f         // Trans Temp
            0x23 -> payload[0].toUByte().toFloat() * 0.1f        // Battery Voltage
            else -> null
        }
    }
}

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

data class ObdMetrics(
    val rpm: Float = 0f,
    val engineLoad: Float = 0f,
    val coolantTemp: Float = 0f,
    val transTemp: Float = 0f,
    val speed: Float = 0f
)

data class NetworkStat(
    val timestamp: Long,
    val packetsPerSecond: Int,
    val errorRate: Float
)

/**
 * Central service handling I/O with the physical hardware interface.
 */
class PciBusManager {
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _metrics = MutableStateFlow(ObdMetrics())
    val metrics: StateFlow<ObdMetrics> = _metrics.asStateFlow()

    private val _rawFrames = MutableStateFlow<List<PciFrame>>(emptyList())
    val rawFrames: StateFlow<List<PciFrame>> = _rawFrames.asStateFlow()
    
    private val _networkStats = MutableStateFlow<List<NetworkStat>>(emptyList())
    val networkStats: StateFlow<List<NetworkStat>> = _networkStats.asStateFlow()
    
    private var currentSecondPackets = 0
    private var currentSecondErrors = 0

    fun updateConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }

    fun connect(deviceAddress: String) {
        _connectionState.value = ConnectionState.CONNECTING
        // TODO: Initiate Bluetooth (e.g., SPP) or USB socket to hardware scanner adapter (ELM327 / STN1110)
        _connectionState.value = ConnectionState.CONNECTED
    }

    fun disconnect() {
        // TODO: Close socket
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    /**
     * Converts a raw incoming byte stream from the hardware into structured data.
     */
    fun processRawBytes(rawStream: ByteArray) {
        currentSecondPackets++
        if (rawStream.size >= 4) {
             val target = rawStream[0].toUByte()
             val source = rawStream[1].toUByte()
             val length = rawStream.size.toUByte()
             val payload = rawStream.sliceArray(3 until rawStream.size - 1)
             val crc = rawStream.last().toUByte()
             val frame = PciFrame(target, source, length, payload, crc)
             
             val currentFrames = _rawFrames.value.toMutableList()
             currentFrames.add(0, frame)
             if (currentFrames.size > 100) currentFrames.removeLast()
             _rawFrames.value = currentFrames

             handleFrame(frame)
        } else {
             currentSecondErrors++
        }
    }
    
    fun tickNetworkStats() {
        val stat = NetworkStat(
            timestamp = System.currentTimeMillis(),
            packetsPerSecond = currentSecondPackets,
            errorRate = if (currentSecondPackets > 0) currentSecondErrors.toFloat() / currentSecondPackets else 0f
        )
        _networkStats.update { current ->
            (current + stat).takeLast(60) // Keep last 60 seconds
        }
        currentSecondPackets = 0
        currentSecondErrors = 0
    }

    private fun handleFrame(frame: PciFrame) {
        if (frame.data.isEmpty()) return

        val mode = frame.data[0].toInt()
        if (frame.data.size > 1) {
            val pid = frame.data[1].toInt()
            val payload = frame.data.sliceArray(2 until frame.data.size)
            
            // Route to appropriate decoder based on response mode
            val value = when (mode) {
                0x41 -> ObdDecoder.decodeService01(pid, payload) // 0x41 is response to 0x01 (Current Data)
                0x62 -> ObdDecoder.decodeChryslerSpecific(pid, payload) // 0x62 is response to 0x22 (Enhanced)
                else -> null
            }

            if (value != null) {
                updateMetrics(pid, value)
            }
        }
    }

    fun updateFordMetrics(rpm: Float? = null, engineLoad: Float? = null, coolantTemp: Float? = null, transTemp: Float? = null, speed: Float? = null) {
        val current = _metrics.value
        _metrics.value = current.copy(
            rpm = rpm ?: current.rpm,
            engineLoad = engineLoad ?: current.engineLoad,
            coolantTemp = coolantTemp ?: current.coolantTemp,
            transTemp = transTemp ?: current.transTemp,
            speed = speed ?: current.speed
        )
    }

    private fun updateMetrics(pid: Int, value: Float) {
        val current = _metrics.value
        _metrics.value = when (pid) {
            0x0C -> current.copy(rpm = value)
            0x04 -> current.copy(engineLoad = value)
            0x05 -> current.copy(coolantTemp = value)
            0x0D -> current.copy(speed = value)
            0x22 -> current.copy(transTemp = value)
            else -> current
        }
    }
}
