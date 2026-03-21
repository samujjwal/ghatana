-- V010: AEP Custom Model Artifacts and Canary Deployments
-- Stores artifact provenance (checksums, URIs, hyperparameters) and
-- canary deployment configuration for AEP custom models.

-- ─── Custom model artifact versions ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS aep_custom_model_versions (
    id                      UUID            NOT NULL DEFAULT gen_random_uuid(),
    tenant_id               VARCHAR(255)    NOT NULL,
    model_id                UUID            NOT NULL,  -- FK to model_registry.id
    model_name              VARCHAR(255)    NOT NULL,
    version                 VARCHAR(100)    NOT NULL,
    artifact_uri            TEXT            NOT NULL,
    artifact_sha256         CHAR(64)        NOT NULL,
    git_commit_sha          VARCHAR(40),
    training_dataset_hash   VARCHAR(64),
    hyperparameters         JSONB           NOT NULL DEFAULT '{}',
    validation_thresholds   JSONB           NOT NULL DEFAULT '{}',
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_aep_custom_model_versions           PRIMARY KEY (id),
    CONSTRAINT uq_aep_custom_model_tenant_name_ver    UNIQUE (tenant_id, model_name, version)
);

-- Fast lookup by tenant + model name for version listing
CREATE INDEX IF NOT EXISTS idx_aep_cmv_tenant_model
    ON aep_custom_model_versions (tenant_id, model_name, created_at DESC);

-- Checksum lookup for duplicate‐artifact detection
CREATE INDEX IF NOT EXISTS idx_aep_cmv_sha256
    ON aep_custom_model_versions (artifact_sha256);

-- ─── Canary deployments ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS aep_canary_deployments (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           VARCHAR(255)    NOT NULL,
    model_name          VARCHAR(255)    NOT NULL,
    production_version  VARCHAR(100)    NOT NULL,
    canary_version      VARCHAR(100)    NOT NULL,
    canary_traffic_pct  SMALLINT        NOT NULL DEFAULT 10
                            CHECK (canary_traffic_pct BETWEEN 0 AND 100),
    status              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE'
                            CHECK (status IN ('ACTIVE','PROMOTED','ROLLED_BACK','PAUSED','FAILED')),
    started_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    concluded_at        TIMESTAMPTZ,

    CONSTRAINT pk_aep_canary_deployments PRIMARY KEY (id)
);

-- Only one ACTIVE or PAUSED canary per tenant+model at a time
CREATE UNIQUE INDEX IF NOT EXISTS uq_aep_canary_active_per_model
    ON aep_canary_deployments (tenant_id, model_name)
    WHERE status IN ('ACTIVE', 'PAUSED');

-- Lookup all canaries for a tenant
CREATE INDEX IF NOT EXISTS idx_aep_canary_tenant_model
    ON aep_canary_deployments (tenant_id, model_name, started_at DESC);

-- ─── Model validation run results ─────────────────────────────────────────────
-- Keeps a history of validation gate evaluations before promotions.
CREATE TABLE IF NOT EXISTS aep_model_validation_runs (
    id              BIGSERIAL       NOT NULL,
    tenant_id       VARCHAR(255)    NOT NULL,
    model_name      VARCHAR(255)    NOT NULL,
    version         VARCHAR(100)    NOT NULL,
    evaluated_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    passed          BOOLEAN         NOT NULL,
    metrics         JSONB           NOT NULL DEFAULT '{}',
    thresholds      JSONB           NOT NULL DEFAULT '{}',
    failure_reasons TEXT[],

    CONSTRAINT pk_aep_model_validation_runs PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_aep_mvr_tenant_model
    ON aep_model_validation_runs (tenant_id, model_name, evaluated_at DESC);
