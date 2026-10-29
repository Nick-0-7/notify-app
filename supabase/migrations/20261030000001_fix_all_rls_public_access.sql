-- 20261030000001_fix_all_rls_public_access.sql
-- Fix all Row Level Security (RLS) policies so that:
-- 1. All students, friends, and guest users can read published notices and college info
-- 2. Anyone (signed in or guest) can create and read Student Hub posts & comments
-- 3. Profiles, attachments, and memberships are visible and accessible without RLS errors

-- 1. COLLEGES
DROP POLICY IF EXISTS "colleges_select_authenticated" ON public.colleges;
DROP POLICY IF EXISTS "colleges_select_all" ON public.colleges;
CREATE POLICY "colleges_select_all" ON public.colleges
    FOR SELECT USING (true);

-- 2. NOTICES (Allow everyone to view published notices)
DROP POLICY IF EXISTS "notices_select_active_members" ON public.notices;
DROP POLICY IF EXISTS "notices_select_all" ON public.notices;
CREATE POLICY "notices_select_all" ON public.notices
    FOR SELECT USING (is_published = true);

-- 3. NOTICE VERSIONS & EXTRACTIONS
DROP POLICY IF EXISTS "versions_select_members" ON public.notice_versions;
DROP POLICY IF EXISTS "versions_select_all" ON public.notice_versions;
CREATE POLICY "versions_select_all" ON public.notice_versions
    FOR SELECT USING (true);

DROP POLICY IF EXISTS "extractions_select_members" ON public.notice_extractions;
DROP POLICY IF EXISTS "extractions_select_all" ON public.notice_extractions;
CREATE POLICY "extractions_select_all" ON public.notice_extractions
    FOR SELECT USING (true);

-- 4. PROFILES (Allow everyone to view profiles so author names and avatars load)
DROP POLICY IF EXISTS "profiles_select_authenticated" ON public.profiles;
DROP POLICY IF EXISTS "profiles_select_all" ON public.profiles;
CREATE POLICY "profiles_select_all" ON public.profiles
    FOR SELECT USING (true);

DROP POLICY IF EXISTS "profiles_insert_own" ON public.profiles;
DROP POLICY IF EXISTS "profiles_insert_all" ON public.profiles;
CREATE POLICY "profiles_insert_all" ON public.profiles
    FOR INSERT WITH CHECK (true);

DROP POLICY IF EXISTS "profiles_update_own" ON public.profiles;
DROP POLICY IF EXISTS "profiles_update_all" ON public.profiles;
CREATE POLICY "profiles_update_all" ON public.profiles
    FOR UPDATE USING (true);

-- 5. STUDENT POSTS (Student Hub - Open access for posting and viewing)
DROP POLICY IF EXISTS "posts_select_college_members" ON public.student_posts;
DROP POLICY IF EXISTS "posts_select_all" ON public.student_posts;
CREATE POLICY "posts_select_all" ON public.student_posts
    FOR SELECT USING (status = 'PUBLISHED' OR author_id = auth.uid());

DROP POLICY IF EXISTS "posts_insert_college_members" ON public.student_posts;
DROP POLICY IF EXISTS "posts_insert_all" ON public.student_posts;
CREATE POLICY "posts_insert_all" ON public.student_posts
    FOR INSERT WITH CHECK (status = 'PUBLISHED');

DROP POLICY IF EXISTS "posts_update_author_or_moderator" ON public.student_posts;
DROP POLICY IF EXISTS "posts_update_all" ON public.student_posts;
CREATE POLICY "posts_update_all" ON public.student_posts
    FOR UPDATE USING (true);

DROP POLICY IF EXISTS "posts_delete_author_or_moderator" ON public.student_posts;
DROP POLICY IF EXISTS "posts_delete_all" ON public.student_posts;
CREATE POLICY "posts_delete_all" ON public.student_posts
    FOR DELETE USING (true);

-- 6. COMMENTS (Student Hub Comments)
DROP POLICY IF EXISTS "comments_select_college_members" ON public.comments;
DROP POLICY IF EXISTS "comments_select_all" ON public.comments;
CREATE POLICY "comments_select_all" ON public.comments
    FOR SELECT USING (status = 'PUBLISHED' OR author_id = auth.uid());

DROP POLICY IF EXISTS "comments_insert_college_members" ON public.comments;
DROP POLICY IF EXISTS "comments_insert_all" ON public.comments;
CREATE POLICY "comments_insert_all" ON public.comments
    FOR INSERT WITH CHECK (status = 'PUBLISHED');

DROP POLICY IF EXISTS "comments_delete_author_or_moderator" ON public.comments;
DROP POLICY IF EXISTS "comments_delete_all" ON public.comments;
CREATE POLICY "comments_delete_all" ON public.comments
    FOR DELETE USING (true);

-- 7. POST ATTACHMENTS
DROP POLICY IF EXISTS "attachments_select_college_members" ON public.post_attachments;
DROP POLICY IF EXISTS "attachments_select_all" ON public.post_attachments;
CREATE POLICY "attachments_select_all" ON public.post_attachments
    FOR SELECT USING (true);

DROP POLICY IF EXISTS "attachments_insert_college_members" ON public.post_attachments;
DROP POLICY IF EXISTS "attachments_insert_all" ON public.post_attachments;
CREATE POLICY "attachments_insert_all" ON public.post_attachments
    FOR INSERT WITH CHECK (true);

-- 8. COLLEGE MEMBERSHIPS
DROP POLICY IF EXISTS "memberships_select_peer_or_self" ON public.college_memberships;
DROP POLICY IF EXISTS "memberships_select_all" ON public.college_memberships;
CREATE POLICY "memberships_select_all" ON public.college_memberships
    FOR SELECT USING (true);

DROP POLICY IF EXISTS "memberships_insert_request" ON public.college_memberships;
DROP POLICY IF EXISTS "memberships_insert_active" ON public.college_memberships;
DROP POLICY IF EXISTS "memberships_insert_all" ON public.college_memberships;
CREATE POLICY "memberships_insert_all" ON public.college_memberships
    FOR INSERT WITH CHECK (true);

-- 9. DEVICE TOKENS (For push notifications)
DROP POLICY IF EXISTS "tokens_select_own" ON public.device_tokens;
DROP POLICY IF EXISTS "tokens_insert_own" ON public.device_tokens;
DROP POLICY IF EXISTS "tokens_update_own" ON public.device_tokens;
DROP POLICY IF EXISTS "tokens_delete_own" ON public.device_tokens;
DROP POLICY IF EXISTS "tokens_all" ON public.device_tokens;
CREATE POLICY "tokens_all" ON public.device_tokens
    FOR ALL USING (true) WITH CHECK (true);

-- 10. Auto-enroll new students into SITCOE on Google Auth signup
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger
SECURITY DEFINER
SET search_path = public
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO public.profiles (id, full_name, avatar_url, bio, is_system_admin)
    VALUES (
        new.id,
        COALESCE(new.raw_user_meta_data->>'full_name', split_part(new.email, '@', 1)),
        new.raw_user_meta_data->>'avatar_url',
        'Student at Notify Community',
        false
    )
    ON CONFLICT (id) DO UPDATE SET
        full_name = COALESCE(excluded.full_name, profiles.full_name),
        avatar_url = COALESCE(excluded.avatar_url, profiles.avatar_url);

    INSERT INTO public.college_memberships (id, college_id, user_id, role, status)
    VALUES (
        gen_random_uuid(),
        'b0f5e79d-9f32-44fd-a437-94f04a393088'::uuid,
        new.id,
        'student',
        'active'
    )
    ON CONFLICT DO NOTHING;

    RETURN new;
END;
$$;
