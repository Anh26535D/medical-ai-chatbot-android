package edu.hust.medicalaichatbot.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import edu.hust.medicalaichatbot.data.local.entity.User
import edu.hust.medicalaichatbot.data.repository.AuthRepository
import edu.hust.medicalaichatbot.utils.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class AuthViewModel(
    private val repository: AuthRepository,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    private val _isGuest = MutableStateFlow(false)
    val isGuest: StateFlow<Boolean> = _isGuest

    init {
        // Auto-login: restore session if token exists
        viewModelScope.launch {
            combine(
                preferenceManager.authTokenFlow,
                preferenceManager.userPhoneFlow
            ) { token, phone ->
                Pair(token, phone)
            }.collect { (token, phone) ->
                if (!token.isNullOrEmpty() && !phone.isNullOrEmpty()) {
                    val user = User(name = "User", phoneNumber = phone, password = "")
                    _currentUser.value = user
                    _isGuest.value = false
                    _authState.value = AuthState.Success(user)
                }
            }
        }
    }

    fun register(user: User) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = repository.register(user)
            result.onSuccess {
                _currentUser.value = user
                _isGuest.value = false
                _authState.value = AuthState.Success(user)
            }.onFailure {
                _authState.value = AuthState.Error(it.message ?: "Registration failed")
            }
        }
    }

    fun login(phone: String, pass: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = repository.login(phone, pass)
            result.onSuccess {
                _currentUser.value = it
                _isGuest.value = false
                _authState.value = AuthState.Success(it)
            }.onFailure {
                _authState.value = AuthState.Error(it.message ?: "Login failed")
            }
        }
    }

    fun loginAsGuest() {
        _isGuest.value = true
        _currentUser.value = null
        _authState.value = AuthState.Guest
    }

    fun logout() {
        viewModelScope.launch {
            preferenceManager.clearAuth()
            _currentUser.value = null
            _isGuest.value = false
            _authState.value = AuthState.Idle
        }
    }

    fun resetState() {
        _authState.value = AuthState.Idle
    }

    class Factory(
        private val repository: AuthRepository,
        private val preferenceManager: PreferenceManager
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return AuthViewModel(repository, preferenceManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Guest : AuthState()
    data class Success(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}
