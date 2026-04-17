-- Durable Audit Trail Plugin — Initial Schema
-- KP-023: Flyway migration for plugin_audit_entries table
-- 
-- Stores immutable audit entries with SHA-256 hash chain for tamper evidence.
-- The previous_hash of the first entry for each entity is NULL.
-- All subsequent entries reference the hash of their preceding entry.

CREATE TABLE IF NOT EXISTS plugin_audit_entries (
    entry_id       VARCHAR(128)  NOT NULL,
    entity_id      VARCHAR(256)  NOT NULL,
    action         VARCHAR(256)  NOT NULL,
    actor_id       VARCHAR(256),
    details        TEXT,
    previous_hash  VARCHAR(256),
    entry_hash     VARCHAR(256)  NOT NULL,
    entry_ts       BIGINT        NOT NULL,

    CONSTRAINT pk_plugin_audit_entries PRIMARY KEY (entry_id)
);

-- Index for fetching the ordered audit trail for a given entity
CREATE INDEX IF NOT EXISTS idx_plugin_audit_entries_entity_ts
    ON plugin_audit_entries (entity_id, entry_ts ASC);

-- Partial index to accelerate latest-hash lookups (used for chain linking)
CREATE INDEX IF NOT EXISTS idx_plugin_audit_entries_entity_latest
    ON plugin_audit_entries (entity_id, entry_ts DESC);
