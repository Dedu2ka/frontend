package com.example.olo.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.olo.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object PushNotifications {
    const val CHANNEL_ID = "olo_messages"
    const val EXTRA_CHAT_ID = "olo_chat_id"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Сообщения",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Уведомления о новых сообщениях"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

class OloFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val session = AuthSessionStore(applicationContext).readSession() ?: return@launch
            runCatching {
                ApiService.create().registerPushToken(
                    "Bearer ${session.token}",
                    RegisterPushTokenRequest(token)
                )
            }.onFailure { Log.e(TAG, "Не удалось обновить FCM-токен на сервере", it) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val chatId = message.data["chatId"] ?: return
        val appState = getSharedPreferences("push_state", Context.MODE_PRIVATE)
        if (appState.getBoolean("app_visible", false) && appState.getString("visible_chat_id", null) == chatId) {
            return
        }
        val title = message.data["title"] ?: "Новое сообщение"
        val body = message.data["body"] ?: "Откройте чат, чтобы прочитать сообщение"
        PushNotifications.createChannel(this)

        val openChatIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(PushNotifications.EXTRA_CHAT_ID, chatId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            chatId.hashCode(),
            openChatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, PushNotifications.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(
                (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
                notification
            )
        } catch (exception: SecurityException) {
            Log.w(TAG, "Нет разрешения показывать уведомления", exception)
        }
    }

    private companion object {
        const val TAG = "OloPush"
    }
}
