package edu.hust.medicalaichatbot.data.repository

import edu.hust.medicalaichatbot.data.local.dao.ChatDao
import edu.hust.medicalaichatbot.data.local.dao.UserDao
import edu.hust.medicalaichatbot.data.local.entity.User
import edu.hust.medicalaichatbot.data.service.BackendHttpClient
import edu.hust.medicalaichatbot.utils.PreferenceManager

class AuthRepository(
    private val userDao: UserDao,
    private val chatDao: ChatDao,
    private val preferenceManager: PreferenceManager
) {
    suspend fun register(user: User): Result<Unit> {
        return try {
            val result = BackendHttpClient.register(user.phoneNumber, user.password)
            if (result.isSuccess) {
                // Migrate guest data to new user
                chatDao.migrateGuestData("guest", user.phoneNumber)
                Result.success(Unit)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Registration failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(phone: String, password: String): Result<User> {
        return try {
            val result = BackendHttpClient.login(phone, password)
            if (result.isSuccess) {
                val token = result.getOrNull()
                preferenceManager.saveAuthToken(token)
                preferenceManager.saveUserPhone(phone)

                // Construct a temporary User object for session state
                val user = User(
                    name = "User",
                    phoneNumber = phone,
                    password = "" // Do not store password locally
                )
                // Migrate guest data to logged in user
                chatDao.migrateGuestData("guest", user.phoneNumber)
                Result.success(user)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Invalid phone number or password"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
