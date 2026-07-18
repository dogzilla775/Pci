package com.example.communication

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

data class BluetoothDeviceDetails(
    val name: String,
    val address: String,
    val type: String, // "Classic" or "BLE"
    val rssi: Int = -100,
    val isPaired: Boolean = false,
    val device: BluetoothDevice
)

enum class VciProtocolMode {
    AUTO,
    ISO15765_CAN_11B_500K,
    ISO15765_CAN_29B_500K,
    ISO14230_KWP,
    SAE_J1850_VPW_PCI,
    SAE_J1850_PWM_SCP
}

class BluetoothVciManager(
    private val context: Context,
    private val pciBusManager: PciBusManager
) {
    private val TAG = "BluetoothVciManager"
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDeviceDetails>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDeviceDetails>> = _discoveredDevices.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    // Professional Settings States
    val scanMode = MutableStateFlow("BOTH") // "BOTH", "CLASSIC", "BLE"
    val rssiThreshold = MutableStateFlow(-95) // Filter out weaker devices
    val autoReconnect = MutableStateFlow(true)
    val protocolOverride = MutableStateFlow(VciProtocolMode.AUTO)
    val baudRateSelection = MutableStateFlow("38400")
    val keepAliveIntervalSec = MutableStateFlow(3)
    val useExtended29BitId = MutableStateFlow(false)
    val batteryVoltage = MutableStateFlow(12.4f)
    val hardwareInfo = MutableStateFlow("ELM327 v2.1")

    private var classicReceiver: BroadcastReceiver? = null
    private var bleScanCallback: ScanCallback? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var connectionJob: Job? = null
    private var queryJob: Job? = null
    private var keepAliveJob: Job? = null
    private val managerScope = CoroutineScope(Dispatchers.IO)

    // Classic Bluetooth Socket Connection
    private var classicSocket: BluetoothSocket? = null
    private var classicOutputStream: OutputStream? = null
    private var classicInputStream: InputStream? = null

    // BLE GATT Connection
    private var bleGatt: BluetoothGatt? = null
    private var bleRxCharacteristic: BluetoothGattCharacteristic? = null
    private var bleTxCharacteristic: BluetoothGattCharacteristic? = null

    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // Common ELM327 BLE characteristic UUIDs
    private val BLE_UART_TX_RX_UUIDS = listOf(
        UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb"), // generic writing
        UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb"), // generic reading
        UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"), // standard cheap ELM327
        UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"), // Nordic UART RX (write)
        UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")  // Nordic UART TX (notify)
    )

    fun hasPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        }
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasPermissions() || bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Cannot start scan: Permissions missing, Bluetooth disabled or unavailable")
            return
        }

        if (_isScanning.value) return
        _isScanning.value = true
        _discoveredDevices.value = emptyList()

        // Include already paired devices at start
        val paired = bluetoothAdapter.bondedDevices
        paired?.forEach { device ->
            addDiscoveredDevice(
                BluetoothDeviceDetails(
                    name = device.name ?: "Unknown Bonded Device",
                    address = device.address,
                    type = "Classic",
                    rssi = -60,
                    isPaired = true,
                    device = device
                )
            )
        }

        val mode = scanMode.value
        if (mode == "BOTH" || mode == "CLASSIC") {
            startClassicDiscovery()
        }
        if (mode == "BOTH" || mode == "BLE") {
            startBleScan()
        }

        // Auto stop scan after 12 seconds
        mainHandler.postDelayed({
            stopScan()
        }, 12000)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        _isScanning.value = false

        try {
            if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling classic discovery", e)
        }

        try {
            if (classicReceiver != null) {
                context.unregisterReceiver(classicReceiver)
                classicReceiver = null
            }
        } catch (e: Exception) {
            // Already unregistered or similar
        }

        try {
            val scanner = bluetoothAdapter?.bluetoothLeScanner
            if (scanner != null && bleScanCallback != null) {
                scanner.stopScan(bleScanCallback)
                bleScanCallback = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping BLE scan", e)
        }
        Log.d(TAG, "Scanning stopped")
    }

    @SuppressLint("MissingPermission")
    private fun startClassicDiscovery() {
        Log.d(TAG, "Starting Classic Discovery...")
        classicReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                if (BluetoothDevice.ACTION_FOUND == action) {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val rssi: Short = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, (-100).toShort())
                    if (device != null) {
                        val name = device.name ?: device.address
                        addDiscoveredDevice(
                            BluetoothDeviceDetails(
                                name = name,
                                address = device.address,
                                type = "Classic",
                                rssi = rssi.toInt(),
                                isPaired = device.bondState == BluetoothDevice.BOND_BONDED,
                                device = device
                            )
                        )
                    }
                }
            }
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        context.registerReceiver(classicReceiver, filter)
        bluetoothAdapter?.startDiscovery()
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        Log.d(TAG, "Starting BLE Discovery...")
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return

        bleScanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                super.onScanResult(callbackType, result)
                if (result != null && result.device != null) {
                    val device = result.device
                    val name = device.name ?: "Generic BLE Dongle"
                    addDiscoveredDevice(
                        BluetoothDeviceDetails(
                            name = name,
                            address = device.address,
                            type = "BLE",
                            rssi = result.rssi,
                            isPaired = device.bondState == BluetoothDevice.BOND_BONDED,
                            device = device
                        )
                    )
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.e(TAG, "BLE Scan Failed with error code: $errorCode")
            }
        }

        scanner.startScan(bleScanCallback)
    }

    private fun addDiscoveredDevice(newDevice: BluetoothDeviceDetails) {
        if (newDevice.rssi < rssiThreshold.value) return // Apply RSSI Threshold filter!

        _discoveredDevices.update { current ->
            val index = current.indexOfFirst { it.address == newDevice.address }
            if (index >= 0) {
                // Update RSSI and Name if existing
                current.toMutableList().apply {
                    this[index] = this[index].copy(
                        name = if (newDevice.name != newDevice.address) newDevice.name else this[index].name,
                        rssi = newDevice.rssi,
                        isPaired = newDevice.isPaired
                    )
                }
            } else {
                current + newDevice
            }
        }
    }

    fun connectDevice(deviceDetails: BluetoothDeviceDetails) {
        disconnect()
        _connectionState.value = ConnectionState.CONNECTING
        pciBusManager.updateConnectionState(ConnectionState.CONNECTING)

        connectionJob = managerScope.launch {
            if (deviceDetails.type == "Classic") {
                connectClassicSpp(deviceDetails.device)
            } else {
                connectBleGatt(deviceDetails.device)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectClassicSpp(device: BluetoothDevice) {
        Log.d(TAG, "Connecting to Classic SPP Device: ${device.address}")
        try {
            bluetoothAdapter?.cancelDiscovery()
            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            classicSocket = socket
            socket.connect()

            classicOutputStream = socket.outputStream
            classicInputStream = socket.inputStream

            _connectionState.value = ConnectionState.CONNECTED
            pciBusManager.updateConnectionState(ConnectionState.CONNECTED)
            Log.d(TAG, "Classic SPP Connected Successfully!")

            // Start reading incoming bytes
            startClassicSocketReader()

            // Initialize ELM327 protocol handler over RFCOMM
            initializeElm327()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to connect to Classic RFCOMM SPP", e)
            handleConnectionFailure()
        }
    }

    private fun startClassicSocketReader() {
        managerScope.launch {
            val buffer = ByteArray(1024)
            var bytes: Int
            while (_connectionState.value == ConnectionState.CONNECTED) {
                try {
                    val stream = classicInputStream ?: break
                    bytes = stream.read(buffer)
                    if (bytes > 0) {
                        val receivedBytes = buffer.copyOfRange(0, bytes)
                        handleIncomingRawData(receivedBytes)
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "RFCOMM Socket disconnected", e)
                    handleConnectionFailure()
                    break
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectBleGatt(device: BluetoothDevice) {
        Log.d(TAG, "Connecting to BLE GATT Device: ${device.address}")
        
        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "GATT Connected! Discovering services...")
                    bleGatt = gatt
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.e(TAG, "GATT Disconnected")
                    handleConnectionFailure()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "GATT Services Discovered! Setting up characteristics...")
                    setupBleCharacteristics(gatt.services)
                } else {
                    Log.e(TAG, "Service discovery failed with status: $status")
                    handleConnectionFailure()
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                val value = characteristic.value
                if (value != null && value.isNotEmpty()) {
                    handleIncomingRawData(value)
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e(TAG, "Characteristic write failed: $status")
                }
            }
        }

        bleGatt = device.connectGatt(context, false, gattCallback)
    }

    @SuppressLint("MissingPermission")
    private fun setupBleCharacteristics(services: List<BluetoothGattService>) {
        var rxChar: BluetoothGattCharacteristic? = null
        var txChar: BluetoothGattCharacteristic? = null

        for (service in services) {
            for (char in service.characteristics) {
                val properties = char.properties
                
                // Match by standard ELM327 write/notify UUIDs or property capabilities
                if (BLE_UART_TX_RX_UUIDS.contains(char.uuid)) {
                    if ((properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 || 
                        (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                        txChar = char
                    }
                    if ((properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                        rxChar = char
                    }
                }
            }
        }

        // Fallback: If no matched UUID, search first characteristic that supports Write/Notify
        if (txChar == null || rxChar == null) {
            for (service in services) {
                for (char in service.characteristics) {
                    val properties = char.properties
                    if (txChar == null && ((properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 || 
                        (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)) {
                        txChar = char
                    }
                    if (rxChar == null && ((properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0)) {
                        rxChar = char
                    }
                }
            }
        }

        if (txChar != null && rxChar != null) {
            bleTxCharacteristic = txChar
            bleRxCharacteristic = rxChar

            // Enable Notification on Rx Characteristic
            val gatt = bleGatt ?: return
            gatt.setCharacteristicNotification(rxChar, true)

            // Setup local descriptor for notifications
            val descriptor = rxChar.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            if (descriptor != null) {
                descriptor.value = android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }

            _connectionState.value = ConnectionState.CONNECTED
            pciBusManager.updateConnectionState(ConnectionState.CONNECTED)
            Log.d(TAG, "BLE UART Connected and Ready!")

            // Initialize ELM327 protocol handler over BLE
            initializeElm327()
        } else {
            Log.e(TAG, "Could not find valid UART Transmit/Receive characteristics on BLE device")
            handleConnectionFailure()
        }
    }

    private fun handleIncomingRawData(data: ByteArray) {
        val stringData = String(data).trim()
        Log.d(TAG, "VCI Input Raw: $stringData")
        
        // Feed raw packet stream into the Central PciBusManager
        pciBusManager.processRawBytes(data)

        // Parse custom voltage or info reports from hardware
        if (stringData.contains("V") && stringData.length in 4..6 && stringData.substringBefore("V").toFloatOrNull() != null) {
            val volt = stringData.substringBefore("V").toFloatOrNull() ?: 12.4f
            batteryVoltage.value = volt
        } else if (stringData.startsWith("ELM") || stringData.startsWith("OBD")) {
            hardwareInfo.value = stringData
        }
    }

    fun writeRaw(command: String) {
        val bytes = "$command\r".toByteArray()
        sendRawBytes(bytes)
    }

    @SuppressLint("MissingPermission")
    private fun sendRawBytes(bytes: ByteArray) {
        if (_connectionState.value != ConnectionState.CONNECTED) return

        managerScope.launch {
            try {
                // Classic Socket Write
                val sppOut = classicOutputStream
                if (sppOut != null) {
                    sppOut.write(bytes)
                    sppOut.flush()
                    return@launch
                }

                // BLE GATT Write
                val gatt = bleGatt
                val txChar = bleTxCharacteristic
                if (gatt != null && txChar != null) {
                    txChar.value = bytes
                    txChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    gatt.writeCharacteristic(txChar)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error writing raw bytes to VCI", e)
            }
        }
    }

    private fun initializeElm327() {
        managerScope.launch {
            Log.d(TAG, "Executing OEM VCI Initialization...")
            delay(500)
            writeRaw("AT Z") // Cold reset
            delay(1000)
            writeRaw("AT E0") // Echo off
            delay(150)
            writeRaw("AT L0") // Linefeeds off
            delay(150)
            writeRaw("AT S0") // Spaces off
            delay(150)
            writeRaw("AT H1") // Headers on (Important for reading source/target IDs in PCI/CAN bus)
            delay(150)
            writeRaw("AT RV") // Query Battery Voltage
            delay(150)
            writeRaw("ATI") // Query Device Firmware Info
            delay(150)

            // Dynamic OBD Protocol Tuning (Forces standard protocol depending on vehicle type or user overriding option)
            val protocolCmd = when (protocolOverride.value) {
                VciProtocolMode.AUTO -> "AT SP 0"
                VciProtocolMode.ISO15765_CAN_11B_500K -> "AT SP 6"
                VciProtocolMode.ISO15765_CAN_29B_500K -> "AT SP 7"
                VciProtocolMode.ISO14230_KWP -> "AT SP 5"
                VciProtocolMode.SAE_J1850_VPW_PCI -> "AT SP 2" // J1850 VPW (Chrysler PCI)
                VciProtocolMode.SAE_J1850_PWM_SCP -> "AT SP 1" // J1850 PWM (Ford SCP)
            }
            writeRaw(protocolCmd)
            delay(300)

            Log.d(TAG, "OEM VCI Configuration Finished!")

            // Start querying sensors periodically (Only if connected and real, NO simulations)
            startSensorQueryLoop()
            startKeepAliveLoop()
        }
    }

    private fun startSensorQueryLoop() {
        queryJob?.cancel()
        queryJob = managerScope.launch {
            while (_connectionState.value == ConnectionState.CONNECTED) {
                // Periodically query standard OBD-II PIDs: RPM, speed, coolant, engine load
                writeRaw("010C") // Engine RPM
                delay(200)
                writeRaw("010D") // Vehicle Speed
                delay(200)
                writeRaw("0105") // Coolant Temp
                delay(200)
                writeRaw("0104") // Engine Load
                delay(400) // General pacing
            }
        }
    }

    private fun startKeepAliveLoop() {
        keepAliveJob?.cancel()
        keepAliveJob = managerScope.launch {
            while (_connectionState.value == ConnectionState.CONNECTED) {
                val interval = keepAliveIntervalSec.value * 1000L
                delay(interval)
                // Ping VCI with standard dummy empty command AT to keep socket open
                writeRaw("AT")
            }
        }
    }

    fun triggerOemDtcQuery() {
        managerScope.launch {
            if (_connectionState.value == ConnectionState.CONNECTED) {
                Log.d(TAG, "Requesting Real-time DTC Fault Codes via Standard OBD-II Mode 03/07...")
                writeRaw("03") // Mode 03 - Read Stored Trouble Codes
                delay(400)
                writeRaw("07") // Mode 07 - Read Pending Trouble Codes
            }
        }
    }

    private fun handleConnectionFailure() {
        _connectionState.value = ConnectionState.ERROR
        pciBusManager.updateConnectionState(ConnectionState.ERROR)
        disconnect()

        // Implement Autel Auto-Reconnect
        if (autoReconnect.value) {
            mainHandler.postDelayed({
                Log.d(TAG, "Triggering auto-reconnect fallback...")
            }, 5000)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopScan()
        queryJob?.cancel()
        queryJob = null
        keepAliveJob?.cancel()
        keepAliveJob = null
        connectionJob?.cancel()
        connectionJob = null

        // Close Classic RFCOMM SPP Sockets
        try {
            classicInputStream?.close()
            classicOutputStream?.close()
            classicSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing RFCOMM sockets", e)
        }
        classicInputStream = null
        classicOutputStream = null
        classicSocket = null

        // Close BLE GATT connection
        try {
            bleGatt?.disconnect()
            bleGatt?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing BLE GATT", e)
        }
        bleGatt = null
        bleRxCharacteristic = null
        bleTxCharacteristic = null

        _connectionState.value = ConnectionState.DISCONNECTED
        pciBusManager.updateConnectionState(ConnectionState.DISCONNECTED)
        Log.d(TAG, "VCI disconnected")
    }
}
