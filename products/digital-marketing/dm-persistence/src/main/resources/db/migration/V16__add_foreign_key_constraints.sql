-- DMOS-P1-13: Add foreign key constraints and indexes
-- This migration adds referential integrity constraints and performance indexes

-- Add FK constraint on dmos_campaigns.workspace_id -> dmos_workspaces.id
ALTER TABLE dmos_campaigns 
ADD CONSTRAINT fk_campaigns_workspace 
FOREIGN KEY (workspace_id) REFERENCES dmos_workspaces(id) ON DELETE CASCADE;

-- Add FK constraint on dmos_approval_snapshots.workspace_id -> dmos_workspaces.id
ALTER TABLE dmos_approval_snapshots 
ADD CONSTRAINT fk_approval_snapshots_workspace 
FOREIGN KEY (workspace_id) REFERENCES dmos_workspaces(id) ON DELETE CASCADE;

-- Add FK constraint on dmos_approval_snapshots.target_workspace_id -> dmos_workspaces.id
ALTER TABLE dmos_approval_snapshots 
ADD CONSTRAINT fk_approval_snapshots_target_workspace 
FOREIGN KEY (target_workspace_id) REFERENCES dmos_workspaces(id) ON DELETE CASCADE;

-- Add composite index on campaigns (workspace_id, status) for filtering
CREATE INDEX IF NOT EXISTS dmos_campaigns_workspace_status_idx 
ON dmos_campaigns (workspace_id, status);

-- Add composite index on campaigns (workspace_id, type) for filtering
CREATE INDEX IF NOT EXISTS dmos_campaigns_workspace_type_idx 
ON dmos_campaigns (workspace_id, type);

-- Add composite index on approval_snapshots (workspace_id, target_type) for filtering
CREATE INDEX IF NOT EXISTS dmos_approval_snapshots_workspace_type_idx 
ON dmos_approval_snapshots (workspace_id, target_type);

-- Add index on approval_snapshots (target_workspace_id) for lookups
CREATE INDEX IF NOT EXISTS dmos_approval_snapshots_target_workspace_idx 
ON dmos_approval_snapshots (target_workspace_id);

-- Add index on approval_snapshots (required_approver_role) for role-based queries
CREATE INDEX IF NOT EXISTS dmos_approval_snapshots_role_idx 
ON dmos_approval_snapshots (required_approver_role);

-- Add index on approval_snapshots (risk_level) for filtering by risk
CREATE INDEX IF NOT EXISTS dmos_approval_snapshots_risk_idx 
ON dmos_approval_snapshots (risk_level);

-- Add index on workspaces (tenant_id, status) for tenant filtering
CREATE INDEX IF NOT EXISTS dmos_workspaces_tenant_status_idx 
ON dmos_workspaces (tenant_id, status);

-- Add index on workspaces (status) for filtering active workspaces
CREATE INDEX IF NOT EXISTS dmos_workspaces_status_idx 
ON dmos_workspaces (status);

-- Add comments
COMMENT ON CONSTRAINT fk_campaigns_workspace ON dmos_campaigns IS 'FK to dmos_workspaces';
COMMENT ON CONSTRAINT fk_approval_snapshots_workspace ON dmos_approval_snapshots IS 'FK to dmos_workspaces';
COMMENT ON CONSTRAINT fk_approval_snapshots_target_workspace ON dmos_approval_snapshots IS 'FK to dmos_workspaces for target';
