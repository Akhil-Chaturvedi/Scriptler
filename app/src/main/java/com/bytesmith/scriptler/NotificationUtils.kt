package com.bytesmith.scriptler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationUtils {

    private const val CHANNEL_ID = "scriptler_channel"
    private const val CHANNEL_NAME = "Scriptler Notifications"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for script execution results"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun sendNotification(context: Context, title: String, message: String) {
        createNotificationChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val truncatedMessage = truncateForNotification(message)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_code)
            .setContentTitle(title)
            .setContentText(truncatedMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(truncatedMessage))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    /**
     * Truncate text for notification display.
     * Notifications should not show full script output — just a preview.
     */
    fun truncateForNotification(text: String, maxLength: Int = 200): String {
        val singleLine = text.lines().firstOrNull { it.isNotBlank() } ?: text
        return if (singleLine.length <= maxLength) singleLine
        else singleLine.take(maxLength) + "…"
    }

    fun cancelAllNotifications(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()
    }
}
