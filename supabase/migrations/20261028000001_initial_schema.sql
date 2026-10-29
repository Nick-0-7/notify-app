-- 20261028000001_initial_schema.sql
-- Notify: College Notification & Student Community Database Schema

-- Enable UUID extension
create extension if not exists "uuid-ossp";
create extension if not exists "pgcrypto";

-- Enums
create type membership_role as enum ('student', 'moderator', 'college_admin', 'system_admin');
create type membership_status as enum ('pending', 'active', 'suspended', 'rejected');
create type source_type_enum as enum ('HOMEPAGE_CRAWL', 'NOTICE_BOARD_PAGE', 'RSS_FEED');
create type source_status_enum as enum ('pending', 'approved_active', 'paused', 'rejected');
create type notice_doc_type as enum ('HTML', 'PDF', 'FEED_ITEM');
create type notice_category_enum as enum (
    'examination',
    'result',
    'placement',
    'scholarship',
    'admission',
    'event',
    'timetable',
    'general'
);
create type post_type_enum as enum ('GENERAL', 'RESOURCE', 'QUESTION', 'EVENT');
create type post_status_enum as enum ('PUBLISHED', 'HIDDEN_MODERATED', 'DELETED');
create type attachment_type_enum as enum ('IMAGE', 'PDF', 'LINK');
create type reaction_type_enum as enum ('LIKE', 'HELPFUL', 'IMPORTANT');
create type report_target_enum as enum ('NOTICE', 'POST', 'COMMENT', 'SOURCE');
create type report_status_enum as enum ('PENDING', 'RESOLVED', 'DISMISSED');
create type report_reason_enum as enum ('INCORRECT_NOTICE', 'SPAM', 'INAPPROPRIATE_CONTENT', 'OTHER');

-- 1. COLLEGES
create table public.colleges (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    slug text not null unique,
    domain text,
    city text,
    state text,
    verified boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index idx_colleges_slug on public.colleges(slug);
create index idx_colleges_verified on public.colleges(verified);

-- 2. PROFILES (1:1 with auth.users)
create table public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    full_name text not null,
    avatar_url text,
    bio text,
    is_system_admin boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index idx_profiles_is_system_admin on public.profiles(is_system_admin);

-- 3. COLLEGE MEMBERSHIPS
create table public.college_memberships (
    id uuid primary key default gen_random_uuid(),
    college_id uuid not null references public.colleges(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    role membership_role not null default 'student',
    status membership_status not null default 'pending',
    student_id_number text,
    joined_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_college_user unique (college_id, user_id)
);
create index idx_memberships_user on public.college_memberships(user_id);
create index idx_memberships_college on public.college_memberships(college_id);
create index idx_memberships_status_role on public.college_memberships(status, role);

-- 4. NOTICE SOURCES
create table public.notice_sources (
    id uuid primary key default gen_random_uuid(),
    college_id uuid not null references public.colleges(id) on delete cascade,
    label text not null,
    url text not null,
    source_type source_type_enum not null default 'NOTICE_BOARD_PAGE',
    status source_status_enum not null default 'pending',
    check_interval_minutes integer not null default 30 check (check_interval_minutes >= 5),
    last_checked_at timestamptz,
    last_success_at timestamptz,
    last_error_message text,
    created_by uuid references public.profiles(id) on delete set null,
    approved_by uuid references public.profiles(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_college_source_url unique (college_id, url)
);
create index idx_sources_college on public.notice_sources(college_id);
create index idx_sources_status on public.notice_sources(status);

-- 5. NOTICES
create table public.notices (
    id uuid primary key default gen_random_uuid(),
    college_id uuid not null references public.colleges(id) on delete cascade,
    source_id uuid not null references public.notice_sources(id) on delete cascade,
    canonical_url text not null,
    original_url text not null,
    title text not null,
    raw_content text not null,
    normalized_content text not null,
    content_hash varchar(64) not null,
    document_type notice_doc_type not null default 'HTML',
    published_at timestamptz,
    first_seen_at timestamptz not null default now(),
    last_checked_at timestamptz not null default now(),
    is_published boolean not null default true,
    version_count integer not null default 1,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_notice_source_hash unique (source_id, content_hash)
);
create index idx_notices_college on public.notices(college_id);
create index idx_notices_published_at on public.notices(published_at desc nulls last);
create index idx_notices_first_seen on public.notices(first_seen_at desc);
create index idx_notices_canonical_url on public.notices(canonical_url);

-- 6. NOTICE VERSIONS
create table public.notice_versions (
    id uuid primary key default gen_random_uuid(),
    notice_id uuid not null references public.notices(id) on delete cascade,
    version_number integer not null,
    title text not null,
    raw_content text not null,
    normalized_content text not null,
    content_hash varchar(64) not null,
    change_summary text,
    created_at timestamptz not null default now(),
    constraint uq_notice_version unique (notice_id, version_number)
);
create index idx_versions_notice on public.notice_versions(notice_id);

-- 7. NOTICE EXTRACTIONS
create table public.notice_extractions (
    id uuid primary key default gen_random_uuid(),
    notice_id uuid not null references public.notices(id) on delete cascade,
    version_id uuid references public.notice_versions(id) on delete set null,
    category notice_category_enum not null default 'general',
    dates_extracted jsonb not null default '[]'::jsonb,
    deadlines_extracted jsonb not null default '[]'::jsonb,
    eligibility_criteria jsonb not null default '[]'::jsonb,
    required_documents jsonb not null default '[]'::jsonb,
    action_items jsonb not null default '[]'::jsonb,
    supporting_passages jsonb not null default '{}'::jsonb,
    confidence_score real not null default 0.0 check (confidence_score >= 0.0 and confidence_score <= 1.0),
    is_verified_by_moderator boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_notice_extraction unique (notice_id)
);
create index idx_extractions_notice on public.notice_extractions(notice_id);
create index idx_extractions_category on public.notice_extractions(category);

-- 8. USER NOTIFICATIONS (In-app inbox)
create table public.user_notifications (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles(id) on delete cascade,
    notice_id uuid references public.notices(id) on delete cascade,
    title text not null,
    body text not null,
    notification_type text not null default 'NEW_NOTICE',
    is_read boolean not null default false,
    read_at timestamptz,
    deep_link text,
    created_at timestamptz not null default now()
);
create index idx_notifications_user_unread on public.user_notifications(user_id, is_read, created_at desc);

-- 9. DEVICE TOKENS (for FCM)
create table public.device_tokens (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles(id) on delete cascade,
    token text not null unique,
    platform text not null default 'android',
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index idx_device_tokens_user on public.device_tokens(user_id, is_active);

-- 10. STUDENT POSTS (Community)
create table public.student_posts (
    id uuid primary key default gen_random_uuid(),
    college_id uuid not null references public.colleges(id) on delete cascade,
    author_id uuid not null references public.profiles(id) on delete cascade,
    title text not null,
    content text not null,
    post_type post_type_enum not null default 'GENERAL',
    status post_status_enum not null default 'PUBLISHED',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index idx_posts_college_created on public.student_posts(college_id, created_at desc);
create index idx_posts_author on public.student_posts(author_id);
create index idx_posts_status on public.student_posts(status);

-- 11. POST ATTACHMENTS
create table public.post_attachments (
    id uuid primary key default gen_random_uuid(),
    post_id uuid not null references public.student_posts(id) on delete cascade,
    file_url text not null,
    storage_path text not null,
    file_name text not null,
    file_size_bytes bigint not null check (file_size_bytes > 0),
    mime_type text not null,
    file_type attachment_type_enum not null,
    sha256_hash varchar(64),
    created_at timestamptz not null default now()
);
create index idx_attachments_post on public.post_attachments(post_id);

-- 12. COMMENTS
create table public.comments (
    id uuid primary key default gen_random_uuid(),
    post_id uuid not null references public.student_posts(id) on delete cascade,
    author_id uuid not null references public.profiles(id) on delete cascade,
    content text not null,
    status post_status_enum not null default 'PUBLISHED',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index idx_comments_post on public.comments(post_id, created_at asc);
create index idx_comments_author on public.comments(author_id);

-- 13. POST REACTIONS
create table public.post_reactions (
    id uuid primary key default gen_random_uuid(),
    post_id uuid not null references public.student_posts(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    reaction_type reaction_type_enum not null default 'LIKE',
    created_at timestamptz not null default now(),
    constraint uq_post_user_reaction unique (post_id, user_id, reaction_type)
);
create index idx_reactions_post on public.post_reactions(post_id);

-- 14. REPORTS
create table public.reports (
    id uuid primary key default gen_random_uuid(),
    reporter_id uuid not null references public.profiles(id) on delete cascade,
    college_id uuid not null references public.colleges(id) on delete cascade,
    target_type report_target_enum not null,
    target_id uuid not null,
    reason_category report_reason_enum not null,
    details text,
    status report_status_enum not null default 'PENDING',
    resolved_by uuid references public.profiles(id) on delete set null,
    resolved_at timestamptz,
    created_at timestamptz not null default now()
);
create index idx_reports_college_status on public.reports(college_id, status);
create index idx_reports_reporter on public.reports(reporter_id);

-- 15. BOOKMARKS
create table public.bookmarks (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles(id) on delete cascade,
    notice_id uuid references public.notices(id) on delete cascade,
    post_id uuid references public.student_posts(id) on delete cascade,
    note text,
    created_at timestamptz not null default now(),
    constraint check_bookmark_target check (
        (notice_id is not null and post_id is null) or
        (notice_id is null and post_id is not null)
    )
);
create unique index uq_bookmark_user_notice on public.bookmarks(user_id, notice_id) where notice_id is not null;
create unique index uq_bookmark_user_post on public.bookmarks(user_id, post_id) where post_id is not null;
create index idx_bookmarks_user on public.bookmarks(user_id);

-- 16. WORKER LEASES (Concurrency lock)
create table public.worker_leases (
    lease_key text primary key,
    holder_id text not null,
    acquired_at timestamptz not null default now(),
    expires_at timestamptz not null
);

-- Updated_at auto-trigger function
create or replace function public.handle_updated_at()
returns trigger as $$
begin
    new.updated_at = now();
    return new;
end;
$$ language plpgsql;

create trigger trg_colleges_updated_at before update on public.colleges
for each row execute function public.handle_updated_at();

create trigger trg_profiles_updated_at before update on public.profiles
for each row execute function public.handle_updated_at();

create trigger trg_college_memberships_updated_at before update on public.college_memberships
for each row execute function public.handle_updated_at();

create trigger trg_notice_sources_updated_at before update on public.notice_sources
for each row execute function public.handle_updated_at();

create trigger trg_notices_updated_at before update on public.notices
for each row execute function public.handle_updated_at();

create trigger trg_notice_extractions_updated_at before update on public.notice_extractions
for each row execute function public.handle_updated_at();

create trigger trg_device_tokens_updated_at before update on public.device_tokens
for each row execute function public.handle_updated_at();

create trigger trg_student_posts_updated_at before update on public.student_posts
for each row execute function public.handle_updated_at();

create trigger trg_comments_updated_at before update on public.comments
for each row execute function public.handle_updated_at();
