package com.notify.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notify.app.data.repositories.RepositoryLocator
import com.notify.app.domain.models.Notice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboardScreen(
    onNavigateToNoticeDetail: (String) -> Unit,
    onNavigateToNoticesTab: () -> Unit,
    onNavigateToCommunityTab: () -> Unit,
    onNavigateToSources: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToBookmarks: () -> Unit,
    onSwitchCollege: () -> Unit,
    onNavigateToMaterials: () -> Unit = {}
) {
    val collegeRepo = RepositoryLocator.collegeRepository
    val noticeRepo = RepositoryLocator.noticeRepository
    val notifRepo = RepositoryLocator.notificationRepository

    val activeCollege by collegeRepo.activeCollegeState.collectAsState()
    var notices by remember { mutableStateOf<List<Notice>>(emptyList()) }
    var unreadNotifCount by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    val activeDeadlinesCount = remember(notices) {
        notices.count { !it.extraction?.deadlinesExtracted.isNullOrEmpty() }
    }
    val urgentNotice = remember(notices) {
        notices.firstOrNull { !it.extraction?.deadlinesExtracted.isNullOrEmpty() } ?: notices.firstOrNull()
    }

    LaunchedEffect(activeCollege) {
        val colId = activeCollege?.id ?: "col-alpha-1"
        notices = noticeRepo.getNotices(colId)
        val notifs = notifRepo.getNotifications()
        unreadNotifCount = notifs.count { !it.isRead }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.clickable { onSwitchCollege() }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = activeCollege?.name ?: "Select College",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Switch college",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "${activeCollege?.city ?: "Campus"} • Active Feed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToBookmarks) {
                        Icon(Icons.Default.BookmarkBorder, contentDescription = "Bookmarks")
                    }
                    IconButton(onClick = onNavigateToNotifications) {
                        BadgedBox(
                            badge = {
                                if (unreadNotifCount > 0) {
                                    Badge { Text("$unreadNotifCount") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Urgent Alert Card (Deadlines & Critical notices)
            item {
                if (urgentNotice != null) {
                    val deadlineText = urgentNotice.extraction?.deadlinesExtracted?.firstOrNull()?.let {
                        "Deadline: ${it.substringBefore("T")}"
                    } ?: (urgentNotice.publishedAt ?: urgentNotice.firstSeenAt)?.substringBefore("T")?.let { "Published: $it" } ?: "Recent Notice"
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToNoticeDetail(urgentNotice.id) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Campaign,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = urgentNotice.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = deadlineText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Metric summary cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Total Notices", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${notices.size}", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Deadlines Active", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$activeDeadlinesCount", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Active Sources", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("2 Live", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Quick Navigation Shortcuts
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToSources,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sources", maxLines = 1)
                    }
                    Button(
                        onClick = onNavigateToMaterials,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.2f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PYQ Hub", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onNavigateToCommunityTab,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Hub", maxLines = 1)
                    }
                }
            }

            // Section Header: Latest Extracted Notices
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Latest Notices",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    TextButton(onClick = onNavigateToNoticesTab) {
                        Text("View All")
                    }
                }
            }

            // List of Recent Notices
            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                items(notices.take(5)) { notice ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToNoticeDetail(notice.id) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
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
                                        text = notice.extraction?.category?.name?.replace("_", " ") ?: "GENERAL",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Text(
                                    text = (notice.publishedAt ?: notice.firstSeenAt)?.substringBefore("T") ?: "Recent",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = notice.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
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

                            notice.extraction?.datesExtracted?.firstOrNull()?.let { dateStr ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Event,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = dateStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
