-- V005: Partition event_store by created_at_utc (monthly range partitions)
-- STORY-K05-005 — Sprint 2
--
-- Converts the flat event_store table to a RANGE-partitioned parent table.
-- Monthly sub-partitions are pre-created for the current month + 3 months ahead.
-- A pg_partman-compatible naming convention is used so the DBA team can automate
-- future partition creation (partition: event_store_YYYY_MM).
--
-- WHY monthly partitions:
--  1. Aligns with financial reporting periods (BS fiscal year = Shrawan-Ashadh).
--  2. Enables cheap DETACH + ARCHIVE of expired partitions for retention (K07-012).
--  3. Partition pruning drops query cost for date-bounded replays (K05-021).
--
-- MIGRATION STRATEGY:
--  Step 1 — Rename existing table to _legacy.
--  Step 2 — Create partitioned parent (same schema but no actual rows).
--  Step 3 — Attach _legacy as a partition covering timestamps before the
--            first monthly partition (catch-all for existing data).
--  Step 4 — Create monthly partitions for current + next 3 months.
--  Step 5 — Create auto-partition management function + trigger.

-- ─────────────────────────────────────────────────────────────────────────────
-- Step 1: Rename existing flat table so we can attach it as a legacy partition
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE event_store RENAME TO event_store_legacy;

-- ─────────────────────────────────────────────────────────────────────────────
-- Step 2: Create partitioned parent table (no rows — structure only)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE event_store (
    event_id         UUID             NOT NULL,
    event_type       VARCHAR(255)     NOT NULL,
    aggregate_id     UUID             NOT NULL,
    aggregate_type   VARCHAR(255)     NOT NULL,
    sequence_number  BIGINT           NOT NULL,
    data             JSONB            NOT NULL,
    metadata         JSONB            NOT NULL DEFAULT '{}',
    created_at_utc   TIMESTAMPTZ      NOT NULL,
    created_at_bs    VARCHAR(10),         -- YYYY-MM-DD in BS; null when calendar enricher unavailable
    UNIQUE (aggregate_id, sequence_number)
) PARTITION BY RANGE (created_at_utc);

-- Re-create global indexes on the parent (inherited by all partitions)
CREATE INDEX idx_event_store_event_type      ON event_store (event_type);
CREATE INDEX idx_event_store_aggregate_id    ON event_store (aggregate_id);
CREATE INDEX idx_event_store_created_at_utc  ON event_store (created_at_utc DESC);

-- ─────────────────────────────────────────────────────────────────────────────
-- Step 3: Attach legacy data as the very-first catch-all partition
-- ─────────────────────────────────────────────────────────────────────────────
-- All rows with created_at_utc < 2026-01-01 land here (i.e., any Sprint 1 seed data).
ALTER TABLE event_store_legacy
    ADD CONSTRAINT event_store_legacy_created_check
    CHECK (created_at_utc < TIMESTAMP WITH TIME ZONE '2026-01-01 00:00:00+00');

ALTER TABLE event_store ATTACH PARTITION event_store_legacy
    FOR VALUES FROM (MINVALUE) TO ('2026-01-01 00:00:00+00');

-- ─────────────────────────────────────────────────────────────────────────────
-- Step 4: Create monthly partitions for the current window (2026-01 to 2026-04)
-- These cover Sprint 1-2 production timestamps plus near-future months.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE event_store_2026_01 PARTITION OF event_store
    FOR VALUES FROM ('2026-01-01 00:00:00+00') TO ('2026-02-01 00:00:00+00');

CREATE TABLE event_store_2026_02 PARTITION OF event_store
    FOR VALUES FROM ('2026-02-01 00:00:00+00') TO ('2026-03-01 00:00:00+00');

CREATE TABLE event_store_2026_03 PARTITION OF event_store
    FOR VALUES FROM ('2026-03-01 00:00:00+00') TO ('2026-04-01 00:00:00+00');

CREATE TABLE event_store_2026_04 PARTITION OF event_store
    FOR VALUES FROM ('2026-04-01 00:00:00+00') TO ('2026-05-01 00:00:00+00');

CREATE TABLE event_store_2026_05 PARTITION OF event_store
    FOR VALUES FROM ('2026-05-01 00:00:00+00') TO ('2026-06-01 00:00:00+00');

CREATE TABLE event_store_2026_06 PARTITION OF event_store
    FOR VALUES FROM ('2026-06-01 00:00:00+00') TO ('2026-07-01 00:00:00+00');

-- ─────────────────────────────────────────────────────────────────────────────
-- Step 5: Auto-partition management function
-- Called by the application's PartitionManager (Java) or a pg_cron job.
-- Creates the next 3 monthly partitions relative to the given timestamp.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION event_store_create_partitions(ahead_months INT DEFAULT 3)
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    v_start  TIMESTAMPTZ;
    v_end    TIMESTAMPTZ;
    v_name   TEXT;
    v_month  INT;
BEGIN
    FOR v_month IN 1..ahead_months LOOP
        v_start := date_trunc('month', now() + (v_month || ' months')::INTERVAL);
        v_end   := v_start + INTERVAL '1 month';
        v_name  := 'event_store_' || to_char(v_start, 'YYYY_MM');

        -- Idempotent — skip if partition already exists
        IF NOT EXISTS (
            SELECT 1 FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE c.relname = v_name
              AND n.nspname = current_schema()
        ) THEN
            EXECUTE format(
                'CREATE TABLE %I PARTITION OF event_store FOR VALUES FROM (%L) TO (%L)',
                v_name, v_start, v_end
            );
            RAISE NOTICE 'Created partition: %', v_name;
        END IF;
    END LOOP;
END;
$$;

COMMENT ON FUNCTION event_store_create_partitions IS
    'Creates the next N monthly partitions for event_store. Call from PartitionManager or pg_cron.';

-- Run once during migration to ensure we have coverage through end of 2026
SELECT event_store_create_partitions(9);
