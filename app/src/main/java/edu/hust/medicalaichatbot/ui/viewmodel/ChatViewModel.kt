package edu.hust.medicalaichatbot.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import edu.hust.medicalaichatbot.domain.model.ChatMessage
import edu.hust.medicalaichatbot.domain.model.ChatThread
import edu.hust.medicalaichatbot.domain.usecase.chat.CreateThreadUseCase
import edu.hust.medicalaichatbot.domain.usecase.chat.GetMessagesUseCase
import edu.hust.medicalaichatbot.domain.usecase.chat.SendMessageUseCase
import edu.hust.medicalaichatbot.utils.Constants
import edu.hust.medicalaichatbot.utils.PreferenceManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
    private val getMessagesUseCase: GetMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val createThreadUseCase: CreateThreadUseCase
) : ViewModel() {

    private val _currentThreadId = MutableStateFlow<String?>(null)
    val currentThreadId = _currentThreadId.asStateFlow()

    private val _userId = MutableStateFlow<String>("guest")

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messages: Flow<PagingData<ChatMessage>> = _currentThreadId
        .filterNotNull()
        .flatMapLatest { threadId ->
            getMessagesUseCase(threadId)
        }
        .cachedIn(viewModelScope)

    fun setCurrentThread(threadId: String?) {
        _currentThreadId.value = threadId
    }

    fun saveCurrentThreadId(preferenceManager: PreferenceManager) {
        viewModelScope.launch {
            preferenceManager.setLastThreadId(_currentThreadId.value)
        }
    }

    fun restoreLastThread(preferenceManager: PreferenceManager) {
        if (_currentThreadId.value == null) {
            viewModelScope.launch {
                _currentThreadId.value = preferenceManager.lastThreadIdFlow.firstOrNull()
            }
        }
    }

    fun setUserId(userId: String) {
        _userId.value = userId
    }

    fun sendMessage(text: String) {
        val threadId = _currentThreadId.value ?: run {
            val newId = UUID.randomUUID().toString()
            viewModelScope.launch {
                createThreadUseCase(
                    ChatThread(
                        id = newId,
                        userId = _userId.value,
                        title = "Cuộc trò chuyện mới",
                        lastUpdated = System.currentTimeMillis(),
                        modelName = Constants.DEFAULT_MODEL
                    )
                )
                _currentThreadId.value = newId
                sendMessage(text)
            }
            return
        }
        if (text.isBlank()) return

        viewModelScope.launch {
            _isLoading.value = true
            sendMessageUseCase(threadId, text, _userId.value)
            _isLoading.value = false
        }
    }

    fun startNewChat() {
        _currentThreadId.value = null
    }

    class Factory(
        private val getMessagesUseCase: GetMessagesUseCase,
        private val sendMessageUseCase: SendMessageUseCase,
        private val createThreadUseCase: CreateThreadUseCase
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(getMessagesUseCase, sendMessageUseCase, createThreadUseCase) as T
        }
    }
}
