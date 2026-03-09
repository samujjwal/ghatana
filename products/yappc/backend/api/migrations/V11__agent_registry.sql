-- YAPPC Database Migration: V11 - Persistent Agent Registry
-- PostgreSQL 16
-- Replaces in-memory agent registry with durable PostgreSQL-backed storage

-- ========== Agent Registry Table ==========
CREATE TABLE IF NOT EXISTS yappc.agent_registry (
    id              VARCHAR(255)  PRIMARY KEY,
    name            VARCHAR(255)  NOT NULL,
    version         VARCHAR(50)   NOT NULL,
    agent_type      VARCHAR(128)  NOT NULL,
    status          VARCHAR(50)   NOT NULL DEFAULT 'INACTIVE',
    capabilities    JSONB         NOT NULL DEFAULT '[]'::jsonb,
    config          JSONB         NOT NULL DEFAULT '{}'::jsonb,
    metadata        JSONB                  DEFAULT '{}'::jsonb,
    health_status   VARCHAR(50)            DEFAULT 'UNKNOWN',
    last_heartbeat  TIMESTAMP WITH TIME ZONE,
    tenant_id       VARCHAR(64)   NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_agent_status   CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','TERMINATED')),
    CONSTRAINT chk_agent_health   CHECK (health_status IN ('HEALTHY','DEGRADED','UNHEALTHY','UNKNOWN')),
    CONSTRAINT uq_agent_tenant    UNIQUE (id, tenant_id)
);

CREATE INDEX IF NOT EXISTS idx_agent_registry_tenant_status
    ON yappc.agent_registry (tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_agent_registry_tenant_type
    ON yappc.agent_registry (tenant_id, agent_type);

CREATE INDEX IF NOT EXISTS idx_agent_registry_capabilities
    ON yappc.agent_registry USING GIN (capabilities);

CREATE INDEX IF NOT EXISTS idx_agent_registry_heartbeat
    ON yappc.agent_registry (tenant_id, last_heartbeat DESC)
    WHERE status = 'ACTIVE';

CREATE TRIGGER trigger_agent_registry_updated_at
    BEFORE UPDATE ON yappc.agent_registry
    FOR EACH ROW EXECUTE FUNCTION yappc.update_updated_at();

-- ========== Agent Execution History Table ==========
CREATE TABLE IF NOT EXISTS yappc.agent_executions (
    id              UUID          PRIMARY KEY DEFAULT uuid_generate_v4(),
    agent_id        VARCHAR(255)  NOT NULL,
    tenant_id       VARCHAR(64)   NOT NULL,
    execution_key   VARCHAR(255)  NOT NULL UNIQUE,  -- idempotency key
    status          VARCHAR(50)   NOT NULL DEFAULT 'RUNNING',
    input_payload   JSONB,
    output_payload  JSONB,
    error_message   TEXT,
    started_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMP WITH TIME ZONE,
    duration_ms     BIGINT,
    metadata        JSONB                  DEFAULT '{}'::jsonb,

    CONSTRAINT chk_exec_status CHECK (status IN ('RUNNING','COMPLETED','FAILED','CANCELLED')),
    CONSTRAINT fk_agent_executions_agent
        FOREIGN KEY (agent_id) REFERENCES yappc.agent_registry(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_agent_executions_agent
    ON yappc.agent_executions (agent_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_executions_tenant
    ON yappc.agent_executions (tenant_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_executions_status
    ON yappc.agent_executions (tenant_id, status);

-- ========== Agent Metrics Table ==========
CREATE TABLE IF NOT EXISTS yappc.agent_metrics (
    id              BIGSERIAL     PRIMARY KEY,
    agent_id        VARCHAR(255)  NOT NULL,
    tenant_id       VARCHAR(64)   NOT NULL,
    metric_name     VARCHAR(128)  NOT NULL,
    metric_value    DOUBLE PRECISION NOT NULL,
    tags            JSONB                  DEFAULT '{}'::jsonb,
    recorded_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_agent_metrics_agent
        FOREIGN KEY (agent_id) REFERENCES yappc.agent_registry(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_agent_metrics_agent_name
    ON yappc.agent_metrics (agent_id, metric_name, recorded_at DESC);

CREATE INDEX IF NOT EXISTS idx_agent_metrics_tenant
    ON yappc.agent_metrics (tenant_id, metric_name, recorded_at DESC);

-- Retention: keep last 30 days only (managed by application or pg_cron)
