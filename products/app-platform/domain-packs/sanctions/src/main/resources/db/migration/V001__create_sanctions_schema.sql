-- ============================================================================
-- D14 Sanctions Schema
-- Stores sanctions list entries, screening results, and review queue.
-- ============================================================================

CREATE TABLE IF NOT EXISTS sanctions_entries (
    entry_id        TEXT        NOT NULL PRIMARY KEY,
    list_type       TEXT        NOT NULL,                    -- OFAC_SDN | UN_CONSOLIDATED | EU_ASSET_FREEZE | NRB_LOCAL
    primary_name    TEXT        NOT NULL,
    aliases         JSONB       NOT NULL DEFAULT '[]',       -- JSON array of name strings
    entity_type     TEXT        NOT NULL,                    -- INDIVIDUAL | ENTITY
    date_of_birth   TEXT,
    nationality     TEXT,
    list_version    TEXT        NOT NULL DEFAULT 'latest',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_sanctions_entries_primary_name
    ON sanctions_entries USING gin (to_tsvector('simple', primary_name));

CREATE INDEX IF NOT EXISTS idx_sanctions_entries_list_type
    ON sanctions_entries (list_type);

-- ─── Screening Results ───────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS screening_results (
    result_id       TEXT        NOT NULL PRIMARY KEY,
    request_id      TEXT        NOT NULL,
    match_found     BOOLEAN     NOT NULL,
    matches         JSONB       NOT NULL DEFAULT '[]',       -- JSON array of MatchResult objects
    decision        TEXT        NOT NULL,                    -- AUTO_BLOCK | HIGH | MEDIUM | LOW
    highest_score   DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    screened_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    reference_id    TEXT                                     -- orderId, onboardingId, etc.
);

CREATE INDEX IF NOT EXISTS idx_screening_results_reference_id
    ON screening_results (reference_id);

CREATE INDEX IF NOT EXISTS idx_screening_results_decision
    ON screening_results (decision) WHERE match_found = true;

CREATE INDEX IF NOT EXISTS idx_screening_results_screened_at
    ON screening_results (screened_at DESC);

-- ─── Review Queue ────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS match_review_queue (
    review_id       TEXT        NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    result_id       TEXT        NOT NULL REFERENCES screening_results(result_id),
    status          TEXT        NOT NULL DEFAULT 'PENDING',  -- PENDING | APPROVED | REJECTED
    reviewer_id     TEXT,
    decided_at      TIMESTAMPTZ,
    decision_notes  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_match_review_queue_status
    ON match_review_queue (status) WHERE status = 'PENDING';

-- ─── Batch Re-screening Checkpoint ───────────────────────────────────────────

CREATE TABLE IF NOT EXISTS batch_rescreening_checkpoint (
    id              SERIAL      PRIMARY KEY,
    last_cursor     TEXT        NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
