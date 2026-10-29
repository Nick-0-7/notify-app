-- 20261028000003_storage_and_functions.sql
-- Storage buckets, user triggers, and administrative stored procedures

-- 1. Automatic profile creation on auth.users sign-up
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
    on conflict (id) do nothing;
    return new;
end;
$$;

-- Drop trigger if exists and recreate
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- 2. Storage Buckets Creation
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
    (
        'community_attachments',
        'community_attachments',
        false, -- private bucket!
        10485760, -- 10MB limit
        array['image/jpeg', 'image/png', 'image/webp', 'application/pdf']
    ),
    (
        'notice_documents',
        'notice_documents',
        true, -- public notices
        20971520, -- 20MB limit for official notices/bulletins
        array['application/pdf', 'image/jpeg', 'image/png', 'text/html', 'text/plain']
    )
on conflict (id) do update set
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

-- 3. Storage Object RLS Policies
-- Community attachments path convention: <college_id>/<post_id>/<filename>
create policy "storage_attachments_select" on storage.objects
    for select to authenticated using (
        bucket_id = 'community_attachments'
        and public.is_active_college_member(auth.uid(), (split_part(name, '/', 1))::uuid)
    );

create policy "storage_attachments_insert" on storage.objects
    for insert to authenticated with check (
        bucket_id = 'community_attachments'
        and public.is_active_college_member(auth.uid(), (split_part(name, '/', 1))::uuid)
    );

create policy "storage_attachments_delete" on storage.objects
    for delete to authenticated using (
        bucket_id = 'community_attachments'
        and (
            public.is_college_admin_or_moderator(auth.uid(), (split_part(name, '/', 1))::uuid)
            or owner = auth.uid()
        )
    );

-- 4. Worker Concurrency Lease acquisition function
create or replace function public.acquire_worker_lease(
    p_lease_key text,
    p_holder_id text,
    p_ttl_seconds integer default 900
)
returns boolean
language plpgsql
security definer
as $$
declare
    v_now timestamptz := now();
    v_expires timestamptz := v_now + (p_ttl_seconds || ' seconds')::interval;
begin
    -- Attempt update if expired or held by same worker
    update public.worker_leases
    set holder_id = p_holder_id,
        acquired_at = v_now,
        expires_at = v_expires
    where lease_key = p_lease_key
      and (expires_at < v_now or holder_id = p_holder_id);

    if found then
        return true;
    end if;

    -- Attempt insert if not present
    insert into public.worker_leases (lease_key, holder_id, acquired_at, expires_at)
    values (p_lease_key, p_holder_id, v_now, v_expires)
    on conflict (lease_key) do update
        set holder_id = p_holder_id,
            acquired_at = v_now,
            expires_at = v_expires
        where public.worker_leases.expires_at < v_now;

    return found;
end;
$$;

create or replace function public.release_worker_lease(
    p_lease_key text,
    p_holder_id text
)
returns boolean
language plpgsql
security definer
as $$
begin
    delete from public.worker_leases
    where lease_key = p_lease_key and holder_id = p_holder_id;
    return found;
end;
$$;
