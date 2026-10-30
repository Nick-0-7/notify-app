-- 20261030000002_study_materials.sql
-- Table and storage bucket for Study Materials, Question Papers (PYQ), Notes & Lab Manuals

CREATE TABLE IF NOT EXISTS public.study_materials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    college_id UUID NOT NULL REFERENCES public.colleges(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    subject TEXT NOT NULL,
    branch TEXT NOT NULL DEFAULT 'ALL',
    semester INTEGER NOT NULL DEFAULT 1 CHECK (semester >= 1 AND semester <= 8),
    category TEXT NOT NULL DEFAULT 'PYQ', -- 'PYQ', 'NOTES', 'LAB_MANUAL', 'SYLLABUS'
    file_url TEXT NOT NULL,
    file_name TEXT NOT NULL,
    file_size_bytes BIGINT NOT NULL DEFAULT 0,
    mime_type TEXT NOT NULL DEFAULT 'application/pdf',
    likes_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_materials_college ON public.study_materials(college_id);
CREATE INDEX IF NOT EXISTS idx_materials_branch_sem ON public.study_materials(branch, semester);
CREATE INDEX IF NOT EXISTS idx_materials_category ON public.study_materials(category);
CREATE INDEX IF NOT EXISTS idx_materials_created ON public.study_materials(created_at DESC);

-- Enable RLS
ALTER TABLE public.study_materials ENABLE ROW LEVEL SECURITY;

-- Policies: Everyone can view, authenticated students can upload
DROP POLICY IF EXISTS "materials_select_all" ON public.study_materials;
CREATE POLICY "materials_select_all" ON public.study_materials
    FOR SELECT USING (true);

DROP POLICY IF EXISTS "materials_insert_all" ON public.study_materials;
CREATE POLICY "materials_insert_all" ON public.study_materials
    FOR INSERT WITH CHECK (true);

DROP POLICY IF EXISTS "materials_update_all" ON public.study_materials;
CREATE POLICY "materials_update_all" ON public.study_materials
    FOR UPDATE USING (true);

DROP POLICY IF EXISTS "materials_delete_own" ON public.study_materials;
CREATE POLICY "materials_delete_own" ON public.study_materials
    FOR DELETE USING (auth.uid() = author_id);

-- Storage bucket for study materials
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'study_materials',
    'study_materials',
    true, -- public readable so students can download without tokens
    15728640, -- 15MB limit
    ARRAY['application/pdf', 'image/jpeg', 'image/png', 'image/webp']
)
ON CONFLICT (id) DO UPDATE SET
    public = true,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

-- Storage object policies for study_materials bucket
DROP POLICY IF EXISTS "storage_materials_select" ON storage.objects;
CREATE POLICY "storage_materials_select" ON storage.objects
    FOR SELECT USING (bucket_id = 'study_materials');

DROP POLICY IF EXISTS "storage_materials_insert" ON storage.objects;
CREATE POLICY "storage_materials_insert" ON storage.objects
    FOR INSERT WITH CHECK (bucket_id = 'study_materials');
