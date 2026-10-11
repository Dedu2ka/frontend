package com.example.olo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [MessageEntity::class, PendingMessageEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun pendingMessageDao(): PendingMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS pending_messages (
                        clientMessageId TEXT NOT NULL PRIMARY KEY,
                        senderId TEXT NOT NULL,
                        chatId TEXT,
                        recipientId TEXT,
                        content TEXT NOT NULL,
                        sentAtUtc TEXT NOT NULL
                    )""".trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_pending_messages_senderId " +
                        "ON pending_messages(senderId)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "olo_db"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}