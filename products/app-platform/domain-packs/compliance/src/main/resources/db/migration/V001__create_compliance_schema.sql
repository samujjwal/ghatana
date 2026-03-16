-- V001__create_compliance_schema.sql
-- D-07 Compliance Engine — Core Schema

-- ─── Compliance Check Records (D07-003) ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS compliance_checks (
    check_id              TEXT        PRIMARY KEY,
    order_id              TEXT        NOT NULL,
    status                TEXT        NOT NULL CHECK (status IN ('PASS','FAIL','REVIEW')),
    rules_evaluated       JSONB       NOT NULL DEFAULT '[]',
    reasons               JSONB       NOT NULL DEFAULT '[]',
    evaluation_duration_ms BIGINT     NOT NULL,
    jurisdiction          TEXT        NOT NULL,
    evaluated_at          TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_compliance_checks_order ON compliance_checks (order_id);
CREATE INDEX IF NOT EXISTS idx_compliance_checks_status ON compliance_checks (status);
CREATE INDEX IF NOT EXISTS idx_compliance_checks_eval_at ON compliance_checks (evaluated_at DESC);

-- ─── Lock-in Records (D07-004, D07-005) ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS lock_in_records (
    lock_in_id       TEXT          PRIMARY KEY,
    client_id        TEXT          NOT NULL,
    instrument_id    TEXT          NOT NULL,
    locked_quantity  NUMERIC(18,6) NOT NULL CHECK (locked_quantity > 0),
    lock_in_start_bs TEXT          NOT NULL,   -- YYYY-MM-DD Bikram Sambat
    lock_in_end_bs   TEXT          NOT NULL,   -- YYYY-MM-DD Bikram Sambat
    lock_in_type     TEXT          NOT NULL CHECK (lock_in_type IN ('PROMOTER','IPO','BONUS','RIGHTS')),
    created_at       TIMESTAMPTZ   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_lock_in_client           ON lock_in_records (client_id);
CREATE INDEX IF NOT EXISTS idx_lock_in_instrument       ON lock_in_records (instrument_id);
CREATE INDEX IF NOT EXISTS idx_lock_in_client_instrument ON lock_in_records (client_id, instrument_id);

-- ─── Restricted / Watch Lists (D07-011) ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS restricted_list_entries (
    entry_id         TEXT          PRIMARY KEY,
    list_type        TEXT          NOT NULL CHECK (list_type IN ('RESTRICTED','WATCH','GREY')),
    instrument_id    TEXT          NOT NULL,
    entity_group_id  TEXT          NOT NULL,
    reason           TEXT,
    start_date_bs    TEXT          NOT NULL,
    end_date_bs      TEXT,         -- null = indefinite
    created_at       TIMESTAMPTZ   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_restricted_instrument ON restricted_list_entries (instrument_id);
CREATE INDEX IF NOT EXISTS idx_restricted_entity     ON restricted_list_entries (entity_group_id);

-- ─── Attestation Records (D07-013) ───────────────────────────────────────────
CREATE TABLE IF NOT EXISTS compliance_attestations (
    attestation_id   TEXT          PRIMARY KEY,
    attestation_type TEXT          NOT NULL,
    client_id        TEXT          NOT NULL,
    due_date_bs      TEXT          NOT NULL,
    status           TEXT          NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING','SIGNED','REVIEWED','EXPIRED')),
    signed_at        TIMESTAMPTZ,
    reviewed_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_attestation_client ON compliance_attestations (client_id);
CREATE INDEX IF NOT EXISTS idx_attestation_status ON compliance_attestations (status);
