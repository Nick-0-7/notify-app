-- Migration: 20261101000001_fix_sitcoe_notice_sources.sql
-- Replace outdated/inactive notice-1 source with active WordPress Notification feed & exam timetable feeds for SITCOE

DO $$
DECLARE
    sitcoe_id uuid := 'b0f5e79d-9f32-44fd-a437-94f04a393088';
BEGIN
    -- 1. Deactivate old/stale notice-1 source
    UPDATE public.notice_sources
    SET status = 'disabled',
        updated_at = now()
    WHERE url LIKE '%sitcoe.ac.in/notice-1/%';

    -- 2. Insert or update the official Notifications Board Page
    INSERT INTO public.notice_sources (id, college_id, label, url, source_type, status, check_interval_minutes, last_checked_at)
    VALUES (
        '927ca3b8-9a15-4a6d-84cb-58c511f1e845',
        sitcoe_id,
        'SITCOE Official Notifications',
        'https://www.sitcoe.ac.in/notification/',
        'NOTICE_BOARD_PAGE',
        'approved_active',
        15,
        now()
    )
    ON CONFLICT (id) DO UPDATE SET
        label = EXCLUDED.label,
        url = EXCLUDED.url,
        source_type = EXCLUDED.source_type,
        status = 'approved_active',
        updated_at = now();

    -- 3. Insert or update the Live Notifications RSS Feed
    INSERT INTO public.notice_sources (id, college_id, label, url, source_type, status, check_interval_minutes, last_checked_at)
    VALUES (
        'b5a943a1-4328-48fe-a90b-0ea4fbcd9741',
        sitcoe_id,
        'SITCOE Notifications RSS Feed',
        'https://www.sitcoe.ac.in/notification/feed/',
        'RSS_FEED',
        'approved_active',
        15,
        now()
    )
    ON CONFLICT (id) DO UPDATE SET
        label = EXCLUDED.label,
        url = EXCLUDED.url,
        source_type = EXCLUDED.source_type,
        status = 'approved_active',
        updated_at = now();

    -- 4. Insert or update the Exam Time Tables RSS Feed
    INSERT INTO public.notice_sources (id, college_id, label, url, source_type, status, check_interval_minutes, last_checked_at)
    VALUES (
        'c4371bf9-e705-4d0f-a38f-51920ee86d12',
        sitcoe_id,
        'SITCOE Exam Time Tables Feed',
        'https://www.sitcoe.ac.in/exam-time-table/feed/',
        'RSS_FEED',
        'approved_active',
        15,
        now()
    )
    ON CONFLICT (id) DO UPDATE SET
        label = EXCLUDED.label,
        url = EXCLUDED.url,
        source_type = EXCLUDED.source_type,
        status = 'approved_active',
        updated_at = now();

    -- 5. Insert or update the Exam Form Filling Portal
    INSERT INTO public.notice_sources (id, college_id, label, url, source_type, status, check_interval_minutes, last_checked_at)
    VALUES (
        'e89104ba-81f3-4e4b-91bb-bc858d4a6730',
        sitcoe_id,
        'SITCOE Exam Form Filling',
        'https://www.sitcoe.ac.in/exam-form-filling/',
        'NOTICE_BOARD_PAGE',
        'approved_active',
        15,
        now()
    )
    ON CONFLICT (id) DO UPDATE SET
        label = EXCLUDED.label,
        url = EXCLUDED.url,
        source_type = EXCLUDED.source_type,
        status = 'approved_active',
        updated_at = now();
END $$;
