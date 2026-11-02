package com.notify.app.core

import android.content.Context
import android.util.Log
import com.notify.app.domain.models.UserProfile
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

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
    private const val KEY_DELETED_NOTIFICATIONS = "deleted_notification_ids"
    private const val KEY_CLEARED_ALL_TIMESTAMP = "cleared_all_notifications_timestamp"
    private const val THREE_MONTHS_MILLIS = 90L * 24L * 60L * 60L * 1000L // 90 days (3 months)

    fun isOlderThanThreeMonths(dateStr: String?): Boolean {
        if (dateStr.isNullOrBlank()) return false
        val clean = dateStr.trim()
        if (clean.equals("Today", ignoreCase = true) || clean.equals("Recently", ignoreCase = true) || clean.equals("Just now", ignoreCase = true)) {
            return false
        }
        val now = System.currentTimeMillis()
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US),
            SimpleDateFormat("dd/MM/yyyy", Locale.US),
            SimpleDateFormat("dd-MM-yyyy", Locale.US),
            SimpleDateFormat("yyyy/MM/dd", Locale.US)
        )
        for (sdf in formats) {
            try {
                val d = sdf.parse(clean)
                if (d != null) {
                    return (now - d.time) > THREE_MONTHS_MILLIS
                }
            } catch (_: Exception) {}
        }
        return false
    }

    fun isBeforeClearedAll(dateStr: String?, clearedAllTs: Long): Boolean {
        if (clearedAllTs <= 0L || dateStr.isNullOrBlank()) return false
        val clean = dateStr.trim()
        if (clean.equals("Today", ignoreCase = true) || clean.equals("Recently", ignoreCase = true) || clean.equals("Just now", ignoreCase = true)) {
            return false
        }
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US),
            SimpleDateFormat("dd/MM/yyyy", Locale.US),
            SimpleDateFormat("dd-MM-yyyy", Locale.US),
            SimpleDateFormat("yyyy/MM/dd", Locale.US)
        )
        for (sdf in formats) {
            try {
                val d = sdf.parse(clean)
                if (d != null) {
                    return d.time <= clearedAllTs
                }
            } catch (_: Exception) {}
        }
        return false
    }

    fun setClearedAllTimestamp(context: Context? = null, timestamp: Long = System.currentTimeMillis()) {
        val ctx = context ?: appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_CLEARED_ALL_TIMESTAMP, timestamp).commit()
        Log.d(TAG, "Recorded inbox clearAll timestamp: $timestamp")
    }

    fun getClearedAllTimestamp(context: Context? = null): Long {
        val ctx = context ?: appContext ?: return 0L
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_CLEARED_ALL_TIMESTAMP, 0L)
    }

    fun saveCachedNotifications(context: Context? = null, notifications: List<com.notify.app.domain.models.UserNotification>) {
        val ctx = context ?: appContext ?: return
        try {
            val deletedIds = getDeletedNotificationIds(ctx)
            val clearedAllTs = getClearedAllTimestamp(ctx)
            // Filter out notifications older than 3 months, before clearAll, and any deleted notifications
            val valid = notifications
                .filterNot { isOlderThanThreeMonths(it.createdAt) }
                .filterNot { isBeforeClearedAll(it.createdAt, clearedAllTs) }
                .filterNot { deletedIds.contains(it.id) || (it.noticeId != null && deletedIds.contains(it.noticeId)) }

            val encoded = jsonHelper.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.notify.app.domain.models.UserNotification.serializer()), valid)
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_CACHED_NOTIFICATIONS_JSON, encoded).commit()
            Log.d(TAG, "Cached ${valid.size} notifications locally (pruned ${notifications.size - valid.size} expired/deleted)")
        } catch (e: Exception) {
            Log.w(TAG, "Error caching notifications: ${e.message}")
        }
    }

    fun getCachedNotifications(context: Context? = null): List<com.notify.app.domain.models.UserNotification> {
        val ctx = context ?: appContext ?: return emptyList()
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CACHED_NOTIFICATIONS_JSON, null) ?: return emptyList()
        return try {
            val decoded = jsonHelper.decodeFromString(kotlinx.serialization.builtins.ListSerializer(com.notify.app.domain.models.UserNotification.serializer()), raw)
            val deletedIds = getDeletedNotificationIds(ctx)
            val clearedAllTs = getClearedAllTimestamp(ctx)
            // Filter out any notification older than 3 months, before clearAll, or deleted by student
            val filtered = decoded
                .filterNot { isOlderThanThreeMonths(it.createdAt) }
                .filterNot { isBeforeClearedAll(it.createdAt, clearedAllTs) }
                .filterNot { deletedIds.contains(it.id) || (it.noticeId != null && deletedIds.contains(it.noticeId)) }

            // Auto-clean cache if older or deleted items were pruned
            if (filtered.size != decoded.size) {
                val encoded = jsonHelper.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.notify.app.domain.models.UserNotification.serializer()), filtered)
                prefs.edit().putString(KEY_CACHED_NOTIFICATIONS_JSON, encoded).apply()
            }
            filtered
        } catch (e: Exception) {
            Log.w(TAG, "Error restoring cached notifications: ${e.message}")
            emptyList()
        }
    }

    fun addNotification(context: Context? = null, notification: com.notify.app.domain.models.UserNotification) {
        if (isOlderThanThreeMonths(notification.createdAt)) return
        val ctx = context ?: appContext
        if (ctx != null) {
            val clearedAllTs = getClearedAllTimestamp(ctx)
            if (isBeforeClearedAll(notification.createdAt, clearedAllTs)) return
            if (getDeletedNotificationIds(ctx).contains(notification.id)) return
        }

        val current = getCachedNotifications(context).toMutableList()
        current.removeAll { it.id == notification.id }
        current.add(0, notification)
        saveCachedNotifications(context, current.take(50))
    }

    private val persistentDeletedIds = java.util.concurrent.CopyOnWriteArraySet<String>()

    fun clearAllNotifications(context: Context? = null) {
        val ctx = context ?: appContext ?: return
        setClearedAllTimestamp(ctx)
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_CACHED_NOTIFICATIONS_JSON).commit()
    }

    fun getDeletedNotificationIds(context: Context? = null): Set<String> {
        val ctx = context ?: appContext
        if (ctx != null) {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_DELETED_NOTIFICATIONS, null)
            if (!json.isNullOrBlank()) {
                try {
                    val ids = jsonHelper.decodeFromString(ListSerializer(String.serializer()), json)
                    persistentDeletedIds.addAll(ids)
                } catch (_: Exception) {}
            }
            // Fallback check legacy string set if present
            try {
                val legacy = prefs.getStringSet("legacy_deleted_notifs", null)
                if (legacy != null) persistentDeletedIds.addAll(legacy)
            } catch (_: Exception) {}
        }
        return persistentDeletedIds.toSet()
    }

    fun markNotificationAsDeleted(context: Context? = null, id: String) {
        markAllAsDeleted(context, listOf(id))
    }

    fun markAllAsDeleted(context: Context? = null, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val ctx = context ?: appContext
        // Always load existing deleted notification IDs before adding new ones
        if (ctx != null) {
            getDeletedNotificationIds(ctx)
        }
        val expanded = mutableSetOf<String>()
        for (item in ids) {
            val clean = item.trim()
            if (clean.isBlank()) continue
            expanded.add(clean)
            if (clean.startsWith("notice_")) {
                expanded.add(clean.removePrefix("notice_"))
            } else {
                expanded.add("notice_$clean")
            }
        }
        persistentDeletedIds.addAll(expanded)

        if (ctx == null) return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val encoded = jsonHelper.encodeToString(ListSerializer(String.serializer()), persistentDeletedIds.toList())
            prefs.edit().putString(KEY_DELETED_NOTIFICATIONS, encoded).commit()
        } catch (e: Exception) {
            Log.w(TAG, "Error saving deleted notification ids: ${e.message}")
        }

        // Also prune from cached notifications list immediately
        try {
            val notifs = getCachedNotifications(ctx).filterNot { expanded.contains(it.id) || (it.noticeId != null && expanded.contains(it.noticeId)) }
            val encodedNotifs = jsonHelper.encodeToString(kotlinx.serialization.builtins.ListSerializer(com.notify.app.domain.models.UserNotification.serializer()), notifs)
            prefs.edit().putString(KEY_CACHED_NOTIFICATIONS_JSON, encodedNotifs).commit()
        } catch (_: Exception) {}
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
