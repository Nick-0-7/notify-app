-- Migration: 20261101000002_cleanup_old_notifications.sql
-- Automatically remove user notifications older than 90 days (3 months) and allow user deletion

-- 1. Clean up existing notifications older than 90 days
DELETE FROM public.user_notifications
WHERE created_at < now() - INTERVAL '90 days';

-- 2. Function to purge notifications older than 90 days
CREATE OR REPLACE FUNCTION public.cleanup_expired_notifications()
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    deleted_rows integer;
BEGIN
    DELETE FROM public.user_notifications
    WHERE created_at < now() - INTERVAL '90 days';
    GET DIAGNOSTICS deleted_rows = ROW_COUNT;
    RETURN deleted_rows;
END;
$$;
