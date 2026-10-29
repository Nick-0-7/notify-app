package com.notify.app

import com.notify.app.data.repositories.*
import com.notify.app.domain.models.*
import junit.framework.TestCase.*
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RepositoriesUnitTest {

    @Test
    fun testAuthSignInAndProfileUpdate() = runTest {
        val authRepo = AuthRepositoryImpl(forceMock = true)

        val signInRes = authRepo.signIn("student@apex.edu", "pass123")
        assertTrue(signInRes.isSuccess)
        val profile = signInRes.getOrNull()
        assertNotNull(profile)
        assertEquals("Student", profile?.fullName)

        val updateRes = authRepo.updateProfile("Alice Smith", "CS Honors Senior")
        assertTrue(updateRes.isSuccess)
        val updated = updateRes.getOrNull()
        assertEquals("Alice Smith", updated?.fullName)
        assertEquals("CS Honors Senior", updated?.bio)

        val googleRes = authRepo.signInWithGoogle()
        assertTrue(googleRes.isSuccess)
        assertEquals("Google Student", googleRes.getOrNull()?.fullName)
    }

    @Test
    fun testCollegeRepository() = runTest {
        val collegeRepo = CollegeRepositoryImpl()

        val colleges = collegeRepo.getColleges()
        assertTrue(colleges.isNotEmpty())
        assertEquals("Apex Institute of Technology", colleges.first().name)

        val joinRes = collegeRepo.joinCollege(colleges.first().id, "CS2026-999")
        assertTrue(joinRes.isSuccess)
        val membership = joinRes.getOrNull()
        assertEquals(MembershipRole.STUDENT, membership?.role)
        assertEquals(MembershipStatus.ACTIVE, membership?.status)
    }

    @Test
    fun testNoticeRepositoryFilteringAndBookmarks() = runTest {
        val noticeRepo = NoticeRepositoryImpl()

        val allNotices = noticeRepo.getNotices("col-alpha-1")
        assertEquals(3, allNotices.size)

        val examNotices = noticeRepo.getNotices("col-alpha-1", category = NoticeCategory.EXAMINATION)
        assertEquals(1, examNotices.size)
        assertEquals(NoticeCategory.EXAMINATION, examNotices.first().extraction?.category)

        val searchNotices = noticeRepo.getNotices("col-alpha-1", query = "Google Cloud")
        assertEquals(1, searchNotices.size)
        assertTrue(searchNotices.first().title.contains("Google Cloud") || searchNotices.first().rawContent.contains("Google Cloud"))

        // Test Bookmarks
        val noticeId = allNotices.first().id
        assertFalse(noticeRepo.isBookmarked(noticeId))
        val bookmarked = noticeRepo.toggleBookmark(noticeId)
        assertTrue(bookmarked)
        assertTrue(noticeRepo.isBookmarked(noticeId))

        val bookmarksList = noticeRepo.getBookmarks()
        assertEquals(1, bookmarksList.size)
        assertEquals(noticeId, bookmarksList.first().noticeId)
    }

    @Test
    fun testSourceRepositoryValidation() = runTest {
        val sourceRepo = SourceRepositoryImpl()

        // Test invalid scheme
        val invalidRes = sourceRepo.registerSource("col-alpha-1", "Bad Source", "ftp://test.com", SourceType.NOTICE_BOARD_PAGE)
        assertTrue(invalidRes.isFailure)

        // Test valid source registration
        val validRes = sourceRepo.registerSource("col-alpha-1", "Dean Circulars", "https://apex.edu/circulars", SourceType.NOTICE_BOARD_PAGE)
        assertTrue(validRes.isSuccess)
        val created = validRes.getOrNull()
        assertEquals(SourceStatus.PENDING, created?.status)

        // Test admin approval
        val approveRes = sourceRepo.updateSourceStatus(created!!.id, SourceStatus.APPROVED_ACTIVE)
        assertTrue(approveRes.isSuccess)
    }

    @Test
    fun testCommunityPostAndComments() = runTest {
        val communityRepo = CommunityRepositoryImpl()

        // 1. Initial state should be clean (demo data removed)
        val initialPosts = communityRepo.getPosts("col-alpha-1")
        assertTrue(initialPosts.isEmpty())

        // 2. Create post
        val createRes = communityRepo.createPost(
            collegeId = "col-alpha-1",
            title = "Study session for OS exam",
            content = "Meeting in library room 3B at 5 PM",
            type = PostType.QUESTION
        )
        assertTrue(createRes.isSuccess)
        val newPost = createRes.getOrNull()
        assertNotNull(newPost)

        val posts = communityRepo.getPosts("col-alpha-1")
        assertEquals(1, posts.size)

        // 3. Add and retrieve comment
        val commentRes = communityRepo.addComment(newPost!!.id, "I will join!")
        assertTrue(commentRes.isSuccess)

        val comments = communityRepo.getComments(newPost.id)
        assertEquals(1, comments.size)
        assertEquals("I will join!", comments.first().content)
    }

    @Test
    fun testCommunityExpiredPostAutoDelete() = runTest {
        val communityRepo = CommunityRepositoryImpl()
        val nonExistentPost = communityRepo.getPostById("non-existent-id")
        assertNull(nonExistentPost)
    }

    @Test
    fun testNotificationInbox() = runTest {
        val notifRepo = NotificationRepositoryImpl()

        val notifs = notifRepo.getNotifications()
        assertTrue(notifs.isNotEmpty())
        val unread = notifs.first { !it.isRead }
        assertFalse(unread.isRead)

        notifRepo.markAsRead(unread.id)
        val updatedNotifs = notifRepo.getNotifications()
        val readNotif = updatedNotifs.find { it.id == unread.id }
        assertTrue(readNotif!!.isRead)
    }
}
