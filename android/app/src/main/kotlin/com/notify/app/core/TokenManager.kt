package com.notify.app.core

import android.content.Context
import android.util.Log
import com.notify.app.data.repositories.RepositoryLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object TokenManager {
    private const val TAG = "TokenManager"
    private const val PREFS_NAME = "notify_fcm_prefs"
    private const val KEY_FCM_TOKEN = "fcm_device_token"

    @Volatile
    var cachedToken: String? = null
        private set

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun saveToken(context: Context, token: String) {
        cachedToken = token
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
        Log.d(TAG, "Cached FCM token locally: ${token.take(15)}...")
    }

    fun getToken(context: Context): String? {
        if (cachedToken != null) return cachedToken
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_FCM_TOKEN, null)
        cachedToken = saved
        return saved
    }

    fun syncStoredToken(context: Context) {
        val token = getToken(context) ?: return
        scope.launch {
            try {
                RepositoryLocator.notificationRepository.registerDeviceToken(token)
                Log.d(TAG, "Synced stored token with backend: ${token.take(15)}...")
            } catch (e: Exception) {
                Log.e(TAG, "Failed syncing stored token: ${e.message}", e)
            }
        }
    }
}
