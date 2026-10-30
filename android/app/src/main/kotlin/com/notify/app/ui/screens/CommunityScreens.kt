package com.notify.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notify.app.data.repositories.RepositoryLocator
import com.notify.app.domain.models.*
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentCommunityScreen(
    onNavigateToPostDetail: (String) -> Unit,
    onNavigateToCreatePost: () -> Unit,
    onNavigateToAuth: () -> Unit = {}
) {
    val collegeRepo = RepositoryLocator.collegeRepository
    val communityRepo = RepositoryLocator.communityRepository
    val activeCollege by collegeRepo.activeCollegeState.collectAsState()

    var selectedType by remember { mutableStateOf<PostType?>(null) }
    var posts by remember { mutableStateOf<List<StudentPost>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showSignInPrompt by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val curProfile by RepositoryLocator.authRepository.currentUserState.collectAsState()
    val isGuest = curProfile == null || curProfile?.id?.startsWith("mock-") == true || curProfile?.id?.startsWith("pending-") == true

    fun loadPosts() {
        isLoading = true
        scope.launch {
            try {
                val colId = activeCollege?.id ?: "b0f5e79d-9f32-44fd-a437-94f04a393088"
                posts = communityRepo.getPosts(colId, selectedType)
            } catch (e: Exception) {
                android.util.Log.e("StudentHub", "Error loading posts: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(activeCollege, selectedType) {
        try {
            val colId = activeCollege?.id ?: "b0f5e79d-9f32-44fd-a437-94f04a393088"
            posts = communityRepo.getPosts(colId, selectedType)
        } catch (e: Exception) {
            android.util.Log.e("StudentHub", "Error fetching posts: ${e.message}")
        } finally {
            isLoading = false
        }
    }

    if (showSignInPrompt) {
        AlertDialog(
            onDismissRequest = { showSignInPrompt = false },
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Sign In Required", fontWeight = FontWeight.Bold) },
            text = {
                Text("Student Hub discussions and messages are reserved for verified college members. Please sign in to create posts and connect with fellow students.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignInPrompt = false
                        onNavigateToAuth()
                    }
                ) {
                    Text("Sign In")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignInPrompt = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Student Hub", fontWeight = FontWeight.Bold)
                        Text(
                            "Discussions auto-expire after 24 hours",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { loadPosts() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (isGuest) {
                        showSignInPrompt = true
                    } else {
                        onNavigateToCreatePost()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Create Post")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Post Type Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedType == null,
                    onClick = { selectedType = null },
                    label = { Text("All Posts") }
                )
                PostType.values().forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = if (selectedType == type) null else type },
                        label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            if (isGuest) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Guest mode. Sign in to post messages & participate in discussions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        FilledTonalButton(
                            onClick = onNavigateToAuth,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Sign In", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (posts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Forum,
                            contentDescription = null,
                            modifier = Modifier.size(54.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No student posts in this category yet.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Share notes, questions, or resources with classmates!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(posts) { post ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToPostDetail(post.id) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Author Header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.secondaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = post.author?.fullName?.take(1) ?: "S",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = post.author?.fullName ?: "Student",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "${post.postType.name} • ${post.createdAt ?: "Recent"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = post.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = post.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Attachments Badge
                                if (post.attachments.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (post.attachments.first().fileType == AttachmentType.PDF) Icons.Default.PictureAsPdf else Icons.Default.Attachment,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = post.attachments.first().fileName,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.ThumbUp,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "${post.reactionsCount.values.sum()}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.ChatBubbleOutline,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "${post.commentsCount}",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
fun CreatePostScreen(
    onBack: () -> Unit,
    onPostCreated: () -> Unit,
    onNavigateToAuth: () -> Unit = {}
) {
    val collegeRepo = RepositoryLocator.collegeRepository
    val communityRepo = RepositoryLocator.communityRepository
    val activeCollege by collegeRepo.activeCollegeState.collectAsState()
    val curProfile by RepositoryLocator.authRepository.currentUserState.collectAsState()
    val isGuest = curProfile == null || curProfile?.id?.startsWith("mock-") == true || curProfile?.id?.startsWith("pending-") == true
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(PostType.GENERAL) }
    var attachedFileName by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Post") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (isGuest) {
                                errorMessage = "Please sign in to publish posts."
                                return@Button
                            }
                            if (title.isBlank() || content.isBlank()) {
                                errorMessage = "Please enter both a title and content."
                                return@Button
                            }
                            isSubmitting = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    val attachments = if (attachedFileName != null) {
                                        listOf(
                                            PostAttachment(
                                                id = UUID.randomUUID().toString(),
                                                postId = "",
                                                fileUrl = "https://apex.edu/files/$attachedFileName",
                                                storagePath = "col-alpha-1/attachments/$attachedFileName",
                                                fileName = attachedFileName ?: "Document.pdf",
                                                fileSizeBytes = 1048576,
                                                mimeType = "application/pdf",
                                                fileType = AttachmentType.PDF
                                            )
                                        )
                                    } else emptyList()

                                    val colId = activeCollege?.id ?: "b0f5e79d-9f32-44fd-a437-94f04a393088"
                                    val res = communityRepo.createPost(colId, title, content, selectedType, attachments)
                                    isSubmitting = false
                                    if (res.isSuccess) {
                                        onPostCreated()
                                    } else {
                                        errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Failed to post"
                                    }
                                } catch (e: Exception) {
                                    isSubmitting = false
                                    errorMessage = e.localizedMessage ?: "Failed to post message"
                                }
                            }
                        },
                        enabled = !isSubmitting && !isGuest
                    ) {
                        Text("Post")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isGuest) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                "Sign In Required",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "Only signed-in members can publish posts in the Student Hub.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Button(onClick = onNavigateToAuth) {
                            Text("Sign In")
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text("Category Tag:", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PostType.values().forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Post Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("What would you like to share or ask?") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(12.dp)
            )

            // Attachment Picker Bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (attachedFileName != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = attachedFileName ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { attachedFileName = null }) {
                            Icon(Icons.Default.Clear, contentDescription = "Remove")
                        }
                    } else {
                        Text("Attach PDF notes or photo (max 10MB)", style = MaterialTheme.typography.bodySmall)
                        Row {
                            IconButton(onClick = { attachedFileName = "Lecture_Unit_3_Notes.pdf" }) {
                                Icon(Icons.Default.AttachFile, contentDescription = "Attach file")
                            }
                            IconButton(onClick = { attachedFileName = "Campus_Whiteboard.jpg" }) {
                                Icon(Icons.Default.Image, contentDescription = "Attach image")
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
fun PostDetailScreen(
    postId: String,
    onBack: () -> Unit,
    onReportPost: (String) -> Unit,
    onNavigateToAuth: () -> Unit = {}
) {
    val communityRepo = RepositoryLocator.communityRepository
    val curProfile by RepositoryLocator.authRepository.currentUserState.collectAsState()
    val isGuest = curProfile == null || curProfile?.id?.startsWith("mock-") == true || curProfile?.id?.startsWith("pending-") == true
    val scope = rememberCoroutineScope()

    var post by remember { mutableStateOf<StudentPost?>(null) }
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var newCommentText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var commentError by remember { mutableStateOf<String?>(null) }
    var isLiked by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            try {
                post = communityRepo.getPostById(postId)
                comments = communityRepo.getComments(postId)
            } catch (e: Exception) {
                android.util.Log.e("PostDetail", "Error loading discussion: ${e.message}")
            }
        }
    }

    LaunchedEffect(postId) {
        refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Discussion") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onReportPost(postId) }) {
                        Icon(Icons.Default.ReportProblem, contentDescription = "Report")
                    }
                }
            )
        },
        bottomBar = {
            if (isGuest) {
                Surface(
                    tonalElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    modifier = Modifier.fillMaxWidth().imePadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 12.dp)
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    "Sign In to Join Discussion",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    "Messaging is reserved for signed-in members",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Button(onClick = onNavigateToAuth) {
                            Text("Sign In")
                        }
                    }
                }
            } else {
                Surface(
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth().imePadding()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (commentError != null) {
                            Text(
                                text = commentError ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newCommentText,
                                onValueChange = {
                                    newCommentText = it
                                    if (commentError != null) commentError = null
                                },
                                placeholder = { Text("Add a comment...") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(20.dp),
                                singleLine = true,
                                enabled = !isSending
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    val trimmed = newCommentText.trim()
                                    if (trimmed.isNotBlank() && !isSending) {
                                        isSending = true
                                        commentError = null
                                        scope.launch {
                                            try {
                                                val result = communityRepo.addComment(postId, trimmed)
                                                if (result.isSuccess) {
                                                    newCommentText = ""
                                                    refresh()
                                                } else {
                                                    commentError = result.exceptionOrNull()?.localizedMessage ?: "Failed to post comment"
                                                }
                                            } catch (e: Exception) {
                                                commentError = e.localizedMessage ?: "Failed to post message"
                                            } finally {
                                                isSending = false
                                            }
                                        }
                                    }
                                },
                                enabled = !isSending && newCommentText.isNotBlank()
                            ) {
                                if (isSending) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        val currentPost = post
        if (currentPost == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    // Post Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = currentPost.author?.fullName?.take(1) ?: "S",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = currentPost.author?.fullName ?: "Student",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "${currentPost.postType.name} • ${currentPost.createdAt ?: "Recent"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = currentPost.title,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = currentPost.content,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            // Attachments
                            if (currentPost.attachments.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.FilePresent,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = currentPost.attachments.first().fileName,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Text(
                                                text = "Verified student document",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = {
                                            isLiked = !isLiked
                                            scope.launch {
                                                communityRepo.toggleReaction(postId, ReactionType.HELPFUL)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isLiked) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isLiked) "Helpful (10)" else "Helpful (9)")
                                    }
                                }
                                Text(
                                    text = "${comments.size} Comments",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Comments (${comments.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                items(comments) { comment ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = comment.author?.fullName ?: "Student",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = comment.createdAt ?: "Just now",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = comment.content, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
