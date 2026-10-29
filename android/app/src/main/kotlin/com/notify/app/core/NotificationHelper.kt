package com.notify.app.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.notify.app.MainActivity

object NotificationHelper {
    const val CHANNEL_NOTICES = "channel_notices"
    const val CHANNEL_NOTICES_NAME = "College Notices"
    const val CHANNEL_ALERTS = "channel_alerts"
    const val CHANNEL_ALERTS_NAME = "High Priority Announcements"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val noticesChannel = NotificationChannel(
                CHANNEL_NOTICES,
                CHANNEL_NOTICES_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Standard college notice updates and announcements"
                enableLights(true)
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                CHANNEL_ALERTS_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent college notifications, deadlines, and critical alerts"
                enableLights(true)
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(noticesChannel)
            notificationManager.createNotificationChannel(alertsChannel)
        }
    }

    fun showNoticeNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        noticeId: String,
        isHighPriority: Boolean = false
    ) {
        val channelId = if (isHighPriority) CHANNEL_ALERTS else CHANNEL_NOTICES

        // Deep link intent: notify://notice/{noticeId}
        val deepLinkIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("notify://notice/$noticeId"),
            context,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notice_id", noticeId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (isHighPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val manager = NotificationManagerCompat.from(context)
            manager.notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Android 13+ POST_NOTIFICATIONS permission not granted
        }
    }
}
