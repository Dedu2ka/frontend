package com.example.olo.data

import com.example.olo.BuildConfig
import com.google.gson.GsonBuilder
import android.content.Context
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response as OkHttpResponse
import okhttp3.Route
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

data class AuthRequest(val username: String, val password: String)
data class AuthResponse(val token: String, val userId: String, val username: String, val refreshToken: String? = null, val expiresInSeconds: Long? = null)
data class RefreshRequest(val refreshToken: String)
data class LogoutRequest(val refreshToken: String? = null)
data class AuthErrorResponse(val message: String)
data class UserDto(
    val id: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?
)

data class UpdateProfileRequest(
    val displayName: String?,
    val avatarUrl: String?
)

data class ChatDto(
    val id: String,
    val type: String,
    val partnerName: String?,
    val partnerDisplayName: String? = null,
    val partnerUsername: String? = null,
    val partnerAvatarUrl: String? = null,
    val lastMessage: String? = null,
    val lastMessageAt: String? = null,
    val lastMessageSenderId: String? = null,
    val unreadCount: Long = 0
)

data class CreateChatRequest(val currentUserId: String, val targetUserId: String)
data class SendMessageRequest(
    val senderId: String,
    val content: String,
    val sentAt: String? = null,
    val clientMessageId: String? = null
)
data class SendDirectMessageRequest(
    val targetUserId: String,
    val content: String,
    val sentAt: String? = null,
    val clientMessageId: String? = null
)
data class WebSocketTicketResponse(val ticket: String, val expiresInSeconds: Long)
data class RegisterPushTokenRequest(val token: String)
data class MessageDto(
    val id: String,
    val chatId: String,
    val senderId: String,
    val content: String,
    val status: String?,
    val timestamp: String? = null,
    val type: String? = null,
    val clientMessageId: String? = null
)

interface ApiService {

    @POST("/api/v1/auth/login")
    suspend fun login(@Body request: AuthRequest): Response<AuthResponse>

    @POST("/api/v1/auth/register")
    suspend fun register(@Body request: AuthRequest): Response<AuthResponse>

    @POST("/api/v1/auth/logout")
    suspend fun logout(@Header("Authorization") token: String, @Body request: LogoutRequest? = null): Response<Unit>

    @POST("/api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<AuthResponse>

    @POST("/api/v1/notifications/device-token")
    suspend fun registerPushToken(
        @Header("Authorization") token: String,
        @Body request: RegisterPushTokenRequest
    ): Response<Unit>

    @DELETE("/api/v1/notifications/device-token")
    suspend fun unregisterPushToken(
        @Header("Authorization") token: String,
        @Query("token") pushToken: String
    ): Response<Unit>

    @GET("/api/v1/users/me")
    suspend fun getCurrentUser(
        @Header("Authorization") token: String,
        @Query("userId") userId: String
    ): Response<UserDto>

    @PUT("/api/v1/users/me")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Query("userId") userId: String,
        @Body request: UpdateProfileRequest
    ): Response<UserDto>

    @GET("/api/v1/users/search")
    suspend fun searchUsers(
        @Header("Authorization") token: String,
        @Query("query") query: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): Response<List<UserDto>>

    @POST("/api/v1/ws-ticket")
    suspend fun createWebSocketTicket(
        @Header("Authorization") token: String
    ): Response<WebSocketTicketResponse>

    @POST("/api/v1/chats/direct")
    suspend fun getOrCreateChat(
        @Header("Authorization") token: String,
        @Body request: CreateChatRequest
    ): Response<ChatDto>

    @GET("/api/v1/chats/direct/{targetUserId}")
    suspend fun findDirectChat(
        @Header("Authorization") token: String,
        @Path("targetUserId") targetUserId: String
    ): Response<ChatDto>

    @POST("/api/v1/chats/direct/messages")
    suspend fun sendDirectMessage(
        @Header("Authorization") token: String,
        @Body request: SendDirectMessageRequest
    ): Response<MessageDto>

    @GET("/api/v1/chats")
    suspend fun getUserChats(
        @Header("Authorization") token: String,
        @Query("userId") userId: String
    ): Response<List<ChatDto>>

    @GET("/api/v1/chats/{chatId}/messages")
    suspend fun getMessages(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: String,
        @Query("beforeMessageId") beforeMessageId: String? = null,
        @Query("limit") limit: Int = 50
    ): Response<List<MessageDto>>

    @POST("/api/v1/chats/{chatId}/read")
    suspend fun markChatRead(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: String
    ): Response<Unit>

    @POST("/api/v1/chats/{chatId}/messages")
    suspend fun sendMessage(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: String,
        @Body request: SendMessageRequest
    ): Response<MessageDto>

    companion object {
        fun create(context: Context? = null): ApiService {
            val builder = OkHttpClient.Builder()
            if (context != null) {
                val sessionStore = AuthSessionStore(context.applicationContext)
                builder.addInterceptor { chain ->
                    val request = chain.request()
                    val supplied = request.header("Authorization")
                    val latest = sessionStore.readSession()?.token
                    val updated = if (supplied?.startsWith("Bearer ") == true && !latest.isNullOrBlank()) {
                        request.newBuilder().header("Authorization", "Bearer $latest").build()
                    } else request
                    chain.proceed(updated)
                }
                builder.authenticator(SessionRefreshAuthenticator(context))
            } else builder.authenticator(Authenticator.NONE)
            val client = builder.build()
            return Retrofit.Builder()
                .client(client)
                .baseUrl(BuildConfig.BASE_URL)
                .addConverterFactory(
                    GsonConverterFactory.create(
                        GsonBuilder().serializeNulls().create()
                    )
                )
                .build()
                .create(ApiService::class.java)
        }
    }
}


private class SessionRefreshAuthenticator(context: Context) : Authenticator {
    private val store = AuthSessionStore(context.applicationContext)
    private val lock = Any()
    private val client = OkHttpClient.Builder().authenticator(Authenticator.NONE).build()
    private val gson = GsonBuilder().create()

    override fun authenticate(route: Route?, response: OkHttpResponse): Request? = synchronized(lock) {
        if (responseCount(response) >= 2) return null
        val oldToken = response.request.header("Authorization")?.removePrefix("Bearer ") ?: return null
        val current = store.readSession() ?: return null
        if (current.token != oldToken) return response.request.newBuilder().header("Authorization", "Bearer ${current.token}").build()
        val refreshToken = current.refreshToken ?: return null
        val requestBody = gson.toJson(RefreshRequest(refreshToken)).toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(BuildConfig.BASE_URL.trimEnd('/') + "/api/v1/auth/refresh").post(requestBody).build()
        val result = runCatching { client.newCall(request).execute().use { r -> if (r.isSuccessful) gson.fromJson(r.body?.string(), AuthResponse::class.java) else null } }.getOrNull() ?: return null
        val updated = AuthSession(current.username, result.userId, result.token, result.refreshToken)
        store.saveSession(updated)
        response.request.newBuilder().header("Authorization", "Bearer ${updated.token}").build()
    }

    private fun responseCount(response: OkHttpResponse): Int { var count = 1; var prior = response.priorResponse; while (prior != null) { count++; prior = prior.priorResponse }; return count }
}
