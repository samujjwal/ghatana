-- Migration V17: Legacy integrity constraints (compatibility placeholder)
--
-- The original V17 script targeted an older schema variant and attempted to add
-- foreign keys/constraints that are incompatible with the current DM persistence
-- model. Later migrations (V21+) provide the active constraints used by the
-- module. Keep V17 as a no-op to preserve migration history ordering.

DO $$
BEGIN
    RAISE NOTICE 'Skipping legacy V17 integrity migration for current schema';
END $$;
