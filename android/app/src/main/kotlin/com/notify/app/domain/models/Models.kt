package com.notify.app.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MembershipRole {
    @SerialName("student") STUDENT,
    @SerialName("moderator") MODERATOR,
    @SerialName("college_admin") COLLEGE_ADMIN,
    @SerialName("system_admin") SYSTEM_ADMIN
}

@Serializable
enum class MembershipStatus {
    @SerialName("pending") PENDING,
    @SerialName("active") ACTIVE,
    @SerialName("suspended") SUSPENDED,
    @SerialName("rejected") REJECTED
}

@Serializable
enum class SourceType {
    @SerialName("HOMEPAGE_CRAWL") HOMEPAGE_CRAWL,
    @SerialName("NOTICE_BOARD_PAGE") NOTICE_BOARD_PAGE,
    @SerialName("RSS_FEED") RSS_FEED
}

@Serializable
enum class SourceStatus {
    @SerialName("pending") PENDING,
    @SerialName("approved_active") APPROVED_ACTIVE,
    @SerialName("paused") PAUSED,
    @SerialName("rejected") REJECTED
}

@Serializable
enum class NoticeCategory {
    @SerialName("examination") EXAMINATION,
    @SerialName("result") RESULT,
    @SerialName("placement") PLACEMENT,
    @SerialName("scholarship") SCHOLARSHIP,
    @SerialName("admission") ADMISSION,
    @SerialName("event") EVENT,
    @SerialName("timetable") TIMETABLE,
    @SerialName("general") GENERAL
}

@Serializable
enum class PostType {
    @SerialName("GENERAL") GENERAL,
    @SerialName("RESOURCE") RESOURCE,
    @SerialName("QUESTION") QUESTION,
    @SerialName("EVENT") EVENT
}

@Serializable
enum class AttachmentType {
    @SerialName("IMAGE") IMAGE,
    @SerialName("PDF") PDF,
    @SerialName("LINK") LINK
}

@Serializable
enum class ReactionType {
    @SerialName("LIKE") LIKE,
    @SerialName("HELPFUL") HELPFUL,
    @SerialName("IMPORTANT") IMPORTANT
}

@Serializable
enum class ReportTarget {
    @SerialName("NOTICE") NOTICE,
    @SerialName("POST") POST,
    @SerialName("COMMENT") COMMENT,
    @SerialName("SOURCE") SOURCE
}

@Serializable
enum class ReportReason {
    @SerialName("INCORRECT_NOTICE") INCORRECT_NOTICE,
    @SerialName("SPAM") SPAM,
    @SerialName("INAPPROPRIATE_CONTENT") INAPPROPRIATE_CONTENT,
    @SerialName("OTHER") OTHER
}

@Serializable
data class College(
    val id: String,
    val name: String,
    val slug: String,
    val domain: String? = null,
    val city: String? = null,
    val state: String? = null,
    val verified: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class UserProfile(
    val id: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val bio: String? = null,
    @SerialName("is_system_admin") val isSystemAdmin: Boolean = false
)

@Serializable
data class CollegeMembership(
    val id: String,
    @SerialName("college_id") val collegeId: String,
    @SerialName("user_id") val userId: String,
    val role: MembershipRole = MembershipRole.STUDENT,
    val status: MembershipStatus = MembershipStatus.PENDING,
    @SerialName("student_id_number") val studentIdNumber: String? = null,
    @SerialName("joined_at") val joinedAt: String? = null,
    val college: College? = null
)

@Serializable
data class NoticeSource(
    val id: String,
    @SerialName("college_id") val collegeId: String,
    val label: String,
    val url: String,
    @SerialName("source_type") val sourceType: SourceType = SourceType.NOTICE_BOARD_PAGE,
    val status: SourceStatus = SourceStatus.PENDING,
    @SerialName("check_interval_minutes") val checkIntervalMinutes: Int = 30,
    @SerialName("last_checked_at") val lastCheckedAt: String? = null,
    @SerialName("last_success_at") val lastSuccessAt: String? = null,
    @SerialName("last_error_message") val lastErrorMessage: String? = null
)

@Serializable
data class Notice(
    val id: String,
    @SerialName("college_id") val collegeId: String,
    @SerialName("source_id") val sourceId: String,
    @SerialName("canonical_url") val canonicalUrl: String,
    @SerialName("original_url") val originalUrl: String,
    val title: String,
    @SerialName("raw_content") val rawContent: String,
    @SerialName("normalized_content") val normalizedContent: String,
    @SerialName("content_hash") val contentHash: String,
    @SerialName("document_type") val documentType: String = "HTML",
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("first_seen_at") val firstSeenAt: String? = null,
    @SerialName("last_checked_at") val lastCheckedAt: String? = null,
    @SerialName("is_published") val isPublished: Boolean = true,
    @SerialName("version_count") val versionCount: Int = 1,
    val extraction: NoticeExtraction? = null
)

@Serializable
data class NoticeExtraction(
    val id: String = "",
    @SerialName("notice_id") val noticeId: String = "",
    val category: NoticeCategory = NoticeCategory.GENERAL,
    @SerialName("dates_extracted") val datesExtracted: List<String> = emptyList(),
    @SerialName("deadlines_extracted") val deadlinesExtracted: List<String> = emptyList(),
    @SerialName("eligibility_criteria") val eligibilityCriteria: List<String> = emptyList(),
    @SerialName("required_documents") val requiredDocuments: List<String> = emptyList(),
    @SerialName("action_items") val actionItems: List<String> = emptyList(),
    @SerialName("confidence_score") val confidenceScore: Float = 0.8f,
    @SerialName("is_verified_by_moderator") val isVerifiedByModerator: Boolean = false
)

@Serializable
data class StudentPost(
    val id: String,
    @SerialName("college_id") val collegeId: String,
    @SerialName("author_id") val authorId: String,
    val title: String,
    val content: String,
    @SerialName("post_type") val postType: PostType = PostType.GENERAL,
    val status: String = "PUBLISHED",
    @SerialName("created_at") val createdAt: String? = null,
    val author: UserProfile? = null,
    val attachments: List<PostAttachment> = emptyList(),
    val commentsCount: Int = 0,
    val reactionsCount: Map<String, Int> = emptyMap(),
    val userReaction: ReactionType? = null
)

@Serializable
data class PostAttachment(
    val id: String,
    @SerialName("post_id") val postId: String,
    @SerialName("file_url") val fileUrl: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("file_size_bytes") val fileSizeBytes: Long,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("file_type") val fileType: AttachmentType
)

@Serializable
data class Comment(
    val id: String,
    @SerialName("post_id") val postId: String,
    @SerialName("author_id") val authorId: String,
    val content: String,
    @SerialName("created_at") val createdAt: String? = null,
    val author: UserProfile? = null
)

@Serializable
data class UserNotification(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("notice_id") val noticeId: String? = null,
    val title: String,
    val body: String,
    @SerialName("notification_type") val notificationType: String = "NEW_NOTICE",
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("deep_link") val deepLink: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Bookmark(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("notice_id") val noticeId: String? = null,
    @SerialName("post_id") val postId: String? = null,
    val note: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val notice: Notice? = null,
    val post: StudentPost? = null
)

@Serializable
data class ContentReport(
    val id: String = "",
    @SerialName("reporter_id") val reporterId: String,
    @SerialName("college_id") val collegeId: String,
    @SerialName("target_type") val targetType: ReportTarget,
    @SerialName("target_id") val targetId: String,
    @SerialName("reason_category") val reasonCategory: ReportReason,
    val details: String? = null,
    val status: String = "PENDING"
)
