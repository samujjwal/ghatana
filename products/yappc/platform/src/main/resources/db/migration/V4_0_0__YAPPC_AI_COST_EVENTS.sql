-- YAPPC AI: Cost Events Persistence
-- Migration: V4_0_0__YAPPC_AI_COST_EVENTS.sql
-- Append-only audit log for LLM token usage and cost per tenant.

CREATE TABLE IF NOT EXISTS ai_cost_events (
    id              VARCHAR(255)  NOT NULL PRIMARY KEY,
    call_id         VARCHAR(255)  NOT NULL,
    tenant_id       VARCHAR(255)  NOT NULL,
    user_id         VARCHAR(255),
    model           VARCHAR(128)  NOT NULL,
    provider        VARCHAR(64)   NOT NULL,
    feature_id      VARCHAR(255),
    tokens_input    INT           NOT NULL DEFAULT 0,
    tokens_output   INT           NOT NULL DEFAULT 0,
    cost_usd        NUMERIC(14,8) NOT NULL DEFAULT 0,
    occurred_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- Primary lookup: per-tenant cost reports
CREATE INDEX IF NOT EXISTS idx_ai_cost_events_tenant_id
    ON ai_cost_events (tenant_id);

-- Provider-scoped reports
CREATE INDEX IF NOT EXISTS idx_ai_cost_events_provider
    ON ai_cost_events (tenant_id, provider);

-- Time-range queries for billing periods
CREATE INDEX IF NOT EXISTS idx_ai_cost_events_occurred_at
    ON ai_cost_events (occurred_at);

-- IMPORTANT: This table is append-only. Never UPDATE or DELETE rows.
-- Corrections must be made with compensating INSERT rows.
