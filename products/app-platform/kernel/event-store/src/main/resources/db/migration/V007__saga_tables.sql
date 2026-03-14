-- V007: Saga orchestration tables (STORY-K05-016 to K05-020)
--
-- Two tables:
--   saga_definitions — versioned saga blueprints (steps, timeouts, topics)
--   saga_instances   — runtime state persisted as event-sourced records
--
-- Column names are aligned with PostgresSagaStore.java to avoid any mapping layer.

-- ─────────────────────────────────────────────────────────────────────────────
-- Saga definitions
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE saga_definitions (
    saga_type    VARCHAR(255) NOT NULL,
    version      INT          NOT NULL,
    description  TEXT,
    steps_json   JSONB        NOT NULL,  -- ordered SagaStep array including timeouts
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (saga_type, version)
);

CREATE INDEX idx_saga_definitions_type ON saga_definitions (saga_type);

-- ─────────────────────────────────────────────────────────────────────────────
-- Saga instances (event-sourced state)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TYPE saga_state AS ENUM (
    'STARTED',
    'STEP_PENDING',
    'STEP_COMPLETE',
    'COMPENSATING',
    'COMPENSATED',
    'COMPLETED',
    'FAILED'
);

CREATE TABLE saga_instances (
    saga_id        VARCHAR(255) NOT NULL PRIMARY KEY,
    saga_type      VARCHAR(255) NOT NULL,
    saga_version   INT          NOT NULL,
    tenant_id      VARCHAR(255) NOT NULL,
    correlation_id VARCHAR(255) NOT NULL,
    saga_state     saga_state   NOT NULL DEFAULT 'STARTED',
    current_step   INT          NOT NULL DEFAULT 0,
    retry_count    INT          NOT NULL DEFAULT 0,
    last_error     TEXT,
    started_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    FOREIGN KEY (saga_type, saga_version) REFERENCES saga_definitions (saga_type, version)
);

CREATE INDEX idx_saga_instances_state       ON saga_instances (saga_state);
CREATE INDEX idx_saga_instances_tenant      ON saga_instances (tenant_id);
CREATE INDEX idx_saga_instances_correlation ON saga_instances (correlation_id);
-- Partial index for timeout checker: only non-terminal sagas need monitoring
CREATE INDEX idx_saga_instances_pending_at  ON saga_instances (updated_at)
    WHERE saga_state IN ('STARTED', 'STEP_PENDING', 'COMPENSATING');

-- Auto-update updated_at on state changes (saga timeout checker relies on this)
CREATE OR REPLACE FUNCTION saga_instances_set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

CREATE TRIGGER saga_instances_updated_at_trigger
    BEFORE UPDATE ON saga_instances
    FOR EACH ROW
    EXECUTE FUNCTION saga_instances_set_updated_at();

COMMENT ON TABLE saga_definitions IS 'Versioned saga blueprints registered by domain services.';
COMMENT ON TABLE saga_instances   IS 'Runtime state for active saga executions. saga_state column uses saga_state ENUM.';
