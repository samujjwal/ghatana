-- Flyway V001: Create pipeline checkpoint tables for exactly-once semantics
-- Maps to: PipelineCheckpointEntity, StepCheckpointEntity, ConsumerOffsetEntity
-- Module: com.ghatana.orchestrator.store

-- ============================================================================
-- Pipeline execution checkpoints — tracks overall pipeline instance lifecycle
-- ============================================================================
CREATE TABLE IF NOT EXISTS pipeline_checkpoints (
    instance_id       VARCHAR(100)    NOT NULL,
    tenant_id         VARCHAR(100)    NOT NULL,
    pipeline_id       VARCHAR(100)    NOT NULL,
    idempotency_key   VARCHAR(200)    NOT NULL,
    status            VARCHAR(20)     NOT NULL,
    state             JSONB,
    result            JSONB,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    current_step_id   VARCHAR(100),
    current_step_name VARCHAR(200),
    completed_steps   INTEGER         NOT NULL DEFAULT 0,
    total_steps       INTEGER         NOT NULL DEFAULT 0,
    version           BIGINT,

    CONSTRAINT pk_pipeline_checkpoints PRIMARY KEY (instance_id),
    CONSTRAINT chk_pipeline_status CHECK (status IN (
        'CREATED', 'RUNNING', 'STEP_SUCCESS', 'STEP_FAILED',
        'COMPLETED', 'FAILED', 'CANCELLED'
    ))
);

-- Unique: prevent duplicate executions per tenant
CREATE UNIQUE INDEX idx_pipeline_checkpoints_tenant_idempotency
    ON pipeline_checkpoints (tenant_id, idempotency_key);

CREATE INDEX idx_pipeline_checkpoints_idempotency ON pipeline_checkpoints (idempotency_key);
CREATE INDEX idx_pipeline_checkpoints_tenant_pipeline ON pipeline_checkpoints (tenant_id, pipeline_id);
CREATE INDEX idx_pipeline_checkpoints_pipeline_id ON pipeline_checkpoints (pipeline_id);
CREATE INDEX idx_pipeline_checkpoints_status ON pipeline_checkpoints (status);
CREATE INDEX idx_pipeline_checkpoints_created_at ON pipeline_checkpoints (created_at);
CREATE INDEX idx_pipeline_checkpoints_updated_at ON pipeline_checkpoints (updated_at);

-- ============================================================================
-- Step execution checkpoints — individual step tracking within a pipeline
-- ============================================================================
CREATE TABLE IF NOT EXISTS step_checkpoints (
    id                BIGSERIAL       NOT NULL,
    instance_id       VARCHAR(100)    NOT NULL,
    step_id           VARCHAR(100)    NOT NULL,
    step_name         VARCHAR(200)    NOT NULL,
    status            VARCHAR(20)     NOT NULL,
    input             JSONB,
    output            JSONB,
    started_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    completed_at      TIMESTAMPTZ,
    error_message     TEXT,
    retry_count       INTEGER         NOT NULL DEFAULT 0,
    version           BIGINT,

    CONSTRAINT pk_step_checkpoints PRIMARY KEY (id),
    CONSTRAINT uk_step_checkpoints_instance_step UNIQUE (instance_id, step_id),
    CONSTRAINT fk_step_checkpoints_instance FOREIGN KEY (instance_id)
        REFERENCES pipeline_checkpoints (instance_id) ON DELETE CASCADE,
    CONSTRAINT chk_step_status CHECK (status IN (
        'CREATED', 'RUNNING', 'STEP_SUCCESS', 'STEP_FAILED',
        'COMPLETED', 'FAILED', 'CANCELLED'
    ))
);

CREATE INDEX idx_step_checkpoints_instance_id ON step_checkpoints (instance_id);
CREATE INDEX idx_step_checkpoints_step_id ON step_checkpoints (step_id);
CREATE INDEX idx_step_checkpoints_status ON step_checkpoints (status);
CREATE INDEX idx_step_checkpoints_started_at ON step_checkpoints (started_at);

-- ============================================================================
-- Consumer offsets — Kafka-style offset tracking for event consumption
-- ============================================================================
CREATE TABLE IF NOT EXISTS consumer_offsets (
    id                BIGSERIAL       NOT NULL,
    tenant_id         VARCHAR(100)    NOT NULL,
    consumer_group    VARCHAR(100)    NOT NULL,
    partition_id      VARCHAR(100)    NOT NULL,
    offset_val        VARCHAR(255)    NOT NULL,
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_consumer_offsets PRIMARY KEY (id),
    CONSTRAINT uk_consumer_offsets_lookup UNIQUE (tenant_id, consumer_group, partition_id)
);

-- Trigger for auto-updating updated_at on pipeline_checkpoints
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_pipeline_checkpoints_updated_at
    BEFORE UPDATE ON pipeline_checkpoints
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_consumer_offsets_updated_at
    BEFORE UPDATE ON consumer_offsets
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Documentation
COMMENT ON TABLE pipeline_checkpoints IS 'Pipeline execution checkpoints for exactly-once semantics and restart capability. Optimistic locking via version column.';
COMMENT ON TABLE step_checkpoints IS 'Per-step execution details within a pipeline instance. FK cascade ensures cleanup on pipeline deletion.';
COMMENT ON TABLE consumer_offsets IS 'Kafka-style consumer offset tracking per (tenant, consumer_group, partition). Supports at-least-once delivery with dedup.';
COMMENT ON COLUMN pipeline_checkpoints.version IS 'JPA @Version for optimistic concurrency control';
COMMENT ON COLUMN pipeline_checkpoints.state IS 'Serialized pipeline execution state as JSONB';
COMMENT ON COLUMN consumer_offsets.offset_val IS 'Opaque offset value (string to support various offset formats)';
