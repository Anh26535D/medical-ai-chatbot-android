package edu.hust.medicalaichatbot.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import edu.hust.medicalaichatbot.domain.model.IoTData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class IoTViewModel : ViewModel() {
    // Tự động lấy URL từ file google-services.json thông qua SDK Firebase
    private val database = FirebaseDatabase.getInstance()
    
    // ĐỊA CHỈ MAC THIẾT BỊ: Người dùng chỉ cần thay đổi giá trị này cho mạch ESP32 mới
    private val deviceMac = "1020BA49D1C8"

    private val latestRef = database.getReference("devices/$deviceMac/latest")

    private val _iotData = MutableStateFlow(IoTData())
    val iotData: StateFlow<IoTData> = _iotData.asStateFlow()

    private val heartRateLimit = 30
    private val history = mutableListOf<Int>()

    init {
        latestRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val hr = snapshot.child("heartRate").getValue(Int::class.java) ?: 0
                val spo2 = snapshot.child("spo2").getValue(Int::class.java) ?: 0
                val temp = snapshot.child("temperature").getValue(Double::class.java) ?: 0.0
                val hum = snapshot.child("humidity").getValue(Double::class.java) ?: 0.0
                val hasFinger = snapshot.child("hasFinger").getValue(Boolean::class.java) ?: false
                val status = snapshot.child("status").getValue(String::class.java) ?: "IDLE"
                
                // CHỈ XỬ LÝ KHI NHỊP TIM > 30 (LOẠI BỎ DATA NHIỄU/CHƯA ĐO)
                if (hr > 30 && hr < 200) {
                    history.add(hr)
                    if (history.size > heartRateLimit) {
                        history.removeAt(0)
                    }
                }

                // TỰ TÍNH TRUNG BÌNH CỘNG SẠCH
                val cleanAvg = if (history.isNotEmpty()) history.average().toInt() else 0

                _iotData.update {
                    it.copy(
                        heartRate = hr,
                        heartRateAvg = cleanAvg,
                        spo2 = spo2,
                        temperature = temp,
                        humidity = hum,
                        hasFinger = hasFinger,
                        status = status,
                        heartRateHistory = history.toList()
                    )
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
