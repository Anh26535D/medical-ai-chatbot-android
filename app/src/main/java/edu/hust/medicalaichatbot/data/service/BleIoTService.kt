package edu.hust.medicalaichatbot.data.service

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import com.espressif.provisioning.DeviceConnectionEvent
import com.espressif.provisioning.ESPConstants
import com.espressif.provisioning.ESPDevice
import com.espressif.provisioning.ESPProvisionManager
import com.espressif.provisioning.listeners.ProvisionListener
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

@SuppressLint("MissingPermission")
class BleIoTService(private val context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = bluetoothManager.adapter
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
    
    private var currentEspDevice: ESPDevice? = null
    private var scanCallback: ScanCallback? = null
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val scannedDevices = _scannedDevices.asStateFlow()

    private val _connectionState = MutableStateFlow(BluetoothProfile.STATE_DISCONNECTED)
    val connectionState = _connectionState.asStateFlow()

    private val _discoveredAddress = MutableStateFlow<String?>(null)
    val discoveredAddress = _discoveredAddress.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName = _connectedDeviceName.asStateFlow()

    private val _provisioningStatus = MutableStateFlow<String?>(null)
    val provisioningStatus = _provisioningStatus.asStateFlow()

    // Store dynamic Service UUID from advertisement packets to support multiple devices in production
    private val deviceServiceUuids = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "BleIoTService"
        private const val DEVICE_PREFIX = "Prov_"
        private const val DEFAULT_POP = "12345678"
    }

    init {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    fun startScanning() {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "Bluetooth adapter not available or disabled")
            return
        }

        if (_isScanning.value) {
            Log.w(TAG, "Already scanning, ignoring request")
            return
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "BluetoothLeScanner not available")
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        _scannedDevices.value = emptyList()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val deviceName = result.device.name
                Log.d(TAG, "Scanned device: $deviceName [${result.device.address}]")
                
                if (deviceName != null && deviceName.startsWith(DEVICE_PREFIX)) {
                    // Extract advertised Service UUID from ScanRecord
                    val serviceUuids = result.scanRecord?.serviceUuids
                    serviceUuids?.firstOrNull()?.let { parcelUuid ->
                        val uuidStr = parcelUuid.uuid.toString()
                        deviceServiceUuids[result.device.address] = uuidStr
                        Log.i(TAG, "Extracted advertised Service UUID: $uuidStr for device ${result.device.address}")
                    }

                    val currentList = _scannedDevices.value.toMutableList()
                    if (currentList.none { it.address == result.device.address }) {
                        Log.i(TAG, "Found matching IoT device: $deviceName")
                        currentList.add(result.device)
                        _scannedDevices.value = currentList
                    }
                }
            }
            
            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed with error: $errorCode")
                _isScanning.value = false
            }
        }
        
        Log.i(TAG, "Starting scan for IoT device...")
        _isScanning.value = true
        scanner.startScan(null, settings, scanCallback)
        
        scope.launch {
            delay(30000) // 30s timeout
            if (_isScanning.value) {
                Log.i(TAG, "Scan timeout reached")
                stopScanning()
            }
        }
    }

    fun stopScanning() {
        if (!_isScanning.value) return
        
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
            Log.i(TAG, "Scan stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan: ${e.message}")
        } finally {
            _isScanning.value = false
            scanCallback = null
        }
    }

    private fun initiateStandardProvisioning(bluetoothDevice: BluetoothDevice, serviceUuid: String? = null) {
        val provisioningManager = ESPProvisionManager.getInstance(context)
        
        // 1. Create ESP Device with Security 1 (AES-128-CTR + ECDH)
        currentEspDevice = provisioningManager.createESPDevice(
            ESPConstants.TransportType.TRANSPORT_BLE,
            ESPConstants.SecurityType.SECURITY_1
        )
        
        // 2. Set Proof of Possession (PoP)
        currentEspDevice?.proofOfPossession = DEFAULT_POP
        
        _provisioningStatus.value = "CONNECTING"
        _connectionState.value = BluetoothProfile.STATE_CONNECTING

        // 3. Connect and establish secure session
        val uuidToUse = if (serviceUuid != null && serviceUuid.length >= 32) {
            serviceUuid
        } else {
            // Fallback to the UUID derived from ESP32 default little-endian array {0xb4, 0xdf, ...}
            "021a9004-0382-4aea-bff4-6b3f1c5adfb4"
        }
        Log.i(TAG, "Connecting to device with service UUID: $uuidToUse")
        
        try {
            currentEspDevice?.connectBLEDevice(bluetoothDevice, uuidToUse)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect BLE device: ${e.message}")
            _provisioningStatus.value = "FAILED"
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDeviceConnectionEvent(event: DeviceConnectionEvent) {
        when (event.eventType) {
            ESPConstants.EVENT_DEVICE_CONNECTED -> {
                Log.i(TAG, "Connected to IoT device successfully")
                _connectionState.value = BluetoothProfile.STATE_CONNECTED
                _provisioningStatus.value = "SECURE_SESSION_ESTABLISHED"
            }
            ESPConstants.EVENT_DEVICE_DISCONNECTED -> {
                Log.i(TAG, "Disconnected from IoT device")
                _connectionState.value = BluetoothProfile.STATE_DISCONNECTED
                _provisioningStatus.value = null
                currentEspDevice = null
            }
            ESPConstants.EVENT_DEVICE_CONNECTION_FAILED -> {
                Log.e(TAG, "Device connection failed")
                _connectionState.value = BluetoothProfile.STATE_DISCONNECTED
                _provisioningStatus.value = "FAILED"
            }
        }
    }

    fun sendWifiCredentials(ssid: String, pass: String) {
        val device = currentEspDevice ?: run {
            Log.e(TAG, "Device not connected via secure session")
            _provisioningStatus.value = "NOT_CONNECTED"
            return
        }

        _provisioningStatus.value = "SENDING"
        
        device.provision(ssid, pass, object : ProvisionListener {
            override fun deviceProvisioningSuccess() {
                Log.i(TAG, "Provisioning successful - device is connecting to WiFi")
                _provisioningStatus.value = "SUCCESS"
                device.disconnectDevice()
            }

            override fun onProvisioningFailed(e: Exception) {
                Log.e(TAG, "Provisioning failed: ${e.message}")
                _provisioningStatus.value = "FAILED"
            }

            override fun wifiConfigSent() {
                Log.i(TAG, "WiFi config sent to device")
            }

            override fun wifiConfigApplied() {
                Log.i(TAG, "WiFi config applied on device")
            }

            override fun wifiConfigFailed(e: Exception) {
                Log.e(TAG, "WiFi config failed: ${e.message}")
                _provisioningStatus.value = "FAILED"
            }

            override fun wifiConfigApplyFailed(e: Exception) {
                Log.e(TAG, "WiFi config apply failed: ${e.message}")
                _provisioningStatus.value = "FAILED"
            }

            override fun provisioningFailedFromDevice(failureReason: ESPConstants.ProvisionFailureReason) {
                Log.e(TAG, "Provisioning failed from device: $failureReason")
                _provisioningStatus.value = "FAILED"
            }

            override fun createSessionFailed(e: Exception) {
                Log.e(TAG, "Secure session creation failed: ${e.message}")
                _provisioningStatus.value = "FAILED"
            }
        })
    }

    fun connectToDevice(device: BluetoothDevice) {
        stopScanning()
        _scannedDevices.value = emptyList()
        
        // 1. Get BLE MAC and derive WiFi MAC (ESP32-S3: BLE MAC = WiFi MAC + 1)
        val bleMac = device.address.replace(":", "").uppercase()
        val derivedWifiMac = try {
            val bleLong = bleMac.toLong(16)
            String.format("%012X", bleLong - 1)
        } catch (e: Exception) {
            bleMac
        }

        // 2. Extract MAC from device name if present
        val deviceName = device.name ?: ""
        val rawNameMac = deviceName.substring(DEVICE_PREFIX.length).uppercase().replace(":", "")
        
        // 3. Use name MAC if it's a full 12-char hex, otherwise use derived WiFi MAC
        val macAddress = if (rawNameMac.length == 12 && rawNameMac.matches(Regex("^[0-9A-F]+$"))) {
            rawNameMac
        } else {
            derivedWifiMac
        }

        _discoveredAddress.value = macAddress
        _connectedDeviceName.value = device.name

        val serviceUuid = deviceServiceUuids[device.address]
        initiateStandardProvisioning(device, serviceUuid)
    }

    fun disconnect() {
        currentEspDevice?.disconnectDevice()
        currentEspDevice = null
        _connectedDeviceName.value = null
        _connectionState.value = BluetoothProfile.STATE_DISCONNECTED
        
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this)
        }
    }

    fun getCurrentSsid(): String? {
        val info = wifiManager.connectionInfo
        if (info != null && info.networkId != -1) {
            var ssid = info.ssid
            if (ssid == "<unknown ssid>") {
                Log.w(TAG, "SSID is obscured. Ensure Location is ON and permission is granted.")
                return null
            }
            if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                ssid = ssid.substring(1, ssid.length - 1)
            }
            return ssid
        }
        return null
    }
}
