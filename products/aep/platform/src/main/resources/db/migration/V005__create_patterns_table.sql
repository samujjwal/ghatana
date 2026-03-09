-- Flyway V005: Create patterns table for pattern detection engine
-- Maps to: com.ghatana.pattern.storage.postgres.PostgresPatternRepository (raw JDBC)

CREATE TABLE IF NOT EXISTS patterns (
    id                UUID            NOT NULL DEFAULT gen_random_uuid(),
    tenant_id         TEXT            NOT NULL,
    name              TEXT            NOT NULL,
    version           INTEGER         NOT NULL,
    description       TEXT,
    labels            TEXT[],
    priority          INTEGER         NOT NULL,
    activation        BOOLEAN         NOT NULL,
    status            TEXT            NOT NULL,
    spec              JSONB           NOT NULL,
    event_types       TEXT[],
    created_at        TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP,
    activated_at      TIMESTAMPTZ,
    compiled_at       TIMESTAMPTZ,

    CONSTRAINT pk_patterns PRIMARY KEY (id),
    CONSTRAINT uk_patterns_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT chk_patterns_status CHECK (status IN (
        'DRAFT', 'COMPILED', 'ACTIVE', 'INACTIVE', 'ERROR', 'ARCHIVED'
    ))
);

-- Indexes from PostgresPatternRepository Javadoc + SQL
CREATE INDEX idx_patterns_tenant ON patterns (tenant_id);
CREATE INDEX idx_patterns_tenant_status ON patterns (tenant_id, status);
CREATE INDEX idx_patterns_event_types ON patterns USING GIN (event_types);

-- Additional indexes for common query patterns
CREATE INDEX idx_patterns_priority ON patterns (tenant_id, priority DESC);
CREATE INDEX idx_patterns_active ON patterns (tenant_id, activation)
    WHERE activation = TRUE;

-- Trigger for updated_at
CREATE OR REPLACE FUNCTION update_patterns_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_patterns_updated_at
    BEFORE UPDATE ON patterns
    FOR EACH ROW EXECUTE FUNCTION update_patterns_updated_at();

-- Documentation
COMMENT ON TABLE patterns IS 'Pattern detection registry — stores compiled pattern specifications for the detection engine. Supports multi-tenant pattern lifecycle.';
COMMENT ON COLUMN patterns.spec IS 'Full PatternSpecification as JSONB — includes conditions, windows, thresholds, actions';
COMMENT ON COLUMN patterns.event_types IS 'PostgreSQL text array of event types this pattern subscribes to. GIN-indexed for containment queries.';
COMMENT ON COLUMN patterns.labels IS 'Classification labels for pattern grouping and filtering';
COMMENT ON COLUMN patterns.priority IS 'Execution priority — higher values evaluated first';
COMMENT ON COLUMN patterns.activation IS 'Runtime activation flag. Only activated patterns participate in detection.';
COMMENT ON COLUMN patterns.compiled_at IS 'Timestamp when the pattern spec was compiled into executable form';
