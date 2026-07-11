package edu.hust.medicalaichatbot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.paging.compose.collectAsLazyPagingItems
import edu.hust.medicalaichatbot.ui.components.CommonTopBar
import edu.hust.medicalaichatbot.ui.components.MainBottomNavigation
import edu.hust.medicalaichatbot.ui.components.MessageInput
import edu.hust.medicalaichatbot.ui.screens.AccountSettingsScreen
import edu.hust.medicalaichatbot.ui.screens.HelpScreen
import edu.hust.medicalaichatbot.ui.screens.HistoryScreen
import edu.hust.medicalaichatbot.ui.screens.HomeScreen
import edu.hust.medicalaichatbot.ui.screens.LoginScreen
import edu.hust.medicalaichatbot.ui.screens.MedicalSummaryScreen
import edu.hust.medicalaichatbot.ui.screens.OnboardingScreen
import edu.hust.medicalaichatbot.ui.screens.ProfileScreen
import edu.hust.medicalaichatbot.ui.screens.RegisterScreen
import edu.hust.medicalaichatbot.ui.screens.SplashScreen
import edu.hust.medicalaichatbot.ui.theme.BackgroundGray
import edu.hust.medicalaichatbot.ui.theme.MedicalAIChatbotTheme
import edu.hust.medicalaichatbot.ui.viewmodel.AuthViewModel
import edu.hust.medicalaichatbot.ui.viewmodel.ChatViewModel
import edu.hust.medicalaichatbot.ui.viewmodel.HistoryViewModel
import edu.hust.medicalaichatbot.ui.viewmodel.ProfileViewModel
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import edu.hust.medicalaichatbot.utils.PreferenceManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import androidx.compose.runtime.rememberCoroutineScope

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferenceManager = PreferenceManager(this)
        lifecycleScope.launch {
            preferenceManager.updateLastVisit()
        }
        
        val container = (application as MedicalAIChatbotApplication).appRepositoryContainer

        enableEdgeToEdge()
        setContent {
            MedicalAIChatbotTheme {
                val authViewModel: AuthViewModel = viewModel(
                    factory = AuthViewModel.Factory(container.authRepository, preferenceManager)
                )

                val chatViewModel: ChatViewModel = viewModel(
                    factory = ChatViewModel.Factory(
                        container.getMessagesUseCase,
                        container.sendMessageUseCase,
                        container.createThreadUseCase
                    )
                )

                val historyViewModel: HistoryViewModel = viewModel(
                    factory = HistoryViewModel.Factory(
                        container.getThreadsUseCase,
                        container.deleteThreadUseCase,
                        container.getMessagesUseCase
                    )
                )

                val profileViewModel: ProfileViewModel = viewModel(
                    factory = ProfileViewModel.Factory(container.profileRepository, authViewModel)
                )

                MedicalApp(authViewModel, chatViewModel, historyViewModel, profileViewModel, preferenceManager)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicalApp(
    authViewModel: AuthViewModel, 
    chatViewModel: ChatViewModel,
    historyViewModel: HistoryViewModel,
    profileViewModel: ProfileViewModel,
    preferenceManager: PreferenceManager
) {
    val navController = rememberNavController()
    val currentUser by authViewModel.currentUser.collectAsState()
    val isGuest by authViewModel.isGuest.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isImeVisible = WindowInsets.isImeVisible

    val showBars = currentRoute in listOf("home", "history", "profile", "help")
    var prefillText by remember { mutableStateOf("") }
    val currentThreadId by chatViewModel.currentThreadId.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    // Save thread ID whenever it changes
    LaunchedEffect(currentThreadId) {
        chatViewModel.saveCurrentThreadId(preferenceManager)
    }

    // Restore thread ID on start
    LaunchedEffect(Unit) {
        chatViewModel.restoreLastThread(preferenceManager)
    }

    LaunchedEffect(currentUser, isGuest) {
        val userId = if (currentUser != null) {
            currentUser!!.phoneNumber
        } else if (isGuest) {
            "guest"
        } else {
            "guest"
        }
        chatViewModel.setUserId(userId)
        historyViewModel.setUserId(userId)
    }

    Scaffold(
        topBar = {
            if (showBars) {
                val title = when (currentRoute) {
                    "history" -> stringResource(R.string.history_title)
                    "profile" -> "Hồ sơ sức khỏe"
                    "help" -> "Trung tâm hỗ trợ"
                    "home" -> null
                    else -> null
                }
                val subtitle = if (currentRoute == "profile") "Trợ lý sức khỏe AI" else null
                CommonTopBar(
                    title = title,
                    subtitle = subtitle,
                    onProfileClick = { navController.navigate("account_settings") }
                )
            }
        },
        bottomBar = {
            if (showBars) {
                Column(modifier = Modifier.imePadding()) {
                    if (currentRoute == "home") {
                        MessageInput(
                            onSendMessage = { chatViewModel.sendMessage(it) },
                            prefillText = prefillText,
                            onPrefillConsumed = { prefillText = "" },
                            showInitialChips = chatViewModel.messages.collectAsLazyPagingItems().itemCount == 0
                        )
                        androidx.compose.material3.HorizontalDivider(
                            color = edu.hust.medicalaichatbot.ui.theme.SurfaceGray.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )
                    }
                    if (!isImeVisible) {
                        MainBottomNavigation(navController = navController)
                    }
                }
            }
        },
        containerColor = BackgroundGray
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
        ) {
            NavHost(
                navController = navController,
                startDestination = "splash"
            ) {
                composable("splash") {
                    val scope = rememberCoroutineScope()
                    SplashScreen(onTimeout = {
                        scope.launch {
                            val shouldShow = preferenceManager.shouldShowOnboardingFlow.firstOrNull() ?: true
                            val token = preferenceManager.authTokenFlow.firstOrNull()
                            val destination = if (shouldShow) {
                                "onboarding"
                            } else if (!token.isNullOrEmpty()) {
                                "home"
                            } else {
                                "login"
                            }
                            navController.navigate(destination) {
                                popUpTo("splash") { inclusive = true }
                            }
                        }
                    })
                }
                composable("onboarding") {
                    val scope = rememberCoroutineScope()
                    OnboardingScreen(onFinish = {
                        scope.launch {
                            preferenceManager.setFirstTimeLaunch(false)
                        }
                        navController.navigate("login") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    })
                }
                composable("login") {
                    LoginScreen(
                        viewModel = authViewModel,
                        onLoginSuccess = { navController.navigate("home") },
                        onSkipLogin = { navController.navigate("home") },
                        onRegisterClick = { navController.navigate("register") }
                    )
                }
                composable("register") {
                    RegisterScreen(
                        viewModel = authViewModel,
                        onBackClick = { navController.popBackStack() },
                        onRegisterSuccess = {
                            navController.navigate("home") {
                                popUpTo("register") { inclusive = true }
                            }
                        },
                        onLoginClick = { navController.navigate("login") }
                    )
                }
                composable("home") {
                    HomeScreen(
                        chatViewModel = chatViewModel,
                        onSendMessage = { chatViewModel.sendMessage(it) },
                        onQuickReplyClick = { question -> prefillText = question }
                    )
                }
                composable("history") {
                    HistoryScreen(
                        viewModel = historyViewModel,
                        authViewModel = authViewModel,
                        onThreadClick = { threadId ->
                            chatViewModel.setCurrentThread(threadId)
                            navController.navigate("home") {
                                popUpTo("history") { inclusive = false }
                            }
                        },
                        onViewSummary = { threadId ->
                            navController.navigate("summary/$threadId")
                        },
                        onLoginClick = {
                            navController.navigate("login")
                        },
                        onNewChatClick = {
                            chatViewModel.startNewChat()
                            navController.navigate("home") {
                                popUpTo("history") { inclusive = false }
                            }
                        }
                    )
                }
                composable("profile") {
                    ProfileScreen(
                        profileViewModel = profileViewModel,
                        authViewModel = authViewModel,
                        onLoginClick = {
                            navController.navigate("login")
                        }
                    )
                }
                composable("account_settings") {
                    AccountSettingsScreen(
                        authViewModel = authViewModel,
                        onBackClick = { navController.popBackStack() },
                        onLogoutSuccess = {
                            navController.navigate("login") {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onLoginClick = {
                            navController.navigate("login") {
                                popUpTo("home") { inclusive = false }
                            }
                        }
                    )
                }
                composable("help") {
                    HelpScreen()
                }
                composable("summary/{threadId}") { backStackEntry ->
                    val threadId =
                        backStackEntry.arguments?.getString("threadId") ?: return@composable
                    MedicalSummaryScreen(
                        threadId = threadId,
                        viewModel = historyViewModel,
                        onBackClick = {
                            navController.popBackStack()
                        },
                        onContinueChat = { id ->
                            chatViewModel.setCurrentThread(id)
                            navController.navigate("home") {
                                popUpTo("history") { inclusive = false }
                            }
                        }
                    )
                }
            }
        }
    }
}
