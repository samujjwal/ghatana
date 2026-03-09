-- Migration: Add expansion_results table for memory expansion feature
-- Phase 1 Week 12: AI Memory Expansion
-- Created: 2025-12-23

-- Create expansion_results table
CREATE TABLE IF NOT EXISTS expansion_results (
  id VARCHAR(255) PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type VARCHAR(50) NOT NULL CHECK (type IN ('summarize', 'extract_themes', 'identify_patterns', 'find_connections')),
  result_data JSONB NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Add indexes for performance
CREATE INDEX idx_expansion_results_user_id ON expansion_results(user_id);
CREATE INDEX idx_expansion_results_created_at ON expansion_results(created_at DESC);
CREATE INDEX idx_expansion_results_type ON expansion_results(type);

-- Add composite index for user + created_at queries
CREATE INDEX idx_expansion_results_user_created ON expansion_results(user_id, created_at DESC);

-- Add GIN index on result_data for JSONB queries
CREATE INDEX idx_expansion_results_data ON expansion_results USING GIN (result_data);

-- Add updated_at trigger
CREATE TRIGGER update_expansion_results_updated_at
  BEFORE UPDATE ON expansion_results
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- Add audit event types for memory expansion
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_type WHERE typname = 'audit_event_type'
  ) THEN
    -- If the enum doesn't exist, we can't alter it
    RAISE NOTICE 'audit_event_type enum not found, skipping';
  ELSE
    -- Add new event types if they don't exist
    BEGIN
      ALTER TYPE audit_event_type ADD VALUE IF NOT EXISTS 'MEMORY_EXPANSION_REQUESTED';
      ALTER TYPE audit_event_type ADD VALUE IF NOT EXISTS 'MEMORY_EXPANSION_COMPLETED';
      ALTER TYPE audit_event_type ADD VALUE IF NOT EXISTS 'MEMORY_EXPANSION_VIEWED';
    EXCEPTION WHEN duplicate_object THEN
      -- Values already exist, ignore
      NULL;
    END;
  END IF;
END$$;

-- Add comments for documentation
COMMENT ON TABLE expansion_results IS 'Stores results from user-initiated memory expansion analysis';
COMMENT ON COLUMN expansion_results.id IS 'Unique identifier for the expansion result';
COMMENT ON COLUMN expansion_results.user_id IS 'User who requested the expansion';
COMMENT ON COLUMN expansion_results.type IS 'Type of expansion: summarize, extract_themes, identify_patterns, or find_connections';
COMMENT ON COLUMN expansion_results.result_data IS 'Full expansion result data in JSON format';
COMMENT ON COLUMN expansion_results.created_at IS 'When the expansion was completed';
COMMENT ON COLUMN expansion_results.updated_at IS 'Last update timestamp';
