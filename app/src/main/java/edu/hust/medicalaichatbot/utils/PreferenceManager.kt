package edu.hust.medicalaichatbot.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "medical_chatbot_preferences")

class PreferenceManager(private val context: Context) {
    private val dataStore = context.dataStore
    private val cryptoManager = CryptoManager(context)

    companion object {
        private val KEY_FIRST_TIME = booleanPreferencesKey("is_first_time")
        private val KEY_LAST_VISIT = longPreferencesKey("last_visit_time")
        private val KEY_LAST_THREAD_ID = stringPreferencesKey("last_thread_id")
        private val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_USER_PHONE = stringPreferencesKey("user_phone")
        private const val RE_SHOW_ONBOARDING_DAYS = 30 // Show again after 30 days of inactivity
    }

    private var shouldShowOnboardingCached: Boolean? = null

    val shouldShowOnboardingFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        if (shouldShowOnboardingCached != null) return@map shouldShowOnboardingCached!!

        val isFirstTime = preferences[KEY_FIRST_TIME] ?: true
        if (isFirstTime) {
            shouldShowOnboardingCached = true
            true
        } else {
            val lastVisit = preferences[KEY_LAST_VISIT] ?: 0L
            val currentTime = System.currentTimeMillis()
            val daysSinceLastVisit = (currentTime - lastVisit) / (1000 * 60 * 60 * 24)
            
            val result = daysSinceLastVisit >= RE_SHOW_ONBOARDING_DAYS
            shouldShowOnboardingCached = result
            result
        }
    }

    suspend fun setFirstTimeLaunch(isFirstTime: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_FIRST_TIME] = isFirstTime
        }
    }

    suspend fun updateLastVisit() {
        dataStore.edit { preferences ->
            preferences[KEY_LAST_VISIT] = System.currentTimeMillis()
        }
    }

    suspend fun setLastThreadId(threadId: String?) {
        dataStore.edit { preferences ->
            if (threadId != null) {
                preferences[KEY_LAST_THREAD_ID] = threadId
            } else {
                preferences.remove(KEY_LAST_THREAD_ID)
            }
        }
    }

    val lastThreadIdFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[KEY_LAST_THREAD_ID]
    }

    suspend fun saveAuthToken(token: String?) {
        dataStore.edit { preferences ->
            if (token != null) {
                val encrypted = cryptoManager.encrypt(token)
                preferences[KEY_AUTH_TOKEN] = encrypted
            } else {
                preferences.remove(KEY_AUTH_TOKEN)
            }
        }
    }

    val authTokenFlow: Flow<String?> = dataStore.data.map { preferences ->
        val encrypted = preferences[KEY_AUTH_TOKEN]
        if (!encrypted.isNullOrEmpty()) {
            try {
                cryptoManager.decrypt(encrypted)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    suspend fun saveUserPhone(phone: String?) {
        dataStore.edit { preferences ->
            if (phone != null) {
                val encrypted = cryptoManager.encrypt(phone)
                preferences[KEY_USER_PHONE] = encrypted
            } else {
                preferences.remove(KEY_USER_PHONE)
            }
        }
    }

    val userPhoneFlow: Flow<String?> = dataStore.data.map { preferences ->
        val encrypted = preferences[KEY_USER_PHONE]
        if (!encrypted.isNullOrEmpty()) {
            try {
                cryptoManager.decrypt(encrypted)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    suspend fun clearAuth() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_AUTH_TOKEN)
            preferences.remove(KEY_USER_PHONE)
        }
    }
}
