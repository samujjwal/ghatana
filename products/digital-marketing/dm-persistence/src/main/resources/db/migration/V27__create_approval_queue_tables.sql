-- Migration V27: Legacy approval queue structures (compatibility placeholder)
--
-- The original V27 migration depended on a legacy approvals schema variant that
-- is not present in the current DM persistence model. Keep the version as a
-- no-op to preserve Flyway history without breaking fresh migrations.

DO $$
BEGIN
    RAISE NOTICE 'Skipping legacy V27 approval queue migration for current schema';
END $$;
