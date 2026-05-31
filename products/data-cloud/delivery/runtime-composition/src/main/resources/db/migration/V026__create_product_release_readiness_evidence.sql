-- V025: Create product release readiness evidence tables
-- DC-003: Add env-specific release readiness evidence with bootstrap/rollback tracking,
-- commit SHA binding, and RLS tenant isolation.

-- Main product release readiness evidence table
CREATE TABLE IF NOT EXISTS product_release_readiness (
    id                     UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id             VARCHAR(255)             NOT NULL,
    product_version        VARCHAR(255)             NOT NULL,
    release_target         VARCHAR(50)              NOT NULL
                               CHECK (release_target IN ('development', 'staging', 'production')),
    release_verdict        VARCHAR(20)              NOT NULL
                               CHECK (release_verdict IN ('pass', 'fail')),
    average_score          DECIMAL(5,4),
    release_target_score   DECIMAL(5,4),
    commit_sha             VARCHAR(64),
    evidence               JSONB                    NOT NULL DEFAULT '{}',
    blocking_gaps          JSONB                    NOT NULL DEFAULT '[]',
    below_target_dimensions JSONB                   NOT NULL DEFAULT '[]',
    tenant_id              VARCHAR(255)             NOT NULL,
    generated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_release_readiness_product_version_target
        UNIQUE (product_id, product_version, release_target, tenant_id)
);

-- Indexes for release readiness queries
CREATE INDEX IF NOT EXISTS idx_release_readiness_product_id
    ON product_release_readiness (product_id);

CREATE INDEX IF NOT EXISTS idx_release_readiness_release_target
    ON product_release_readiness (release_target);

CREATE INDEX IF NOT EXISTS idx_release_readiness_verdict
    ON product_release_readiness (release_verdict);

CREATE INDEX IF NOT EXISTS idx_release_readiness_commit_sha
    ON product_release_readiness (commit_sha);

CREATE INDEX IF NOT EXISTS idx_release_readiness_tenant_id
    ON product_release_readiness (tenant_id);

CREATE INDEX IF NOT EXISTS idx_release_readiness_generated_at
    ON product_release_readiness (generated_at DESC);

-- GIN index for JSONB evidence queries (blocking gaps, evidence refs)
CREATE INDEX IF NOT EXISTS idx_release_readiness_blocking_gaps_gin
    ON product_release_readiness USING GIN (blocking_gaps);

CREATE INDEX IF NOT EXISTS idx_release_readiness_evidence_gin
    ON product_release_readiness USING GIN (evidence);

-- Row Level Security for tenant isolation
ALTER TABLE product_release_readiness ENABLE ROW LEVEL SECURITY;

CREATE POLICY product_release_readiness_tenant_isolation ON product_release_readiness
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant_id', true));

-- Trigger for updated_at maintenance
CREATE TRIGGER trigger_update_product_release_readiness_updated_at
    BEFORE UPDATE ON product_release_readiness
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE product_release_readiness IS
    'Env-scoped product release readiness evidence. Each row is bound to a commit SHA, '
    'product version, and target environment (development|staging|production). '
    'Provides blocking_gaps and verdict for release gate decisions.';

COMMENT ON COLUMN product_release_readiness.release_target IS
    'Target deployment environment. Must be one of: development, staging, production.';

COMMENT ON COLUMN product_release_readiness.commit_sha IS
    'Git commit SHA this evidence is bound to. Enforces traceability of release gates.';

COMMENT ON COLUMN product_release_readiness.blocking_gaps IS
    'JSONB array of P0 blocking gaps with severity, gate, reason, message, environment.';

-- Bootstrap evidence table (per-product, per-environment)
CREATE TABLE IF NOT EXISTS product_bootstrap_evidence (
    id              UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      VARCHAR(255)             NOT NULL,
    environment     VARCHAR(50)              NOT NULL
                        CHECK (environment IN ('development', 'staging', 'production')),
    commit_sha      VARCHAR(64),
    validated       BOOLEAN                  NOT NULL DEFAULT false,
    validated_at    TIMESTAMP WITH TIME ZONE,
    bootstrap_data  JSONB                    NOT NULL DEFAULT '{}',
    fail_closed     BOOLEAN                  NOT NULL DEFAULT false,
    tenant_id       VARCHAR(255)             NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_bootstrap_evidence_product_env
        UNIQUE (product_id, environment, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_bootstrap_evidence_product_id
    ON product_bootstrap_evidence (product_id);

CREATE INDEX IF NOT EXISTS idx_bootstrap_evidence_environment
    ON product_bootstrap_evidence (environment);

CREATE INDEX IF NOT EXISTS idx_bootstrap_evidence_validated
    ON product_bootstrap_evidence (validated);

CREATE INDEX IF NOT EXISTS idx_bootstrap_evidence_tenant_id
    ON product_bootstrap_evidence (tenant_id);

ALTER TABLE product_bootstrap_evidence ENABLE ROW LEVEL SECURITY;

CREATE POLICY product_bootstrap_evidence_tenant_isolation ON product_bootstrap_evidence
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant_id', true));

CREATE TRIGGER trigger_update_product_bootstrap_evidence_updated_at
    BEFORE UPDATE ON product_bootstrap_evidence
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE product_bootstrap_evidence IS
    'Per-product, per-environment bootstrap evidence. Tracks whether all required '
    'infrastructure components (postgres, migrations, secrets, storage, distributedCache) '
    'are validated before release gate can pass.';

-- Rollback evidence table (per-product, per-environment)
CREATE TABLE IF NOT EXISTS product_rollback_evidence (
    id                           UUID                     PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id                   VARCHAR(255)             NOT NULL,
    environment                  VARCHAR(50)              NOT NULL
                                     CHECK (environment IN ('development', 'staging', 'production')),
    commit_sha                   VARCHAR(64),
    validated                    BOOLEAN                  NOT NULL DEFAULT false,
    drill_status                 VARCHAR(50),
    drill_run_at                 TIMESTAMP WITH TIME ZONE,
    deployment_manifest_history  JSONB                    NOT NULL DEFAULT '[]',
    artifact_selection_policy    JSONB                    NOT NULL DEFAULT '{}',
    approval_contract            JSONB                    NOT NULL DEFAULT '{}',
    rollback_data                JSONB                    NOT NULL DEFAULT '{}',
    fail_closed                  BOOLEAN                  NOT NULL DEFAULT false,
    tenant_id                    VARCHAR(255)             NOT NULL,
    created_at                   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_rollback_evidence_product_env
        UNIQUE (product_id, environment, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_rollback_evidence_product_id
    ON product_rollback_evidence (product_id);

CREATE INDEX IF NOT EXISTS idx_rollback_evidence_environment
    ON product_rollback_evidence (environment);

CREATE INDEX IF NOT EXISTS idx_rollback_evidence_validated
    ON product_rollback_evidence (validated);

CREATE INDEX IF NOT EXISTS idx_rollback_evidence_tenant_id
    ON product_rollback_evidence (tenant_id);

ALTER TABLE product_rollback_evidence ENABLE ROW LEVEL SECURITY;

CREATE POLICY product_rollback_evidence_tenant_isolation ON product_rollback_evidence
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant_id', true));

CREATE TRIGGER trigger_update_product_rollback_evidence_updated_at
    BEFORE UPDATE ON product_rollback_evidence
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE product_rollback_evidence IS
    'Per-product, per-environment rollback evidence. Tracks deployment manifest history '
    '(last 10), artifact selection policy, approval contract, and drill results. '
    'Required for staging/prod release gates to pass.';
