-- supabase/tests/rls_tests.sql
-- Automated pgTAP or SQL transaction tests for Row Level Security (RLS)
-- Can be executed via Supabase CLI (`supabase test db`) or psql against test database

begin;

-- Create pgTAP extension if available
create extension if not exists pgtap;

select plan(12);

-- 1. Setup Test Fixtures in Transaction
create temp table test_vars as
select
    gen_random_uuid() as college_a_id,
    gen_random_uuid() as college_b_id,
    gen_random_uuid() as student_a_id,
    gen_random_uuid() as student_b_id,
    gen_random_uuid() as admin_a_id,
    gen_random_uuid() as source_a_id,
    gen_random_uuid() as notice_a_id,
    gen_random_uuid() as post_a_id;

-- Seed Colleges
insert into public.colleges (id, name, slug, domain, verified)
select college_a_id, 'Alpha Engineering College', 'alpha-eng', 'alpha.edu', true from test_vars;

insert into public.colleges (id, name, slug, domain, verified)
select college_b_id, 'Beta Institute of Technology', 'beta-tech', 'beta.edu', true from test_vars;

-- Seed auth users & profiles (simulated)
insert into public.profiles (id, full_name, bio, is_system_admin)
select student_a_id, 'Alice Student', 'Bio Alice', false from test_vars;

insert into public.profiles (id, full_name, bio, is_system_admin)
select student_b_id, 'Bob Student', 'Bio Bob', false from test_vars;

insert into public.profiles (id, full_name, bio, is_system_admin)
select admin_a_id, 'Arthur Admin', 'Bio Arthur', false from test_vars;

-- Seed Memberships
-- Alice is active member in College A
insert into public.college_memberships (college_id, user_id, role, status)
select college_a_id, student_a_id, 'student', 'active' from test_vars;

-- Bob is active member in College B
insert into public.college_memberships (college_id, user_id, role, status)
select college_b_id, student_b_id, 'student', 'active' from test_vars;

-- Arthur is college_admin in College A
insert into public.college_memberships (college_id, user_id, role, status)
select college_a_id, admin_a_id, 'college_admin', 'active' from test_vars;

-- Seed Notice Source in College A
insert into public.notice_sources (id, college_id, label, url, status)
select source_a_id, college_a_id, 'Official Board', 'https://alpha.edu/notices', 'approved_active' from test_vars;

-- Seed Notice in College A
insert into public.notices (id, college_id, source_id, canonical_url, original_url, title, raw_content, normalized_content, content_hash, is_published)
select notice_a_id, college_a_id, source_a_id, 'https://alpha.edu/notices/1', 'https://alpha.edu/notices/1', 'End Sem Exams', 'Exam timetable', 'exam timetable', 'hash1234567890abcdef', true from test_vars;

-- Seed Post in College A by Alice
insert into public.student_posts (id, college_id, author_id, title, content, status)
select post_a_id, college_a_id, student_a_id, 'Study Group for Physics', 'Anyone interested?', 'PUBLISHED' from test_vars;

-- =========================================================================
-- TEST 1: Alice (member of College A) CAN read notices in College A
-- =========================================================================
set local role authenticated;
select set_config('request.jwt.claim.sub', (select student_a_id::text from test_vars), true);

select ok(
    exists (select 1 from public.notices where id = (select notice_a_id from test_vars)),
    'Test 1: Student in College A can read College A notices'
);

-- =========================================================================
-- TEST 2: Bob (member of College B) CANNOT read notices in College A
-- =========================================================================
select set_config('request.jwt.claim.sub', (select student_b_id::text from test_vars), true);

select ok(
    not exists (select 1 from public.notices where id = (select notice_a_id from test_vars)),
    'Test 2: Student in College B CANNOT read College A notices (cross-college isolation)'
);

-- =========================================================================
-- TEST 3: Bob (College B) CANNOT view posts from College A
-- =========================================================================
select ok(
    not exists (select 1 from public.student_posts where id = (select post_a_id from test_vars)),
    'Test 3: Cross-college student cannot view posts from another college'
);

-- =========================================================================
-- TEST 4: Alice (College A) CAN view her own college posts
-- =========================================================================
select set_config('request.jwt.claim.sub', (select student_a_id::text from test_vars), true);

select ok(
    exists (select 1 from public.student_posts where id = (select post_a_id from test_vars)),
    'Test 4: College A student can read College A posts'
);

-- =========================================================================
-- TEST 5: Bob CANNOT insert a post into College A
-- =========================================================================
select set_config('request.jwt.claim.sub', (select student_b_id::text from test_vars), true);

prepare insert_cross_college_post as
insert into public.student_posts (college_id, author_id, title, content)
select college_a_id, student_b_id, 'Intruder Post', 'Spam' from test_vars;

select throws_ok(
    'insert_cross_college_post',
    'new row violates row-level security policy for table "student_posts"',
    'Test 5: Student cannot post in another college'
);

-- =========================================================================
-- TEST 6: Student CANNOT escalate their own role to college_admin
-- =========================================================================
select set_config('request.jwt.claim.sub', (select student_a_id::text from test_vars), true);

prepare student_role_escalate as
update public.college_memberships
set role = 'college_admin'
where user_id = (select student_a_id from test_vars);

select throws_ok(
    'student_role_escalate',
    'new row violates row-level security policy for table "college_memberships"',
    'Test 6: Student cannot self-escalate membership role'
);

-- =========================================================================
-- TEST 7: Alice CANNOT approve a notice source (only admin can)
-- =========================================================================
prepare student_approve_source as
update public.notice_sources
set status = 'approved_active'
where id = (select source_a_id from test_vars);

select throws_ok(
    'student_approve_source',
    'new row violates row-level security policy for table "notice_sources"',
    'Test 7: Student cannot approve notice sources'
);

-- =========================================================================
-- TEST 8: Admin Arthur CAN update notice source
-- =========================================================================
select set_config('request.jwt.claim.sub', (select admin_a_id::text from test_vars), true);

select lives_ok(
    $$ update public.notice_sources set label = 'Updated Label' where id = (select source_a_id from test_vars) $$,
    'Test 8: College admin can update notice source'
);

-- =========================================================================
-- TEST 9: Student Alice can update her own bio
-- =========================================================================
select set_config('request.jwt.claim.sub', (select student_a_id::text from test_vars), true);

select lives_ok(
    $$ update public.profiles set bio = 'Updated Alice Bio' where id = (select student_a_id from test_vars) $$,
    'Test 9: User can update their own profile'
);

-- =========================================================================
-- TEST 10: Student Alice CANNOT update another user profile (Bob)
-- =========================================================================
update public.profiles set bio = 'Hacked Bio' where id = (select student_b_id from test_vars);
select is(
    (select bio from public.profiles where id = (select student_b_id from test_vars)),
    'Bio Bob',
    'Test 10: User cannot alter another users profile'
);

-- =========================================================================
-- TEST 11: Student Alice CAN bookmark a notice
-- =========================================================================
select lives_ok(
    $$ insert into public.bookmarks (user_id, notice_id, note) select student_a_id, notice_a_id, 'Review later' from test_vars $$,
    'Test 11: User can bookmark a notice'
);

-- =========================================================================
-- TEST 12: Bob CANNOT see Alice bookmarks
-- =========================================================================
select set_config('request.jwt.claim.sub', (select student_b_id::text from test_vars), true);

select ok(
    not exists (select 1 from public.bookmarks where user_id = (select student_a_id from test_vars)),
    'Test 12: User cannot view another users bookmarks'
);

select * from finish();

rollback;
