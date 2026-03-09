-- Flyway V002: Create entities table for EntityRecord
-- Maps to: com.ghatana.datacloud.EntityRecord (@Entity, table = "entities")
-- Parent: com.ghatana.datacloud.DataRecord (@MappedSuperclass)

CREATE TABLE IF NOT EXISTS entities (
    -- DataRecord base fields
    id                UUID            NOT NULL,
    tenant_id         VARCHAR(255)    NOT NULL,
    collection_name   VARCHAR(255)    NOT NULL,
    record_type       VARCHAR(50)     NOT NULL,
    data              JSONB           DEFAULT '{}'::jsonb,
    metadata          JSONB           DEFAULT '{}'::jsonb,
    created_at        TIMESTAMPTZ,
    created_by        VARCHAR(255),

    -- EntityRecord-specific fields
    version           INTEGER         DEFAULT 1,
    active            BOOLEAN         DEFAULT TRUE,
    updated_at        TIMESTAMPTZ,
    updated_by        VARCHAR(255),

    CONSTRAINT pk_entities PRIMARY KEY (id),
    CONSTRAINT chk_entities_record_type CHECK (record_type IN ('ENTITY', 'EVENT', 'TIMESERIES', 'DOCUMENT', 'GRAPH'))
);

-- Performance indexes
CREATE INDEX idx_entities_tenant ON entities (tenant_id);
CREATE INDEX idx_entities_collection ON entities (tenant_id, collection_name);
CREATE INDEX idx_entities_active ON entities (tenant_id, collection_name, active);
CREATE INDEX idx_entities_created_at ON entities (tenant_id, collection_name, created_at DESC);

-- Documentation
COMMENT ON TABLE entities IS 'Mutable entity store for EntityRecord. Supports optimistic locking via version column and soft-delete via active flag.';
COMMENT ON COLUMN entities.id IS 'UUID primary key, auto-generated in @PrePersist if null';
COMMENT ON COLUMN entities.tenant_id IS 'Tenant isolation identifier. Not updatable after creation.';
COMMENT ON COLUMN entities.version IS 'Optimistic locking version (@Version). Incremented on each update.';
COMMENT ON COLUMN entities.active IS 'Soft-delete flag. FALSE = logically deleted.';
COMMENT ON COLUMN entities.data IS 'Entity payload as JSONB';
COMMENT ON COLUMN entities.metadata IS 'Extensible metadata as JSONB';
