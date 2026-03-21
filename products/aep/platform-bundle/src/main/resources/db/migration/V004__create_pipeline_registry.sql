-- Flyway V004: Create pipeline registry for pipeline definitions
-- Stores pipeline topology, versioning, and deployment metadata

CREATE TABLE IF NOT EXISTS pipeline_registry (
    pipeline_id       VARCHAR(100)    NOT NULL,
    tenant_id         VARCHAR(100)    NOT NULL,
    name              VARCHAR(255)    NOT NULL,
    description       TEXT,
    version           INTEGER         NOT NULL DEFAULT 1,
    status            VARCHAR(20)     NOT NULL DEFAULT 'DRAFT',
    stages            JSONB           NOT NULL DEFAULT '[]'::jsonb,
    edges             JSONB           NOT NULL DEFAULT '[]'::jsonb,
    config            JSONB           DEFAULT '{}'::jsonb,
    metadata          JSONB           DEFAULT '{}'::jsonb,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(255),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by        VARCHAR(255),
    published_at      TIMESTAMPTZ,
    deprecated_at     TIMESTAMPTZ,
    opt_lock_version  BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_pipeline_registry PRIMARY KEY (pipeline_id),
    CONSTRAINT uk_pipeline_tenant_name_version UNIQUE (tenant_id, name, version),
    CONSTRAINT chk_pipeline_status CHECK (status IN (
        'DRAFT', 'PUBLISHED', 'ACTIVE', 'PAUSED', 'DEPRECATED', 'ARCHIVED'
    ))
);

-- Query patterns: list by tenant, find active, version lookup
CREATE INDEX idx_pipeline_registry_tenant ON pipeline_registry (tenant_id);
CREATE INDEX idx_pipeline_registry_tenant_status ON pipeline_registry (tenant_id, status);
CREATE INDEX idx_pipeline_registry_name ON pipeline_registry (tenant_id, name);

-- Partial index: only active pipelines for hot-path queries
CREATE INDEX idx_pipeline_registry_active
    ON pipeline_registry (tenant_id, name)
    WHERE status = 'ACTIVE';

-- Trigger for updated_at
CREATE TRIGGER trg_pipeline_registry_updated_at
    BEFORE UPDATE ON pipeline_registry
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Documentation
COMMENT ON TABLE pipeline_registry IS 'Pipeline definition registry — stores DAG topology (stages + edges), configuration, and lifecycle state. Versioned per tenant.';
COMMENT ON COLUMN pipeline_registry.stages IS 'Array of PipelineStage definitions as JSONB: [{id, name, operatorId, config}]';
COMMENT ON COLUMN pipeline_registry.edges IS 'Array of PipelineEdge definitions as JSONB: [{from, to, type, condition}]. Types: PRIMARY, ERROR, FALLBACK, BROADCAST';
COMMENT ON COLUMN pipeline_registry.config IS 'Pipeline-level configuration (timeout, concurrency, retry policy) as JSONB';
COMMENT ON COLUMN pipeline_registry.opt_lock_version IS 'Optimistic locking version for concurrent pipeline updates';
COMMENT ON COLUMN pipeline_registry.status IS 'Pipeline lifecycle: DRAFT → PUBLISHED → ACTIVE → PAUSED/DEPRECATED → ARCHIVED';
