-- V014: Create agent_rollouts table for tenant-scoped release rollout tracking
--
-- Stores AgentRolloutRecord instances: approval state, traffic split,
-- fallback configuration, kill-switch, and the approval lifecycle.

CREATE TABLE IF NOT EXISTS agent_rollouts (
    id                  BIGSERIAL PRIMARY KEY,
    rollout_id          VARCHAR(255)    NOT NULL,
    agent_release_id    VARCHAR(255)    NOT NULL,
    tenant_id           VARCHAR(255)    NOT NULL,
    target_environment  VARCHAR(255)    NOT NULL,
    traffic_split_pct   INTEGER         NOT NULL CHECK (traffic_split_pct BETWEEN 0 AND 100),
    fallback_release_id VARCHAR(255),
    approval_state      VARCHAR(50)     NOT NULL DEFAULT 'PENDING',
    requested_by        VARCHAR(255)    NOT NULL,
    approved_by         VARCHAR(255),
    rejected_by         VARCHAR(255),
    rejected_reason     TEXT,
    kill_switch         BOOLEAN         NOT NULL DEFAULT FALSE,
    requested_at        TIMESTAMPTZ     NOT NULL,
    decided_at          TIMESTAMPTZ,
    expires_at          TIMESTAMPTZ,
    data                JSONB           NOT NULL DEFAULT '{}',
    CONSTRAINT ck_rollout_state CHECK (
        approval_state IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'ROLLED_BACK')
    )
);

-- Unique rollout ID per tenant (global rollout IDs are unique but enforce at DB level too)
CREATE UNIQUE INDEX IF NOT EXISTS uidx_agent_rollouts_rollout_id
    ON agent_rollouts (tenant_id, rollout_id);

-- Lookup by release: find all rollouts for a given agent release
CREATE INDEX IF NOT EXISTS idx_agent_rollouts_release_id
    ON agent_rollouts (tenant_id, agent_release_id);

-- Lookup by tenant + environment: active rollouts in production
CREATE INDEX IF NOT EXISTS idx_agent_rollouts_tenant_env
    ON agent_rollouts (tenant_id, target_environment);

-- Lookup by approval state: find pending rollouts awaiting review
CREATE INDEX IF NOT EXISTS idx_agent_rollouts_state
    ON agent_rollouts (tenant_id, approval_state);

-- Partial index: pending rollouts needing a decision (for approval queue queries)
CREATE INDEX IF NOT EXISTS idx_agent_rollouts_pending
    ON agent_rollouts (tenant_id, requested_at)
    WHERE approval_state = 'PENDING';

-- Partial index: approved and active (non-killed) rollouts for runtime routing
CREATE INDEX IF NOT EXISTS idx_agent_rollouts_active
    ON agent_rollouts (tenant_id, target_environment, agent_release_id)
    WHERE approval_state = 'APPROVED' AND kill_switch = FALSE;
