-- 20261029000004_fix_community_rls.sql
-- Fix Student Hub & College Membership RLS so all students and friends can see and participate in discussions

-- 1. Allow public / guest / student read access to all published posts
drop policy if exists "posts_select_college_members" on public.student_posts;
create policy "posts_select_college_members" on public.student_posts
    for select using (
        status = 'PUBLISHED'
        or author_id = auth.uid()
    );

-- 2. Allow any authenticated student to insert published posts
drop policy if exists "posts_insert_college_members" on public.student_posts;
create policy "posts_insert_college_members" on public.student_posts
    for insert to authenticated with check (
        author_id = auth.uid()
        and status = 'PUBLISHED'
    );

-- 3. Allow public read access to comments on published posts
drop policy if exists "comments_select_college_members" on public.comments;
create policy "comments_select_college_members" on public.comments
    for select using (
        status = 'PUBLISHED'
        or author_id = auth.uid()
    );

-- 4. Allow any authenticated student to comment
drop policy if exists "comments_insert_college_members" on public.comments;
create policy "comments_insert_college_members" on public.comments
    for insert to authenticated with check (
        author_id = auth.uid()
        and status = 'PUBLISHED'
    );

-- 5. Allow students to view and join active college membership
drop policy if exists "memberships_select_peer_or_self" on public.college_memberships;
create policy "memberships_select_peer_or_self" on public.college_memberships
    for select using (true);

drop policy if exists "memberships_insert_request" on public.college_memberships;
create policy "memberships_insert_active" on public.college_memberships
    for insert to authenticated with check (
        user_id = auth.uid()
    );

-- 6. Update user creation trigger to automatically enroll new students into default SITCOE college
create or replace function public.handle_new_user()
returns trigger
security definer
set search_path = public
language plpgsql
as $$
begin
    insert into public.profiles (id, full_name, avatar_url, bio, is_system_admin)
    values (
        new.id,
        coalesce(new.raw_user_meta_data->>'full_name', split_part(new.email, '@', 1)),
        new.raw_user_meta_data->>'avatar_url',
        'Student at Notify Community',
        false
    )
    on conflict (id) do update set
        full_name = coalesce(excluded.full_name, profiles.full_name),
        avatar_url = coalesce(excluded.avatar_url, profiles.avatar_url);

    -- Auto-enroll into SITCOE default college as active student
    insert into public.college_memberships (id, college_id, user_id, role, status)
    values (
        gen_random_uuid(),
        'b0f5e79d-9f32-44fd-a437-94f04a393088'::uuid,
        new.id,
        'student',
        'active'
    )
    on conflict do nothing;

    return new;
end;
$$;
