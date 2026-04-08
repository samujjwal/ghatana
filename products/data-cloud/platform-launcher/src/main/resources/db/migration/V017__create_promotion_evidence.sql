-- V017: Create promotion_evidence table for memory promotion audit trail
--
-- Records each memory promotion decision (episodic → procedural path).
-- Stores the evidence score, promotion step, approving agent/user, and
-- outcome for auditability and re-evaluation purposes.

CREATE TABLE IF NOT EXISTS promotion_evidence (
    id                  BIGSERIAL       PRIMARY KEY,
    evidence_id         VARCHAR(255)    NOT NULL,
    tenant_id           VARCHAR(255)    NOT NULL,
    agent_id            VARCHAR(255)    NOT NULL,
    namespace_id        VARCHAR(255)    NOT NULL,
    source_memory_id    VARCHAR(255)    NOT NULL,
    target_memory_id    VARCHAR(255),
    promotion_step      VARCHAR(100)    NOT NULL,
    step_ordinal        INTEGER         NOT NULL CHECK (step_ordinal >= 1),
    score               DOUBLE PRECISION CHECK (score BETWEEN 0.0 AND 1.0),
    passed              BOOLEAN         NOT NULL,
    approver_id         VARCHAR(255),
    approved_at         TIMESTAMPTZ,
    rejected_reason     TEXT,
    promoted_at         TIMESTAMPTZ     NOT NULL,
    data                JSONB           NOT NULL DEFAULT '{}'
);

-- Unique evidence record per tenant
CREATE UNIQUE INDEX IF NOT EXISTS uidx_promotion_evidence_id
    ON promotion_evidence (tenant_id, evidence_id);

-- Lookup by source memory across the promotion pipeline
CREATE INDEX IF NOT EXISTS idx_promotion_evidence_source
    ON promotion_evidence (tenant_id, source_memory_id);

-- Lookup by namespace: full promotion history for a namespace
CREATE INDEX IF NOT EXISTS idx_promotion_evidence_namespace
    ON promotion_evidence (tenant_id, namespace_id, promoted_at DESC);

-- Lookup by agent and step: find all evidence at a given promotion step
CREATE INDEX IF NOT EXISTS idx_promotion_evidence_step
    ON promotion_evidence (tenant_id, agent_id, promotion_step);

-- Partial index: passed promotions for procedural memory ingestion
CREATE INDEX IF NOT EXISTS idx_promotion_evidence_passed
    ON promotion_evidence (tenant_id, agent_id, approved_at)
    WHERE passed = TRUE;
