package edu.hust.medicalaichatbot.data.service

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

// Data Models
data class RegisterRequest(
    val phone: String,
    val password: String
)

data class LoginRequest(
    val phone: String,
    val password: String
)

data class LoginResponse(
    val token: String
)

data class ConfirmRequest(
    val user_code: String,
    val mac_address: String,
    val session_id: String,
    val pin_pop_signature: String
)

data class ConfirmResponse(
    val message: String?
)

interface BackendApiService {

    @POST("api/v1/auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<Void>

    @POST("api/v1/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @POST("api/v1/oauth/device/confirm")
    suspend fun confirmDevice(
        @Header("Authorization") token: String,
        @Body request: ConfirmRequest
    ): Response<ConfirmResponse>
}
