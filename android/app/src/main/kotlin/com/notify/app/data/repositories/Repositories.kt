package com.notify.app.data.repositories

import com.notify.app.core.SupabaseClientProvider
import com.notify.app.domain.models.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

// ==========================================
// 1. AUTH REPOSITORY
// ==========================================
interface AuthRepository {
    val currentUserState: StateFlow<UserProfile?>
    suspend fun signUp(email: String, pass: String, fullName: String): Result<UserProfile>
    suspend fun signIn(email: String, pass: String): Result<UserProfile>
    suspend fun signInWithGoogle(): Result<UserProfile>
    suspend fun signOut(): Result<Unit>
    suspend fun updateProfile(fullName: String, bio: String): Result<UserProfile>
    suspend fun refreshCurrentUser(): UserProfile?
}

class AuthRepositoryImpl(private val forceMock: Boolean = false) : AuthRepository {
    private val isLive get() = !forceMock && SupabaseClientProvider.isConfigured

    private val _currentUser = MutableStateFlow<UserProfile?>(
        UserProfile(
            id = "mock-user-1",
            fullName = "John Doe",
            bio = "Computer Science Student | 3rd Year",
            avatarUrl = null,
            isSystemAdmin = true
        )
    )
    override val currentUserState: StateFlow<UserProfile?> = _currentUser

    override suspend fun signUp(email: String, pass: String, fullName: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            if (isLive) {
                val authRes = SupabaseClientProvider.auth.signUpWith(Email) {
                    this.email = email
                    this.password = pass
                    data = buildJsonObject { put("full_name", fullName) }
                }
                val uid = authRes?.id ?: UUID.randomUUID().toString()
                val profile = UserProfile(id = uid, fullName = fullName, bio = "Student", isSystemAdmin = false)
                _currentUser.value = profile
                syncDeviceTokenIfAvailable()
                Result.success(profile)
            } else {
                val profile = UserProfile(id = UUID.randomUUID().toString(), fullName = fullName, bio = "Enthusiastic Scholar", isSystemAdmin = false)
                _currentUser.value = profile
                Result.success(profile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signIn(email: String, pass: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            if (isLive) {
                SupabaseClientProvider.auth.signInWith(Email) {
                    this.email = email
                    this.password = pass
                }
                val user = SupabaseClientProvider.auth.currentUserOrNull()
                val profile = if (user != null) {
                    try {
                        SupabaseClientProvider.postgrest.from("profiles")
                            .select { filter { eq("id", user.id) } }
                            .decodeSingle<UserProfile>()
                    } catch (_: Exception) {
                        UserProfile(id = user.id, fullName = email.substringBefore("@"), bio = "Student")
                    }
                } else {
                    UserProfile(id = UUID.randomUUID().toString(), fullName = email.substringBefore("@"), bio = "Student")
                }
                _currentUser.value = profile
                syncDeviceTokenIfAvailable()
                Result.success(profile)
            } else {
                val profile = UserProfile(
                    id = "demo-user-1",
                    fullName = email.substringBefore("@").replaceFirstChar { it.uppercase() },
                    bio = "Computer Engineering '26",
                    isSystemAdmin = true
                )
                _currentUser.value = profile
                Result.success(profile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogle(): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            if (isLive) {
                SupabaseClientProvider.auth.signInWith(Google)
                val user = SupabaseClientProvider.auth.currentUserOrNull()
                val profile = if (user != null) {
                    val meta = user.userMetadata
                    val name = meta?.get("full_name")?.toString()?.trim('"') ?: user.email?.substringBefore("@") ?: "Google Student"
                    UserProfile(id = user.id, fullName = name, bio = "Student", isSystemAdmin = false)
                } else {
                    UserProfile(id = UUID.randomUUID().toString(), fullName = "Google Student", bio = "Student", isSystemAdmin = false)
                }
                _currentUser.value = profile
                syncDeviceTokenIfAvailable()
                Result.success(profile)
            } else {
                val profile = UserProfile(
                    id = "google-user-1",
                    fullName = "Google Student",
                    bio = "Verified Google Scholar",
                    isSystemAdmin = false
                )
                _currentUser.value = profile
                Result.success(profile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun syncDeviceTokenIfAvailable() {
        val token = com.notify.app.core.TokenManager.cachedToken
        if (token != null) {
            try {
                RepositoryLocator.notificationRepository.registerDeviceToken(token)
            } catch (_: Exception) {}
        }
    }

    override suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (SupabaseClientProvider.isConfigured) {
                SupabaseClientProvider.auth.signOut()
            }
            _currentUser.value = null
            Result.success(Unit)
        } catch (e: Exception) {
            _currentUser.value = null
            Result.success(Unit)
        }
    }

    override suspend fun updateProfile(fullName: String, bio: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        val current = _currentUser.value ?: return@withContext Result.failure(IllegalStateException("Not logged in"))
        val updated = current.copy(fullName = fullName, bio = bio)
        try {
            if (isLive) {
                SupabaseClientProvider.postgrest.from("profiles").update(updated) {
                    filter { eq("id", current.id) }
                }
            }
            _currentUser.value = updated
            Result.success(updated)
        } catch (e: Exception) {
            _currentUser.value = updated
            Result.success(updated)
        }
    }

    override suspend fun refreshCurrentUser(): UserProfile? {
        return _currentUser.value
    }
}

// ==========================================
// 2. COLLEGE REPOSITORY
// ==========================================
interface CollegeRepository {
    val activeCollegeState: StateFlow<College?>
    suspend fun getColleges(): List<College>
    suspend fun getUserMemberships(userId: String): List<CollegeMembership>
    suspend fun joinCollege(collegeId: String, studentId: String): Result<CollegeMembership>
    fun setActiveCollege(college: College)
}

class CollegeRepositoryImpl : CollegeRepository {
    private val defaultCollege = College(
        id = "col-alpha-1",
        name = "Apex Institute of Technology",
        slug = "apex-institute",
        domain = "apex.edu",
        city = "Seattle",
        state = "Washington",
        verified = true
    )

    private val sitcoeCollege = College(
        id = "b0f5e79d-9f32-44fd-a437-94f04a393088",
        name = "Sharad Institute of Technology College of Engineering (SITCOE)",
        slug = "sitcoe",
        domain = "sitcoe.ac.in",
        city = "Yadrav",
        state = "Maharashtra",
        verified = true
    )

    private val sampleColleges = mutableListOf(
        defaultCollege,
        sitcoeCollege,
        College(
            id = "col-beta-2",
            name = "National College of Engineering",
            slug = "national-eng",
            domain = "nce.edu",
            city = "Boston",
            state = "Massachusetts",
            verified = true
        ),
        College(
            id = "col-gamma-3",
            name = "Metropolitan State University",
            slug = "metro-state",
            domain = "msu.edu",
            city = "Austin",
            state = "Texas",
            verified = false
        )
    )

    private val memberships = mutableListOf(
        CollegeMembership(
            id = "mem-1",
            collegeId = sitcoeCollege.id,
            userId = "mock-user-1",
            role = MembershipRole.STUDENT,
            status = MembershipStatus.ACTIVE,
            studentIdNumber = "SIT-2024-ENG-108",
            college = sitcoeCollege
        ),
        CollegeMembership(
            id = "mem-2",
            collegeId = defaultCollege.id,
            userId = "mock-user-1",
            role = MembershipRole.COLLEGE_ADMIN,
            status = MembershipStatus.ACTIVE,
            studentIdNumber = "CS2023-882",
            college = defaultCollege
        )
    )

    private val _activeCollege = MutableStateFlow<College?>(sitcoeCollege)
    override val activeCollegeState: StateFlow<College?> = _activeCollege

    override suspend fun getColleges(): List<College> = withContext(Dispatchers.IO) {
        if (SupabaseClientProvider.isConfigured) {
            try {
                SupabaseClientProvider.postgrest.from("colleges").select().decodeList<College>()
            } catch (_: Exception) {
                sampleColleges
            }
        } else {
            sampleColleges
        }
    }

    override suspend fun getUserMemberships(userId: String): List<CollegeMembership> = withContext(Dispatchers.IO) {
        memberships.filter { it.userId == userId }
    }

    override suspend fun joinCollege(collegeId: String, studentId: String): Result<CollegeMembership> = withContext(Dispatchers.IO) {
        val col = sampleColleges.find { it.id == collegeId }
            ?: return@withContext Result.failure(IllegalArgumentException("College not found"))
        val mem = CollegeMembership(
            id = UUID.randomUUID().toString(),
            collegeId = collegeId,
            userId = "mock-user-1",
            role = MembershipRole.STUDENT,
            status = MembershipStatus.ACTIVE,
            studentIdNumber = studentId,
            college = col
        )
        memberships.add(mem)
        _activeCollege.value = col
        Result.success(mem)
    }

    override fun setActiveCollege(college: College) {
        _activeCollege.value = college
    }
}

// ==========================================
// 3. NOTICE REPOSITORY
// ==========================================
interface NoticeRepository {
    suspend fun getNotices(collegeId: String, category: NoticeCategory? = null, query: String? = null): List<Notice>
    suspend fun getNoticeById(noticeId: String): Notice?
    suspend fun toggleBookmark(noticeId: String, note: String? = null): Boolean
    suspend fun isBookmarked(noticeId: String): Boolean
    suspend fun getBookmarks(): List<Bookmark>
}

class NoticeRepositoryImpl : NoticeRepository {
    private val bookmarkedNoticeIds = mutableSetOf<String>()

    private val sampleNotices = mutableListOf(
        Notice(
            id = "not-001",
            collegeId = "col-alpha-1",
            sourceId = "src-001",
            canonicalUrl = "https://apex.edu/notices/2026/fall-midterm-schedule",
            originalUrl = "https://apex.edu/notices/2026/fall-midterm-schedule",
            title = "Fall 2026 Midterm Examination Timetable Released",
            rawContent = "Office of the Controller of Examinations. All department heads are requested to inform students that Midterm exams will commence from November 15, 2026. Hall tickets available on portal from Nov 10.",
            normalizedContent = "midterm exams commence november 15 2026 hall tickets portal november 10",
            contentHash = "a1b2c3d4e5f6789012345678abcdef0123456789abcdef0123456789abcdef01",
            documentType = "HTML",
            publishedAt = "2026-10-25T10:00:00Z",
            extraction = NoticeExtraction(
                id = "ext-001",
                noticeId = "not-001",
                category = NoticeCategory.EXAMINATION,
                datesExtracted = listOf("2026-11-15 (Exam Commencement)", "2026-11-10 (Hall Tickets Download)"),
                deadlinesExtracted = listOf("2026-11-10T17:00:00Z"),
                eligibilityCriteria = listOf("Minimum 75% attendance required", "All semester fees cleared"),
                requiredDocuments = listOf("Printed Hall Ticket", "Valid Student Identity Card"),
                actionItems = listOf("Download Hall Ticket before Nov 10", "Verify Exam Hall Seat Number"),
                confidenceScore = 0.95f,
                isVerifiedByModerator = true
            )
        ),
        Notice(
            id = "not-002",
            collegeId = "col-alpha-1",
            sourceId = "src-002",
            canonicalUrl = "https://apex.edu/placements/2026/google-cloud-recruitment",
            originalUrl = "https://apex.edu/placements/2026/google-cloud-recruitment",
            title = "Campus Placement Drive: Google Cloud Software Engineering Internships",
            rawContent = "Training and Placement Cell announcement. Cloud computing engineering roles for 3rd and 4th year students. Register through placement ERP before October 31, 2026.",
            normalizedContent = "campus placement drive software engineering internships register erp october 31",
            contentHash = "b2c3d4e5f6789012345678abcdef0123456789abcdef0123456789abcdef02",
            documentType = "PDF",
            publishedAt = "2026-10-26T14:30:00Z",
            extraction = NoticeExtraction(
                id = "ext-002",
                noticeId = "not-002",
                category = NoticeCategory.PLACEMENT,
                datesExtracted = listOf("2026-10-31 (Registration Deadline)", "2026-11-05 (Online Aptitude Test)"),
                deadlinesExtracted = listOf("2026-10-31T23:59:59Z"),
                eligibilityCriteria = listOf("B.Tech / M.Tech CS / IT / ECE", "CGPA >= 7.5 with no active backlogs"),
                requiredDocuments = listOf("Updated Resume (1 page)", "Grade sheets semesters 1-4"),
                actionItems = listOf("Complete ERP Registration", "Submit GitHub / Portfolio links"),
                confidenceScore = 0.92f,
                isVerifiedByModerator = true
            )
        ),
        Notice(
            id = "not-003",
            collegeId = "col-alpha-1",
            sourceId = "src-001",
            canonicalUrl = "https://apex.edu/scholarships/2026/merit-cum-means",
            originalUrl = "https://apex.edu/scholarships/2026/merit-cum-means",
            title = "Annual Merit-Cum-Means Scholarship Scheme Application",
            rawContent = "Applications are invited from eligible students for the 2026 Merit-Cum-Means Financial Assistance Scholarship. Submit income certificates to Academic Dean office.",
            normalizedContent = "merit cum means financial assistance scholarship income certificate academic dean",
            contentHash = "c3d4e5f6789012345678abcdef0123456789abcdef0123456789abcdef03",
            documentType = "HTML",
            publishedAt = "2026-10-27T09:15:00Z",
            extraction = NoticeExtraction(
                id = "ext-003",
                noticeId = "not-003",
                category = NoticeCategory.SCHOLARSHIP,
                datesExtracted = listOf("2026-11-20 (Application Submission Deadline)"),
                deadlinesExtracted = listOf("2026-11-20T16:00:00Z"),
                eligibilityCriteria = listOf("Family annual income < $6,000", "Top 20% academic ranking"),
                requiredDocuments = listOf("Parent Income Certificate", "Previous Year Marksheet", "Bank Passbook Copy"),
                actionItems = listOf("Collect Form from Dean Office", "Submit verified documents by Nov 20"),
                confidenceScore = 0.88f,
                isVerifiedByModerator = false
            )
        ),
        Notice(
            id = "6e95adb2-72d9-43bc-ba9a-690fa9d9386e",
            collegeId = "b0f5e79d-9f32-44fd-a437-94f04a393088",
            sourceId = "464858d7-9a35-48d6-bc7d-0eec2c46fa68",
            canonicalUrl = "https://www.sitcoe.ac.in/",
            originalUrl = "https://www.sitcoe.ac.in/",
            title = "Sharad Institute of Technology College of Engineering - Official Circular",
            rawContent = "AICTE Scholarship and MAHADBT Scholarship schemes application notifications now active for academic year 2024-25. Visit student development portal for guidelines.",
            normalizedContent = "aicte scholarship mahadbt scholarship schemes application notifications active 2024 25",
            contentHash = "97fe5a271bde15a4e9a62a148a773eea21052df4cbca835ff32289f6418a411e",
            documentType = "HTML",
            publishedAt = "2026-10-29T02:36:41Z",
            extraction = NoticeExtraction(
                id = "db1a1af7-cb66-4090-8fff-9608842167c6",
                noticeId = "6e95adb2-72d9-43bc-ba9a-690fa9d9386e",
                category = NoticeCategory.SCHOLARSHIP,
                datesExtracted = listOf("2024-25 Academic Session"),
                deadlinesExtracted = listOf("2024-11-30T17:00:00Z"),
                eligibilityCriteria = listOf("Enrolled Students", "Valid Domicile / Category Certificate"),
                requiredDocuments = listOf("Income Certificate", "Caste Validity (if applicable)", "Bank Passbook Copy"),
                actionItems = listOf("Apply on MAHADBT Portal", "Submit copy to Student Welfare Office"),
                confidenceScore = 0.95f,
                isVerifiedByModerator = true
            )
        ),
        Notice(
            id = "eb96fab9-ca8c-40e6-895e-75bdb33dcd69",
            collegeId = "b0f5e79d-9f32-44fd-a437-94f04a393088",
            sourceId = "927ca3b8-9a15-4a6d-84cb-58c511f1e845",
            canonicalUrl = "https://www.sitcoe.ac.in/wp-content/uploads/DSY-ACAP-Admission-Notice-2024-25-1.pdf",
            originalUrl = "https://www.sitcoe.ac.in/wp-content/uploads/DSY-ACAP-Admission-Notice-2024-25-1.pdf",
            title = "Direct Second Year (DSY) ACAP Admission Notice 2024-25",
            rawContent = "Notice regarding Against CAP (ACAP) vacancies for Direct Second Year Engineering admissions for AY 2024-25. Eligible diploma candidates must report to admission cell.",
            normalizedContent = "direct second year dsy acap admission notice against cap vacancies engineering",
            contentHash = "4de6c3c29a3465d0b82f807bf5112b6a70a24c78accf1b8b847f6ee5aed62562",
            documentType = "PDF",
            publishedAt = "2026-10-29T02:37:58Z",
            extraction = NoticeExtraction(
                id = "ext-dsy-01",
                noticeId = "eb96fab9-ca8c-40e6-895e-75bdb33dcd69",
                category = NoticeCategory.ADMISSION,
                datesExtracted = listOf("2024-25 Academic Year"),
                deadlinesExtracted = listOf("2024-11-15T18:00:00Z"),
                eligibilityCriteria = listOf("Completed Diploma in Engineering with >= 45% (40% reserved)"),
                requiredDocuments = listOf("Diploma Final Marksheet", "Leaving Certificate", "Allotment Letter"),
                actionItems = listOf("Report to Admission Cell with original documents"),
                confidenceScore = 0.96f,
                isVerifiedByModerator = true
            )
        ),
        Notice(
            id = "1772a9e1-6ce5-4ace-9f53-c79b35a9f634",
            collegeId = "b0f5e79d-9f32-44fd-a437-94f04a393088",
            sourceId = "927ca3b8-9a15-4a6d-84cb-58c511f1e845",
            canonicalUrl = "https://www.sitcoe.ac.in/wp-content/uploads/FYBTech-ILACAP-Admission-Notice-2024-25-1.pdf",
            originalUrl = "https://www.sitcoe.ac.in/wp-content/uploads/FYBTech-ILACAP-Admission-Notice-2024-25-1.pdf",
            title = "First Year (FY-BTech) IL & ACAP Admission Notice 2024-25",
            rawContent = "Institutional Level (IL) and Against CAP vacancy rounds for First Year B.Tech admissions 2024-25. Verification of eligibility and merit display at college premises.",
            normalizedContent = "first year fy btech il acap admission notice institutional level cap vacancy rounds",
            contentHash = "ac6d9e7c26aa1e3e0bc63b8b7af23816bc1150db6baf290594428dffc9852c6f",
            documentType = "PDF",
            publishedAt = "2026-10-29T02:38:01Z",
            extraction = NoticeExtraction(
                id = "ext-fybtech-01",
                noticeId = "1772a9e1-6ce5-4ace-9f53-c79b35a9f634",
                category = NoticeCategory.ADMISSION,
                datesExtracted = listOf("2024-25"),
                deadlinesExtracted = listOf("2024-11-10T17:00:00Z"),
                eligibilityCriteria = listOf("HSC (10+2) with Physics & Mathematics + MHT-CET / JEE Score"),
                requiredDocuments = listOf("HSC Marksheet", "MHT-CET / JEE Scorecard", "Nationality / Domicile"),
                actionItems = listOf("Submit IL Application form at Registrar Desk"),
                confidenceScore = 0.94f,
                isVerifiedByModerator = true
            )
        ),
        Notice(
            id = "a0d55f42-7703-44be-b30e-06a41c9f467e",
            collegeId = "b0f5e79d-9f32-44fd-a437-94f04a393088",
            sourceId = "927ca3b8-9a15-4a6d-84cb-58c511f1e845",
            canonicalUrl = "https://www.sitcoe.ac.in/wp-content/uploads/MTech-ILACAP-Admission-Notice-2024-25-1.pdf",
            originalUrl = "https://www.sitcoe.ac.in/wp-content/uploads/MTech-ILACAP-Admission-Notice-2024-25-1.pdf",
            title = "Master of Technology (M.Tech) IL & ACAP Admission Notice 2024-25",
            rawContent = "Applications invited for vacant seats in M.Tech programs (Mechanical, Civil, Computer, Electrical) for Academic Year 2024-25.",
            normalizedContent = "master technology mtech il acap admission notice vacant seats 2024 25",
            contentHash = "e807527acdad2f174d3ddb2f220594d7003b1eec603b28c89d1030211e3911ab",
            documentType = "PDF",
            publishedAt = "2026-10-29T02:38:05Z",
            extraction = NoticeExtraction(
                id = "ext-mtech-01",
                noticeId = "a0d55f42-7703-44be-b30e-06a41c9f467e",
                category = NoticeCategory.ADMISSION,
                datesExtracted = listOf("2024-25 Session"),
                deadlinesExtracted = listOf("2024-11-20T17:00:00Z"),
                eligibilityCriteria = listOf("B.E. / B.Tech degree with valid GATE or Non-GATE qualification"),
                requiredDocuments = listOf("Degree Certificate", "GATE Scorecard", "Transfer Certificate"),
                actionItems = listOf("Submit PG application to Academic Section"),
                confidenceScore = 0.93f,
                isVerifiedByModerator = false
            )
        ),
        Notice(
            id = "592e319d-185c-4ae1-bece-85476edbe9f7",
            collegeId = "b0f5e79d-9f32-44fd-a437-94f04a393088",
            sourceId = "927ca3b8-9a15-4a6d-84cb-58c511f1e845",
            canonicalUrl = "https://www.sitcoe.ac.in/wp-content/uploads/Branch-Transfer-Notice-24-25-2.pdf",
            originalUrl = "https://www.sitcoe.ac.in/wp-content/uploads/Branch-Transfer-Notice-24-25-2.pdf",
            title = "Internal Branch Transfer Notification for AY 2024-25",
            rawContent = "Students interested in change of branch for Second Year B.Tech must submit branch transfer application forms. Merit list will be prepared based on First Year CGPA.",
            normalizedContent = "internal branch transfer notification second year btech cgpa merit list",
            contentHash = "abb8bf9a40b713834892734b6175290779dd39f1fa1559f418bb723396600a8a",
            documentType = "PDF",
            publishedAt = "2026-10-29T02:38:09Z",
            extraction = NoticeExtraction(
                id = "ext-branch-01",
                noticeId = "592e319d-185c-4ae1-bece-85476edbe9f7",
                category = NoticeCategory.EXAMINATION,
                datesExtracted = listOf("2024-25"),
                deadlinesExtracted = listOf("2024-11-05T17:00:00Z"),
                eligibilityCriteria = listOf("Cleared all Semester 1 and 2 subjects without backlogs"),
                requiredDocuments = listOf("First Year Marksheet copy", "NOC from current department"),
                actionItems = listOf("Submit Branch Transfer form before deadline"),
                confidenceScore = 0.95f,
                isVerifiedByModerator = true
            )
        )
    )

    override suspend fun getNotices(collegeId: String, category: NoticeCategory?, query: String?): List<Notice> = withContext(Dispatchers.IO) {
        sampleNotices.filter { notice ->
            val matchCollege = notice.collegeId == collegeId
            val matchCategory = category == null || notice.extraction?.category == category
            val matchQuery = query.isNullOrBlank() || notice.title.contains(query, ignoreCase = true) || notice.rawContent.contains(query, ignoreCase = true)
            matchCollege && matchCategory && matchQuery
        }
    }

    override suspend fun getNoticeById(noticeId: String): Notice? = withContext(Dispatchers.IO) {
        sampleNotices.find { it.id == noticeId }
    }

    override suspend fun toggleBookmark(noticeId: String, note: String?): Boolean = withContext(Dispatchers.IO) {
        if (bookmarkedNoticeIds.contains(noticeId)) {
            bookmarkedNoticeIds.remove(noticeId)
            false
        } else {
            bookmarkedNoticeIds.add(noticeId)
            true
        }
    }

    override suspend fun isBookmarked(noticeId: String): Boolean = withContext(Dispatchers.IO) {
        bookmarkedNoticeIds.contains(noticeId)
    }

    override suspend fun getBookmarks(): List<Bookmark> = withContext(Dispatchers.IO) {
        bookmarkedNoticeIds.mapNotNull { nid ->
            val notice = sampleNotices.find { it.id == nid }
            notice?.let {
                Bookmark(
                    id = UUID.randomUUID().toString(),
                    userId = "mock-user-1",
                    noticeId = nid,
                    notice = it
                )
            }
        }
    }
}

// ==========================================
// 4. NOTICE SOURCE REPOSITORY
// ==========================================
interface SourceRepository {
    suspend fun getSources(collegeId: String): List<NoticeSource>
    suspend fun registerSource(collegeId: String, label: String, url: String, type: SourceType): Result<NoticeSource>
    suspend fun updateSourceStatus(sourceId: String, status: SourceStatus): Result<Unit>
}

class SourceRepositoryImpl : SourceRepository {
    private val sources = mutableListOf(
        NoticeSource(
            id = "464858d7-9a35-48d6-bc7d-0eec2c46fa68",
            collegeId = "b0f5e79d-9f32-44fd-a437-94f04a393088",
            label = "SITCOE Official Portal",
            url = "https://www.sitcoe.ac.in/",
            sourceType = SourceType.HOMEPAGE_CRAWL,
            status = SourceStatus.APPROVED_ACTIVE,
            checkIntervalMinutes = 15,
            lastCheckedAt = "2026-10-29T08:08:00Z",
            lastSuccessAt = "2026-10-29T08:08:00Z"
        ),
        NoticeSource(
            id = "927ca3b8-9a15-4a6d-84cb-58c511f1e845",
            collegeId = "b0f5e79d-9f32-44fd-a437-94f04a393088",
            label = "SITCOE Notices & Circulars",
            url = "https://www.sitcoe.ac.in/notice-1/",
            sourceType = SourceType.NOTICE_BOARD_PAGE,
            status = SourceStatus.APPROVED_ACTIVE,
            checkIntervalMinutes = 15,
            lastCheckedAt = "2026-10-29T08:08:00Z",
            lastSuccessAt = "2026-10-29T08:08:00Z"
        ),
        NoticeSource(
            id = "src-001",
            collegeId = "col-alpha-1",
            label = "College Official Circulars",
            url = "https://apex.edu/circulars",
            sourceType = SourceType.NOTICE_BOARD_PAGE,
            status = SourceStatus.APPROVED_ACTIVE,
            checkIntervalMinutes = 15,
            lastCheckedAt = "2026-10-28T02:45:00Z",
            lastSuccessAt = "2026-10-28T02:45:00Z"
        ),
        NoticeSource(
            id = "src-002",
            collegeId = "col-alpha-1",
            label = "Placement Cell RSS Feed",
            url = "https://apex.edu/placements/feed.xml",
            sourceType = SourceType.RSS_FEED,
            status = SourceStatus.APPROVED_ACTIVE,
            checkIntervalMinutes = 30,
            lastCheckedAt = "2026-10-28T02:30:00Z",
            lastSuccessAt = "2026-10-28T02:30:00Z"
        ),
        NoticeSource(
            id = "src-003",
            collegeId = "col-alpha-1",
            label = "Student Affairs Board",
            url = "https://apex.edu/student-affairs/notices",
            sourceType = SourceType.NOTICE_BOARD_PAGE,
            status = SourceStatus.PENDING,
            checkIntervalMinutes = 60
        )
    )

    override suspend fun getSources(collegeId: String): List<NoticeSource> = withContext(Dispatchers.IO) {
        sources.filter { it.collegeId == collegeId }
    }

    override suspend fun registerSource(
        collegeId: String,
        label: String,
        url: String,
        type: SourceType
    ): Result<NoticeSource> = withContext(Dispatchers.IO) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return@withContext Result.failure(IllegalArgumentException("URL must start with http:// or https://"))
        }
        val newSource = NoticeSource(
            id = UUID.randomUUID().toString(),
            collegeId = collegeId,
            label = label.trim(),
            url = trimmed,
            sourceType = type,
            status = SourceStatus.PENDING
        )
        sources.add(newSource)
        Result.success(newSource)
    }

    override suspend fun updateSourceStatus(sourceId: String, status: SourceStatus): Result<Unit> = withContext(Dispatchers.IO) {
        val idx = sources.indexOfFirst { it.id == sourceId }
        if (idx != -1) {
            sources[idx] = sources[idx].copy(status = status)
            Result.success(Unit)
        } else {
            Result.failure(IllegalArgumentException("Source not found"))
        }
    }
}

// ==========================================
// 5. STUDENT COMMUNITY REPOSITORY
// ==========================================
interface CommunityRepository {
    suspend fun getPosts(collegeId: String, type: PostType? = null): List<StudentPost>
    suspend fun createPost(
        collegeId: String,
        title: String,
        content: String,
        type: PostType,
        attachments: List<PostAttachment> = emptyList()
    ): Result<StudentPost>
    suspend fun getPostById(postId: String): StudentPost?
    suspend fun getComments(postId: String): List<Comment>
    suspend fun addComment(postId: String, content: String): Result<Comment>
    suspend fun toggleReaction(postId: String, reactionType: ReactionType): Result<Unit>
    suspend fun deletePost(postId: String): Result<Unit>
}

class CommunityRepositoryImpl : CommunityRepository {
    private val posts = mutableListOf(
        StudentPost(
            id = "post-sit-001",
            collegeId = "b0f5e79d-9f32-44fd-a437-94f04a393088",
            authorId = "mock-user-1",
            title = "SITCOE Hackathon 2024 - Tech Innovators Club",
            content = "Hey SITCOEians! Calling all computer, IT, and electronics students for our upcoming annual project showcase & hackathon. Teams of up to 4 members can register at the department lab.",
            postType = PostType.EVENT,
            createdAt = "2026-10-28T10:00:00Z",
            author = UserProfile(id = "mock-user-1", fullName = "Nikhil Sharma", bio = "SITCOE Student"),
            commentsCount = 4,
            reactionsCount = mapOf("LIKE" to 22, "IMPORTANT" to 15),
            userReaction = ReactionType.LIKE
        ),
        StudentPost(
            id = "post-sit-002",
            collegeId = "b0f5e79d-9f32-44fd-a437-94f04a393088",
            authorId = "user-2",
            title = "Direct Second Year (DSY) Syllabus & Lecture Schedule",
            content = "For newly admitted DSY students in Mechanical & Computer branches, here are the drive links to subject syllabus, lab manuals, and timetable.",
            postType = PostType.RESOURCE,
            createdAt = "2026-10-28T12:30:00Z",
            author = UserProfile(id = "user-2", fullName = "Aarav Patil", bio = "SITCOE Student Council"),
            commentsCount = 2,
            reactionsCount = mapOf("LIKE" to 18, "HELPFUL" to 14)
        ),
        StudentPost(
            id = "post-001",
            collegeId = "col-alpha-1",
            authorId = "mock-user-1",
            title = "Discrete Mathematics Past 5 Years Solved Papers",
            content = "Hey everyone! Uploaded clean, compiled notes and past year solved question papers for the upcoming midterms. Hope this helps!",
            postType = PostType.RESOURCE,
            createdAt = "2026-10-27T16:20:00Z",
            author = UserProfile(id = "mock-user-1", fullName = "Nikhil Sharma", bio = "CS 3rd Year"),
            attachments = listOf(
                PostAttachment(
                    id = "att-1",
                    postId = "post-001",
                    fileUrl = "https://apex.edu/files/discrete-math-notes.pdf",
                    storagePath = "col-alpha-1/post-001/notes.pdf",
                    fileName = "Discrete_Math_Solved_Papers.pdf",
                    fileSizeBytes = 2450000,
                    mimeType = "application/pdf",
                    fileType = AttachmentType.PDF
                )
            ),
            commentsCount = 3,
            reactionsCount = mapOf("LIKE" to 14, "HELPFUL" to 9),
            userReaction = ReactionType.HELPFUL
        ),
        StudentPost(
            id = "post-002",
            collegeId = "col-alpha-1",
            authorId = "user-2",
            title = "Forming a Team for National Hackathon 2026",
            content = "Looking for 2 frontend/mobile developers (Compose or Flutter) and 1 ML engineer for the Smart Campus challenge. Ping here or DM!",
            postType = PostType.QUESTION,
            createdAt = "2026-10-27T18:00:00Z",
            author = UserProfile(id = "user-2", fullName = "Aarav Patel", bio = "Software Club Lead"),
            commentsCount = 5,
            reactionsCount = mapOf("LIKE" to 8, "IMPORTANT" to 4)
        )
    )

    private val comments = mutableListOf(
        Comment(
            id = "c-1",
            postId = "post-001",
            authorId = "user-2",
            content = "This is insanely helpful, thanks a lot Nikhil!",
            createdAt = "2026-10-27T16:45:00Z",
            author = UserProfile(id = "user-2", fullName = "Aarav Patel")
        ),
        Comment(
            id = "c-2",
            postId = "post-001",
            authorId = "user-3",
            content = "Are solutions for Chapter 4 included?",
            createdAt = "2026-10-27T17:10:00Z",
            author = UserProfile(id = "user-3", fullName = "Priya Rao")
        )
    )

    override suspend fun getPosts(collegeId: String, type: PostType?): List<StudentPost> = withContext(Dispatchers.IO) {
        posts.filter { it.collegeId == collegeId && (type == null || it.postType == type) }
    }

    override suspend fun createPost(
        collegeId: String,
        title: String,
        content: String,
        type: PostType,
        attachments: List<PostAttachment>
    ): Result<StudentPost> = withContext(Dispatchers.IO) {
        val newPost = StudentPost(
            id = UUID.randomUUID().toString(),
            collegeId = collegeId,
            authorId = "mock-user-1",
            title = title.trim(),
            content = content.trim(),
            postType = type,
            createdAt = "Just now",
            author = UserProfile(id = "mock-user-1", fullName = "John Doe"),
            attachments = attachments,
            commentsCount = 0,
            reactionsCount = emptyMap()
        )
        posts.add(0, newPost)
        Result.success(newPost)
    }

    override suspend fun getPostById(postId: String): StudentPost? = withContext(Dispatchers.IO) {
        posts.find { it.id == postId }
    }

    override suspend fun getComments(postId: String): List<Comment> = withContext(Dispatchers.IO) {
        comments.filter { it.postId == postId }
    }

    override suspend fun addComment(postId: String, content: String): Result<Comment> = withContext(Dispatchers.IO) {
        val newComment = Comment(
            id = UUID.randomUUID().toString(),
            postId = postId,
            authorId = "mock-user-1",
            content = content.trim(),
            createdAt = "Just now",
            author = UserProfile(id = "mock-user-1", fullName = "John Doe")
        )
        comments.add(newComment)
        Result.success(newComment)
    }

    override suspend fun toggleReaction(postId: String, reactionType: ReactionType): Result<Unit> = withContext(Dispatchers.IO) {
        Result.success(Unit)
    }

    override suspend fun deletePost(postId: String): Result<Unit> = withContext(Dispatchers.IO) {
        posts.removeAll { it.id == postId }
        Result.success(Unit)
    }
}

// ==========================================
// 6. NOTIFICATION REPOSITORY
// ==========================================
interface NotificationRepository {
    suspend fun getNotifications(): List<UserNotification>
    suspend fun markAsRead(notificationId: String)
    suspend fun registerDeviceToken(token: String)
}

class NotificationRepositoryImpl : NotificationRepository {
    private val notifications = mutableListOf(
        UserNotification(
            id = "notif-1",
            userId = "mock-user-1",
            noticeId = "not-001",
            title = "New Notice: Fall 2026 Midterm Schedule",
            body = "The examination timetable has been published. Exams begin Nov 15.",
            isRead = false,
            deepLink = "notify://notice/not-001",
            createdAt = "10 minutes ago"
        ),
        UserNotification(
            id = "notif-2",
            userId = "mock-user-1",
            noticeId = "not-002",
            title = "Placement Alert: Google Cloud Recruitment",
            body = "Software engineering internships open for 3rd and 4th year students.",
            isRead = true,
            deepLink = "notify://notice/not-002",
            createdAt = "2 hours ago"
        )
    )

    override suspend fun getNotifications(): List<UserNotification> = withContext(Dispatchers.IO) {
        notifications
    }

    override suspend fun markAsRead(notificationId: String) {
        val idx = notifications.indexOfFirst { it.id == notificationId }
        if (idx != -1) {
            notifications[idx] = notifications[idx].copy(isRead = true)
        }
    }

    override suspend fun registerDeviceToken(token: String) {
        withContext(Dispatchers.IO) {
            try {
                if (SupabaseClientProvider.isConfigured) {
                    val user = SupabaseClientProvider.auth.currentUserOrNull()
                    if (user != null) {
                        val tokenData = buildJsonObject {
                            put("user_id", user.id)
                            put("token", token)
                            put("platform", "android")
                            put("is_active", true)
                        }
                        SupabaseClientProvider.postgrest.from("device_tokens").upsert(tokenData)
                        android.util.Log.d("NotificationRepo", "Device token registered to Supabase for ${user.id}")
                    } else {
                        android.util.Log.d("NotificationRepo", "User not logged in yet. Device token cached for login.")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("NotificationRepo", "Failed registering device token: ${e.message}", e)
            }
        }
    }
}

// ==========================================
// 7. CONTENT REPORT REPOSITORY
// ==========================================
interface ReportRepository {
    suspend fun submitReport(
        collegeId: String,
        targetType: ReportTarget,
        targetId: String,
        reason: ReportReason,
        details: String?
    ): Result<Unit>
    suspend fun getReports(collegeId: String): List<ContentReport>
}

class ReportRepositoryImpl : ReportRepository {
    private val reports = mutableListOf<ContentReport>()

    override suspend fun submitReport(
        collegeId: String,
        targetType: ReportTarget,
        targetId: String,
        reason: ReportReason,
        details: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        reports.add(
            ContentReport(
                id = UUID.randomUUID().toString(),
                reporterId = "mock-user-1",
                collegeId = collegeId,
                targetType = targetType,
                targetId = targetId,
                reasonCategory = reason,
                details = details,
                status = "PENDING"
            )
        )
        Result.success(Unit)
    }

    override suspend fun getReports(collegeId: String): List<ContentReport> = withContext(Dispatchers.IO) {
        reports.filter { it.collegeId == collegeId }
    }
}

// Single instance locator for MVVM ViewModels
object RepositoryLocator {
    val authRepository: AuthRepository by lazy { AuthRepositoryImpl() }
    val collegeRepository: CollegeRepository by lazy { CollegeRepositoryImpl() }
    val noticeRepository: NoticeRepository by lazy { NoticeRepositoryImpl() }
    val sourceRepository: SourceRepository by lazy { SourceRepositoryImpl() }
    val communityRepository: CommunityRepository by lazy { CommunityRepositoryImpl() }
    val notificationRepository: NotificationRepository by lazy { NotificationRepositoryImpl() }
    val reportRepository: ReportRepository by lazy { ReportRepositoryImpl() }
}
