package edu.hust.medicalaichatbot.domain.model

data class IoTData(
    val heartRate: Int = 0,
    val heartRateAvg: Int = 0,
    val spo2: Int = 0,
    val temperature: Double = 0.0,
    val humidity: Double = 0.0,
    val hasFinger: Boolean = false,
    val status: String = "IDLE",
    val timestamp: Long = 0L,
    val heartRateHistory: List<Int> = emptyList()
)
