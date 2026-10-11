package com.example.olo.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

sealed interface OutboxResult {
    data class Sent(val pending: PendingMessageEntity, val message: MessageDto) : OutboxResult
    data class Rejected(val pending: PendingMessageEntity, val statusCode: Int) : OutboxResult
}

/** Persists unsent messages and retries them in order with the server's idempotency key. */
class PendingMessageOutbox(
    private val api: ApiService,
    private val dao: PendingMessageDao
) {
    private val drainMutex = Mutex()

    suspend fun enqueue(message: PendingMessageEntity) = dao.enqueue(message)
    suspend fun remove(clientMessageId: String) = dao.remove(clientMessageId)
    suspend fun pendingFor(senderId: String) = dao.getPendingMessages(senderId)

    suspend fun drain(
        senderId: String,
        token: String,
        isSessionActive: () -> Boolean
    ): List<OutboxResult> = drainMutex.withLock {
        val results = mutableListOf<OutboxResult>()
        for (pending in dao.getPendingMessages(senderId)) {
            if (!isSessionActive()) break
            val response = if (pending.chatId.isNullOrBlank()) {
                val recipientId = pending.recipientId ?: continue
                api.sendDirectMessage(
                    bearer(token),
                    SendDirectMessageRequest(
                        targetUserId = recipientId,
                        content = pending.content,
                        sentAt = pending.sentAtUtc,
                        clientMessageId = pending.clientMessageId
                    )
                )
            } else {
                api.sendMessage(
                    bearer(token),
                    pending.chatId,
                    SendMessageRequest(
                        senderId = senderId,
                        content = pending.content,
                        sentAt = pending.sentAtUtc,
                        clientMessageId = pending.clientMessageId
                    )
                )
            }

            if (response.isSuccessful) {
                val sent = response.body() ?: break
                dao.remove(pending.clientMessageId)
                results += OutboxResult.Sent(pending, sent)
            } else if (response.code() in 400..499 && response.code() != 401) {
                dao.remove(pending.clientMessageId)
                results += OutboxResult.Rejected(pending, response.code())
            } else {
                break
            }
        }
        results
    }

    fun toMessageDto(pending: PendingMessageEntity) = MessageDto(
        id = pending.clientMessageId,
        chatId = pending.chatId.orEmpty(),
        senderId = pending.senderId,
        content = pending.content,
        status = "PENDING",
        timestamp = pending.sentAtUtc,
        clientMessageId = pending.clientMessageId
    )

    private fun bearer(token: String): String = if (token.startsWith("Bearer ")) token else "Bearer $token"
}
