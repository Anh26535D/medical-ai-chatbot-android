package edu.hust.medicalaichatbot.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.*
import edu.hust.medicalaichatbot.data.service.BleIoTService
import edu.hust.medicalaichatbot.data.service.BackendHttpClient
import edu.hust.medicalaichatbot.domain.model.IoTData
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class IoTViewModel(application: Application) : AndroidViewModel(application) {
    private val bleService = BleIoTService(application)
    private val _firebaseData = MutableStateFlow(IoTData())
    val iotData: StateFlow<IoTData> = _firebaseData.asStateFlow()

    private val _historyData = MutableStateFlow<List<IoTData>>(emptyList())
    val historyData: StateFlow<List<IoTData>> = _historyData.asStateFlow()

    val bleConnectionState = bleService.connectionState
    val connectedDeviceAddress = bleService.discoveredAddress
    val provisioningStatus = bleService.provisioningStatus

    private val database = FirebaseDatabase.getInstance("https://caromaster-default-rtdb.asia-southeast1.firebasedatabase.app")
    private val prefs = application.getSharedPreferences("iot_prefs", Context.MODE_PRIVATE)

    // NEW: Expose the active ID to the UI
    private val _currentDeviceId = MutableStateFlow(prefs.getString("last_device_id", "1020BA49D1C8") ?: "1020BA49D1C8")
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

    private val _confirmStatus = MutableStateFlow<String?>(null)
    val confirmStatus: StateFlow<String?> = _confirmStatus.asStateFlow()

    private var pollingQuery: Query? = null
    private val pollingListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            for (child in snapshot.children) {
                val key = child.key ?: continue
                val userCode = child.child("user_code").getValue(String::class.java)
                if (userCode != null) {
                    val parts = key.split("_")
                    val sessId = if (parts.size > 1) parts.subList(1, parts.size).joinToString("_") else "session_xyz"
                    _discoveredUserCode.value = userCode
                    _activeSessionId.value = sessId
                    Log.i("IoT_Pairing_Flow", "Firebase Sync: Discovered user_code=$userCode for session=$sessId")
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
            val data = parseIoTData(snapshot) ?: return
            if (data.timestamp <= _firebaseData.value.timestamp) return

            val currentHistory = _firebaseData.value.heartRateHistory.toMutableList()
            if (data.heartRate > 0) {
                currentHistory.add(data.heartRate)
                if (currentHistory.size > 20) currentHistory.removeAt(0)
            }
            _firebaseData.value = data.copy(heartRateHistory = currentHistory)
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
                    Log.i(TAG, "New device discovered: $newAddress. Updating listeners...")
                    prefs.edit().putString("last_device_id", newAddress).apply()
                    _currentDeviceId.value = newAddress // Update the state
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
        val newDeviceRef = database.getReference("devices/$deviceId/latest")
        val newHistoryQuery = database.getReference("devices/$deviceId/history").limitToLast(50)

        newDeviceRef.keepSynced(true)
        newDeviceRef.addValueEventListener(valueEventListener)
        newHistoryQuery.addValueEventListener(historyEventListener)

        activeDeviceRef = newDeviceRef
        activeHistoryQuery = newHistoryQuery

        val newPollingQuery = database.getReference("provisioning_polling")
            .orderByKey()
            .startAt(deviceId)
            .endAt(deviceId + "\uf8ff")
        newPollingQuery.addValueEventListener(pollingListener)
        pollingQuery = newPollingQuery
        
        Log.i(TAG, "Auto-sync active for path: devices/$deviceId")
    }

    private fun parseIoTData(snapshot: DataSnapshot): IoTData? {
        val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
        if (timestamp == 0L) return null
        return IoTData(
            heartRate = snapshot.child("heartRate").getValue(Int::class.java) ?: 0,
            spo2 = snapshot.child("spo2").getValue(Int::class.java) ?: 0,
            temperature = snapshot.child("temperature").getValue(Double::class.java) ?: 0.0,
            humidity = snapshot.child("humidity").getValue(Double::class.java) ?: 0.0,
            hasFinger = snapshot.child("hasFinger").getValue(Boolean::class.java) ?: false,
            status = snapshot.child("status").getValue(String::class.java) ?: "IDLE",
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

    fun disconnectBle() = bleService.disconnect()

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

    fun confirmDevice(userCode: String, macAddress: String, sessionId: String, userPhone: String, userPass: String) {
        viewModelScope.launch {
            Log.i("IoT_Pairing_Flow", "User clicked Confirm Pairing. UserCode=$userCode, MAC=$macAddress, Session=$sessionId")
            _confirmStatus.value = "CONFIRMING"
            
            // Step 1: Login / Register on Backend Go
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

            val jwt = loginResult.getOrNull() ?: ""
            Log.i("IoT_Pairing_Flow", "Authentication successful. Obtained JWT Token: ${jwt.substring(0, 15)}...")

            // Step 2: Compute PIN PoP HMAC-SHA256 Signature
            Log.i("IoT_Pairing_Flow", "Step 2: Computing HMAC-SHA256 signature using PIN PoP (12345678)")
            val message = "$userCode:$macAddress:$sessionId"
            val signature = computeHmacSha256(message, "12345678")
            Log.i("IoT_Pairing_Flow", "Computed PIN PoP signature: $signature")

            // Step 3: Call Confirm Device API
            Log.i("IoT_Pairing_Flow", "Step 3: Sending confirmation request to Backend Go...")
            val confirmResult = BackendHttpClient.confirmDevice(userCode, macAddress, sessionId, signature, jwt)
            if (confirmResult.isSuccess) {
                Log.i("IoT_Pairing_Flow", "Ghép đôi thành công! Device pairing approved by backend.")
                _confirmStatus.value = "SUCCESS"
                database.getReference("provisioning_polling/${macAddress}_${sessionId}").removeValue()
                _discoveredUserCode.value = null
                _activeSessionId.value = null
            } else {
                val errMsg = confirmResult.exceptionOrNull()?.message
                Log.e("IoT_Pairing_Flow", "Pairing confirmation failed on backend: $errMsg")
                _confirmStatus.value = "FAILED: $errMsg"
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
