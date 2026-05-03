-- Migration V13: Create attribution models table (DMOS-P3-005)
CREATE TABLE IF NOT EXISTS dmos_attribution_models (
    model_id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    model_name VARCHAR(255) NOT NULL,
    model_type VARCHAR(50) NOT NULL,
    touchpoint_weights JSONB,
    confidence_interval_lower DOUBLE PRECISION,
    confidence_interval_upper DOUBLE PRECISION,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_attribution_models_tenant ON dmos_attribution_models(tenant_id);
CREATE INDEX idx_attribution_models_workspace ON dmos_attribution_models(workspace_id);
CREATE INDEX idx_attribution_models_active ON dmos_attribution_models(workspace_id, active);
