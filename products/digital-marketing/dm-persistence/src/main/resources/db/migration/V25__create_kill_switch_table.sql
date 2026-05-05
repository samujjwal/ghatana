-- P1-024: Create kill switch table for emergency circuit breakers
--
-- This table stores kill switch states for emergency circuit breaking.
-- It supports global, tenant, workspace, and feature-level kill switches.

CREATE TABLE IF NOT EXISTS dmos_kill_switches (
    id SERIAL PRIMARY KEY,
    scope VARCHAR(32) NOT NULL,           -- GLOBAL, TENANT, WORKSPACE, FEATURE
    scope_id VARCHAR(128) NOT NULL,       -- tenant_id, workspace_id, or feature_name
    feature VARCHAR(128) NOT NULL,        -- feature identifier (e.g., google_ads.publish)
    status VARCHAR(32) NOT NULL,           -- ACTIVE, INACTIVE
    reason TEXT,                           -- why the kill switch was activated
    activated_by VARCHAR(128),             -- who activated the kill switch
    activated_at TIMESTAMP WITH TIME ZONE,
    deactivated_by VARCHAR(128),
    deactivated_at TIMESTAMP WITH TIME ZONE,

    -- Unique constraint: only one active kill switch per scope/feature combination
    CONSTRAINT uq_kill_switch_scope_feature UNIQUE (scope, scope_id, feature),

    -- Valid scope values
    CONSTRAINT chk_kill_switch_scope CHECK (scope IN ('GLOBAL', 'TENANT', 'WORKSPACE', 'FEATURE')),

    -- Valid status values
    CONSTRAINT chk_kill_switch_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- Index for quick lookups by feature
CREATE INDEX idx_kill_switch_feature ON dmos_kill_switches(feature, status);

-- Index for tenant-scoped queries
CREATE INDEX idx_kill_switch_tenant ON dmos_kill_switches(scope, scope_id, status)
    WHERE scope = 'TENANT';

-- Index for active kill switches (most common query)
CREATE INDEX idx_kill_switch_active ON dmos_kill_switches(status)
    WHERE status = 'ACTIVE';

-- Documentation
COMMENT ON TABLE dmos_kill_switches IS 'P1-024: Emergency circuit breaker states for critical operations';
COMMENT ON COLUMN dmos_kill_switches.scope IS 'Scope level: GLOBAL, TENANT, WORKSPACE, or FEATURE';
COMMENT ON COLUMN dmos_kill_switches.scope_id IS 'ID for TENANT (tenant_id), WORKSPACE (workspace_id), or FEATURE (feature name)';
COMMENT ON COLUMN dmos_kill_switches.feature IS 'Feature identifier being controlled (e.g., google_ads.publish)';
COMMENT ON COLUMN dmos_kill_switches.status IS 'ACTIVE or INACTIVE';
COMMENT ON COLUMN dmos_kill_switches.reason IS 'Human-readable reason for activation';
COMMENT ON COLUMN dmos_kill_switches.activated_by IS 'User/system that activated the kill switch';
COMMENT ON COLUMN dmos_kill_switches.activated_at IS 'When the kill switch was activated';

-- Insert default kill switches (all inactive initially)
INSERT INTO dmos_kill_switches (scope, scope_id, feature, status, reason)
VALUES
    ('GLOBAL', '*', 'google_ads.publish', 'INACTIVE', 'Default state'),
    ('GLOBAL', '*', 'google_ads.update', 'INACTIVE', 'Default state'),
    ('GLOBAL', '*', 'ai.generation', 'INACTIVE', 'Default state'),
    ('GLOBAL', '*', 'budget.modification', 'INACTIVE', 'Default state'),
    ('GLOBAL', '*', 'campaign.activation', 'INACTIVE', 'Default state')
ON CONFLICT (scope, scope_id, feature) DO NOTHING;
