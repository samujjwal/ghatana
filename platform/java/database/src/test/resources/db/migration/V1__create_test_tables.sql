-- V1__create_test_tables.sql
-- Test migration script for FlywayMigrationIT

CREATE TABLE IF NOT EXISTS test_items (
    id          BIGSERIAL   PRIMARY KEY,
    name        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_test_items_name ON test_items(name);
