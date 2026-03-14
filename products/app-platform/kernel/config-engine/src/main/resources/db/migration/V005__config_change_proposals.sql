-- Flyway V005: Config Change Proposals — maker-checker workflow for config mutations
--
-- Implements four-eyes principle for config changes (STORY-K02-014).
-- Proposals start as PENDING; a different user (checker) approves or rejects them.
-- On APPROVED, the change is applied to config_entries. The record is append-only:
-- status transitions are recorded; proposals are never deleted.

CREATE TYPE config_proposal_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED');

CREATE TABLE IF NOT EXISTS config_change_proposals (
    proposal_id     UUID                    NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(100)            NOT NULL,
    namespace       VARCHAR(255)            NOT NULL,
    config_key      VARCHAR(255)            NOT NULL,
    proposed_value  TEXT                    NOT NULL,
    hierarchy_level VARCHAR(50)             NOT NULL,    -- GLOBAL | JURISDICTION | TENANT | USER | SESSION
    level_id        VARCHAR(255)            NOT NULL,
    schema_namespace VARCHAR(255)           NOT NULL,
    status          config_proposal_status  NOT NULL DEFAULT 'PENDING',
    proposed_by     VARCHAR(255)            NOT NULL,    -- actor who created the proposal
    proposed_at     TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    reviewed_by     VARCHAR(255),                        -- checker actor (null until reviewed)
    reviewed_at     TIMESTAMPTZ,
    rejection_reason TEXT,                               -- populated on REJECTED
    applied_at      TIMESTAMPTZ,                         -- when the change was written to config_entries
    maker_audit_id  VARCHAR(255),                        -- audit entry ID for the proposal action
    checker_audit_id VARCHAR(255),                       -- audit entry ID for the review action

    CONSTRAINT pk_config_change_proposals PRIMARY KEY (proposal_id),
    CONSTRAINT chk_proposal_reviewer CHECK (
        (status = 'PENDING'  AND reviewed_by IS NULL)
        OR (status IN ('APPROVED', 'REJECTED') AND reviewed_by IS NOT NULL)
    )
);

-- Tenant + namespace queries: list pending approvals per namespace
CREATE INDEX idx_config_proposals_pending
    ON config_change_proposals (tenant_id, namespace, status)
    WHERE status = 'PENDING';

-- Lookup by proposer for "my pending proposals"
CREATE INDEX idx_config_proposals_proposer
    ON config_change_proposals (tenant_id, proposed_by, status);

COMMENT ON TABLE config_change_proposals IS
    'Maker-checker workflow for config mutations. '
    'Proposals are PENDING until a different user approves or rejects them. '
    'On APPROVED, the value is written to config_entries.';

COMMENT ON COLUMN config_change_proposals.status IS
    'PENDING = awaiting review; APPROVED = applied; REJECTED = declined.';
