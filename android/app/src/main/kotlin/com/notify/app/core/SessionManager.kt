package com.notify.app.core

import android.content.Context
import android.util.Log
import com.notify.app.domain.models.UserProfile

object SessionManager {
    private const val TAG = "SessionManager"
    private const val PREFS_NAME = "notify_session_prefs"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_FULL_NAME = "full_name"
    private const val KEY_BIO = "bio"
    private const val KEY_AVATAR_URL = "avatar_url"
    private const val KEY_IS_ADMIN = "is_admin"
    private const val KEY_ACTIVE_COLLEGE_ID = "active_college_id"

    private var appContext: Context? = null
    val context: Context? get() = appContext

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun saveProfile(context: Context? = null, profile: UserProfile) {
        val ctx = context ?: appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_USER_ID, profile.id)
            .putString(KEY_FULL_NAME, profile.fullName)
            .putString(KEY_BIO, profile.bio)
            .putString(KEY_AVATAR_URL, profile.avatarUrl)
            .putBoolean(KEY_IS_ADMIN, profile.isSystemAdmin)
            .apply()
        Log.d(TAG, "Saved user profile locally: ${profile.fullName} (${profile.id})")
    }

    fun getSavedProfile(context: Context? = null): UserProfile? {
        val ctx = context ?: appContext ?: return null
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_USER_ID, null) ?: return null
        val fullName = prefs.getString(KEY_FULL_NAME, "Student") ?: "Student"
        val bio = prefs.getString(KEY_BIO, "Student") ?: "Student"
        val avatarUrl = prefs.getString(KEY_AVATAR_URL, null)
        val isAdmin = prefs.getBoolean(KEY_IS_ADMIN, false)
        return UserProfile(
            id = id,
            fullName = fullName,
            bio = bio,
            avatarUrl = avatarUrl,
            isSystemAdmin = isAdmin
        )
    }

    fun clearSession(context: Context? = null) {
        val ctx = context ?: appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_FULL_NAME)
            .remove(KEY_BIO)
            .remove(KEY_AVATAR_URL)
            .remove(KEY_IS_ADMIN)
            .apply()
        Log.d(TAG, "Cleared user session")
    }

    fun saveActiveCollegeId(context: Context? = null, collegeId: String) {
        val ctx = context ?: appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACTIVE_COLLEGE_ID, collegeId).apply()
    }

    fun getActiveCollegeId(context: Context? = null): String? {
        val ctx = context ?: appContext ?: return null
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE_COLLEGE_ID, null)
    }

    private const val KEY_CACHED_POSTS_JSON = "cached_posts_json"
    private val jsonHelper = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun saveCachedPosts(context: Context? = null, posts: List<com.notify.app.domain.models.StudentPost>) {
        val ctx = context ?: appContext ?: return
        try {
            val encoded = jsonHelper.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.notify.app.domain.models.StudentPost.serializer()), posts)
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_CACHED_POSTS_JSON, encoded).apply()
            Log.d(TAG, "Cached ${posts.size} student posts locally")
        } catch (e: Exception) {
            Log.w(TAG, "Error caching posts: ${e.message}")
        }
    }

    fun getCachedPosts(context: Context? = null): List<com.notify.app.domain.models.StudentPost> {
        val ctx = context ?: appContext ?: return emptyList()
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CACHED_POSTS_JSON, null) ?: return emptyList()
        return try {
            jsonHelper.decodeFromString(kotlinx.serialization.builtins.ListSerializer(com.notify.app.domain.models.StudentPost.serializer()), raw)
        } catch (e: Exception) {
            Log.w(TAG, "Error restoring cached posts: ${e.message}")
            emptyList()
        }
    }

    private const val KEY_CACHED_MATERIALS_JSON = "cached_materials_json"

    fun saveCachedMaterials(context: Context? = null, materials: List<com.notify.app.domain.models.StudyMaterial>) {
        val ctx = context ?: appContext ?: return
        try {
            val encoded = jsonHelper.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.notify.app.domain.models.StudyMaterial.serializer()), materials)
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_CACHED_MATERIALS_JSON, encoded).apply()
            Log.d(TAG, "Cached ${materials.size} study materials locally")
        } catch (e: Exception) {
            Log.w(TAG, "Error caching study materials: ${e.message}")
        }
    }

    fun getCachedMaterials(context: Context? = null): List<com.notify.app.domain.models.StudyMaterial> {
        val ctx = context ?: appContext ?: return emptyList()
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CACHED_MATERIALS_JSON, null) ?: return emptyList()
        return try {
            jsonHelper.decodeFromString(kotlinx.serialization.builtins.ListSerializer(com.notify.app.domain.models.StudyMaterial.serializer()), raw)
        } catch (e: Exception) {
            Log.w(TAG, "Error restoring cached materials: ${e.message}")
            emptyList()
        }
    }

    private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
    private const val KEY_BOOKMARKS = "bookmarked_notice_ids"
    private const val KEY_CACHED_NOTIFICATIONS_JSON = "cached_notifications_json"
    private const val KEY_READ_NOTIFICATIONS = "read_notification_ids"

    fun saveCachedNotifications(context: Context? = null, notifications: List<com.notify.app.domain.models.UserNotification>) {
        val ctx = context ?: appContext ?: return
        try {
            val encoded = jsonHelper.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.notify.app.domain.models.UserNotification.serializer()), notifications)
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_CACHED_NOTIFICATIONS_JSON, encoded).apply()
            Log.d(TAG, "Cached ${notifications.size} notifications locally")
        } catch (e: Exception) {
            Log.w(TAG, "Error caching notifications: ${e.message}")
        }
    }

    fun getCachedNotifications(context: Context? = null): List<com.notify.app.domain.models.UserNotification> {
        val ctx = context ?: appContext ?: return emptyList()
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CACHED_NOTIFICATIONS_JSON, null) ?: return emptyList()
        return try {
            jsonHelper.decodeFromString(kotlinx.serialization.builtins.ListSerializer(com.notify.app.domain.models.UserNotification.serializer()), raw)
        } catch (e: Exception) {
            Log.w(TAG, "Error restoring cached notifications: ${e.message}")
            emptyList()
        }
    }

    fun addNotification(context: Context? = null, notification: com.notify.app.domain.models.UserNotification) {
        val current = getCachedNotifications(context).toMutableList()
        current.removeAll { it.id == notification.id }
        current.add(0, notification)
        saveCachedNotifications(context, current.take(50))
    }

    fun clearAllNotifications(context: Context? = null) {
        val ctx = context ?: appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_CACHED_NOTIFICATIONS_JSON).apply()
    }

    fun markNotificationAsRead(context: Context? = null, id: String) {
        val ctx = context ?: appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_READ_NOTIFICATIONS, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(id)
        prefs.edit().putStringSet(KEY_READ_NOTIFICATIONS, current).apply()

        // Also update cached list if present
        val notifs = getCachedNotifications(ctx).map {
            if (it.id == id) it.copy(isRead = true) else it
        }
        saveCachedNotifications(ctx, notifs)
    }

    fun getReadNotificationIds(context: Context? = null): Set<String> {
        val ctx = context ?: appContext ?: return emptySet()
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_READ_NOTIFICATIONS, emptySet()) ?: emptySet()
    }

    fun saveBookmarks(context: Context? = null, noticeIds: Set<String>) {
        val ctx = context ?: appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_BOOKMARKS, noticeIds).apply()
    }

    fun getBookmarks(context: Context? = null): Set<String> {
        val ctx = context ?: appContext ?: return emptySet()
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_BOOKMARKS, null) ?: emptySet()
    }

    fun setHasSeenOnboarding(seen: Boolean = true, context: Context? = null) {
        val ctx = context ?: appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, seen).apply()
        Log.d(TAG, "Set hasSeenOnboarding = $seen")
    }

    fun hasSeenOnboarding(context: Context? = null): Boolean {
        val ctx = context ?: appContext ?: return false
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, false)
    }
}
