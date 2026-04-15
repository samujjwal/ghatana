-- Flyway V004: Create collections metadata table
-- Provides collection-level configuration and schema management for Data Cloud

CREATE TABLE IF NOT EXISTS collections (
    id                UUID            NOT NULL DEFAULT gen_random_uuid(),
    tenant_id         VARCHAR(255)    NOT NULL,
    name              VARCHAR(255)    NOT NULL,
    display_name      VARCHAR(500),
    description       TEXT,
    record_type       VARCHAR(50)     NOT NULL,
    schema            JSONB           DEFAULT '{}'::jsonb,
    config            JSONB           DEFAULT '{}'::jsonb,
    retention_days    INTEGER,
    tier              VARCHAR(20)     DEFAULT 'HOT',
    active            BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(255),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_by        VARCHAR(255),
    version           INTEGER         NOT NULL DEFAULT 1,

    CONSTRAINT pk_collections PRIMARY KEY (id),
    CONSTRAINT uk_collections_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT chk_collections_record_type CHECK (record_type IN ('ENTITY', 'EVENT', 'TIMESERIES', 'DOCUMENT', 'GRAPH')),
    CONSTRAINT chk_collections_tier CHECK (tier IN ('HOT', 'WARM', 'COOL', 'COLD'))
);

-- Performance indexes
CREATE INDEX idx_collections_tenant ON collections (tenant_id);
CREATE INDEX idx_collections_tenant_type ON collections (tenant_id, record_type);
CREATE INDEX idx_collections_active ON collections (tenant_id, active);

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_collections_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    NEW.version = OLD.version + 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_collections_updated_at
    BEFORE UPDATE ON collections
    FOR EACH ROW EXECUTE FUNCTION update_collections_updated_at();

-- Documentation
COMMENT ON TABLE collections IS 'Collection registry — stores metadata, schema, and configuration for each data collection in the Data Cloud.';
COMMENT ON COLUMN collections.name IS 'Unique collection name within a tenant. Used as collection_name FK in record tables.';
COMMENT ON COLUMN collections.record_type IS 'Type of records stored: ENTITY, EVENT, TIMESERIES, DOCUMENT, or GRAPH';
COMMENT ON COLUMN collections.schema IS 'Optional JSON Schema definition for collection records';
COMMENT ON COLUMN collections.config IS 'Collection-level configuration (partitioning strategy, compaction, etc.)';
COMMENT ON COLUMN collections.retention_days IS 'Data retention period in days. NULL = indefinite retention.';
COMMENT ON COLUMN collections.tier IS 'Storage tier: HOT (fast), WARM, COOL, COLD (archive). Maps to StorageTier enum.';
