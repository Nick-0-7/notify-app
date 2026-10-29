package com.notify.app.data.repositories

import com.notify.app.core.SupabaseClientProvider
import com.notify.app.domain.models.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
    fun setCurrentUser(profile: UserProfile)
    fun initialize(context: android.content.Context)
}

class AuthRepositoryImpl(private val forceMock: Boolean = false) : AuthRepository {
    private val isLive get() = !forceMock && SupabaseClientProvider.isConfigured

    private val _currentUser = MutableStateFlow<UserProfile?>(
        com.notify.app.core.SessionManager.getSavedProfile()
    )
    override val currentUserState: StateFlow<UserProfile?> = _currentUser

    override fun initialize(context: android.content.Context) {
        com.notify.app.core.SessionManager.init(context)
        val saved = com.notify.app.core.SessionManager.getSavedProfile(context)
        if (saved != null) {
            _currentUser.value = saved
        }
        if (isLive) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val user = SupabaseClientProvider.auth.currentUserOrNull()
                    if (user != null) {
                        val meta = user.userMetadata
                        val name = meta?.get("full_name")?.toString()?.trim('"')
                            ?: user.email?.substringBefore("@")
                            ?: "Student"
                        val profile = UserProfile(
                            id = user.id,
                            fullName = name,
                            bio = "Student",
                            isSystemAdmin = false
                        )
                        _currentUser.value = profile
                        com.notify.app.core.SessionManager.saveProfile(context, profile)
                        syncDeviceTokenIfAvailable()
                    }
                } catch (e: Exception) {
                    android.util.Log.w("AuthRepo", "Error restoring session: ${e.message}")
                }
            }
        }
    }

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
                SupabaseClientProvider.auth.signInWith(Google, redirectUrl = "notify://login-callback")
                val user = SupabaseClientProvider.auth.currentUserOrNull()
                val profile = if (user != null) {
                    val meta = user.userMetadata
                    val name = meta?.get("full_name")?.toString()?.trim('"') ?: user.email?.substringBefore("@") ?: "Google Student"
                    UserProfile(id = user.id, fullName = name, bio = "Student", isSystemAdmin = false)
                } else {
                    UserProfile(id = "pending-google", fullName = "Google Student", bio = "Student", isSystemAdmin = false)
                }
                if (user != null) {
                    _currentUser.value = profile
                    com.notify.app.core.SessionManager.saveProfile(profile = profile)
                    syncDeviceTokenIfAvailable()
                }
                Result.success(profile)
            } else {
                val profile = UserProfile(
                    id = "google-user-1",
                    fullName = "Google Student",
                    bio = "Verified Google Scholar",
                    isSystemAdmin = false
                )
                _currentUser.value = profile
                com.notify.app.core.SessionManager.saveProfile(profile = profile)
                Result.success(profile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun setCurrentUser(profile: UserProfile) {
        _currentUser.value = profile
        com.notify.app.core.SessionManager.saveProfile(profile = profile)
        CoroutineScope(Dispatchers.IO).launch {
            syncDeviceTokenIfAvailable()
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
            com.notify.app.core.SessionManager.clearSession()
            _currentUser.value = null
            Result.success(Unit)
        } catch (e: Exception) {
            com.notify.app.core.SessionManager.clearSession()
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
    fun initialize(context: android.content.Context)
}

class CollegeRepositoryImpl(private val forceMock: Boolean = false) : CollegeRepository {
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

    override fun initialize(context: android.content.Context) {
        com.notify.app.core.SessionManager.init(context)
        val savedId = com.notify.app.core.SessionManager.getActiveCollegeId(context)
        if (savedId != null) {
            val matching = sampleColleges.find { it.id == savedId }
            if (matching != null) {
                _activeCollege.value = matching
            }
        }
    }

    override suspend fun getColleges(): List<College> = withContext(Dispatchers.IO) {
        if (SupabaseClientProvider.isConfigured && !forceMock) {
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
        val curUser = RepositoryLocator.authRepository.currentUserState.value
        val actualUid = curUser?.id ?: "f8ba1cd8-d42b-4132-829b-e8f6dc453259"
        val mem = CollegeMembership(
            id = UUID.randomUUID().toString(),
            collegeId = collegeId,
            userId = actualUid,
            role = MembershipRole.STUDENT,
            status = MembershipStatus.ACTIVE,
            studentIdNumber = studentId,
            college = col
        )
        if (SupabaseClientProvider.isConfigured && !forceMock) {
            try {
                val payload = buildJsonObject {
                    put("id", mem.id)
                    put("college_id", collegeId)
                    put("user_id", actualUid)
                    put("role", "student")
                    put("status", "active")
                    put("student_id_number", studentId)
                }
                SupabaseClientProvider.postgrest.from("college_memberships").insert(payload)
            } catch (e: Exception) {
                android.util.Log.w("CollegeRepo", "Error syncing college membership: ${e.message}")
            }
        }
        memberships.add(mem)
        _activeCollege.value = col
        com.notify.app.core.SessionManager.saveActiveCollegeId(collegeId = col.id)
        Result.success(mem)
    }

    override fun setActiveCollege(college: College) {
        _activeCollege.value = college
        com.notify.app.core.SessionManager.saveActiveCollegeId(collegeId = college.id)
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

class NoticeRepositoryImpl(private val forceMock: Boolean = false) : NoticeRepository {
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
            rawContent = "AICTE Scholarship and MAHADBT Scholarship schemes application notifications now active for academic year 2026-27. Visit student development portal for guidelines.",
            normalizedContent = "aicte scholarship mahadbt scholarship schemes application notifications active 2026 27",
            contentHash = "97fe5a271bde15a4e9a62a148a773eea21052df4cbca835ff32289f6418a411e",
            documentType = "HTML",
            publishedAt = "2026-10-29T02:36:41Z",
            extraction = NoticeExtraction(
                id = "db1a1af7-cb66-4090-8fff-9608842167c6",
                noticeId = "6e95adb2-72d9-43bc-ba9a-690fa9d9386e",
                category = NoticeCategory.SCHOLARSHIP,
                datesExtracted = listOf("2026-27 Academic Session"),
                deadlinesExtracted = listOf("2026-11-30T17:00:00Z"),
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
            canonicalUrl = "https://www.sitcoe.ac.in/notices/midterm-2026.pdf",
            originalUrl = "https://www.sitcoe.ac.in/notices/midterm-2026.pdf",
            title = "SITCOE Midterm Examination Timetable (Nov 2026)",
            rawContent = "Midterm examination schedule for all Engineering departments. Exams will commence from November 18, 2026. Students must carry valid hall tickets.",
            normalizedContent = "midterm examination schedule engineering departments commence november 18 2026",
            contentHash = "4de6c3c29a3465d0b82f807bf5112b6a70a24c78accf1b8b847f6ee5aed62562",
            documentType = "PDF",
            publishedAt = "2026-10-29T10:00:00Z",
            extraction = NoticeExtraction(
                id = "ext-midterm-01",
                noticeId = "eb96fab9-ca8c-40e6-895e-75bdb33dcd69",
                category = NoticeCategory.EXAMINATION,
                datesExtracted = listOf("2026-11-18 (Exam Commencement)", "2026-11-12 (Hall Tickets)"),
                deadlinesExtracted = listOf("2026-11-12T17:00:00Z"),
                eligibilityCriteria = listOf("75% Minimum Attendance", "No Outstanding Tuition Fees"),
                requiredDocuments = listOf("Printed Hall Ticket", "College Identity Card"),
                actionItems = listOf("Download hall ticket from student portal", "Verify exam seating block"),
                confidenceScore = 0.96f,
                isVerifiedByModerator = true
            )
        ),
        Notice(
            id = "1772a9e1-6ce5-4ace-9f53-c79b35a9f634",
            collegeId = "b0f5e79d-9f32-44fd-a437-94f04a393088",
            sourceId = "927ca3b8-9a15-4a6d-84cb-58c511f1e845",
            canonicalUrl = "https://www.sitcoe.ac.in/placements/campus-drive-2026.pdf",
            originalUrl = "https://www.sitcoe.ac.in/placements/campus-drive-2026.pdf",
            title = "Campus Placement Drive: TCS & Infosys Software Roles 2026-27",
            rawContent = "Training and Placement Department circular: Joint campus recruitment drive for final year B.Tech students. Online registrations open until November 25, 2026.",
            normalizedContent = "training placement department joint campus recruitment drive final year btech",
            contentHash = "ac6d9e7c26aa1e3e0bc63b8b7af23816bc1150db6baf290594428dffc9852c6f",
            documentType = "PDF",
            publishedAt = "2026-10-29T11:00:00Z",
            extraction = NoticeExtraction(
                id = "ext-placement-01",
                noticeId = "1772a9e1-6ce5-4ace-9f53-c79b35a9f634",
                category = NoticeCategory.PLACEMENT,
                datesExtracted = listOf("2026-11-25 (Registration Deadline)", "2026-12-02 (Aptitude Test)"),
                deadlinesExtracted = listOf("2026-11-25T23:59:59Z"),
                eligibilityCriteria = listOf("B.Tech CSE/IT/ECE/Mech", "CGPA 6.5 or above without active backlogs"),
                requiredDocuments = listOf("Updated Resume", "Photographs", "Semester Grade Sheets"),
                actionItems = listOf("Register on TPO Portal", "Upload latest resume"),
                confidenceScore = 0.95f,
                isVerifiedByModerator = true
            )
        ),
        Notice(
            id = "a0d55f42-7703-44be-b30e-06a41c9f467e",
            collegeId = "b0f5e79d-9f32-44fd-a437-94f04a393088",
            sourceId = "927ca3b8-9a15-4a6d-84cb-58c511f1e845",
            canonicalUrl = "https://www.sitcoe.ac.in/events/annual-hackathon-2026.pdf",
            originalUrl = "https://www.sitcoe.ac.in/events/annual-hackathon-2026.pdf",
            title = "State Level Technical Symposium & Hackathon 2026",
            rawContent = "Department of Computer Science invites project entries for National Level Hackathon 2026. Exciting cash prizes and internship offers for winners.",
            normalizedContent = "state level technical symposium hackathon project entries cash prizes",
            contentHash = "e807527acdad2f174d3ddb2f220594d7003b1eec603b28c89d1030211e3911ab",
            documentType = "PDF",
            publishedAt = "2026-10-29T12:00:00Z",
            extraction = NoticeExtraction(
                id = "ext-hackathon-01",
                noticeId = "a0d55f42-7703-44be-b30e-06a41c9f467e",
                category = NoticeCategory.EVENT,
                datesExtracted = listOf("2026-12-10 (Event Date)", "2026-11-30 (Abstract Submission)"),
                deadlinesExtracted = listOf("2026-11-30T23:59:00Z"),
                eligibilityCriteria = listOf("Teams of 2 to 4 engineering students"),
                requiredDocuments = listOf("Project Abstract PPT", "College ID Card"),
                actionItems = listOf("Submit team details online before Nov 30"),
                confidenceScore = 0.93f,
                isVerifiedByModerator = true
            )
        ),
        Notice(
            id = "not-w26-reval-01",
            collegeId = "b0f5e79d-9f32-44fd-a437-94f04a393088",
            sourceId = "a7102f00-13c6-431d-83a2-7309e1caa332",
            canonicalUrl = "https://forms.gle/WM52KtEUacsNdxV49",
            originalUrl = "https://forms.gle/WM52KtEUacsNdxV49",
            title = "Revaluation of Winter Semester Examination 2026 (Backlog Course) Final Year B.Tech (Sem-VII)",
            rawContent = "Examination Cell Circular: Online revaluation applications open for Winter Semester Examination 2026 Backlog courses Final Year B.Tech Semester VII. Fill form via direct portal link.",
            normalizedContent = "revaluation winter semester examination 2026 backlog course final year btech sem vii",
            contentHash = "f0a1b2c3d4e5f6789012345678abcdef0123456789abcdef0123456789abc01",
            documentType = "HTML",
            publishedAt = "2026-10-29T14:00:00Z",
            extraction = NoticeExtraction(
                id = "ext-w26-01",
                noticeId = "not-w26-reval-01",
                category = NoticeCategory.EXAMINATION,
                datesExtracted = listOf("Winter Semester Examination 2026", "2026-11-10 (Application Deadline)"),
                deadlinesExtracted = listOf("2026-11-10T23:59:00Z"),
                eligibilityCriteria = listOf("Final Year B.Tech Sem-VII Backlog candidates"),
                requiredDocuments = listOf("Winter 2026 Mark Sheet Copy", "Fee Payment Receipt"),
                actionItems = listOf("Complete online Google Form application before deadline"),
                confidenceScore = 0.98f,
                isVerifiedByModerator = true
            )
        ),
        Notice(
            id = "not-w26-photo-01",
            collegeId = "b0f5e79d-9f32-44fd-a437-94f04a393088",
            sourceId = "a7102f00-13c6-431d-83a2-7309e1caa332",
            canonicalUrl = "https://forms.gle/sVFqBCU211aSpEA4A",
            originalUrl = "https://forms.gle/sVFqBCU211aSpEA4A",
            title = "Photocopy of Winter Semester Examination 2026 (Backlog Course) Final Year B.Tech (Sem-VII)",
            rawContent = "Controller of Examinations: Apply for verified photocopies of answer books for Winter Semester Examination 2026 Backlog courses Final Year B.Tech.",
            normalizedContent = "photocopy winter semester examination 2026 backlog course final year btech sem vii",
            contentHash = "f0a1b2c3d4e5f6789012345678abcdef0123456789abcdef0123456789abc02",
            documentType = "HTML",
            publishedAt = "2026-10-29T14:05:00Z",
            extraction = NoticeExtraction(
                id = "ext-w26-02",
                noticeId = "not-w26-photo-01",
                category = NoticeCategory.EXAMINATION,
                datesExtracted = listOf("Winter Semester Examination 2026", "2026-11-10 (Photocopy Deadline)"),
                deadlinesExtracted = listOf("2026-11-10T23:59:00Z"),
                eligibilityCriteria = listOf("Final Year B.Tech Sem-VII students"),
                requiredDocuments = listOf("Hall Ticket Number", "Online Transaction Reference"),
                actionItems = listOf("Submit Google Form request with subject details"),
                confidenceScore = 0.98f,
                isVerifiedByModerator = true
            )
        ),
        Notice(
            id = "not-w26-reval-nep",
            collegeId = "b0f5e79d-9f32-44fd-a437-94f04a393088",
            sourceId = "a7102f00-13c6-431d-83a2-7309e1caa332",
            canonicalUrl = "https://forms.gle/pi3nuXSCJUQh5Ckm8",
            originalUrl = "https://forms.gle/pi3nuXSCJUQh5Ckm8",
            title = "Revaluation of Winter Semester Examination 2026 (Backlog Course NEP Syllabus) FY. B. Tech (Sem-I)",
            rawContent = "Examination Section notification: Revaluation registration active for First Year B.Tech Semester-I NEP Backlog students.",
            normalizedContent = "revaluation winter semester examination 2026 backlog course nep syllabus fy b tech sem i",
            contentHash = "f0a1b2c3d4e5f6789012345678abcdef0123456789abcdef0123456789abc03",
            documentType = "HTML",
            publishedAt = "2026-10-29T14:10:00Z",
            extraction = NoticeExtraction(
                id = "ext-w26-03",
                noticeId = "not-w26-reval-nep",
                category = NoticeCategory.EXAMINATION,
                datesExtracted = listOf("Winter Semester 2026", "2026-11-12 (Submission Deadline)"),
                deadlinesExtracted = listOf("2026-11-12T23:59:00Z"),
                eligibilityCriteria = listOf("FY B.Tech NEP Syllabus Backlog Course students"),
                requiredDocuments = listOf("PRN Number", "Subject Codes list"),
                actionItems = listOf("Fill form and upload challan before Nov 12"),
                confidenceScore = 0.97f,
                isVerifiedByModerator = true
            )
        ),
        Notice(
            id = "not-w26-photo-nep",
            collegeId = "b0f5e79d-9f32-44fd-a437-94f04a393088",
            sourceId = "a7102f00-13c6-431d-83a2-7309e1caa332",
            canonicalUrl = "https://forms.gle/NdgLQnKkFhGnRzpv7",
            originalUrl = "https://forms.gle/NdgLQnKkFhGnRzpv7",
            title = "Photocopy of Winter Semester Examination 2026 (Backlog Course NEP Syllabus) FY. B. Tech (Sem-I)",
            rawContent = "Official circular: Photocopy of evaluated answer book applications open for FY B.Tech Semester I NEP syllabus.",
            normalizedContent = "photocopy winter semester examination 2026 backlog course nep syllabus fy b tech sem i",
            contentHash = "f0a1b2c3d4e5f6789012345678abcdef0123456789abcdef0123456789abc04",
            documentType = "HTML",
            publishedAt = "2026-10-29T14:15:00Z",
            extraction = NoticeExtraction(
                id = "ext-w26-04",
                noticeId = "not-w26-photo-nep",
                category = NoticeCategory.EXAMINATION,
                datesExtracted = listOf("Winter Semester 2026", "2026-11-12 (Deadline)"),
                deadlinesExtracted = listOf("2026-11-12T23:59:00Z"),
                eligibilityCriteria = listOf("FY B.Tech NEP students"),
                requiredDocuments = listOf("Challan Copy", "Student Identity Card"),
                actionItems = listOf("Submit online application via portal"),
                confidenceScore = 0.97f,
                isVerifiedByModerator = true
            )
        )
    )

    private fun isRecent(notice: Notice): Boolean {
        // Freshness filter: Keep notices from recent academic cycles (2025-2026), reject 2023 or older
        val obsoleteRegex = Regex("""\b(202[0-3]|201\d)\b""")
        if (obsoleteRegex.containsMatchIn(notice.title) && !notice.title.contains("2026") && !notice.title.contains("2025")) return false
        val pub = notice.publishedAt ?: notice.firstSeenAt ?: return true
        val year = pub.take(4).toIntOrNull() ?: return true
        return year >= 2025
    }

    override suspend fun getNotices(collegeId: String, category: NoticeCategory?, query: String?): List<Notice> = withContext(Dispatchers.IO) {
        val baseList: List<Notice> = if (SupabaseClientProvider.isConfigured && !forceMock) {
            try {
                val live = SupabaseClientProvider.postgrest.from("notices")
                    .select(io.github.jan.supabase.postgrest.query.Columns.raw("*, notice_extractions(*)")) {
                        filter {
                            eq("college_id", collegeId)
                            eq("is_published", true)
                        }
                    }.decodeList<Notice>()
                if (live.isNotEmpty()) (live + sampleNotices).distinctBy { it.id } else sampleNotices
            } catch (_: Exception) {
                sampleNotices
            }
        } else {
            sampleNotices
        }

        baseList.filter { notice ->
            val matchRecent = isRecent(notice)
            val matchCollege = notice.collegeId == collegeId || (collegeId == "col-alpha-1" && notice.collegeId == "col-alpha-1")
            val matchCategory = category == null || notice.extraction?.category == category
            val matchQuery = query.isNullOrBlank() || notice.title.contains(query, ignoreCase = true) || notice.rawContent.contains(query, ignoreCase = true)
            matchRecent && matchCollege && matchCategory && matchQuery
        }
    }

    override suspend fun getNoticeById(noticeId: String): Notice? = withContext(Dispatchers.IO) {
        if (SupabaseClientProvider.isConfigured && !forceMock) {
            try {
                val live = SupabaseClientProvider.postgrest.from("notices")
                    .select(io.github.jan.supabase.postgrest.query.Columns.raw("*, notice_extractions(*)")) {
                        filter {
                            eq("id", noticeId)
                        }
                    }.decodeList<Notice>()
                if (live.isNotEmpty()) return@withContext live.first()
            } catch (_: Exception) {}
        }
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

class CommunityRepositoryImpl(private val forceMock: Boolean = false) : CommunityRepository {
    // Local cache + fallback
    private val posts = mutableListOf<StudentPost>()
    private val comments = mutableListOf<Comment>()

    private fun isExpired(createdAtStr: String?): Boolean {
        // Keep all student posts permanently in the community feed
        return false
    }

    private fun purgeExpiredPosts() {
        val expiredIds = posts.filter { isExpired(it.createdAt) }.map { it.id }.toSet()
        if (expiredIds.isNotEmpty()) {
            posts.removeAll { it.id in expiredIds }
            comments.removeAll { it.postId in expiredIds }
        }
    }

    override suspend fun getPosts(collegeId: String, type: PostType?): List<StudentPost> = withContext(Dispatchers.IO) {
        purgeExpiredPosts()
        val baseList: List<StudentPost> = if (SupabaseClientProvider.isConfigured && !forceMock) {
            try {
                val live = SupabaseClientProvider.postgrest.from("student_posts")
                    .select(io.github.jan.supabase.postgrest.query.Columns.raw("*, author:profiles!student_posts_author_id_fkey(*)")) {
                        filter {
                            eq("college_id", collegeId)
                            eq("status", "PUBLISHED")
                            if (type != null) {
                                eq("post_type", type.name)
                            }
                        }
                        order(column = "created_at", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    }.decodeList<StudentPost>()
                if (live.isNotEmpty()) {
                    posts.clear()
                    posts.addAll(live)
                    live
                } else posts
            } catch (e: Exception) {
                println("[CommunityRepo] Error fetching live student posts from Supabase: ${e.message}")
                posts
            }
        } else {
            posts
        }
        baseList.filter { it.collegeId == collegeId && (type == null || it.postType == type) && !isExpired(it.createdAt) }
    }

    override suspend fun createPost(
        collegeId: String,
        title: String,
        content: String,
        type: PostType,
        attachments: List<PostAttachment>
    ): Result<StudentPost> = withContext(Dispatchers.IO) {
        purgeExpiredPosts()
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val user = if (SupabaseClientProvider.isConfigured && !forceMock) {
            try { SupabaseClientProvider.auth.currentUserOrNull() } catch (_: Throwable) { null }
        } else null
        val curProfile = RepositoryLocator.authRepository.currentUserState.value
        val authorId = user?.id ?: curProfile?.id ?: "5349d494-f295-4d76-a51b-e4d657544fc2"
        val authorName = curProfile?.fullName ?: "Student"

        val postId = UUID.randomUUID().toString()
        val nowIso = isoFormat.format(Date())

        val newPost = StudentPost(
            id = postId,
            collegeId = collegeId,
            authorId = authorId,
            title = title.trim(),
            content = content.trim(),
            postType = type,
            status = "PUBLISHED",
            createdAt = nowIso,
            author = UserProfile(id = authorId, fullName = authorName),
            attachments = attachments,
            commentsCount = 0,
            reactionsCount = emptyMap()
        )

        if (SupabaseClientProvider.isConfigured && !forceMock) {
            try {
                val postPayload = buildJsonObject {
                    put("id", postId)
                    put("college_id", collegeId)
                    put("author_id", authorId)
                    put("title", title.trim())
                    put("content", content.trim())
                    put("post_type", type.name)
                    put("status", "PUBLISHED")
                }
                SupabaseClientProvider.postgrest.from("student_posts").insert(postPayload)
                println("[CommunityRepo] Inserted post $postId to Supabase")
            } catch (e: Exception) {
                println("[CommunityRepo] Failed to upload post to Supabase: ${e.message}")
            }
        }

        posts.add(0, newPost)
        Result.success(newPost)
    }

    override suspend fun getPostById(postId: String): StudentPost? = withContext(Dispatchers.IO) {
        purgeExpiredPosts()
        val cached = posts.find { it.id == postId && !isExpired(it.createdAt) }
        if (cached != null) return@withContext cached

        if (SupabaseClientProvider.isConfigured && !forceMock) {
            try {
                val live = SupabaseClientProvider.postgrest.from("student_posts")
                    .select(io.github.jan.supabase.postgrest.query.Columns.raw("*, author:profiles!student_posts_author_id_fkey(*)")) {
                        filter { eq("id", postId) }
                    }.decodeList<StudentPost>().firstOrNull()
                if (live != null && !isExpired(live.createdAt)) {
                    return@withContext live
                }
            } catch (_: Exception) {}
        }
        null
    }

    override suspend fun getComments(postId: String): List<Comment> = withContext(Dispatchers.IO) {
        purgeExpiredPosts()
        if (SupabaseClientProvider.isConfigured && !forceMock) {
            try {
                val liveComments = SupabaseClientProvider.postgrest.from("comments")
                    .select(io.github.jan.supabase.postgrest.query.Columns.raw("*, author:profiles!comments_author_id_fkey(*)")) {
                        filter { eq("post_id", postId) }
                        order(column = "created_at", order = io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                    }.decodeList<Comment>()
                if (liveComments.isNotEmpty()) {
                    return@withContext (liveComments + comments.filter { it.postId == postId }).distinctBy { it.id }
                }
            } catch (_: Exception) {}
        }
        comments.filter { it.postId == postId }
    }

    override suspend fun addComment(postId: String, content: String): Result<Comment> = withContext(Dispatchers.IO) {
        purgeExpiredPosts()
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val user = if (SupabaseClientProvider.isConfigured && !forceMock) {
            try { SupabaseClientProvider.auth.currentUserOrNull() } catch (_: Throwable) { null }
        } else null
        val curProfile = RepositoryLocator.authRepository.currentUserState.value
        val authorId = user?.id ?: curProfile?.id ?: "5349d494-f295-4d76-a51b-e4d657544fc2"
        val authorName = curProfile?.fullName ?: "Student"

        val commentId = UUID.randomUUID().toString()
        val nowIso = isoFormat.format(Date())

        val newComment = Comment(
            id = commentId,
            postId = postId,
            authorId = authorId,
            content = content.trim(),
            createdAt = nowIso,
            author = UserProfile(id = authorId, fullName = authorName)
        )

        if (SupabaseClientProvider.isConfigured && !forceMock) {
            try {
                val commentPayload = buildJsonObject {
                    put("id", commentId)
                    put("post_id", postId)
                    put("author_id", authorId)
                    put("content", content.trim())
                }
                SupabaseClientProvider.postgrest.from("comments").insert(commentPayload)
                println("[CommunityRepo] Inserted comment $commentId to Supabase")
            } catch (e: Exception) {
                println("[CommunityRepo] Failed to upload comment to Supabase: ${e.message}")
            }
        }

        comments.add(newComment)

        // Increment comments count on parent post
        val idx = posts.indexOfFirst { it.id == postId }
        if (idx != -1) {
            posts[idx] = posts[idx].copy(commentsCount = posts[idx].commentsCount + 1)
        }

        Result.success(newComment)
    }

    override suspend fun toggleReaction(postId: String, reactionType: ReactionType): Result<Unit> = withContext(Dispatchers.IO) {
        purgeExpiredPosts()
        val idx = posts.indexOfFirst { it.id == postId }
        if (idx != -1) {
            val currentPost = posts[idx]
            val key = reactionType.name
            val currentCount = currentPost.reactionsCount[key] ?: 0
            val newMap = currentPost.reactionsCount.toMutableMap()
            if (currentPost.userReaction == reactionType) {
                newMap[key] = (currentCount - 1).coerceAtLeast(0)
                posts[idx] = currentPost.copy(reactionsCount = newMap, userReaction = null)
            } else {
                newMap[key] = currentCount + 1
                posts[idx] = currentPost.copy(reactionsCount = newMap, userReaction = reactionType)
            }
        }
        Result.success(Unit)
    }

    override suspend fun deletePost(postId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (SupabaseClientProvider.isConfigured && !forceMock) {
            try {
                SupabaseClientProvider.postgrest.from("student_posts").delete {
                    filter { eq("id", postId) }
                }
            } catch (_: Exception) {}
        }
        posts.removeAll { it.id == postId }
        comments.removeAll { it.postId == postId }
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
