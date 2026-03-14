-- Flyway V006: Add display_name column to entities (zero-downtime expand step)
-- ============================================================================
-- This is PHASE 1 of the expand/contract sequence for promoting display_name
-- from an embedded JSONB field (entities.data->>'display_name') to a dedicated
-- indexed column.
--
-- Zero-downtime rationale:
--   Adding a nullable column on PostgreSQL 11+ is an instant metadata-only
--   operation — no table rewrite, no lock held beyond a brief ACCESS EXCLUSIVE
--   for the catalog update.
--
-- Next steps (performed by DataMigrationService at startup, not in this file):
--   BACKFILL: UPDATE entities SET display_name = data->>'display_name'
--             WHERE display_name IS NULL AND data ? 'display_name'
--             ... executed in 1 000-row batches by BackfillEntitiesDisplayName
--
-- Future Flyway migration (V007):
--   CREATE INDEX CONCURRENTLY idx_entities_display_name ... (separate migration,
--   outside transaction, via ConcurrentIndexMigration)
--
-- Future Flyway migration (V008) — after full backfill verified:
--   ALTER TABLE entities ALTER COLUMN display_name SET NOT NULL
-- ============================================================================

ALTER TABLE entities ADD COLUMN IF NOT EXISTS display_name VARCHAR(500);

COMMENT ON COLUMN entities.display_name IS
    'Human-readable display name promoted from data->>''display_name'' JSONB field. '
    'Nullable until DataMigrationService backfill is complete (V006 expand step). '
    'NOT NULL constraint added in V008 after backfill is fully verified.';
