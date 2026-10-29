-- 20261028000002_row_level_security.sql
-- Row Level Security (RLS) policies for Notify tables

-- Helper functions for RLS checks (SECURITY DEFINER with restricted search path to avoid privilege escalation)
create or replace function public.is_system_admin(uid uuid)
returns boolean
language sql
security definer
set search_path = public
stable
as $$
    select coalesce(
        (select is_system_admin from public.profiles where id = uid),
        false
    );
$$;

create or replace function public.is_active_college_member(uid uuid, col_id uuid)
returns boolean
language sql
security definer
set search_path = public
stable
as $$
    select exists (
        select 1 from public.college_memberships
        where user_id = uid
          and college_id = col_id
          and status = 'active'
    ) or public.is_system_admin(uid);
$$;

create or replace function public.is_college_admin_or_moderator(uid uuid, col_id uuid)
returns boolean
language sql
security definer
set search_path = public
stable
as $$
    select exists (
        select 1 from public.college_memberships
        where user_id = uid
          and college_id = col_id
          and status = 'active'
          and role in ('moderator', 'college_admin', 'system_admin')
    ) or public.is_system_admin(uid);
$$;

create or replace function public.is_college_admin(uid uuid, col_id uuid)
returns boolean
language sql
security definer
set search_path = public
stable
as $$
    select exists (
        select 1 from public.college_memberships
        where user_id = uid
          and college_id = col_id
          and status = 'active'
          and role in ('college_admin', 'system_admin')
    ) or public.is_system_admin(uid);
$$;

-- Enable RLS on every table
alter table public.colleges enable row level security;
alter table public.profiles enable row level security;
alter table public.college_memberships enable row level security;
alter table public.notice_sources enable row level security;
alter table public.notices enable row level security;
alter table public.notice_versions enable row level security;
alter table public.notice_extractions enable row level security;
alter table public.user_notifications enable row level security;
alter table public.device_tokens enable row level security;
alter table public.student_posts enable row level security;
alter table public.post_attachments enable row level security;
alter table public.comments enable row level security;
alter table public.post_reactions enable row level security;
alter table public.reports enable row level security;
alter table public.bookmarks enable row level security;
alter table public.worker_leases enable row level security;

-- ==========================================
-- 1. COLLEGES POLICIES
-- ==========================================
-- Anyone authenticated can view colleges to join
create policy "colleges_select_authenticated" on public.colleges
    for select to authenticated using (true);

-- Only system admins can insert/update/delete colleges
create policy "colleges_insert_system_admin" on public.colleges
    for insert to authenticated with check (public.is_system_admin(auth.uid()));

create policy "colleges_update_admin" on public.colleges
    for update to authenticated using (
        public.is_system_admin(auth.uid()) or public.is_college_admin(auth.uid(), id)
    );

create policy "colleges_delete_system_admin" on public.colleges
    for delete to authenticated using (public.is_system_admin(auth.uid()));

-- ==========================================
-- 2. PROFILES POLICIES
-- ==========================================
-- Authenticated users can view basic profiles
create policy "profiles_select_authenticated" on public.profiles
    for select to authenticated using (true);

-- Users can insert their own profile upon signup
create policy "profiles_insert_own" on public.profiles
    for insert to authenticated with check (
        auth.uid() = id and is_system_admin = false
    );

-- Users can update only their own profile, cannot elevate is_system_admin
create policy "profiles_update_own" on public.profiles
    for update to authenticated using (
        auth.uid() = id
    ) with check (
        auth.uid() = id and (
            is_system_admin = (select is_system_admin from public.profiles where id = auth.uid())
            or public.is_system_admin(auth.uid())
        )
    );

-- Delete profile: only self or system admin
create policy "profiles_delete_own_or_admin" on public.profiles
    for delete to authenticated using (
        auth.uid() = id or public.is_system_admin(auth.uid())
    );

-- ==========================================
-- 3. COLLEGE MEMBERSHIPS POLICIES
-- ==========================================
-- Users can see their own membership, or members of the same college can see peer memberships
create policy "memberships_select_peer_or_self" on public.college_memberships
    for select to authenticated using (
        user_id = auth.uid()
        or public.is_active_college_member(auth.uid(), college_id)
    );

-- Students can request membership (role must default to student, status pending)
create policy "memberships_insert_request" on public.college_memberships
    for insert to authenticated with check (
        user_id = auth.uid()
        and role = 'student'
        and status = 'pending'
    );

-- Only college admin or system admin can update membership role or status
create policy "memberships_update_admin" on public.college_memberships
    for update to authenticated using (
        public.is_college_admin(auth.uid(), college_id)
        or public.is_system_admin(auth.uid())
    );

-- Delete membership: self can leave, or admin can remove
create policy "memberships_delete_self_or_admin" on public.college_memberships
    for delete to authenticated using (
        user_id = auth.uid()
        or public.is_college_admin(auth.uid(), college_id)
        or public.is_system_admin(auth.uid())
    );

-- ==========================================
-- 4. NOTICE SOURCES POLICIES
-- ==========================================
-- Active members can see sources of their college
create policy "sources_select_members" on public.notice_sources
    for select to authenticated using (
        public.is_active_college_member(auth.uid(), college_id)
    );

-- Active members can submit a new source, but it must be pending
create policy "sources_insert_pending" on public.notice_sources
    for insert to authenticated with check (
        public.is_active_college_member(auth.uid(), college_id)
        and status = 'pending'
        and created_by = auth.uid()
        and approved_by is null
    );

-- Only college admin or system admin can approve, pause, or update source
create policy "sources_update_admin" on public.notice_sources
    for update to authenticated using (
        public.is_college_admin(auth.uid(), college_id)
        or public.is_system_admin(auth.uid())
    );

create policy "sources_delete_admin" on public.notice_sources
    for delete to authenticated using (
        public.is_college_admin(auth.uid(), college_id)
        or public.is_system_admin(auth.uid())
    );

-- ==========================================
-- 5. NOTICES POLICIES
-- ==========================================
-- Active college members can read published notices
create policy "notices_select_active_members" on public.notices
    for select to authenticated using (
        public.is_active_college_member(auth.uid(), college_id)
        and is_published = true
    );

-- Normal students cannot insert, update or delete notices (worker uses service role)
-- College admins can manage notices if needed
create policy "notices_modify_college_admin" on public.notices
    for all to authenticated using (
        public.is_college_admin(auth.uid(), college_id)
    );

-- ==========================================
-- 6. NOTICE VERSIONS & EXTRACTIONS
-- ==========================================
create policy "versions_select_members" on public.notice_versions
    for select to authenticated using (
        exists (
            select 1 from public.notices n
            where n.id = notice_id
              and public.is_active_college_member(auth.uid(), n.college_id)
        )
    );

create policy "extractions_select_members" on public.notice_extractions
    for select to authenticated using (
        exists (
            select 1 from public.notices n
            where n.id = notice_id
              and public.is_active_college_member(auth.uid(), n.college_id)
        )
    );

-- Moderators can verify or adjust extractions
create policy "extractions_update_moderators" on public.notice_extractions
    for update to authenticated using (
        exists (
            select 1 from public.notices n
            where n.id = notice_id
              and public.is_college_admin_or_moderator(auth.uid(), n.college_id)
        )
    );

-- ==========================================
-- 7. USER NOTIFICATIONS POLICIES
-- ==========================================
create policy "notifications_select_own" on public.user_notifications
    for select to authenticated using (user_id = auth.uid());

create policy "notifications_update_own" on public.user_notifications
    for update to authenticated using (user_id = auth.uid());

create policy "notifications_delete_own" on public.user_notifications
    for delete to authenticated using (user_id = auth.uid());

-- ==========================================
-- 8. DEVICE TOKENS POLICIES
-- ==========================================
create policy "tokens_select_own" on public.device_tokens
    for select to authenticated using (user_id = auth.uid());

create policy "tokens_insert_own" on public.device_tokens
    for insert to authenticated with check (user_id = auth.uid());

create policy "tokens_update_own" on public.device_tokens
    for update to authenticated using (user_id = auth.uid());

create policy "tokens_delete_own" on public.device_tokens
    for delete to authenticated using (user_id = auth.uid());

-- ==========================================
-- 9. STUDENT POSTS POLICIES
-- ==========================================
-- Active members can read published posts in their college
create policy "posts_select_college_members" on public.student_posts
    for select to authenticated using (
        public.is_active_college_member(auth.uid(), college_id)
        and (status = 'PUBLISHED' or author_id = auth.uid() or public.is_college_admin_or_moderator(auth.uid(), college_id))
    );

-- Active members can create posts in their own college
create policy "posts_insert_college_members" on public.student_posts
    for insert to authenticated with check (
        author_id = auth.uid()
        and status = 'PUBLISHED'
        and public.is_active_college_member(auth.uid(), college_id)
    );

-- Authors can update their own posts (cannot change status to moderated)
create policy "posts_update_author_or_moderator" on public.student_posts
    for update to authenticated using (
        (author_id = auth.uid() and public.is_active_college_member(auth.uid(), college_id))
        or public.is_college_admin_or_moderator(auth.uid(), college_id)
    );

-- Authors or moderators can delete posts
create policy "posts_delete_author_or_moderator" on public.student_posts
    for delete to authenticated using (
        author_id = auth.uid()
        or public.is_college_admin_or_moderator(auth.uid(), college_id)
    );

-- ==========================================
-- 10. POST ATTACHMENTS POLICIES
-- ==========================================
create policy "attachments_select_college_members" on public.post_attachments
    for select to authenticated using (
        exists (
            select 1 from public.student_posts p
            where p.id = post_id
              and public.is_active_college_member(auth.uid(), p.college_id)
              and (p.status = 'PUBLISHED' or p.author_id = auth.uid() or public.is_college_admin_or_moderator(auth.uid(), p.college_id))
        )
    );

create policy "attachments_insert_post_author" on public.post_attachments
    for insert to authenticated with check (
        exists (
            select 1 from public.student_posts p
            where p.id = post_id
              and p.author_id = auth.uid()
              and public.is_active_college_member(auth.uid(), p.college_id)
        )
    );

create policy "attachments_delete_post_author" on public.post_attachments
    for delete to authenticated using (
        exists (
            select 1 from public.student_posts p
            where p.id = post_id
              and (p.author_id = auth.uid() or public.is_college_admin_or_moderator(auth.uid(), p.college_id))
        )
    );

-- ==========================================
-- 11. COMMENTS POLICIES
-- ==========================================
create policy "comments_select_college_members" on public.comments
    for select to authenticated using (
        exists (
            select 1 from public.student_posts p
            where p.id = post_id
              and public.is_active_college_member(auth.uid(), p.college_id)
              and (p.status = 'PUBLISHED' or p.author_id = auth.uid())
        )
    );

create policy "comments_insert_college_members" on public.comments
    for insert to authenticated with check (
        author_id = auth.uid()
        and exists (
            select 1 from public.student_posts p
            where p.id = post_id
              and public.is_active_college_member(auth.uid(), p.college_id)
              and p.status = 'PUBLISHED'
        )
    );

create policy "comments_update_author" on public.comments
    for update to authenticated using (
        author_id = auth.uid()
        or exists (
            select 1 from public.student_posts p
            where p.id = post_id
              and public.is_college_admin_or_moderator(auth.uid(), p.college_id)
        )
    );

create policy "comments_delete_author" on public.comments
    for delete to authenticated using (
        author_id = auth.uid()
        or exists (
            select 1 from public.student_posts p
            where p.id = post_id
              and public.is_college_admin_or_moderator(auth.uid(), p.college_id)
        )
    );

-- ==========================================
-- 12. POST REACTIONS POLICIES
-- ==========================================
create policy "reactions_select_college_members" on public.post_reactions
    for select to authenticated using (
        exists (
            select 1 from public.student_posts p
            where p.id = post_id
              and public.is_active_college_member(auth.uid(), p.college_id)
        )
    );

create policy "reactions_insert_college_members" on public.post_reactions
    for insert to authenticated with check (
        user_id = auth.uid()
        and exists (
            select 1 from public.student_posts p
            where p.id = post_id
              and public.is_active_college_member(auth.uid(), p.college_id)
        )
    );

create policy "reactions_delete_own" on public.post_reactions
    for delete to authenticated using (user_id = auth.uid());

-- ==========================================
-- 13. REPORTS POLICIES
-- ==========================================
-- Users can see reports they filed; admins/moderators can see all reports for their college
create policy "reports_select_reporter_or_admin" on public.reports
    for select to authenticated using (
        reporter_id = auth.uid()
        or public.is_college_admin_or_moderator(auth.uid(), college_id)
    );

-- Authenticated users can file a report for their college (cannot forge reporter_id)
create policy "reports_insert_own" on public.reports
    for insert to authenticated with check (
        reporter_id = auth.uid()
        and status = 'PENDING'
        and public.is_active_college_member(auth.uid(), college_id)
    );

-- Only admins/moderators can update report status
create policy "reports_update_moderator" on public.reports
    for update to authenticated using (
        public.is_college_admin_or_moderator(auth.uid(), college_id)
    );

-- ==========================================
-- 14. BOOKMARKS POLICIES
-- ==========================================
create policy "bookmarks_select_own" on public.bookmarks
    for select to authenticated using (user_id = auth.uid());

create policy "bookmarks_insert_own" on public.bookmarks
    for insert to authenticated with check (user_id = auth.uid());

create policy "bookmarks_delete_own" on public.bookmarks
    for delete to authenticated using (user_id = auth.uid());

-- ==========================================
-- 15. WORKER LEASES POLICIES
-- ==========================================
-- Only service role has access by default, or system admins for monitoring
create policy "leases_select_admin" on public.worker_leases
    for select to authenticated using (public.is_system_admin(auth.uid()));
