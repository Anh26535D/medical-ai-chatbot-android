package edu.hust.medicalaichatbot.data.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object BackendHttpClient {
    private const val TAG = "BackendHttpClient"
    // Go Backend API base URL
    private const val BASE_URL = "http://10.0.2.2:8080" // 10.0.2.2 points to localhost of host machine in Android emulator

    suspend fun register(phone: String, pass: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/v1/auth/register")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true

            val json = JSONObject().apply {
                put("phone", phone)
                put("password", pass)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(json.toString()) }

            val code = conn.responseCode
            if (code == 201 || code == 200) {
                Result.success("Success")
            } else {
                val errorStream = conn.errorStream ?: conn.inputStream
                val errorResponse = BufferedReader(InputStreamReader(errorStream)).readText()
                val errorMsg = JSONObject(errorResponse).optString("error", "Registration failed")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Register error", e)
            Result.failure(e)
        }
    }

    suspend fun login(phone: String, pass: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/v1/auth/login")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true

            val json = JSONObject().apply {
                put("phone", phone)
                put("password", pass)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(json.toString()) }

            val code = conn.responseCode
            if (code == 200) {
                val response = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                val token = JSONObject(response).getString("token")
                Result.success(token)
            } else {
                val errorStream = conn.errorStream ?: conn.inputStream
                val errorResponse = BufferedReader(InputStreamReader(errorStream)).readText()
                val errorMsg = JSONObject(errorResponse).optString("error", "Login failed")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login error", e)
            Result.failure(e)
        }
    }

    suspend fun confirmDevice(
        userCode: String,
        macAddress: String,
        sessionId: String,
        signature: String,
        token: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/v1/oauth/device/confirm")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true

            val json = JSONObject().apply {
                put("user_code", userCode)
                put("mac_address", macAddress)
                put("session_id", sessionId)
                put("pin_pop_signature", signature)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(json.toString()) }

            val code = conn.responseCode
            if (code == 200) {
                val response = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                val message = JSONObject(response).optString("message", "Device confirmed")
                Result.success(message)
            } else {
                val errorStream = conn.errorStream ?: conn.inputStream
                val errorResponse = BufferedReader(InputStreamReader(errorStream)).readText()
                val errorMsg = JSONObject(errorResponse).optString("error", "Confirmation failed")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Confirm error", e)
            Result.failure(e)
        }
    }
}
