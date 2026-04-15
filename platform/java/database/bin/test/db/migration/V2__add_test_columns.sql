-- V2__add_test_columns.sql
-- Second migration for FlywayMigrationIT to verify incremental migrations

ALTER TABLE test_items
    ADD COLUMN IF NOT EXISTS description TEXT;
