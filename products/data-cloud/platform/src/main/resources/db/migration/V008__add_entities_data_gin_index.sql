-- V008: GIN index for JSONB attribute queries on entities table
-- Uses IF NOT EXISTS for idempotent re-runs.
-- jsonb_path_ops operator class is chosen because Data-Cloud exclusively uses
-- the @>, @?, and @@ JSONB path operators in its filter expressions.
-- DC3-M9: CONCURRENTLY removed so this migration runs inside a Flyway transaction.
-- For zero-downtime index creation on a live table, execute manually out-of-band:
--   CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_entities_data_gin ON entities USING GIN (data jsonb_path_ops);
CREATE INDEX IF NOT EXISTS idx_entities_data_gin
    ON entities USING GIN (data jsonb_path_ops);
