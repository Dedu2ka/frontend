package com.example.olo.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PendingMessageDao {
    @Query("SELECT * FROM pending_messages WHERE senderId = :senderId ORDER BY sentAtUtc ASC, clientMessageId ASC")
    suspend fun getPendingMessages(senderId: String): List<PendingMessageEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(message: PendingMessageEntity)

    @Query("DELETE FROM pending_messages WHERE clientMessageId = :clientMessageId")
    suspend fun remove(clientMessageId: String)
}
