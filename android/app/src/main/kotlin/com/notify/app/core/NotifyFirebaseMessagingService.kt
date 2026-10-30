package com.notify.app.core

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.notify.app.data.repositories.RepositoryLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotifyFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Received FCM registration token: ${token.take(15)}...")
        
        TokenManager.saveToken(applicationContext, token)
        serviceScope.launch {
            try {
                RepositoryLocator.notificationRepository.registerDeviceToken(token)
                Log.d(TAG, "Device token registered to Supabase successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error registering FCM token: ${e.message}", e)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM message received from: ${remoteMessage.from}")

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "New College Notice"

        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: "A new announcement has been published."

        val noticeId = remoteMessage.data["notice_id"] ?: "general"
        val isHighPriority = remoteMessage.priority == RemoteMessage.PRIORITY_HIGH ||
                remoteMessage.data["priority"] == "high" ||
                remoteMessage.data["channel_id"] == NotificationHelper.CHANNEL_ALERTS

        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        // Save into local notification inbox
        val userNotif = com.notify.app.domain.models.UserNotification(
            id = "fcm_$notificationId",
            userId = SessionManager.getSavedProfile(applicationContext)?.id ?: "guest",
            noticeId = if (noticeId == "general" || noticeId.isBlank()) null else noticeId,
            title = title,
            body = body,
            notificationType = if (isHighPriority) "ALERT" else "NEW_NOTICE",
            isRead = false,
            createdAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date())
        )
        SessionManager.addNotification(applicationContext, userNotif)

        NotificationHelper.showNoticeNotification(
            context = applicationContext,
            notificationId = notificationId,
            title = title,
            body = body,
            noticeId = noticeId,
            isHighPriority = isHighPriority
        )
    }

    companion object {
        private const val TAG = "NotifyFCM"
    }
}
