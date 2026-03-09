-- Migration: Add is_active column to children table for soft delete support
-- Description: Adds soft delete functionality to children table by introducing is_active flag

-- Add is_active column to children table with default value of true
ALTER TABLE children ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT true;

-- Create index on is_active for efficient filtering
CREATE INDEX IF NOT EXISTS idx_children_is_active ON children(is_active);

-- Create composite index for common queries filtering by user and active status
CREATE INDEX IF NOT EXISTS idx_children_user_active ON children(user_id, is_active);
