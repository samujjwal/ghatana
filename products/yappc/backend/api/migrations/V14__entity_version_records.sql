-- V14: Entity version records table
-- Provides durable storage for entity version history (replaces InMemoryVersionRecord)

CREATE TABLE IF NOT EXISTS entity_version_records (
    id               UUID        NOT NULL PRIMARY KEY,
    tenant_id        VARCHAR(255) NOT NULL,
    entity_id        UUID        NOT NULL,
    version_number   INT         NOT NULL,
    entity_snapshot  JSONB       NOT NULL,
    author           VARCHAR(255) NOT NULL,
    version_timestamp TIMESTAMPTZ NOT NULL,
    reason           TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_entity_version UNIQUE (tenant_id, entity_id, version_number)
);

-- Fast lookup of all versions for a given entity
CREATE INDEX IF NOT EXISTS idx_evr_tenant_entity
    ON entity_version_records (tenant_id, entity_id, version_number ASC);

-- Fast lookup of latest version (ORDER BY version_number DESC LIMIT 1)
CREATE INDEX IF NOT EXISTS idx_evr_tenant_entity_desc
    ON entity_version_records (tenant_id, entity_id, version_number DESC);

COMMENT ON TABLE entity_version_records IS 'Durable entity version history — one row per version snapshot';
COMMENT ON COLUMN entity_version_records.entity_snapshot IS 'Full JSON snapshot of the Entity at this version';
