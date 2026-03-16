-- V001__create_reconciliation_schema.sql
-- D-13 Client Money Reconciliation: orchestration, balance snapshots, statements, audit trail

-- Reconciliation runs (one per day per run type)
CREATE TABLE recon_runs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recon_date          DATE NOT NULL,
    recon_date_bs       VARCHAR(10),
    run_type            VARCHAR(30) NOT NULL DEFAULT 'DAILY',
    operator_id         UUID,
    status              VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    internal_balance_hash VARCHAR(64),   -- SHA-256 of balance snapshot
    external_balance_hash VARCHAR(64),
    match_count         INT DEFAULT 0,
    break_count         INT DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_run_status CHECK (status IN ('SCHEDULED','RUNNING','COMPLETED','FAILED'))
);
CREATE UNIQUE INDEX idx_recon_run_date ON recon_runs(recon_date, run_type);

-- Internal balance snapshots (from K-16 ledger as-of recon date)
CREATE TABLE recon_balance_snapshots (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id              UUID NOT NULL REFERENCES recon_runs(id),
    client_id           UUID NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    available_balance   NUMERIC(28, 12) NOT NULL,
    earmarked_balance   NUMERIC(28, 12) NOT NULL DEFAULT 0,
    total_balance       NUMERIC(28, 12) NOT NULL,
    as_of_date          DATE NOT NULL,
    as_of_date_bs       VARCHAR(10),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_snap_run ON recon_balance_snapshots(run_id);
CREATE INDEX idx_snap_client ON recon_balance_snapshots(client_id);

-- External bank statements
CREATE TABLE statement_entries (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_id             VARCHAR(50) NOT NULL,
    account_number      VARCHAR(50) NOT NULL,
    stmt_date           DATE NOT NULL,
    stmt_date_bs        VARCHAR(10),
    reference           VARCHAR(128),
    narrative           TEXT,
    amount              NUMERIC(28, 12) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    counterparty        VARCHAR(128),
    source_format       VARCHAR(20) NOT NULL,   -- CSV | MT940 | MT942 | API
    raw_line            TEXT,
    is_duplicate        BOOLEAN NOT NULL DEFAULT FALSE,
    is_valid            BOOLEAN NOT NULL DEFAULT TRUE,
    quarantine_reason   TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_stmt_date_bank ON statement_entries(stmt_date, bank_id);
CREATE INDEX idx_stmt_reference ON statement_entries(reference);

-- Reconciliation audit trail (immutable, append-only)
CREATE TABLE recon_audit_log (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id              UUID NOT NULL REFERENCES recon_runs(id),
    event_time          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    event_type          VARCHAR(40) NOT NULL,   -- e.g., STEP_STARTED, BREAK_DETECTED, RESOLVED
    actor_id            UUID,
    details             JSONB
);
CREATE INDEX idx_recon_audit_run ON recon_audit_log(run_id, event_time);
