package com.notify.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.notify.app.core.AppUpdateInfo
import com.notify.app.core.UpdateManager
import com.notify.app.domain.models.ReportTarget
import com.notify.app.ui.components.UpdateDialog
import com.notify.app.ui.screens.*

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Auth : Screen("auth")
    object CollegeSelect : Screen("college_select")
    object Main : Screen("main")
    data class NoticeDetail(val noticeId: String) : Screen("notice_detail/$noticeId")
    object SourceManagement : Screen("sources")
    object CreatePost : Screen("create_post")
    data class PostDetail(val postId: String) : Screen("post_detail/$postId")
    object NotificationInbox : Screen("inbox")
    object Bookmarks : Screen("bookmarks")
    object StudyMaterials : Screen("study_materials")
    data class ReportContent(val targetType: ReportTarget, val targetId: String) : Screen("report")
    object AdminModeration : Screen("admin")
}

enum class BottomTab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    NOTICES("Notices", Icons.AutoMirrored.Filled.Announcement),
    HUB("Student Hub", Icons.Default.Forum),
    PROFILE("Profile", Icons.Default.Person)
}

@Composable
fun AppNavigation(
    initialNoticeId: String? = null
) {
    var currentScreen by remember {
        mutableStateOf<Screen>(
            if (initialNoticeId != null) Screen.NoticeDetail(initialNoticeId) else Screen.Splash
        )
    }

    val backStack = remember { mutableStateListOf<Screen>() }

    fun navigateTo(screen: Screen) {
        if (currentScreen != screen) {
            backStack.add(currentScreen)
            currentScreen = screen
        }
    }

    fun popBack() {
        if (backStack.isNotEmpty()) {
            currentScreen = backStack.removeLast()
        } else if (currentScreen !is Screen.Main && currentScreen !is Screen.Splash) {
            currentScreen = Screen.Main
        }
    }

    // Hardware & gesture back handler: safely intercept system back button on any sub-screen
    BackHandler(enabled = currentScreen !is Screen.Main && currentScreen !is Screen.Splash) {
        popBack()
    }

    Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
        when (screen) {
            is Screen.Splash -> {
                SplashScreen(
                    onNavigateNext = {
                        val hasSeenOnboarding = com.notify.app.core.SessionManager.hasSeenOnboarding()
                        val savedProfile = com.notify.app.core.SessionManager.getSavedProfile()
                        if (!hasSeenOnboarding && savedProfile == null) {
                            currentScreen = Screen.Auth
                        } else {
                            currentScreen = Screen.Main
                        }
                    }
                )
            }
            is Screen.Auth -> {
                AuthScreen(
                    onAuthSuccess = {
                        com.notify.app.core.SessionManager.setHasSeenOnboarding(true)
                        if (backStack.isNotEmpty()) {
                            popBack()
                        } else {
                            currentScreen = Screen.Main
                        }
                    },
                    onSkipToDashboard = {
                        com.notify.app.core.SessionManager.setHasSeenOnboarding(true)
                        if (backStack.isNotEmpty()) {
                            popBack()
                        } else {
                            currentScreen = Screen.Main
                        }
                    },
                    onBack = if (backStack.isNotEmpty()) { { popBack() } } else null
                )
            }
            is Screen.CollegeSelect -> {
                CollegeSelectScreen(
                    onCollegeSelected = { popBack() },
                    onBack = { popBack() }
                )
            }
            is Screen.Main -> {
                MainContainerScreen(
                    onNavigateToNoticeDetail = { noticeId -> navigateTo(Screen.NoticeDetail(noticeId)) },
                    onNavigateToSources = { navigateTo(Screen.SourceManagement) },
                    onNavigateToNotifications = { navigateTo(Screen.NotificationInbox) },
                    onNavigateToBookmarks = { navigateTo(Screen.Bookmarks) },
                    onNavigateToMaterials = { navigateTo(Screen.StudyMaterials) },
                    onSwitchCollege = { navigateTo(Screen.CollegeSelect) },
                    onNavigateToCreatePost = { navigateTo(Screen.CreatePost) },
                    onNavigateToPostDetail = { postId -> navigateTo(Screen.PostDetail(postId)) },
                    onNavigateToAdmin = { navigateTo(Screen.AdminModeration) },
                    onNavigateToAuth = { navigateTo(Screen.Auth) },
                    onSignOut = {
                        backStack.clear()
                        currentScreen = Screen.Auth
                    }
                )
            }
            is Screen.NoticeDetail -> {
                NoticeDetailScreen(
                    noticeId = screen.noticeId,
                    onBack = { popBack() },
                    onReportNotice = { nid -> navigateTo(Screen.ReportContent(ReportTarget.NOTICE, nid)) }
                )
            }
            is Screen.SourceManagement -> {
                SourceManagementScreen(
                    onBack = { popBack() }
                )
            }
            is Screen.CreatePost -> {
                CreatePostScreen(
                    onBack = { popBack() },
                    onPostCreated = { popBack() },
                    onNavigateToAuth = { navigateTo(Screen.Auth) }
                )
            }
            is Screen.PostDetail -> {
                PostDetailScreen(
                    postId = screen.postId,
                    onBack = { popBack() },
                    onReportPost = { pid -> navigateTo(Screen.ReportContent(ReportTarget.POST, pid)) },
                    onNavigateToAuth = { navigateTo(Screen.Auth) }
                )
            }
            is Screen.NotificationInbox -> {
                NotificationInboxScreen(
                    onBack = { popBack() },
                    onNavigateToNotice = { noticeId -> navigateTo(Screen.NoticeDetail(noticeId)) }
                )
            }
            is Screen.Bookmarks -> {
                BookmarksScreen(
                    onBack = { popBack() },
                    onNavigateToNotice = { noticeId -> navigateTo(Screen.NoticeDetail(noticeId)) }
                )
            }
            is Screen.ReportContent -> {
                ReportContentScreen(
                    targetType = screen.targetType,
                    targetId = screen.targetId,
                    onBack = { popBack() }
                )
            }
            is Screen.AdminModeration -> {
                AdminModerationScreen(
                    onBack = { popBack() }
                )
            }
            is Screen.StudyMaterials -> {
                StudyMaterialScreen(
                    onBack = { popBack() },
                    onNavigateToAuth = { navigateTo(Screen.Auth) }
                )
            }
        }
    }
}

@Composable
fun MainContainerScreen(
    onNavigateToNoticeDetail: (String) -> Unit,
    onNavigateToSources: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToBookmarks: () -> Unit,
    onNavigateToMaterials: () -> Unit,
    onSwitchCollege: () -> Unit,
    onNavigateToCreatePost: () -> Unit,
    onNavigateToPostDetail: (String) -> Unit,
    onNavigateToAdmin: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(BottomTab.HOME) }
    var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }

    // If on Student Hub, Notices, or Profile tab, pressing back returns to Home tab first
    BackHandler(enabled = selectedTab != BottomTab.HOME) {
        selectedTab = BottomTab.HOME
    }

    LaunchedEffect(Unit) {
        val update = UpdateManager.checkForUpdates(context)
        if (update != null && update.isUpdateAvailable) {
            availableUpdate = update
        }
    }

    availableUpdate?.let { updateInfo ->
        UpdateDialog(
            updateInfo = updateInfo,
            onDismiss = { availableUpdate = null }
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                tonalElevation = 6.dp
            ) {
                BottomTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                BottomTab.HOME -> HomeDashboardScreen(
                    onNavigateToNoticeDetail = onNavigateToNoticeDetail,
                    onNavigateToNoticesTab = { selectedTab = BottomTab.NOTICES },
                    onNavigateToCommunityTab = { selectedTab = BottomTab.HUB },
                    onNavigateToSources = onNavigateToSources,
                    onNavigateToNotifications = onNavigateToNotifications,
                    onNavigateToBookmarks = onNavigateToBookmarks,
                    onSwitchCollege = onSwitchCollege,
                    onNavigateToMaterials = onNavigateToMaterials
                )
                BottomTab.NOTICES -> NoticeFeedScreen(
                    onNavigateToNoticeDetail = onNavigateToNoticeDetail
                )
                BottomTab.HUB -> StudentCommunityScreen(
                    onNavigateToPostDetail = onNavigateToPostDetail,
                    onNavigateToCreatePost = onNavigateToCreatePost,
                    onNavigateToAuth = onNavigateToAuth
                )
                BottomTab.PROFILE -> UserProfileScreen(
                    onSwitchCollege = onSwitchCollege,
                    onNavigateToAdmin = onNavigateToAdmin,
                    onSignOut = onSignOut,
                    onNavigateToAuth = onNavigateToAuth
                )
            }
        }
    }
}
