-- V17__residual_islands_full_schema.sql
-- P1-20: Add full schema columns to residual_islands table for source fidelity
-- Enables tracking source span, checksum, raw fragment ref, reason, risk, review requirement

-- Add new columns to residual_islands table
ALTER TABLE residual_islands
ADD COLUMN IF NOT EXISTS source_span TEXT,
ADD COLUMN IF NOT EXISTS checksum VARCHAR(255),
ADD COLUMN IF NOT EXISTS raw_fragment_ref TEXT,
ADD COLUMN IF NOT EXISTS reason TEXT,
ADD COLUMN IF NOT EXISTS review_required BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS risk_score DOUBLE PRECISION DEFAULT 0.0,
ADD COLUMN IF NOT EXISTS workspace_id VARCHAR(255),
ADD COLUMN IF NOT EXISTS snapshot_id VARCHAR(255);

-- Update existing records with default values
UPDATE residual_islands
SET review_required = FALSE,
    risk_score = 0.0
WHERE review_required IS NULL OR risk_score IS NULL;

-- Update index to include workspace_id
DROP INDEX IF EXISTS idx_residual_islands_scope;
CREATE INDEX idx_residual_islands_scope
ON residual_islands(tenant_id, workspace_id, project_id, snapshot_id);

-- Create index for checksum-based lookups
CREATE INDEX IF NOT EXISTS idx_residual_islands_checksum
ON residual_islands(checksum);

-- Create index for review-required islands
CREATE INDEX IF NOT EXISTS idx_residual_islands_review_required
ON residual_islands(review_required, risk_score DESC);
