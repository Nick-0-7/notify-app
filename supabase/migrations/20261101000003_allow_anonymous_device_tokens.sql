-- Allow device tokens to be registered for guest users without requiring a logged-in user profile
ALTER TABLE public.device_tokens ALTER COLUMN user_id DROP NOT NULL;

-- Ensure RLS allows anonymous and authenticated users to insert/update device tokens
DROP POLICY IF EXISTS "tokens_all" ON public.device_tokens;
CREATE POLICY "tokens_all" ON public.device_tokens
    FOR ALL USING (true) WITH CHECK (true);
