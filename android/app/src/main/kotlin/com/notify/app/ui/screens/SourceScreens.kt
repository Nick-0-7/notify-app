package com.notify.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.notify.app.domain.models.NoticeSource
import com.notify.app.domain.models.SourceStatus
import com.notify.app.domain.models.SourceType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceManagementScreen(
    onBack: () -> Unit
) {
    val collegeRepo = RepositoryLocator.collegeRepository
    val sourceRepo = RepositoryLocator.sourceRepository
    val activeCollege by collegeRepo.activeCollegeState.collectAsState()

    var sources by remember { mutableStateOf<List<NoticeSource>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }

    var labelInput by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(SourceType.NOTICE_BOARD_PAGE) }
    var dialogError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadSources() {
        isLoading = true
        scope.launch {
            val colId = activeCollege?.id ?: "col-alpha-1"
            sources = sourceRepo.getSources(colId)
            isLoading = false
        }
    }

    LaunchedEffect(activeCollege) {
        loadSources()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monitored Sources") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { loadSources() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    labelInput = ""
                    urlInput = ""
                    dialogError = null
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Source")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Website & RSS notice sources monitored for ${activeCollege?.name ?: "your campus"}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (sources.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No sources configured yet. Tap + to register one.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(sources) { src ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = src.label,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.weight(1f)
                                    )
                                    val (statusBg, statusFg, statusText) = when (src.status) {
                                        SourceStatus.APPROVED_ACTIVE -> Triple(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.onPrimaryContainer,
                                            "Active"
                                        )
                                        SourceStatus.PENDING -> Triple(
                                            MaterialTheme.colorScheme.tertiaryContainer,
                                            MaterialTheme.colorScheme.onTertiaryContainer,
                                            "Pending Review"
                                        )
                                        SourceStatus.PAUSED -> Triple(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                            "Paused"
                                        )
                                        SourceStatus.REJECTED -> Triple(
                                            MaterialTheme.colorScheme.errorContainer,
                                            MaterialTheme.colorScheme.onErrorContainer,
                                            "Rejected"
                                        )
                                    }
                                    Surface(
                                        color = statusBg,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = statusText,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = statusFg,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = src.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Type: ${src.sourceType.name.replace("_", " ")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Interval: ${src.checkIntervalMinutes} mins",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                src.lastCheckedAt?.let {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Last checked: $it",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Register College Source") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "New sources are verified and reviewed by administrators before crawler activation.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        if (dialogError != null) {
                            Text(
                                text = dialogError ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        OutlinedTextField(
                            value = labelInput,
                            onValueChange = { labelInput = it },
                            label = { Text("Source Label (e.g., Exam Cell)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text("URL (https://college.edu/notices)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Text("Source Format:", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = selectedType == SourceType.NOTICE_BOARD_PAGE,
                                onClick = { selectedType = SourceType.NOTICE_BOARD_PAGE },
                                label = { Text("Notice Board") }
                            )
                            FilterChip(
                                selected = selectedType == SourceType.RSS_FEED,
                                onClick = { selectedType = SourceType.RSS_FEED },
                                label = { Text("RSS Feed") }
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmedUrl = urlInput.trim()
                            if (labelInput.isBlank()) {
                                dialogError = "Please enter a source label."
                                return@Button
                            }
                            if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
                                dialogError = "URL must start with http:// or https://"
                                return@Button
                            }
                            if (trimmedUrl.contains("localhost") || trimmedUrl.contains("127.0.0.1") || trimmedUrl.contains("169.254")) {
                                dialogError = "Private and loopback addresses are blocked."
                                return@Button
                            }

                            scope.launch {
                                val colId = activeCollege?.id ?: "col-alpha-1"
                                val res = sourceRepo.registerSource(colId, labelInput, trimmedUrl, selectedType)
                                if (res.isSuccess) {
                                    showAddDialog = false
                                    loadSources()
                                } else {
                                    dialogError = res.exceptionOrNull()?.localizedMessage ?: "Failed to add source"
                                }
                            }
                        }
                    ) {
                        Text("Submit for Review")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
