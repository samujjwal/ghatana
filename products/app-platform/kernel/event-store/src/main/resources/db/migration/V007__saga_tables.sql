-- V007: Saga orchestration tables (STORY-K05-016 to K05-020)
--
-- Two tables:
--   saga_definitions — versioned saga blueprints (steps, timeouts, topics)
--   saga_instances   — runtime state persisted as event-sourced records

-- ─────────────────────────────────────────────────────────────────────────────
-- Saga definitions
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE saga_definitions (
    saga_name      VARCHAR(255) NOT NULL,
    version        INT          NOT NULL,
    definition     JSONB        NOT NULL,  -- SagaDefinition JSON (steps, timeouts, topics)
    is_active      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (saga_name, version)
);

-- Only one active version per saga name at a time
CREATE UNIQUE INDEX idx_saga_definitions_active
    ON saga_definitions (saga_name)
    WHERE is_active = TRUE;

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
    saga_id            UUID         NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    saga_name          VARCHAR(255) NOT NULL,
    saga_version       INT          NOT NULL,
    state              saga_state   NOT NULL DEFAULT 'STARTED',
    correlation_id     VARCHAR(255),
    tenant_id          VARCHAR(255) NOT NULL,
    current_step_index INT          NOT NULL DEFAULT 0,
    payload            JSONB        NOT NULL DEFAULT '{}',  -- initial trigger payload
    step_results       JSONB        NOT NULL DEFAULT '{}',  -- results per step (keyed by step name)
    error_details      JSONB,                               -- last error if FAILED
    started_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at       TIMESTAMPTZ,
    FOREIGN KEY (saga_name, saga_version)
        REFERENCES saga_definitions (saga_name, version)
);

CREATE INDEX idx_saga_instances_state       ON saga_instances (state);
CREATE INDEX idx_saga_instances_correlation ON saga_instances (correlation_id);
CREATE INDEX idx_saga_instances_tenant      ON saga_instances (tenant_id);
CREATE INDEX idx_saga_instances_updated_at  ON saga_instances (updated_at)
    WHERE state IN ('STARTED', 'STEP_PENDING', 'COMPENSATING');

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

COMMENT ON TABLE saga_definitions  IS 'Versioned saga blueprints registered by domain services.';
COMMENT ON TABLE saga_instances    IS 'Event-sourced runtime state for active saga executions.';
