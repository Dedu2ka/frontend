package com.example.olo.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "pending_messages", indices = [Index(value = ["senderId"])])
data class PendingMessageEntity(
    @PrimaryKey val clientMessageId: String,
    val senderId: String,
    val chatId: String?,
    val recipientId: String?,
    val content: String,
    val sentAtUtc: String
)
