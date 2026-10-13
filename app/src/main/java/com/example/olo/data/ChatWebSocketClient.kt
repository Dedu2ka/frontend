package com.example.olo.data

import android.util.Log
import com.example.olo.BuildConfig
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit

data class WsPayload(
    val type: String? = null,
    val id: String? = null,
    val chatId: String? = null,
    val senderId: String? = null,
    val content: String? = null,
    val status: String? = null,
    val timestamp: String? = null,
    val message: String? = null,
    val clientMessageId: String? = null
)

enum class ConnectionStatus {
    CONNECTING,
    CONNECTED,
    SERVER_AVAILABLE,
    RECONNECTING,
    ERROR,
    DISCONNECTED
}

class ChatWebSocketClient(
    private val apiService: ApiService,
    private val token: String,
    private val onEvent: (WsPayload) -> Unit,
    private val baseUrl: String = BuildConfig.BASE_URL,
    private val onConnected: () -> Unit = {},
    private val onConnectionChanged: (ConnectionStatus) -> Unit = {}
) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    @Volatile private var stopped = false
    @Volatile private var connecting = false
    @Volatile private var socketConnected = false
    @Volatile private var retryAttempt = 0
    private var reconnectJob: Job? = null
    private var connectTimeoutJob: Job? = null

    @Synchronized
    fun connect() {
        if (stopped || connecting || webSocket != null) return
        onConnectionChanged(if (retryAttempt == 0) ConnectionStatus.CONNECTING else ConnectionStatus.RECONNECTING)
        connecting = true
        scope.launch {
            try {
                val response = apiService.createWebSocketTicket(bearerToken())
                val ticket = response.body()?.ticket
                if (!response.isSuccessful || ticket.isNullOrBlank()) {
                    Log.e("WS", "Не удалось получить ticket: HTTP ${response.code()}")
                    onConnectionChanged(ConnectionStatus.ERROR)
                    scheduleReconnect()
                    return@launch
                }

                val base = baseUrl.toHttpUrl()
                val socketUrl = base.newBuilder()
                    .scheme(if (base.isHttps) "wss" else "ws")
                    .addPathSegments("ws")
                    .addQueryParameter("ticket", ticket)
                    .build()

                val socket = client.newWebSocket(
                    Request.Builder().url(socketUrl).build(),
                    object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                            if (stopped || this@ChatWebSocketClient.webSocket !== webSocket) return
                            socketConnected = true
                            connectTimeoutJob?.cancel()
                            connectTimeoutJob = null
                            retryAttempt = 0
                            Log.d("WS", "Успешное подключение к backend")
                            onConnectionChanged(ConnectionStatus.CONNECTED)
                            onConnected()
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            if (stopped || this@ChatWebSocketClient.webSocket !== webSocket) return
                            runCatching { gson.fromJson(text, WsPayload::class.java) }
                                .onSuccess { onEvent(it) }
                                .onFailure { Log.e("WS", "Ошибка разбора WebSocket-события", it) }
                        }

                        override fun onFailure(
                            webSocket: WebSocket,
                            t: Throwable,
                            response: okhttp3.Response?
                        ) {
                            if (stopped || this@ChatWebSocketClient.webSocket !== webSocket) return
                            socketConnected = false
                            connectTimeoutJob?.cancel()
                            connectTimeoutJob = null
                            this@ChatWebSocketClient.webSocket = null
                            onConnectionChanged(ConnectionStatus.RECONNECTING)
                            Log.e("WS", "Ошибка соединения: ${t.message}", t)
                            scheduleReconnect()
                        }

                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                            if (stopped || this@ChatWebSocketClient.webSocket !== webSocket) return
                            socketConnected = false
                            connectTimeoutJob?.cancel()
                            connectTimeoutJob = null
                            this@ChatWebSocketClient.webSocket = null
                            onConnectionChanged(ConnectionStatus.RECONNECTING)
                            Log.w("WS", "Соединение закрыто ($code): $reason")
                            scheduleReconnect()
                        }
                    }
                )
                webSocket = socket
                connectTimeoutJob?.cancel()
                connectTimeoutJob = scope.launch {
                    delay(15_000)
                    synchronized(this@ChatWebSocketClient) {
                        if (!stopped && !socketConnected && this@ChatWebSocketClient.webSocket === socket) {
                            Log.e("WS", "Тайм-аут подключения WebSocket")
                            this@ChatWebSocketClient.webSocket = null
                            onConnectionChanged(ConnectionStatus.RECONNECTING)
                            socket.cancel()
                            scheduleReconnect()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("WS", "Ошибка получения WebSocket ticket", e)
                onConnectionChanged(ConnectionStatus.ERROR)
                scheduleReconnect()
            } finally {
                connecting = false
            }
        }
    }

    @Synchronized
    fun disconnect() {
        stopped = true
        socketConnected = false
        onConnectionChanged(ConnectionStatus.DISCONNECTED)
        reconnectJob?.cancel()
        reconnectJob = null
        connectTimeoutJob?.cancel()
        connectTimeoutJob = null
        webSocket?.close(1000, "Chat closed")
        webSocket = null
        scope.cancel()
    }

    @Synchronized
    private fun scheduleReconnect() {
        if (stopped || reconnectJob?.isActive == true) return
        val delayMillis = (1_000L shl retryAttempt.coerceAtMost(5)).coerceAtMost(30_000L)
        retryAttempt++
        reconnectJob = scope.launch {
            delay(delayMillis)
            synchronized(this@ChatWebSocketClient) { reconnectJob = null }
            connect()
        }
    }

    private fun bearerToken(): String =
        if (token.startsWith("Bearer ")) token else "Bearer $token"
}
