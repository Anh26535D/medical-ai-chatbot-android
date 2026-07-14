package edu.hust.medicalaichatbot.ui.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.*
import edu.hust.medicalaichatbot.data.service.BleIoTService
import edu.hust.medicalaichatbot.data.service.BackendHttpClient
import edu.hust.medicalaichatbot.domain.model.IoTData
import edu.hust.medicalaichatbot.utils.PreferenceManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class IoTViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        // Matches the backend's Redis device-flow session TTL (300*time.Second in
        // DeviceAuthorizeHandler) — a provisioning_polling entry outliving this is stale.
        private const val PAIRING_SESSION_TTL_SECONDS = 300L
    }


    private val bleService = BleIoTService(application)
    private val _firebaseData = MutableStateFlow(IoTData())
    val iotData: StateFlow<IoTData> = _firebaseData.asStateFlow()

    private val _historyData = MutableStateFlow<List<IoTData>>(emptyList())
    val historyData: StateFlow<List<IoTData>> = _historyData.asStateFlow()

    val bleConnectionState = bleService.connectionState
    val connectedDeviceAddress = bleService.discoveredAddress
    val connectedDeviceName = bleService.connectedDeviceName
    val provisioningStatus = bleService.provisioningStatus
    val scannedDevices = bleService.scannedDevices
    val isScanning = bleService.isScanning

    private val database = FirebaseDatabase.getInstance("https://caromaster-default-rtdb.asia-southeast1.firebasedatabase.app")
    private val prefs = application.getSharedPreferences("iot_prefs", Context.MODE_PRIVATE)

    private val _currentDeviceId = MutableStateFlow(prefs.getString("last_device_id", "") ?: "")
    val currentDeviceId: StateFlow<String> = _currentDeviceId.asStateFlow()

    private var activeDeviceRef: DatabaseReference? = null
    private var activeHistoryQuery: Query? = null

    private val _currentSsid = MutableStateFlow("")
    val currentSsid: StateFlow<String> = _currentSsid.asStateFlow()

    // OAUTH RFC 8628 States
    private val _discoveredUserCode = MutableStateFlow<String?>(null)
    val discoveredUserCode: StateFlow<String?> = _discoveredUserCode.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _activePairingNonce = MutableStateFlow<String?>(null)
    val activePairingNonce: StateFlow<String?> = _activePairingNonce.asStateFlow()

    private val _activePairingMac = MutableStateFlow<String?>(null)
    val activePairingMac: StateFlow<String?> = _activePairingMac.asStateFlow()

    private val _confirmStatus = MutableStateFlow<String?>(null)
    val confirmStatus: StateFlow<String?> = _confirmStatus.asStateFlow()

    private var pollingQuery: Query? = null
    private val pollingListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val nowSeconds = System.currentTimeMillis() / 1000
            for (child in snapshot.children) {
                val key = child.key ?: continue
                val userCode = child.child("user_code").getValue(String::class.java)
                    ?: child.child("UserCode").getValue(String::class.java)
                val pairingNonce = child.child("pairing_nonce").getValue(String::class.java)
                    ?: child.child("PairingNonce").getValue(String::class.java)
                val createdAt = child.child("created_at").getValue(Long::class.java)
                    ?: child.child("CreatedAt").getValue(Long::class.java)

                // Entries older than the backend's device-flow session TTL are stale — the
                // matching Redis session has already expired server-side. RTDB has no
                // native TTL, and entries can outlive that expiry if cleanup didn't run
                // (e.g. an older client build blocked by security rules), so filter by age
                // here rather than trusting that every entry present is still live.
                if (createdAt != null && nowSeconds - createdAt > PAIRING_SESSION_TTL_SECONDS) {
                    Log.d("IoT_Pairing_Flow", "Ignoring stale provisioning entry: $key (age=${nowSeconds - createdAt}s)")
                    child.ref.removeValue()
                    continue
                }

                if (userCode != null) {
                    val parts = key.split("_")
                    val mac = parts[0]
                    val sessId = if (parts.size > 1) parts.subList(1, parts.size).joinToString("_") else "session_xyz"
                    _discoveredUserCode.value = userCode
                    _activeSessionId.value = sessId
                    _activePairingNonce.value = pairingNonce
                    _activePairingMac.value = mac
                    Log.i("IoT_Pairing_Flow", "Firebase Sync: Discovered MAC=$mac, UserCode=$userCode, PairingNonce=$pairingNonce for session=$sessId")
                }
            }
        }
        override fun onCancelled(error: DatabaseError) {
            Log.e(TAG, "Polling listener error: ${error.message}")
        }
    }

    private var isGuest = true

    private val TAG = "IoTViewModel"

    private val valueEventListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            Log.d(TAG, "onDataChange: path=${snapshot.ref.path}, exists=${snapshot.exists()}, value=${snapshot.value}")
            val data = parseIoTData(snapshot)
            Log.d(TAG, "parsed data object: $data")
            if (data == null) return

            val currentHistory = _firebaseData.value.heartRateHistory.toMutableList()
            currentHistory.add(data.heartRate)
            if (currentHistory.size > 20) currentHistory.removeAt(0)
            _firebaseData.value = data.copy(heartRateHistory = currentHistory)
            Log.i(TAG, "Updated _firebaseData: heartRate=${data.heartRate}, spo2=${data.spo2}, temp=${data.temperature}, hum=${data.humidity}")
        }
        override fun onCancelled(error: DatabaseError) {
            Log.e(TAG, "Firebase error: ${error.message}")
        }
    }

    private val historyEventListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val historyList = snapshot.children.mapNotNull { parseIoTData(it) }
            _historyData.value = historyList.sortedByDescending { it.timestamp }.take(50)
        }
        override fun onCancelled(error: DatabaseError) {}
    }

    init {
        try {
            database.setPersistenceEnabled(true)
        } catch (e: Exception) {
            Log.w(TAG, "Persistence setting: ${e.message}")
        }

        _currentSsid.value = bleService.getCurrentSsid() ?: ""
        setupFirebaseListeners()

        database.getReference(".info/connected").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                Log.d(TAG, "Firebase connection status: ${if (connected) "Connected" else "Disconnected"}")
                if (connected) activeDeviceRef?.keepSynced(true)
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        viewModelScope.launch {
            bleService.discoveredAddress
                .filterNotNull()
                .distinctUntilChanged()
                .collect { newAddress ->
                    Log.i(TAG, "New temporary device scanned: $newAddress. Updating listener for pairing...")
                    setupFirebaseListeners()
                }
        }

        viewModelScope.launch {
            isScanning.collect { 
                Log.d(TAG, "isScanning state changed: $it. Updating listeners...")
                setupFirebaseListeners()
            }
        }
    }

    private fun setupFirebaseListeners() {
        if (isGuest) return
        activeDeviceRef?.removeEventListener(valueEventListener)
        activeHistoryQuery?.removeEventListener(historyEventListener)
        pollingQuery?.removeEventListener(pollingListener)

        val deviceId = _currentDeviceId.value
        // Lắng nghe tất cả yêu cầu pairing trên Firebase nếu chưa có thiết bị ghép đôi
        if (deviceId.isEmpty()) {
            val newPollingQuery = database.getReference("provisioning_polling")
            newPollingQuery.addValueEventListener(pollingListener)
            pollingQuery = newPollingQuery
            Log.i(TAG, "Subscribed to global provisioning polling...")
        } else {
            pollingQuery = null
        }

        if (deviceId.isEmpty()) {
            Log.i(TAG, "No device paired. Clearing live data and skipping telemetry Firebase sync.")
            _firebaseData.value = IoTData()
            _historyData.value = emptyList()
            activeDeviceRef = null
            activeHistoryQuery = null
            return
        }

        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
        val cleanDeviceId = deviceId.replace(":", "").uppercase()
        val newDeviceRef = database.getReference("users/$uid/devices/$cleanDeviceId/telemetry/latest")
        val newHistoryQuery = database.getReference("users/$uid/devices/$cleanDeviceId/history").limitToLast(50)

        newDeviceRef.keepSynced(true)
        newDeviceRef.addValueEventListener(valueEventListener)
        newHistoryQuery.addValueEventListener(historyEventListener)

        activeDeviceRef = newDeviceRef
        activeHistoryQuery = newHistoryQuery
        
        Log.i(TAG, "Auto-sync telemetry active for path: users/$uid/devices/$cleanDeviceId")
    }

    private fun parseIoTData(snapshot: DataSnapshot): IoTData? {
        // Firebase sends abbreviated keys: t=timestamp, hum=humidity, temp=temperature
        val timestamp = snapshot.child("t").getValue(Long::class.java)
            ?: snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
        if (timestamp == 0L) return null
        val bpm = snapshot.child("bpm").getValue(Int::class.java)
            ?: snapshot.child("heartRate").getValue(Int::class.java) ?: 0
        val status = snapshot.child("status").getValue(String::class.java) ?: "IDLE"
        val hasFinger = if (snapshot.hasChild("hasFinger")) {
            snapshot.child("hasFinger").getValue(Boolean::class.java) ?: false
        } else {
            status != "IDLE" && bpm > 0
        }
        return IoTData(
            heartRate = bpm,
            spo2 = snapshot.child("spo2").getValue(Int::class.java) ?: 0,
            temperature = snapshot.child("temp").getValue(Double::class.java)
                ?: snapshot.child("temperature").getValue(Double::class.java) ?: 0.0,
            humidity = snapshot.child("hum").getValue(Double::class.java)
                ?: snapshot.child("humidity").getValue(Double::class.java) ?: 0.0,
            hasFinger = hasFinger,
            status = status,
            timestamp = timestamp
        )
    }

    fun connectBle() {
        if (isGuest) {
            Log.w(TAG, "Blocking connectBle for guest user")
            return
        }
        bleService.startScanning()
    }

    fun stopScanning() {
        bleService.stopScanning()
    }

    fun connectToDevice(device: BluetoothDevice) {
        if (isGuest) return
        bleService.connectToDevice(device)
    }

    fun disconnectBle() = bleService.disconnect()

    fun unpairDevice() {
        val deviceId = _currentDeviceId.value
        if (deviceId.isNotEmpty()) {
            viewModelScope.launch {
                val preferenceManager = PreferenceManager(getApplication())
                val token = preferenceManager.authTokenFlow.firstOrNull()
                if (!token.isNullOrEmpty()) {
                    Log.i(TAG, "Sending unpair request to backend for MAC: $deviceId")
                    val cleanMac = deviceId.replace(":", "").uppercase()
                    val result = BackendHttpClient.unpairDevice(cleanMac, token)
                    if (result.isSuccess) {
                        Log.i(TAG, "Device unpaired successfully on backend")
                    } else {
                        Log.w(TAG, "Failed to unpair device on backend: ${result.exceptionOrNull()?.message}")
                    }
                }
            }
        }
        prefs.edit().remove("last_device_id").apply()
        _currentDeviceId.value = ""
        setupFirebaseListeners()
    }

    fun sendWifiCredentials(s: String, p: String) {
        if (isGuest) {
            Log.w(TAG, "Blocking sendWifiCredentials for guest user")
            return
        }
        bleService.sendWifiCredentials(s, p)
    }

    fun setGuestStatus(guest: Boolean) {
        if (this.isGuest == guest) return
        
        this.isGuest = guest
        if (guest) {
            Log.i(TAG, "Guest mode active: Clearing IoT listeners and data")
            activeDeviceRef?.removeEventListener(valueEventListener)
            activeHistoryQuery?.removeEventListener(historyEventListener)
            pollingQuery?.removeEventListener(pollingListener)
            activeDeviceRef = null
            activeHistoryQuery = null
            pollingQuery = null
            _firebaseData.value = IoTData()
            _historyData.value = emptyList()
            _discoveredUserCode.value = null
            _activeSessionId.value = null
            bleService.disconnect()
        } else {
            Log.i(TAG, "User authenticated: Initializing IoT sync")
            setupFirebaseListeners()
        }
    }

    fun refreshFirebaseData() {
        if (isGuest) return
        activeDeviceRef?.addListenerForSingleValueEvent(valueEventListener)
    }

    fun confirmDevice(userCode: String, macAddress: String, sessionId: String, userPhone: String = "", userPass: String = "") {
        viewModelScope.launch {
            Log.i("IoT_Pairing_Flow", "User clicked Confirm Pairing. UserCode=$userCode, MAC=$macAddress, Session=$sessionId")
            _confirmStatus.value = "CONFIRMING"
            
            // Check if there is an existing token in PreferenceManager first to avoid login with empty password on auto-login
            val preferenceManager = PreferenceManager(getApplication())
            val existingToken = preferenceManager.authTokenFlow.firstOrNull()
            
            val jwt = if (!existingToken.isNullOrEmpty()) {
                Log.i("IoT_Pairing_Flow", "Found existing JWT Token in preferences. Skipping login/register.")
                existingToken
            } else {
                if (userPhone.isEmpty() || userPass.isEmpty()) {
                    Log.e("IoT_Pairing_Flow", "Authentication failed: No active token found and login credentials are blank.")
                    _confirmStatus.value = "AUTH_FAILED: Login required"
                    return@launch
                }
                // Step 1: Login / Register on Backend Go (only if no token)
                Log.i("IoT_Pairing_Flow", "Step 1: Authenticating user with Backend Go... Phone=$userPhone")
                var loginResult = BackendHttpClient.login(userPhone, userPass)
                if (loginResult.isFailure) {
                    Log.w("IoT_Pairing_Flow", "Login failed. Trying to register user first...")
                    val regResult = BackendHttpClient.register(userPhone, userPass)
                    if (regResult.isSuccess) {
                        Log.i("IoT_Pairing_Flow", "User registration on Backend Go successful. Retrying login...")
                        loginResult = BackendHttpClient.login(userPhone, userPass)
                    }
                }

                if (loginResult.isFailure) {
                    Log.e("IoT_Pairing_Flow", "Authentication failed on backend: ${loginResult.exceptionOrNull()?.message}")
                    _confirmStatus.value = "AUTH_FAILED"
                    return@launch
                }
                loginResult.getOrNull() ?: ""
            }

            Log.i("IoT_Pairing_Flow", "Authentication successful. Using JWT Token: ${jwt.substring(0, minOf(15, jwt.length))}...")

            // Step 2: Compute PIN PoP HMAC-SHA256 Signature (Use dynamic PairingNonce from Firebase)
            val nonce = _activePairingNonce.value ?: "pin-pop-secret-key"
            Log.i("IoT_Pairing_Flow", "Step 2: Computing HMAC-SHA256 signature using secret '$nonce'")
            val message = "$userCode:$macAddress:$sessionId"
            val signature = computeHmacSha256(message, nonce)
            Log.i("IoT_Pairing_Flow", "Computed PIN PoP signature: $signature")

            // Step 3: Call Confirm Device API
            Log.i("IoT_Pairing_Flow", "Step 3: Sending confirmation request to Backend Go...")
            val confirmResult = BackendHttpClient.confirmDevice(userCode, macAddress, sessionId, signature, jwt)
            if (confirmResult.isSuccess) {
                Log.i("IoT_Pairing_Flow", "Ghép đôi thành công! Device pairing approved by backend.")
                
                // Save paired device permanently now
                prefs.edit().putString("last_device_id", macAddress).apply()
                _currentDeviceId.value = macAddress
                
                _confirmStatus.value = "SUCCESS"
                val cleanMac = macAddress.replace(":", "").uppercase()
                database.getReference("provisioning_polling/${cleanMac}_${sessionId}").removeValue()
                _discoveredUserCode.value = null
                _activeSessionId.value = null
                setupFirebaseListeners() // Initialize secure telemetry listen paths
            } else {
                val errMsg = confirmResult.exceptionOrNull()?.message ?: "Unknown error"
                Log.e("IoT_Pairing_Flow", "Pairing confirmation failed on backend: $errMsg")
                _confirmStatus.value = "FAILED: $errMsg"
                
                // Nếu phiên hết hạn hoặc không tìm thấy, dọn dẹp Firebase và đóng dialog
                if (errMsg.contains("expired", ignoreCase = true) || errMsg.contains("not found", ignoreCase = true)) {
                    val cleanMac = macAddress.replace(":", "").uppercase()
                    database.getReference("provisioning_polling/${cleanMac}_${sessionId}").removeValue()
                    _discoveredUserCode.value = null
                    _activeSessionId.value = null
                    _activePairingMac.value = null
                    setupFirebaseListeners()
                }
            }
        }
    }

    private fun computeHmacSha256(message: String, key: String): String {
        val sha256HMAC = javax.crypto.Mac.getInstance("HmacSHA256")
        val secretKey = javax.crypto.spec.SecretKeySpec(key.toByteArray(Charsets.US_ASCII), "HmacSHA256")
        sha256HMAC.init(secretKey)
        val hash = sha256HMAC.doFinal(message.toByteArray(Charsets.US_ASCII))
        return hash.joinToString("") { String.format("%02x", it) }
    }

    override fun onCleared() {
        super.onCleared()
        activeDeviceRef?.removeEventListener(valueEventListener)
        activeHistoryQuery?.removeEventListener(historyEventListener)
        pollingQuery?.removeEventListener(pollingListener)
        bleService.disconnect()
    }
}
