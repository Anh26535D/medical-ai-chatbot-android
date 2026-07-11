package edu.hust.medicalaichatbot

import android.content.Context
import edu.hust.medicalaichatbot.data.local.AppDatabase
import edu.hust.medicalaichatbot.data.repository.AuthRepository
import edu.hust.medicalaichatbot.data.repository.ChatRepositoryImpl
import edu.hust.medicalaichatbot.data.repository.ProfileRepository
import edu.hust.medicalaichatbot.data.service.LocationService
import edu.hust.medicalaichatbot.domain.usecase.chat.CreateThreadUseCase
import edu.hust.medicalaichatbot.domain.usecase.chat.DeleteThreadUseCase
import edu.hust.medicalaichatbot.domain.usecase.chat.GetMessagesUseCase
import edu.hust.medicalaichatbot.domain.usecase.chat.GetThreadsUseCase
import edu.hust.medicalaichatbot.domain.usecase.chat.SendMessageUseCase
import edu.hust.medicalaichatbot.utils.Constants
import edu.hust.medicalaichatbot.utils.PreferenceManager

class AppRepositoryContainer(context: Context) {
    val database = AppDatabase.getDatabase(context)
    val preferenceManager = PreferenceManager(context)
    
    val authRepository = AuthRepository(database.userDao(), database.chatDao(), preferenceManager)
    
    val locationService = LocationService(context)
    
    val chatRepository = ChatRepositoryImpl(
        chatDao = database.chatDao(),
        modelName = Constants.DEFAULT_MODEL,
        locationService = locationService
    )

    val getMessagesUseCase = GetMessagesUseCase(chatRepository)
    val sendMessageUseCase = SendMessageUseCase(chatRepository)
    val createThreadUseCase = CreateThreadUseCase(chatRepository)
    val getThreadsUseCase = GetThreadsUseCase(chatRepository)
    val deleteThreadUseCase = DeleteThreadUseCase(chatRepository)

    val profileRepository = ProfileRepository(database.userProfileDao())
}