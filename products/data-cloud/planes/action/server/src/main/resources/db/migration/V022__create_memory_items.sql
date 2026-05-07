CREATE TABLE IF NOT EXISTS memory_items (
    id             TEXT PRIMARY KEY,
    type           TEXT NOT NULL,
    tenant_id      TEXT NOT NULL,
    sphere_id      TEXT,
    content        JSONB NOT NULL,
    classification TEXT NOT NULL DEFAULT 'UNCLASSIFIED',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at     TIMESTAMPTZ,
    deleted_at     TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_memory_items_tenant
    ON memory_items (tenant_id);

CREATE INDEX IF NOT EXISTS idx_memory_items_type
    ON memory_items (type);
