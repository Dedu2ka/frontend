package com.example.olo

import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.core.content.ContextCompat
import com.example.olo.data.ApiService
import com.example.olo.data.AppDatabase
import com.example.olo.data.AuthSession
import com.example.olo.data.AuthSessionStore
import com.example.olo.data.AccountRepository
import com.example.olo.data.AccountResult
import com.example.olo.data.UserRepository
import com.example.olo.data.UserResult
import com.example.olo.data.UpdateProfileRequest
import com.example.olo.data.PushNotifications
import com.example.olo.data.RegisterPushTokenRequest
import com.google.firebase.messaging.FirebaseMessaging
import com.example.olo.ui.*
import com.example.olo.ui.theme.OloTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

enum class Screen { LOADING, LOGIN, REGISTER, SEARCH, CHAT, SETTINGS }

class MainActivity : ComponentActivity() {

    private val apiService by lazy { ApiService.create(applicationContext) }
    private val authSessionStore by lazy { AuthSessionStore(applicationContext) }
    private val accountRepository by lazy { AccountRepository(apiService, authSessionStore) }
    private val userRepository by lazy { UserRepository(apiService) }
    private var pendingChatId by mutableStateOf<String?>(null)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Log.i("OloPush", "Пользователь не разрешил показывать уведомления")
    }

    private val chatViewModel: ChatViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(apiService, AppDatabase.getDatabase(applicationContext).pendingMessageDao()) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingChatId = intent?.getStringExtra(PushNotifications.EXTRA_CHAT_ID)
        PushNotifications.createChannel(this)
        requestNotificationPermission()
        setContent {
            OloTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val scope = rememberCoroutineScope()

                    var currentScreen by remember { mutableStateOf(Screen.LOADING) }
                    val screenBackStack = remember { mutableStateListOf<Screen>() }
                    var rememberedUsername by remember { mutableStateOf("") }
                    var userToken by remember { mutableStateOf("") }
                    var currentUserId by remember { mutableStateOf("") }
                    var currentUsername by remember { mutableStateOf("") }
                    var currentDisplayName by remember { mutableStateOf("") }
                    var currentAvatarUrl by remember { mutableStateOf("") }
                    var authError by remember { mutableStateOf<String?>(null) }

                    var activeChatName by remember { mutableStateOf("") }
                    var activeChatAvatarUrl by remember { mutableStateOf<String?>(null) }
                    var searchResults by remember { mutableStateOf(emptyList<UserItem>()) }

                    val chatListState by chatViewModel.chats.collectAsState()
                    val connectionStatus by chatViewModel.connectionStatus.collectAsState()
                    val chatErrorState by chatViewModel.errorState.collectAsState()
                    val activeChatIdState by chatViewModel.activeChatId.collectAsState()

                    LaunchedEffect(chatErrorState) {
                        chatErrorState?.let { message ->
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                            chatViewModel.clearError()
                        }
                    }

                    LaunchedEffect(currentScreen, activeChatIdState) {
                        getSharedPreferences("push_state", MODE_PRIVATE).edit()
                            .putString(
                                "visible_chat_id",
                                activeChatIdState.takeIf { currentScreen == Screen.CHAT }
                            )
                            .apply()
                    }

                    fun navigateTo(screen: Screen) {
                        if (screen != currentScreen) {
                            if (currentScreen == Screen.CHAT) chatViewModel.setChatVisible(false)
                            screenBackStack.add(currentScreen)
                            currentScreen = screen
                        }
                    }

                    fun navigateBack() {
                        if (screenBackStack.isNotEmpty()) {
                            val previous = screenBackStack.removeAt(screenBackStack.lastIndex)
                            if (currentScreen == Screen.CHAT) chatViewModel.setChatVisible(false)
                            currentScreen = previous
                            if (previous == Screen.CHAT) {
                                chatViewModel.activeChatId.value?.let {
                                    chatViewModel.openChat(userToken, currentUserId, it)
                                }
                            }
                        } else {
                            moveTaskToBack(true)
                        }
                    }

                    BackHandler(enabled = currentScreen != Screen.LOADING) { navigateBack() }

                    LaunchedEffect(Unit) {
                        rememberedUsername = authSessionStore.readSavedUsername().orEmpty()
                        val session = authSessionStore.readSession()
                        if (session == null) {
                            currentScreen = Screen.LOGIN
                        } else {
                            val profileResponse = runCatching {
                                apiService.getCurrentUser("Bearer ${session.token}", session.userId)
                            }.getOrNull()

                            if (profileResponse?.code() == 401 || profileResponse?.code() == 403) {
                                authSessionStore.clearSession()
                                currentScreen = Screen.LOGIN
                            } else {
                                userToken = session.token
                                currentUserId = session.userId
                                currentUsername = session.username
                                rememberedUsername = session.username
                                profileResponse?.takeIf { it.isSuccessful }?.body()?.let { user ->
                                    currentDisplayName = user.displayName ?: ""
                                    currentAvatarUrl = user.avatarUrl ?: ""
                                }
                                screenBackStack.clear()
                                chatViewModel.startSession(userToken, currentUserId)
                                chatViewModel.loadUserChats(userToken, currentUserId)
                                registerPushToken(session.token)
                                currentScreen = Screen.SEARCH
                            }
                        }
                    }

                    LaunchedEffect(pendingChatId, userToken, currentUserId) {
                        val requestedChatId = pendingChatId
                        if (requestedChatId.isNullOrBlank() || userToken.isBlank() || currentUserId.isBlank()) return@LaunchedEffect
                        runCatching {
                            apiService.getUserChats("Bearer $userToken", currentUserId)
                        }.getOrNull()?.body()?.firstOrNull { it.id == requestedChatId }?.let { chat ->
                            activeChatName = chat.partnerName ?: "Чат"
                            activeChatAvatarUrl = chat.partnerAvatarUrl
                            screenBackStack.clear()
                            chatViewModel.openChat(userToken, currentUserId, chat.id)
                            currentScreen = Screen.CHAT
                        }
                        pendingChatId = null
                    }

                    fun logout() {
                        chatViewModel.endSession()
                        val tokenToRevoke = userToken
                        val refreshTokenToRevoke = authSessionStore.readSession()?.refreshToken
                        if (tokenToRevoke.isNotBlank()) unregisterPushToken(tokenToRevoke)
                        if (tokenToRevoke.isNotBlank()) {
                            scope.launch {
                                runCatching { apiService.logout("Bearer $tokenToRevoke", com.example.olo.data.LogoutRequest(refreshTokenToRevoke)) }
                            }
                        }
                        userToken = ""
                        currentUserId = ""
                        currentUsername = ""
                        currentDisplayName = ""
                        currentAvatarUrl = ""
                        searchResults = emptyList()
                        authError = null
                        authSessionStore.clearSession()
                        screenBackStack.clear()
                        currentScreen = Screen.LOGIN
                    }

                    fun fetchUserProfile(token: String, userId: String) {
                        scope.launch {
                            try {
                                when (val result = userRepository.getProfile(token, userId)) {
                                    is UserResult.Success -> {
                                        currentDisplayName = result.value.displayName.orEmpty()
                                        currentAvatarUrl = result.value.avatarUrl.orEmpty()
                                    }
                                    is UserResult.Failure -> Toast.makeText(
                                        this@MainActivity,
                                        "Не удалось загрузить профиль (HTTP ${result.statusCode})",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Toast.makeText(
                                    this@MainActivity,
                                    "Не удалось подключиться к серверу для загрузки профиля",
                                    Toast.LENGTH_LONG
                                ).show()
                                Log.e("Profile", "Profile request failed", e)
                            }
                        }
                    }

                    fun finishAuthentication(session: AuthSession, loadProfile: Boolean) {
                        userToken = session.token
                        currentUserId = session.userId
                        currentUsername = session.username
                        rememberedUsername = session.username
                        registerPushToken(session.token)
                        chatViewModel.startSession(session.token, session.userId)
                        chatViewModel.loadUserChats(session.token, session.userId)
                        if (loadProfile) fetchUserProfile(session.token, session.userId)
                        authError = null
                        screenBackStack.clear()
                        currentScreen = Screen.SEARCH
                    }

                    when (currentScreen) {
                        Screen.LOADING -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF131541)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF6B8AFF))
                        }

                        Screen.LOGIN -> AuthScreen(
                            initialUsername = rememberedUsername,
                            errorMessage = authError,
                            onLoginClick = { username, password ->
                                scope.launch {
                                    try {
                                        when (val result = accountRepository.login(username, password)) {
                                            is AccountResult.Authenticated -> finishAuthentication(result.session, loadProfile = true)
                                            is AccountResult.Rejected -> authError = result.message
                                        }
                                    } catch (e: Exception) {
                                        Log.e("OloLogin", "Не удалось выполнить запрос входа", e)
                                        authError = "Ошибка подключения: ${e.javaClass.simpleName}: ${e.localizedMessage ?: "нет деталей"}"
                                    }
                                }
                            },
                            onNavigateToRegister = {
                                authError = null
                                navigateTo(Screen.REGISTER)
                            }
                        )

                        Screen.REGISTER -> RegisterScreen(
                            onRegisterClick = { username, password ->
                                scope.launch {
                                    try {
                                        when (val result = accountRepository.register(username, password)) {
                                            is AccountResult.Authenticated -> finishAuthentication(result.session, loadProfile = false)
                                            is AccountResult.Rejected -> Toast.makeText(
                                                this@MainActivity,
                                                result.message,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(this@MainActivity, "Ошибка сети", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onNavigateToLogin = { navigateBack() }
                        )

                        Screen.SEARCH -> SearchScreen(
                            userList = searchResults,
                            chatList = chatListState,
                            currentUserId = currentUserId,
                            connectionStatus = connectionStatus,
                            onSearchQueryChange = { query ->
                                scope.launch {
                                    try {
                                        if (query.isNotBlank()) {
                                            when (val result = userRepository.search(userToken, query)) {
                                                is UserResult.Success -> searchResults = result.value.map {
                                                    UserItem(
                                                        id = it.id,
                                                        username = it.username,
                                                        displayName = it.displayName,
                                                        avatarUrl = it.avatarUrl
                                                    )
                                                }
                                                is UserResult.Failure -> {
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "Поиск не загрузился (HTTP ${result.statusCode})",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                }
                                            }
                                        } else {
                                            searchResults = emptyList()
                                        }
                                    } catch (e: Exception) {
                                        if (e is CancellationException) throw e
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Не удалось подключиться к серверу для поиска",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        Log.e("Search", "User search failed", e)
                                    }
                                }
                            },
                            onUserClick = { selectedUser ->
                                chatViewModel.initChatWithUser(
                                    userToken = userToken,
                                    currentUserId = currentUserId,
                                    targetUserId = selectedUser.id,
                                    targetName = selectedUser.displayName?.takeIf { it.isNotBlank() }
                                        ?: selectedUser.username,
                                    targetAvatarUrl = selectedUser.avatarUrl,
                                    onChatReady = { partnerName, partnerAvatarUrl ->
                                        activeChatName = partnerName
                                        activeChatAvatarUrl = partnerAvatarUrl
                                        navigateTo(Screen.CHAT)
                                    }
                                )
                            },
                            onChatClick = { chatId ->
                                val foundChat = chatListState.find { it.id == chatId }
                                activeChatName = foundChat?.partnerName ?: "Чат"
                                activeChatAvatarUrl = foundChat?.partnerAvatarUrl

                                chatViewModel.openChat(userToken, currentUserId, chatId)
                                navigateTo(Screen.CHAT)
                            },
                            onNavigateToSettings = { navigateTo(Screen.SETTINGS) },
                            onNavigateToSearch = {
                                chatViewModel.loadUserChats(userToken, currentUserId)
                                navigateTo(Screen.SEARCH)
                            }
                        )

                        Screen.CHAT -> {
                            val messagesState by chatViewModel.messages.collectAsState()
                            val hasOlderMessages by chatViewModel.hasOlderMessages.collectAsState()
                            val loadingOlderMessages by chatViewModel.loadingOlderMessages.collectAsState()

                            val uiMessages = messagesState.map { msg ->
                                MessageUiItem(
                                    id = msg.id,
                                    senderId = msg.senderId,
                                    content = msg.content,
                                    timestamp = msg.timestamp,
                                    status = msg.status
                                )
                            }

                            ChatScreen(
                                currentUserId = currentUserId,
                                chatPartnerName = activeChatName,
                                chatPartnerAvatarUrl = activeChatAvatarUrl,
                                messages = uiMessages,
                                connectionStatus = connectionStatus,
                                onSendMessage = { newMsgText ->
                                    chatViewModel.sendMessage(
                                        userToken = userToken,
                                        senderId = currentUserId,
                                        content = newMsgText
                                    )
                                },
                                hasOlderMessages = hasOlderMessages,
                                loadingOlderMessages = loadingOlderMessages,
                                onLoadOlderMessages = { chatViewModel.loadOlderMessages(userToken, chatViewModel.activeChatId.value ?: "") },
                                onNavigateToSettings = { navigateTo(Screen.SETTINGS) },
                                onNavigateToSearch = {
                                    chatViewModel.loadUserChats(userToken, currentUserId)
                                    navigateTo(Screen.SEARCH)
                                }
                            )
                        }

                        Screen.SETTINGS -> SettingsScreen(
                            currentUsername = currentUsername,
                            currentDisplayName = currentDisplayName,
                            currentAvatarUrl = currentAvatarUrl,
                            onSaveProfile = { newDisplayName, newAvatarUrl ->
                                scope.launch {
                                    try {
                                        when (val result = userRepository.updateProfile(
                                            userToken,
                                            currentUserId,
                                            UpdateProfileRequest(newDisplayName, newAvatarUrl)
                                        )) {
                                            is UserResult.Success -> {
                                                currentDisplayName = result.value.displayName.orEmpty()
                                                currentAvatarUrl = result.value.avatarUrl.orEmpty()
                                                chatViewModel.loadUserChats(userToken, currentUserId)
                                                Toast.makeText(this@MainActivity, "Профиль сохранен!", Toast.LENGTH_SHORT).show()
                                            }
                                            is UserResult.Failure -> {
                                                val message = result.message?.let {
                                                    "Не удалось сохранить профиль: $it"
                                                } ?: "Не удалось сохранить профиль (HTTP ${result.statusCode})"
                                                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        if (e is CancellationException) throw e
                                        Log.e("Profile", "Profile update request failed", e)
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Не удалось связаться с сервером. Проверь подключение и попробуй снова.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            onNavigateToSettings = { navigateTo(Screen.SETTINGS) },
                            onNavigateToSearch = {
                                chatViewModel.loadUserChats(userToken, currentUserId)
                                navigateTo(Screen.SEARCH)
                            },
                            onLogout = { logout() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingChatId = intent.getStringExtra(PushNotifications.EXTRA_CHAT_ID)
    }

    override fun onStart() {
        super.onStart()
        getSharedPreferences("push_state", MODE_PRIVATE).edit().putBoolean("app_visible", true).apply()
        chatViewModel.onHostResumed()
    }

    override fun onStop() {
        chatViewModel.onHostPaused()
        getSharedPreferences("push_state", MODE_PRIVATE).edit()
            .putBoolean("app_visible", false)
            .putString("visible_chat_id", null)
            .apply()
        super.onStop()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun registerPushToken(authToken: String) {
        try {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    lifecycleScope.launch {
                        runCatching {
                            apiService.registerPushToken(
                                if (authToken.startsWith("Bearer ")) authToken else "Bearer $authToken",
                                RegisterPushTokenRequest(token)
                            )
                        }.onFailure { Log.e("OloPush", "Не удалось зарегистрировать устройство", it) }
                    }
                }
                .addOnFailureListener { Log.e("OloPush", "Не удалось получить FCM-токен", it) }
        } catch (exception: Exception) {
            Log.i("OloPush", "Push пока не настроен в Firebase", exception)
        }
    }

    private fun unregisterPushToken(authToken: String) {
        try {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                lifecycleScope.launch {
                    runCatching {
                        apiService.unregisterPushToken(
                            if (authToken.startsWith("Bearer ")) authToken else "Bearer $authToken",
                            token
                        )
                    }.onFailure { Log.e("OloPush", "Не удалось удалить push-токен", it) }
                }
            }
        } catch (exception: Exception) {
            Log.i("OloPush", "Push не настроен; регистрацию токена пропускаю", exception)
        }
    }
}
