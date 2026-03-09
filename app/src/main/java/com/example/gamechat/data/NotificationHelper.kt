package com.example.gamechat.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.gamechat.MainActivity
import com.example.gamechat.R

object NotificationHelper {
    private const val CHANNEL_ID_CHAT = "chat_messages"
    private const val NOTIFICATION_ID_CHAT = 1001

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chatChannel = NotificationChannel(
                CHANNEL_ID_CHAT,
                context.getString(R.string.notification_channel_chat),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_chat_description)
                setShowBadge(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(chatChannel)
        }
    }

    fun showChatNotification(context: Context, senderName: String, messageText: String, roomName: String) {
        // Проверяем разрешена ли отправка уведомлений
        if (!UserPreferences.isChatNotificationsEnabled(context)) {
            return
        }

        // Создаем intent для открытия чата при нажатии на уведомление
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CHAT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_chat_title, roomName))
            .setContentText(context.getString(R.string.notification_chat_message, senderName, messageText))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(context.getString(R.string.notification_chat_message, senderName, messageText)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_CHAT, notification)
        } catch (e: SecurityException) {
            // Игнорируем ошибки разрешений - уведомление просто не покажется
        }
    }
}