-- ====================================================================
-- AI Resume Analyzer — Supabase Database Migration & RLS Setup
-- Run this script in the Supabase SQL Editor (Database -> SQL Editor)
-- ====================================================================

-- 1. Create analysis_history table
CREATE TABLE IF NOT EXISTS public.analysis_history (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    resume_filename TEXT NOT NULL,
    job_description TEXT NOT NULL,
    ats_score INTEGER NOT NULL DEFAULT 0,
    analysis_json JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 2. Index for faster query performance per user
CREATE INDEX IF NOT EXISTS idx_analysis_history_user_id ON public.analysis_history(user_id);
CREATE INDEX IF NOT EXISTS idx_analysis_history_created_at ON public.analysis_history(created_at DESC);

-- 3. Enable Row Level Security (RLS)
ALTER TABLE public.analysis_history ENABLE ROW LEVEL SECURITY;

-- 4. Policy: Authenticated users can read only their own analysis records
CREATE POLICY "Users can read own analysis history"
    ON public.analysis_history
    FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

-- 5. Policy: Authenticated users can insert their own analysis records
CREATE POLICY "Users can insert own analysis history"
    ON public.analysis_history
    FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

-- Note: The Supabase Service Role Key bypasses RLS automatically,
-- allowing the Spring Boot backend to insert records on behalf of authenticated users.
