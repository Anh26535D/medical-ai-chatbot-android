package edu.hust.medicalaichatbot.data.service

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import edu.hust.medicalaichatbot.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object BackendHttpClient {
    private const val TAG = "BackendHttpClient"
    private const val BASE_URL = BuildConfig.BASE_URL

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val apiService = retrofit.create(BackendApiService::class.java)

    suspend fun register(phone: String, pass: String): Result<String> {
        return try {
            val response = apiService.register(RegisterRequest(phone, pass))
            if (response.isSuccessful) {
                Result.success("Success")
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMsg = errorBody?.let {
                    try {
                        org.json.JSONObject(it).optString("error", "Registration failed")
                    } catch (e: Exception) {
                        "Registration failed"
                    }
                } ?: "Registration failed"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Register error", e)
            Result.failure(e)
        }
    }

    // Firebase RTDB rules gate reads on auth.uid === $uid, and the app builds telemetry
    // paths from FirebaseAuth's own uid — so FirebaseAuth must be signed in with the SAME
    // uid the backend uses (the JWT's uid_user claim), not an unrelated anonymous uid.
    // The backend mints a Firebase custom token carrying that exact uid at login time.
    private suspend fun signInWithFirebaseCustomToken(token: String) {
        suspendCancellableCoroutine<Unit> { cont ->
            FirebaseAuth.getInstance().signInWithCustomToken(token)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }
    }

    suspend fun login(phone: String, pass: String): Result<String> {
        return try {
            val response = apiService.login(LoginRequest(phone, pass))
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    body.firebase_token?.let { fbToken ->
                        try {
                            signInWithFirebaseCustomToken(fbToken)
                        } catch (e: Exception) {
                            Log.w(TAG, "Firebase custom token sign-in failed: ${e.message}")
                        }
                    }
                    Result.success(body.token)
                } else {
                    Result.failure(Exception("Empty login response"))
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMsg = errorBody?.let {
                    try {
                        org.json.JSONObject(it).optString("error", "Login failed")
                    } catch (e: Exception) {
                        "Login failed"
                    }
                } ?: "Login failed"
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
    ): Result<String> {
        return try {
            val authHeader = "Bearer $token"
            val response = apiService.confirmDevice(
                authHeader,
                ConfirmRequest(
                    user_code = userCode,
                    mac_address = macAddress,
                    session_id = sessionId,
                    pin_pop_signature = signature
                )
            )
            if (response.isSuccessful) {
                val body = response.body()
                Result.success(body?.message ?: "Device confirmed")
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMsg = errorBody?.let {
                    try {
                        org.json.JSONObject(it).optString("error", "Confirmation failed")
                    } catch (e: Exception) {
                        "Confirmation failed"
                    }
                } ?: "Confirmation failed"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Confirm error", e)
            Result.failure(e)
        }
    }

    suspend fun unpairDevice(macAddress: String, token: String): Result<String> {
        return try {
            val authHeader = "Bearer $token"
            val response = apiService.unpairDevice(authHeader, macAddress)
            if (response.isSuccessful) {
                Result.success(response.body()?.message ?: "Device unpaired successfully")
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMsg = errorBody?.let {
                    try {
                        org.json.JSONObject(it).optString("error", "Unpairing failed")
                    } catch (e: Exception) {
                        "Unpairing failed"
                    }
                } ?: "Unpairing failed"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unpair error", e)
            Result.failure(e)
        }
    }
}
