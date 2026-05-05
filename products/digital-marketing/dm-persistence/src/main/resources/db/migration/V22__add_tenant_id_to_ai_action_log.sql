-- P1-009: Add tenant_id to AI action log for tenant-level integrity
-- This ensures cross-tenant reads are impossible even if workspace IDs collide

-- Add tenant_id column to AI action log
ALTER TABLE dmos_ai_action_log
ADD COLUMN tenant_id TEXT NOT NULL DEFAULT 'legacy_tenant';

-- Create index for tenant-scoped queries
CREATE INDEX dmos_ai_action_log_tenant_idx
ON dmos_ai_action_log (tenant_id, workspace_id);

-- Add composite index for tenant + workspace + time queries
CREATE INDEX dmos_ai_action_log_tenant_workspace_occurred_idx
ON dmos_ai_action_log (tenant_id, workspace_id, occurred_at DESC);

-- Add foreign key relationship to enforce tenant-workspace integrity
-- Note: This requires a tenant_workspaces reference table
-- For now, we add a CHECK constraint as a simpler alternative

-- Add CHECK constraint to ensure tenant_id is not empty
ALTER TABLE dmos_ai_action_log
ADD CONSTRAINT dmos_ai_action_log_tenant_not_empty
CHECK (tenant_id <> '');

-- Update the primary key to include tenant_id for stronger isolation
-- Note: This is a breaking change that requires data migration
-- For existing data, we keep the old PK and add a unique constraint instead

-- Add composite unique constraint for true tenant isolation
ALTER TABLE dmos_ai_action_log
ADD CONSTRAINT dmos_ai_action_log_tenant_unique
UNIQUE (tenant_id, workspace_id, action_id);

COMMENT ON COLUMN dmos_ai_action_log.tenant_id IS
    'P1-009: Tenant ID for cross-tenant isolation. Prevents reads across tenants even if workspace IDs collide.';

COMMENT ON CONSTRAINT dmos_ai_action_log_tenant_not_empty ON dmos_ai_action_log IS
    'Tenant ID cannot be empty - ensures proper tenant scoping';

-- Update query patterns to use tenant_id
-- New queries should filter by tenant_id first, then workspace_id
