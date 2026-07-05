package edu.hust.medicalaichatbot.ui.viewmodel

import android.app.Application
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.*
import edu.hust.medicalaichatbot.data.service.BleIoTService
import edu.hust.medicalaichatbot.domain.model.IoTData
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class IoTViewModel(application: Application) : AndroidViewModel(application) {
    private val bleService = BleIoTService(application)
    private val _firebaseData = MutableStateFlow(IoTData())

    val iotData: StateFlow<IoTData> = _firebaseData.asStateFlow()
    val bleConnectionState = bleService.connectionState
    val connectedDeviceAddress = bleService.discoveredAddress
    val provisioningStatus = bleService.provisioningStatus

    private val database = FirebaseDatabase.getInstance()
    private var deviceRef = database.getReference("devices/1020BA49D1C8/latest")
    private var currentDeviceId = "1020BA49D1C8"

    private val _currentSsid = MutableStateFlow("")
    val currentSsid: StateFlow<String> = _currentSsid.asStateFlow()

    private val TAG = "IoTViewModel"

    private val valueEventListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
            
            // Only update if we have new data
            if (timestamp == 0L || timestamp <= _firebaseData.value.timestamp) return

            val heartRate = snapshot.child("heartRate").getValue(Int::class.java) ?: 0
            val heartRateAvg = snapshot.child("heartRateAvg").getValue(Int::class.java) ?: 0
            val spo2 = snapshot.child("spo2").getValue(Int::class.java) ?: 0
            val temperature = snapshot.child("temperature").getValue(Double::class.java) ?: 0.0
            val humidity = snapshot.child("humidity").getValue(Double::class.java) ?: 0.0
            val hasFinger = snapshot.child("hasFinger").getValue(Boolean::class.java) ?: false
            val status = snapshot.child("status").getValue(String::class.java) ?: "IDLE"
            
            val currentHistory = _firebaseData.value.heartRateHistory.toMutableList()
            if (heartRate > 0) {
                currentHistory.add(heartRate)
                if (currentHistory.size > 20) {
                    currentHistory.removeAt(0)
                }
            }

            _firebaseData.value = IoTData(
                heartRate = heartRate,
                heartRateAvg = heartRateAvg,
                spo2 = spo2,
                temperature = temperature,
                humidity = humidity,
                hasFinger = hasFinger,
                status = status,
                timestamp = timestamp,
                heartRateHistory = currentHistory
            )
            Log.d(TAG, "Data updated for $currentDeviceId: HR=$heartRate, SpO2=$spo2")
        }

        override fun onCancelled(error: DatabaseError) {
            Log.e(TAG, "Firebase error: ${error.message}")
        }
    }

    init {
        Log.i(TAG, "Initializing with default device: $currentDeviceId")
        deviceRef.addValueEventListener(valueEventListener)
        
        // Update current SSID from system
        _currentSsid.value = bleService.getCurrentSsid() ?: ""
        
        // Listen for device discovery via BLE to update Firebase path
        viewModelScope.launch {
            bleService.discoveredAddress
                .filterNotNull()
                .distinctUntilChanged()
                .collect { newAddress ->
                    Log.i(TAG, "Switching Firebase path to new device: $newAddress")
                    deviceRef.removeEventListener(valueEventListener)
                    currentDeviceId = newAddress
                    deviceRef = database.getReference("devices/$newAddress/latest")
                    deviceRef.addValueEventListener(valueEventListener)
                }
        }
    }

    fun connectBle() {
        bleService.startScanning()
        // Refresh SSID when connecting
        _currentSsid.value = bleService.getCurrentSsid() ?: ""
    }

    fun disconnectBle() {
        bleService.disconnect()
    }

    fun sendWifiCredentials(ssid: String, pass: String) {
        bleService.sendWifiCredentials(ssid, pass)
    }

    override fun onCleared() {
        super.onCleared()
        deviceRef.removeEventListener(valueEventListener)
        bleService.disconnect()
    }
}
