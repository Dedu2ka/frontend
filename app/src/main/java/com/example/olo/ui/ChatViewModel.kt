package com.example.olo.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.olo.data.ApiService
import com.example.olo.data.ChatDto
import com.example.olo.data.ChatWebSocketClient
import com.example.olo.data.MessageDto
import com.example.olo.data.OutboxResult
import com.example.olo.data.PendingMessageDao
import com.example.olo.data.PendingMessageEntity
import com.example.olo.data.PendingMessageOutbox
import com.example.olo.data.WsPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class ChatViewModel(
    private val apiService: ApiService,
    private val pendingMessageDao: PendingMessageDao
) : ViewModel() {

    private val outbox = PendingMessageOutbox(apiService, pendingMessageDao)

    private val _activeChatId = MutableStateFlow<String?>(null)
    val activeChatId: StateFlow<String?> = _activeChatId
    private val _chatVisible = MutableStateFlow(false)
    private val _messages = MutableStateFlow<List<MessageDto>>(emptyList())
    val messages: StateFlow<List<MessageDto>> = _messages
    private val _chats = MutableStateFlow<List<ChatDto>>(emptyList())
    val chats: StateFlow<List<ChatDto>> = _chats
    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState

    fun clearError() {
        _errorState.value = null
    }

    private var webSocketClient: ChatWebSocketClient? = null
    private var realtimeSyncJob: Job? = null
    private var sessionJob = SupervisorJob(viewModelScope.coroutineContext[Job])
    private var sessionScope = CoroutineScope(viewModelScope.coroutineContext + sessionJob)
    private var activeUserId: String? = null
    private var pendingRecipientId: String? = null
    private val outboxMutex = Mutex()
    private var activeToken: String = ""

    // Замена Instant.now() (требует API 26, а minSdk = 24)
    private fun nowUtcIso(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

    fun startSession(userToken: String, userId: String) {
        val sessionChanged = activeUserId != userId || activeToken != userToken || !sessionJob.isActive
        if (sessionChanged) {
            realtimeSyncJob?.cancel()
            realtimeSyncJob = null
            sessionJob.cancel()
            sessionJob = SupervisorJob(viewModelScope.coroutineContext[Job])
            sessionScope = CoroutineScope(viewModelScope.coroutineContext + sessionJob)
            disconnectWebSocket()

            if (activeUserId != null && activeUserId != userId) {
                _activeChatId.value = null
                _chatVisible.value = false
                pendingRecipientId = null
                _messages.value = emptyList()
                _chats.value = emptyList()
            }
            activeToken = userToken
            activeUserId = userId
        }
        if (webSocketClient == null) connectWebSocket(userToken)
        startRealtimeSync()
    }

    fun endSession() {
        // Cancel every in-flight request before clearing visible account data.
        sessionJob.cancel()
        realtimeSyncJob?.cancel()
        realtimeSyncJob = null
        disconnectWebSocket()
        activeToken = ""
        activeUserId = null
        _activeChatId.value = null
        _chatVisible.value = false
        pendingRecipientId = null
        _messages.value = emptyList()
        _chats.value = emptyList()
        _errorState.value = null
    }

    fun onHostResumed() {
        val userId = activeUserId ?: return
        val token = activeToken.takeIf { it.isNotBlank() } ?: return
        startSession(token, userId)
    }

    fun onHostPaused() {
        realtimeSyncJob?.cancel()
        realtimeSyncJob = null
    }

    private fun startRealtimeSync() {
        if (realtimeSyncJob?.isActive == true) return
        realtimeSyncJob = sessionScope.launch {
            while (isActive) {
                synchronizeVisibleData()
                delay(2_000)
            }
        }
    }

    private suspend fun synchronizeVisibleData() {
        val userId = activeUserId ?: return
        val token = activeToken.takeIf { it.isNotBlank() } ?: return
        flushPendingMessages()
        if (_chatVisible.value) {
            _activeChatId.value?.let { synchronizeActiveChat(token, userId, it) }
        }
        refreshUserChats(bearer(token), userId, reportErrors = false)
    }

    fun loadUserChats(userToken: String, userId: String) {
        val authToken = bearer(userToken)
        sessionScope.launch {
            refreshUserChats(authToken, userId, reportErrors = true)
        }
    }

    private suspend fun refreshUserChats(authToken: String, userId: String, reportErrors: Boolean) {
        try {
            val response = apiService.getUserChats(authToken, userId)
            val chats = response.body()
            if (response.isSuccessful && chats != null) {
                _chats.value = chats
                if (reportErrors) _errorState.value = null
            } else {
                Log.e("ChatViewModel", "Ошибка загрузки списка чатов: HTTP ${response.code()}")
                if (reportErrors) _errorState.value = "Не удалось загрузить чаты (HTTP ${response.code()})"
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("ChatViewModel", "Исключение при загрузке списка чатов", e)
            if (reportErrors) _errorState.value = "Ошибка загрузки чатов: ${e.javaClass.simpleName}"
        }
    }

    private suspend fun synchronizeActiveChat(userToken: String, userId: String, chatId: String) {
        val authToken = bearer(userToken)
        try {
            val response = apiService.getMessages(authToken, chatId)
            if (!response.isSuccessful) {
                Log.e("ChatViewModel", "Ошибка синхронизации сообщений: HTTP ${response.code()}")
                return
            }
            val latestMessages = response.body() ?: return
            val oldIds = _messages.value.asSequence().map { it.id }.toHashSet()
            _messages.value = latestMessages

            val hasNewIncoming = latestMessages.any {
                it.senderId != userId && it.id !in oldIds
            }
            if (hasNewIncoming) {
                val readResponse = apiService.markChatRead(authToken, chatId)
                if (readResponse.isSuccessful) {
                    val refreshed = apiService.getMessages(authToken, chatId)
                    if (refreshed.isSuccessful) _messages.value = refreshed.body().orEmpty()
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("ChatViewModel", "Исключение при синхронизации открытого чата", e)
        }
    }

    fun setActiveChat(chatId: String) {
        _activeChatId.value = chatId
    }

    fun setChatVisible(visible: Boolean) {
        _chatVisible.value = visible
    }

    fun initChatWithUser(
        userToken: String,
        currentUserId: String,
        targetUserId: String,
        targetName: String,
        targetAvatarUrl: String?,
        onChatReady: (partnerName: String, partnerAvatarUrl: String?) -> Unit
    ) {
        startSession(userToken, currentUserId)
        val authToken = bearer(userToken)
        sessionScope.launch {
            try {
                val response = apiService.findDirectChat(authToken, targetUserId)
                if (response.code() == 404) {
                    _activeChatId.value = null
                    pendingRecipientId = targetUserId
                    _messages.value = emptyList()
                    _chatVisible.value = true
                    restorePendingDraftMessages(currentUserId, targetUserId)
                    onChatReady(targetName, targetAvatarUrl)
                    return@launch
                }

                val chat = response.body()
                if (!response.isSuccessful || chat == null) {
                    Log.e("ChatViewModel", "Ошибка поиска существующего чата: HTTP ${response.code()}")
                    _errorState.value = "Не удалось открыть чат"
                    return@launch
                }

                pendingRecipientId = null
                if (_activeChatId.value != chat.id) _messages.value = emptyList()
                _activeChatId.value = chat.id
                _chatVisible.value = true
                onChatReady(
                    chat.partnerName?.takeIf { it.isNotBlank() } ?: targetName,
                    chat.partnerAvatarUrl ?: targetAvatarUrl
                )
                openChat(userToken, currentUserId, chat.id)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("ChatViewModel", "Исключение при поиске чата", e)
                _activeChatId.value = null
                pendingRecipientId = targetUserId
                _messages.value = emptyList()
                _chatVisible.value = true
                restorePendingDraftMessages(currentUserId, targetUserId)
                onChatReady(targetName, targetAvatarUrl)
            }
        }
    }

    fun loadMessages(userToken: String, chatId: String) {
        sessionScope.launch { refreshMessages(bearer(userToken), chatId) }
    }

    fun openChat(userToken: String, userId: String, chatId: String) {
        val authToken = bearer(userToken)
        if (_activeChatId.value != chatId) _messages.value = emptyList()
        pendingRecipientId = null
        _activeChatId.value = chatId
        _chatVisible.value = true
        startSession(userToken, userId)
        sessionScope.launch {
            restorePendingChatMessages(userId, chatId)
            try {
                val response = apiService.markChatRead(authToken, chatId)
                if (!response.isSuccessful) Log.e("ChatViewModel", "Ошибка отметки прочтения: ${response.code()}")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("ChatViewModel", "Исключение при отметке прочтения", e)
            }
            refreshMessages(authToken, chatId)
            loadUserChats(userToken, userId)
        }
    }

    private suspend fun refreshMessages(authToken: String, chatId: String) {
        try {
            val res = apiService.getMessages(authToken, chatId)
            if (res.isSuccessful && res.body() != null) {
                val senderId = activeUserId
                val pending = if (senderId == null) emptyList() else {
                    outbox.pendingFor(senderId)
                        .filter { it.chatId == chatId }
                        .map(outbox::toMessageDto)
                }
                _messages.value = (res.body().orEmpty() + pending)
                    .distinctBy { it.id }
                    .sortedBy { it.timestamp.orEmpty() }
            } else Log.e("ChatViewModel", "Ошибка загрузки сообщений: ${res.code()}")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("ChatViewModel", "Исключение при загрузке сообщений", e)
        }
    }

    private suspend fun restorePendingChatMessages(senderId: String, chatId: String) {
        val pending = outbox.pendingFor(senderId)
            .filter { it.chatId == chatId }
            .map(outbox::toMessageDto)
        _messages.value = (_messages.value + pending).distinctBy { it.id }
            .sortedBy { it.timestamp.orEmpty() }
    }

    private suspend fun restorePendingDraftMessages(senderId: String, recipientId: String) {
        val pending = outbox.pendingFor(senderId)
            .filter { it.recipientId == recipientId }
            .map(outbox::toMessageDto)
        _messages.value = (_messages.value + pending).distinctBy { it.id }
            .sortedBy { it.timestamp.orEmpty() }
    }

    // Мьютекс защищает от параллельной отправки одной и той же очереди
    // (вызывается и из цикла синхронизации, и из sendMessage).
    private suspend fun flushPendingMessages() {
        outboxMutex.withLock {
            try {
                val senderId = activeUserId ?: return
                val token = activeToken.takeIf { it.isNotBlank() } ?: return
                val results = outbox.drain(senderId, token) {
                    sessionJob.isActive && activeUserId == senderId && activeToken == token
                }
                for (result in results) {
                    when (result) {
                        is OutboxResult.Sent -> {
                            val pending = result.pending
                            val sent = result.message
                            val wasOpenDraft = pending.recipientId != null && pendingRecipientId == pending.recipientId
                            val belongsToOpenChat = pending.chatId != null && _activeChatId.value == pending.chatId
                            if (wasOpenDraft || belongsToOpenChat) {
                                if (wasOpenDraft) {
                                    pendingRecipientId = null
                                    _activeChatId.value = sent.chatId
                                }
                                _messages.value = _messages.value
                                    .filterNot { it.id == pending.clientMessageId || it.clientMessageId == pending.clientMessageId }
                                    .filterNot { it.id == sent.id } + sent
                                refreshMessages(bearer(token), sent.chatId)
                            }
                            loadUserChats(token, senderId)
                        }
                        is OutboxResult.Rejected -> {
                            _messages.value = _messages.value.filterNot {
                                it.id == result.pending.clientMessageId || it.clientMessageId == result.pending.clientMessageId
                            }
                            _errorState.value = "Не удалось отправить сообщение (HTTP ${result.statusCode})"
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.d("ChatViewModel", "Офлайн-очередь ждёт подключения", e)
            }
        }
    }

    private fun connectWebSocket(userToken: String) {
        webSocketClient?.disconnect()
        val connectionSession = sessionJob
        webSocketClient = ChatWebSocketClient(
            apiService,
            userToken,
            onEvent = { event ->
                if (sessionJob === connectionSession && connectionSession.isActive) {
                    handleWebSocketEvent(event)
                }
            },
            onConnected = {
                if (sessionJob === connectionSession && connectionSession.isActive) {
                    synchronizeAfterWebSocketConnect()
                }
            }
        ).also { it.connect() }
    }

    private fun synchronizeAfterWebSocketConnect() {
        val userId = activeUserId ?: return
        val token = activeToken.takeIf { it.isNotBlank() } ?: return
        loadUserChats(token, userId)
        if (_chatVisible.value) _activeChatId.value?.let { loadMessages(token, it) }
    }

    fun disconnectWebSocket() {
        webSocketClient?.disconnect()
        webSocketClient = null
    }

    private fun handleWebSocketEvent(event: WsPayload) {
        sessionScope.launch {
            when (event.type) {
                "NEW_MESSAGE" -> {
                    val chatId = event.chatId?.takeIf { it.isNotBlank() } ?: return@launch
                    val messageId = event.id?.takeIf { it.isNotBlank() } ?: return@launch
                    val message = MessageDto(
                        id = messageId,
                        chatId = chatId,
                        senderId = event.senderId.orEmpty(),
                        content = event.content.orEmpty(),
                        status = event.status ?: "SENT",
                        timestamp = event.timestamp,
                        type = event.type,
                        clientMessageId = event.clientMessageId
                    )
                    val userId = activeUserId
                    if (userId != null && message.senderId == userId && message.clientMessageId != null) {
                        return@launch
                    }
                    val currentChatIsOpen = _chatVisible.value && _activeChatId.value == chatId
                    if (currentChatIsOpen) {
                        _messages.value = _messages.value.filterNot { it.id == message.id } + message
                    }

                    if (userId != null && activeToken.isNotBlank()) {
                        if (currentChatIsOpen && message.senderId != userId) {
                            runCatching { apiService.markChatRead(bearer(activeToken), chatId) }
                                .onFailure { Log.e("ChatViewModel", "Не удалось отметить входящее прочитанным", it) }
                            refreshMessages(bearer(activeToken), chatId)
                        }
                        loadUserChats(activeToken, userId)
                    }
                }
                "MESSAGE_STATUS" -> {
                    if (event.chatId != _activeChatId.value) return@launch
                    val status = event.status ?: return@launch
                    _messages.value = _messages.value.map { message ->
                        if (message.id == event.id) message.copy(status = status) else message
                    }
                }
            }
        }
    }

    fun sendMessage(userToken: String, senderId: String, content: String) {
        if (activeUserId != senderId || activeToken != userToken) {
            Log.e("ChatViewModel", "Отправка отменена: активная сессия изменилась")
            return
        }
        val chatId = _activeChatId.value
        val recipientId = pendingRecipientId
        if (chatId.isNullOrBlank() && recipientId.isNullOrBlank()) {
            Log.e("ChatViewModel", "Невозможно отправить сообщение: нет активного чата или адресата")
            return
        }
        val pending = PendingMessageEntity(
            clientMessageId = UUID.randomUUID().toString(),
            senderId = senderId,
            chatId = chatId?.takeIf { it.isNotBlank() },
            recipientId = if (chatId.isNullOrBlank()) recipientId else null,
            content = content,
            sentAtUtc = nowUtcIso()
        )
        sessionScope.launch {
            try {
                outbox.enqueue(pending)
                _messages.value = (_messages.value + outbox.toMessageDto(pending)).distinctBy { it.id }
                    .sortedBy { it.timestamp.orEmpty() }
                flushPendingMessages()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("ChatViewModel", "Не удалось сохранить сообщение в офлайн-очередь", e)
                _errorState.value = "Не удалось сохранить сообщение на устройстве"
            }
        }
    }

    private fun bearer(token: String): String = if (token.startsWith("Bearer ")) token else "Bearer $token"

    override fun onCleared() {
        sessionJob.cancel()
        realtimeSyncJob?.cancel()
        disconnectWebSocket()
        super.onCleared()
    }
}