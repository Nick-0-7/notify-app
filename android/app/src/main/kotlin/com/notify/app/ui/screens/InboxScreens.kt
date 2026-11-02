package com.notify.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.notify.app.core.DateUtils
import com.notify.app.data.repositories.RepositoryLocator
import com.notify.app.domain.models.Bookmark
import com.notify.app.domain.models.UserNotification
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotificationInboxScreen(
    onBack: () -> Unit,
    onNavigateToNotice: (String) -> Unit
) {
    val context = LocalContext.current
    val notifRepo = RepositoryLocator.notificationRepository
    var notifications by remember { mutableStateOf<List<UserNotification>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedNotifForDetail by remember { mutableStateOf<UserNotification?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()
    val areNotificationsEnabled = remember(notifications) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun load() {
        isLoading = true
        scope.launch {
            notifications = notifRepo.getNotifications()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        load()
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear All Notifications?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete all notifications from your inbox? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            notifRepo.clearAll()
                            notifications = emptyList()
                            selectedIds = emptySet()
                            isSelectionMode = false
                            showClearAllDialog = false
                        }
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text("Delete Selected Notifications?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete ${selectedIds.size} selected notification(s)?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = selectedIds
                        scope.launch {
                            notifRepo.deleteNotifications(toDelete)
                            notifications = notifications.filterNot { toDelete.contains(it.id) }
                            selectedIds = emptySet()
                            isSelectionMode = false
                            showDeleteSelectedDialog = false
                        }
                    }
                ) {
                    Text("Delete (${selectedIds.size})", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text("${selectedIds.size} selected", fontWeight = FontWeight.Bold)
                    } else {
                        Column {
                            Text("Notification Inbox", fontWeight = FontWeight.Bold)
                            val unreadCount = notifications.count { !it.isRead }
                            Text(
                                text = if (unreadCount > 0) "$unreadCount unread updates" else "All caught up",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedIds = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(
                            onClick = {
                                selectedIds = if (selectedIds.size == notifications.size) {
                                    emptySet()
                                } else {
                                    notifications.map { it.id }.toSet()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (selectedIds.size == notifications.size) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = "Toggle select all"
                            )
                        }
                        IconButton(
                            onClick = { showDeleteSelectedDialog = true },
                            enabled = selectedIds.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete selected",
                                tint = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    } else {
                        if (notifications.isNotEmpty()) {
                            IconButton(onClick = { isSelectionMode = true }) {
                                Icon(Icons.Default.Checklist, contentDescription = "Select notifications")
                            }
                        }
                        if (notifications.any { !it.isRead }) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        notifRepo.markAllAsRead()
                                        notifications = notifications.map { it.copy(isRead = true) }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.DoneAll, contentDescription = "Mark all as read")
                            }
                        }
                        if (notifications.isNotEmpty()) {
                            IconButton(onClick = { showClearAllDialog = true }) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear all notifications")
                            }
                        }
                        IconButton(onClick = { load() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (selectedNotifForDetail != null) {
            val n = selectedNotifForDetail!!
            AlertDialog(
                onDismissRequest = { selectedNotifForDetail = null },
                title = { Text(n.title, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(n.body, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Received: ${DateUtils.formatToDdMmYyyy(n.createdAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedNotifForDetail = null }) {
                        Text("Close")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            val targetId = n.id
                            selectedNotifForDetail = null
                            scope.launch {
                                notifRepo.deleteNotification(targetId)
                                notifications = notifications.filter { it.id != targetId }
                            }
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.NotificationsNone,
                        contentDescription = null,
                        modifier = Modifier.size(54.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No notifications yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Official updates, notices, and announcements will appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // If device notifications are disabled, prompt student to enable them
                if (!areNotificationsEnabled) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.NotificationsOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Notifications Turned Off",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        "Enable notifications in device settings to receive instant popup alerts when new notices are published.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                            }
                                        } else {
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.fromParts("package", context.packageName, null)
                                            }
                                        }
                                        context.startActivity(intent)
                                    }
                                ) {
                                    Text("Turn On", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                items(notifications, key = { it.id }) { notif ->
                    val isSelected = selectedIds.contains(notif.id)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            } else if (notif.isRead) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Tappable content area for reading / navigating
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .combinedClickable(
                                        onClick = {
                                            if (isSelectionMode) {
                                                selectedIds = if (isSelected) selectedIds - notif.id else selectedIds + notif.id
                                            } else {
                                                scope.launch { notifRepo.markAsRead(notif.id) }
                                                notifications = notifications.map {
                                                    if (it.id == notif.id) it.copy(isRead = true) else it
                                                }
                                                if (!notif.noticeId.isNullOrBlank() && notif.noticeId != "general") {
                                                    onNavigateToNotice(notif.noticeId)
                                                } else {
                                                    selectedNotifForDetail = notif
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            if (!isSelectionMode) {
                                                isSelectionMode = true
                                                selectedIds = setOf(notif.id)
                                            }
                                        }
                                    )
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelectionMode) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedIds = if (checked) selectedIds + notif.id else selectedIds - notif.id
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                } else if (!notif.isRead) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                } else {
                                    Icon(
                                        imageVector = if (notif.notificationType == "ALERT") Icons.Default.Campaign else Icons.Default.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = notif.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = notif.body,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = DateUtils.formatToDdMmYyyy(notif.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Independent delete button (never triggers item navigation)
                            if (!isSelectionMode) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            notifRepo.deleteNotification(notif.id)
                                            notifications = notifications.filter { it.id != notif.id }
                                        }
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Delete notification",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onBack: () -> Unit,
    onNavigateToNotice: (String) -> Unit
) {
    val noticeRepo = RepositoryLocator.noticeRepository
    var bookmarks by remember { mutableStateOf<List<Bookmark>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        bookmarks = noticeRepo.getBookmarks()
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Bookmarks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (bookmarks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.BookmarkBorder,
                        contentDescription = null,
                        modifier = Modifier.size(54.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No bookmarks saved yet", style = MaterialTheme.typography.titleMedium)
                    Text("Bookmark important circulars or student notes for offline reference.", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(bookmarks) { bmk ->
                    val notice = bmk.notice
                    if (notice != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToNotice(notice.id) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = notice.extraction?.category?.name ?: "NOTICE",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    Text(
                                        text = DateUtils.formatPublishedDate(notice.publishedAt ?: notice.firstSeenAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = notice.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = notice.rawContent,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
