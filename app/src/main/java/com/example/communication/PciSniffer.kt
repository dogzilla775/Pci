package com.example.communication

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SniffedPid(
    val targetAddress: String,
    val sourceAddress: String,
    val mode: String,
    val pid: String,
    val frequency: Int,
    val lastPayloadHex: String
)

class PciSniffer(private val pciBusManager: PciBusManager) {
    private val _sniffedPids = MutableStateFlow<Map<String, SniffedPid>>(emptyMap())
    
    private val _sniffedPidsList = MutableStateFlow<List<SniffedPid>>(emptyList())
    val sniffedPids: StateFlow<List<SniffedPid>> = _sniffedPidsList.asStateFlow()

    private val _isSniffing = MutableStateFlow(false)
    val isSniffing: StateFlow<Boolean> = _isSniffing.asStateFlow()
    
    private val _filterTarget = MutableStateFlow<String>("")
    val filterTarget: StateFlow<String> = _filterTarget.asStateFlow()

    fun startSniffing() {
        _isSniffing.value = true
        _sniffedPids.value = emptyMap()
    }

    fun stopSniffing() {
        _isSniffing.value = false
    }
    
    fun setFilterTarget(targetHex: String) {
        _filterTarget.value = targetHex.uppercase()
    }

    suspend fun collectFrames() {
        pciBusManager.rawFrames.collect { frames ->
            if (frames.isNotEmpty() && _isSniffing.value) {
                analyzeFrame(frames.first())
            }
        }
    }

    private fun analyzeFrame(frame: PciFrame) {
        val targetStr = frame.targetAddress.toString(16).uppercase().padStart(2, '0')
        if (_filterTarget.value.isNotEmpty() && targetStr != _filterTarget.value) return

        if (frame.data.size >= 2) {
            val modeStr = frame.data[0].toString(16).uppercase().padStart(2, '0')
            val pidStr = frame.data[1].toString(16).uppercase().padStart(2, '0')
            val sourceStr = frame.sourceAddress.toString(16).uppercase().padStart(2, '0')
            
            val key = "$targetStr-$sourceStr-$modeStr-$pidStr"
            val payloadHex = frame.data.joinToString(" ") { it.toString(16).uppercase().padStart(2, '0') }
            
            _sniffedPids.update { current ->
                val existing = current[key]
                val updated = existing?.copy(
                    frequency = existing.frequency + 1,
                    lastPayloadHex = payloadHex
                ) ?: SniffedPid(targetStr, sourceStr, modeStr, pidStr, 1, payloadHex)
                
                val newMap = current + (key to updated)
                _sniffedPidsList.value = newMap.values.toList().sortedByDescending { it.frequency }
                newMap
            }
        }
    }
}
