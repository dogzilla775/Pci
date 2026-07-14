package com.example.communication

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Represents a raw CAN frame from the Ford MS-CAN or HS-CAN Bus.
 */
data class CanFrame(
    val bus: String, // "HS-CAN" or "MS-CAN"
    val id: Int,
    val length: Int,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CanFrame
        if (bus != other.bus) return false
        if (id != other.id) return false
        if (length != other.length) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = bus.hashCode()
        result = 31 * result + id
        result = 31 * result + length
        result = 31 * result + data.contentHashCode()
        return result
    }
}

class FordCanProtocolHandler(private val busManager: PciBusManager) {
    
    private val _canFrames = MutableStateFlow<List<CanFrame>>(emptyList())
    val canFrames: StateFlow<List<CanFrame>> = _canFrames.asStateFlow()

    fun processCanFrame(bus: String, id: Int, data: ByteArray) {
        val frame = CanFrame(bus, id, data.size, data)
        val currentFrames = _canFrames.value.toMutableList()
        currentFrames.add(0, frame)
        if (currentFrames.size > 100) currentFrames.removeLast()
        _canFrames.value = currentFrames

        // Handle specific Ford CAN IDs
        when (id) {
            0x201 -> { // Engine RPM (example)
                if (data.size >= 2) {
                    val rpm = ((data[0].toUByte().toInt() * 256) + data[1].toUByte().toInt()) / 4f
                    busManager.updateFordMetrics(rpm = rpm)
                }
            }
            0x202 -> { // Vehicle Speed (example)
                if (data.size >= 2) {
                    val speed = ((data[0].toUByte().toInt() * 256) + data[1].toUByte().toInt()) * 0.01f
                    busManager.updateFordMetrics(speed = speed)
                }
            }
            0x420 -> { // Coolant Temp (example)
                if (data.size >= 1) {
                    val coolant = data[0].toUByte().toFloat() - 40f
                    busManager.updateFordMetrics(coolantTemp = coolant)
                }
            }
            0x421 -> { // Transmission Temp (example)
                if (data.size >= 1) {
                    val transTemp = data[0].toUByte().toFloat() - 40f
                    busManager.updateFordMetrics(transTemp = transTemp)
                }
            }
        }
    }
}
