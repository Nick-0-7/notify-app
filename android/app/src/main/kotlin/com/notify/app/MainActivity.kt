package com.notify.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.notify.app.core.NotificationHelper
import com.notify.app.core.TokenManager
import com.notify.app.ui.navigation.AppNavigation
import com.notify.app.ui.theme.NotifyTheme
import io.github.jan.supabase.auth.handleDeeplinks

class MainActivity : ComponentActivity() {

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        Log.d("MainActivity", "Notification permission granted: $isGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Android notification channels
        NotificationHelper.createNotificationChannels(this)

        // Request POST_NOTIFICATIONS on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Fetch and initialize FCM device token
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("MainActivity", "FCM Device Token: $token")
                TokenManager.saveToken(this, token)
                TokenManager.syncStoredToken(this)
            } else {
                Log.w("MainActivity", "Fetching FCM registration token failed", task.exception)
            }
        }

        // Extract deep-link notice id if opened from notification or URL scheme
        val deepLinkNoticeId = extractNoticeId(intent)

        // Handle OAuth deep link callbacks
        handleAuthDeeplink(intent)

        enableEdgeToEdge()
        setContent {
            NotifyTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(initialNoticeId = deepLinkNoticeId)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeeplink(intent)
    }

    private fun handleAuthDeeplink(intent: Intent?) {
        if (intent == null) return
        if (com.notify.app.core.SupabaseClientProvider.isConfigured) {
            try {
                com.notify.app.core.SupabaseClientProvider.client.handleDeeplinks(intent = intent) { session ->
                    Log.d("MainActivity", "OAuth session received: ${session.user?.id}")
                    val user = session.user
                    if (user != null) {
                        val meta = user.userMetadata
                        val name = meta?.get("full_name")?.toString()?.trim('"')
                            ?: user.email?.substringBefore("@")
                            ?: "Google Student"
                        val profile = com.notify.app.domain.models.UserProfile(
                            id = user.id,
                            fullName = name,
                            bio = "Student",
                            isSystemAdmin = false
                        )
                        com.notify.app.data.repositories.RepositoryLocator.authRepository.setCurrentUser(profile)
                    }
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Error processing auth deep link: ${e.message}")
            }
        }
    }

    private fun extractNoticeId(intent: Intent?): String? {
        if (intent == null) return null

        // 1. Check intent extra
        val extraId = intent.getStringExtra("notice_id")
        if (!extraId.isNullOrBlank()) return extraId

        // 2. Check deep-link URI: notify://notice/{noticeId}
        val data: Uri? = intent.data
        if (data != null && data.scheme == "notify" && data.host == "notice") {
            val path = data.pathSegments
            if (!path.isNullOrEmpty()) {
                return path.first()
            }
        }
        return null
    }
}
