package com.example.olo.data

import com.example.olo.BuildConfig
import com.google.gson.GsonBuilder
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

data class AuthRequest(val username: String, val password: String)
data class AuthResponse(val token: String, val userId: String, val username: String)
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
    val partnerAvatarUrl: String? = null,
    val lastMessage: String? = null,
    val lastMessageAt: String? = null,
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
    suspend fun logout(@Header("Authorization") token: String): Response<Unit>

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
        @Path("chatId") chatId: String
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
        fun create(): ApiService {
            return Retrofit.Builder()
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
