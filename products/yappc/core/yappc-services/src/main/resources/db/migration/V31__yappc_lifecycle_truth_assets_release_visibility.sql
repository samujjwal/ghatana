-- YAPPC lifecycle truth, durable audit, and product-family asset registry.
-- Backs the YAPPC-only production hardening plan:
--   * fail-closed durable audit
--   * canonical per-phase state and history
--   * reusable product-family asset catalog
--   * product release readiness and Kernel visibility read models

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS lifecycle_audit_events (
    id VARCHAR(255) PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_lifecycle_audit_events_tenant_time
    ON lifecycle_audit_events(tenant_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_lifecycle_audit_events_type
    ON lifecycle_audit_events(event_type);

CREATE TABLE IF NOT EXISTS lifecycle_phase_states (
    phase_state_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    project_id VARCHAR(255) NOT NULL,
    phase VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    current_execution_id VARCHAR(255),
    gate_context JSONB NOT NULL DEFAULT '{}'::jsonb,
    artifacts JSONB NOT NULL DEFAULT '{}'::jsonb,
    evidence JSONB NOT NULL DEFAULT '[]'::jsonb,
    runtime_health JSONB NOT NULL DEFAULT '{}'::jsonb,
    feature_flags JSONB NOT NULL DEFAULT '[]'::jsonb,
    tenant_entitlements JSONB NOT NULL DEFAULT '[]'::jsonb,
    entered_at TIMESTAMP WITH TIME ZONE,
    exited_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, workspace_id, project_id, phase)
);

CREATE INDEX IF NOT EXISTS idx_lifecycle_phase_states_scope
    ON lifecycle_phase_states(tenant_id, workspace_id, project_id);
CREATE INDEX IF NOT EXISTS idx_lifecycle_phase_states_phase_status
    ON lifecycle_phase_states(phase, status);

CREATE TABLE IF NOT EXISTS lifecycle_phase_state_history (
    history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phase_state_id UUID NOT NULL REFERENCES lifecycle_phase_states(phase_state_id) ON DELETE CASCADE,
    tenant_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    project_id VARCHAR(255) NOT NULL,
    phase VARCHAR(64) NOT NULL,
    version INTEGER NOT NULL,
    transition_event VARCHAR(128) NOT NULL,
    execution_id VARCHAR(255),
    actor_id VARCHAR(255) NOT NULL,
    correlation_id VARCHAR(255) NOT NULL,
    status VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_lifecycle_phase_state_history_scope
    ON lifecycle_phase_state_history(tenant_id, workspace_id, project_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_lifecycle_phase_state_history_phase
    ON lifecycle_phase_state_history(phase, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_lifecycle_phase_state_history_correlation
    ON lifecycle_phase_state_history(correlation_id);

CREATE TABLE IF NOT EXISTS product_family_assets (
    asset_id VARCHAR(255) PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    product_key VARCHAR(128) NOT NULL,
    asset_type VARCHAR(128) NOT NULL,
    source_product VARCHAR(128) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    domain VARCHAR(128) NOT NULL,
    paths JSONB NOT NULL DEFAULT '[]'::jsonb,
    maturity VARCHAR(64) NOT NULL,
    reuse_mode VARCHAR(64) NOT NULL,
    dependencies JSONB NOT NULL DEFAULT '[]'::jsonb,
    tests JSONB NOT NULL DEFAULT '[]'::jsonb,
    product_usage JSONB NOT NULL DEFAULT '[]'::jsonb,
    owner VARCHAR(255) NOT NULL,
    promotion_state VARCHAR(64) NOT NULL DEFAULT 'candidate',
    promotion_target VARCHAR(255),
    compatibility JSONB NOT NULL DEFAULT '{}'::jsonb,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_product_family_assets_scope
    ON product_family_assets(tenant_id, product_key);
CREATE INDEX IF NOT EXISTS idx_product_family_assets_discovery
    ON product_family_assets(asset_type, maturity, reuse_mode, domain);

CREATE TABLE IF NOT EXISTS product_family_asset_history (
    history_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id VARCHAR(255) NOT NULL REFERENCES product_family_assets(asset_id) ON DELETE CASCADE,
    tenant_id VARCHAR(255) NOT NULL,
    version INTEGER NOT NULL,
    promotion_state VARCHAR(64) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    correlation_id VARCHAR(255) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS product_release_readiness (
    readiness_id VARCHAR(255) PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    product_key VARCHAR(128) NOT NULL,
    workspace_id VARCHAR(255),
    project_id VARCHAR(255),
    verdict VARCHAR(64) NOT NULL,
    gate_status JSONB NOT NULL DEFAULT '[]'::jsonb,
    blockers JSONB NOT NULL DEFAULT '[]'::jsonb,
    evidence_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
    foundation_readiness JSONB NOT NULL DEFAULT '[]'::jsonb,
    connector_gates JSONB NOT NULL DEFAULT '[]'::jsonb,
    approval_gates JSONB NOT NULL DEFAULT '[]'::jsonb,
    ai_action_gates JSONB NOT NULL DEFAULT '[]'::jsonb,
    doc_truth_warnings JSONB NOT NULL DEFAULT '[]'::jsonb,
    trace_id VARCHAR(255),
    version INTEGER NOT NULL DEFAULT 1,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, product_key, workspace_id, project_id)
);

CREATE INDEX IF NOT EXISTS idx_product_release_readiness_product
    ON product_release_readiness(tenant_id, product_key, updated_at DESC);
