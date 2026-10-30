package com.notify.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.notify.app.core.CompressedFileResult
import com.notify.app.core.FileCompressor
import com.notify.app.core.MaterialViewer
import com.notify.app.data.repositories.RepositoryLocator
import com.notify.app.domain.models.MaterialCategory
import com.notify.app.domain.models.StudyMaterial
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyMaterialScreen(
    onBack: () -> Unit,
    onNavigateToAuth: () -> Unit
) {
    val collegeRepo = RepositoryLocator.collegeRepository
    val materialRepo = RepositoryLocator.materialRepository
    val authRepo = RepositoryLocator.authRepository

    val activeCollege by collegeRepo.activeCollegeState.collectAsState()
    val curProfile by authRepo.currentUserState.collectAsState()
    val isGuest = curProfile == null || curProfile?.id?.startsWith("mock-") == true || curProfile?.id?.startsWith("pending-") == true

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<MaterialCategory?>(null) }
    var selectedSemester by remember { mutableStateOf(0) } // 0 = All
    var semesterMenuExpanded by remember { mutableStateOf(false) }

    var materials by remember { mutableStateOf<List<StudyMaterial>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showUploadDialog by remember { mutableStateOf(false) }
    var showGuestPrompt by remember { mutableStateOf(false) }

    val semesters = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8)

    fun loadMaterials() {
        isLoading = true
        scope.launch {
            val colId = activeCollege?.id ?: "b0f5e79d-9f32-44fd-a437-94f04a393088"
            materials = materialRepo.getMaterials(
                collegeId = colId,
                branch = "CSE",
                semester = if (selectedSemester == 0) null else selectedSemester,
                category = selectedCategory,
                query = searchQuery
            )
            isLoading = false
        }
    }

    LaunchedEffect(activeCollege, selectedCategory, selectedSemester, searchQuery) {
        loadMaterials()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("CSE PYQ & Study Hub", fontWeight = FontWeight.Bold)
                        Text(
                            text = "${activeCollege?.name?.take(25) ?: "Campus"} • Computer Science",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { loadMaterials() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (isGuest) {
                        showGuestPrompt = true
                    } else {
                        showUploadDialog = true
                    }
                },
                icon = { Icon(Icons.Default.UploadFile, contentDescription = null) },
                text = { Text("Share Material") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search subject, title, or exam paper...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All Types") }
                )
                MaterialCategory.values().forEach { cat ->
                    val label = when (cat) {
                        MaterialCategory.PYQ -> "📄 PYQs"
                        MaterialCategory.NOTES -> "📚 Notes"
                        MaterialCategory.LAB_MANUAL -> "🧪 Lab Manuals"
                        MaterialCategory.SYLLABUS -> "📋 Syllabus"
                    }
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                        label = { Text(label) }
                    )
                }
            }

            // Semester Dropdown Selector & Department Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CSE Department",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Dropdown Menu Button for Semesters
                Box {
                    OutlinedButton(
                        onClick = { semesterMenuExpanded = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selectedSemester > 0) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Text(
                            text = if (selectedSemester == 0) "All Semesters" else "Semester $selectedSemester",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            if (semesterMenuExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = "Select Semester",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = semesterMenuExpanded,
                        onDismissRequest = { semesterMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "All Semesters (Sem 1 - 8)",
                                    fontWeight = if (selectedSemester == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedSemester == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            leadingIcon = {
                                if (selectedSemester == 0) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            onClick = {
                                selectedSemester = 0
                                semesterMenuExpanded = false
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        (1..8).forEach { sem ->
                            val yearLabel = when (sem) {
                                1, 2 -> "First Year (FY)"
                                3, 4 -> "Second Year (SY)"
                                5, 6 -> "Third Year (TY)"
                                else -> "Final Year (B.Tech)"
                            }
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            "Semester $sem",
                                            fontWeight = if (selectedSemester == sem) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selectedSemester == sem) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            yearLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                leadingIcon = {
                                    if (selectedSemester == sem) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                onClick = {
                                    selectedSemester = sem
                                    semesterMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (materials.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(54.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No CSE materials found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Be the first to share a question paper or notes for this CSE semester!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(materials) { mat ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // Subject badge + Category chip
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = mat.subject,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }

                                        Surface(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "${mat.branch} • Sem ${mat.semester}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        color = when (mat.category) {
                                            MaterialCategory.PYQ -> MaterialTheme.colorScheme.errorContainer
                                            MaterialCategory.NOTES -> MaterialTheme.colorScheme.tertiaryContainer
                                            MaterialCategory.LAB_MANUAL -> MaterialTheme.colorScheme.primaryContainer
                                            MaterialCategory.SYLLABUS -> MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = mat.category.name,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = when (mat.category) {
                                                MaterialCategory.PYQ -> MaterialTheme.colorScheme.onErrorContainer
                                                MaterialCategory.NOTES -> MaterialTheme.colorScheme.onTertiaryContainer
                                                MaterialCategory.LAB_MANUAL -> MaterialTheme.colorScheme.onPrimaryContainer
                                                MaterialCategory.SYLLABUS -> MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Title
                                Text(
                                    text = mat.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Contributor & File Info
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "By ${mat.author?.fullName ?: "Student"} • ${FileCompressor.formatBytes(mat.fileSizeBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = mat.createdAt?.substringBefore("T") ?: "Recent",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Action Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            MaterialViewer.openMaterial(context, mat, scope)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            if (mat.mimeType.contains("pdf")) Icons.Default.PictureAsPdf else Icons.Default.Visibility,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("View / Download")
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        // Like button
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    materialRepo.likeMaterial(mat.id)
                                                    // reload local
                                                    materials = materials.map {
                                                        if (it.id == mat.id) it.copy(likesCount = it.likesCount + 1) else it
                                                    }
                                                }
                                            }
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.ThumbUp,
                                                    contentDescription = "Like",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                if (mat.likesCount > 0) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "${mat.likesCount}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }

                                        // Share button
                                        IconButton(
                                            onClick = {
                                                MaterialViewer.shareMaterial(context, mat)
                                            }
                                        ) {
                                            Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Guest Prompt Modal
    if (showGuestPrompt) {
        AlertDialog(
            onDismissRequest = { showGuestPrompt = false },
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Sign In Required", fontWeight = FontWeight.Bold) },
            text = {
                Text("Sharing study materials and question papers is open to all signed-in college students. Please sign in or create an account to contribute.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGuestPrompt = false
                        onNavigateToAuth()
                    }
                ) {
                    Text("Sign In / Sign Up")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGuestPrompt = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Upload Study Material Modal
    if (showUploadDialog) {
        UploadMaterialDialog(
            collegeId = activeCollege?.id ?: "b0f5e79d-9f32-44fd-a437-94f04a393088",
            onDismiss = { showUploadDialog = false },
            onUploadSuccess = {
                showUploadDialog = false
                Toast.makeText(context, "Material shared successfully! 🎉", Toast.LENGTH_SHORT).show()
                loadMaterials()
            }
        )
    }
}

@Composable
fun UploadMaterialDialog(
    collegeId: String,
    onDismiss: () -> Unit,
    onUploadSuccess: () -> Unit
) {
    val materialRepo = RepositoryLocator.materialRepository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("CSE") }
    var semester by remember { mutableStateOf(5) }
    var semesterUploadExpanded by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(MaterialCategory.PYQ) }

    var selectedFileResult by remember { mutableStateOf<CompressedFileResult?>(null) }
    var isCompressing by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isCompressing = true
            errorMessage = null
            scope.launch {
                val result = FileCompressor.compressAndPrepareFile(context, uri)
                result.onSuccess {
                    selectedFileResult = it
                    if (title.isBlank()) {
                        title = it.fileName.substringBeforeLast(".")
                    }
                }.onFailure {
                    errorMessage = it.message ?: "Failed to process selected file"
                }
                isCompressing = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = {
            Text("Share Study Material", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Upload question papers, formula sheets, or lab manuals directly from your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Category selector
                Text("Material Type:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MaterialCategory.values().forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.name) }
                        )
                    }
                }

                // Subject
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject (e.g., Operating Systems)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title / Description (e.g. Winter 2025 Midterm)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                // Fixed CSE Department indicator and Semester selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Branch", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Computer Science (CSE)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    // Semester Dropdown Selector
                    Box(modifier = Modifier.weight(0.9f)) {
                        OutlinedButton(
                            onClick = { semesterUploadExpanded = true },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text("Semester", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Sem $semester", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                }
                                Icon(
                                    if (semesterUploadExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = semesterUploadExpanded,
                            onDismissRequest = { semesterUploadExpanded = false }
                        ) {
                            (1..8).forEach { sem ->
                                val yr = when (sem) {
                                    1, 2 -> "FY"
                                    3, 4 -> "SY"
                                    5, 6 -> "TY"
                                    else -> "Final"
                                }
                                DropdownMenuItem(
                                    text = { Text("Semester $sem ($yr)", fontWeight = if (semester == sem) FontWeight.Bold else FontWeight.Normal) },
                                    leadingIcon = {
                                        if (semester == sem) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    },
                                    onClick = {
                                        semester = sem
                                        semesterUploadExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 1-Tap File Picker Button
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !isUploading && !isCompressing) {
                            filePickerLauncher.launch(arrayOf("application/pdf", "image/*"))
                        },
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isCompressing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Optimizing & compressing file...", style = MaterialTheme.typography.bodySmall)
                        } else if (selectedFileResult != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (selectedFileResult!!.mimeType.contains("pdf")) Icons.Default.PictureAsPdf else Icons.Default.Image,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = selectedFileResult!!.fileName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Optimized Size: ${selectedFileResult!!.formattedSize} ✓",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        } else {
                            Icon(Icons.Default.UploadFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Select PDF or Camera Scan", fontWeight = FontWeight.SemiBold)
                            Text("Auto-compressed to save device & mobile data", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (subject.isBlank() || title.isBlank() || selectedFileResult == null) {
                        errorMessage = "Please enter Subject, Title, and select a file."
                        return@Button
                    }
                    isUploading = true
                    errorMessage = null
                    scope.launch {
                        val file = selectedFileResult!!
                        val res = materialRepo.uploadMaterial(
                            collegeId = collegeId,
                            title = title.trim(),
                            subject = subject.trim(),
                            branch = branch.trim(),
                            semester = semester.coerceIn(1, 8),
                            category = category,
                            fileBytes = file.bytes,
                            fileName = file.fileName,
                            mimeType = file.mimeType
                        )
                        isUploading = false
                        res.onSuccess {
                            onUploadSuccess()
                        }.onFailure {
                            errorMessage = it.message ?: "Failed uploading material"
                        }
                    }
                },
                enabled = !isUploading && !isCompressing && selectedFileResult != null
            ) {
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Uploading...")
                } else {
                    Text("Publish Material")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isUploading) {
                Text("Cancel")
            }
        }
    )
}
