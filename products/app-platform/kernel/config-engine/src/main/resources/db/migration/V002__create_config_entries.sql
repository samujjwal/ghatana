-- V002: Hierarchical config entries table
-- Stores config values scoped to specific hierarchy levels and IDs.

CREATE TABLE IF NOT EXISTS config_entries (
    namespace        TEXT         NOT NULL,
    key              TEXT         NOT NULL,
    value            JSONB        NOT NULL,
    level            TEXT         NOT NULL CHECK (level IN ('GLOBAL', 'JURISDICTION', 'TENANT', 'USER', 'SESSION')),
    level_id         TEXT         NOT NULL,
    schema_namespace TEXT         NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- A namespace+key is unique per level+level_id (one entry per scope)
    CONSTRAINT pk_config_entries PRIMARY KEY (namespace, key, level, level_id),

    -- schema_namespace is a soft reference; no FK enforced to allow seed entries
    -- before schema registration and to support multi-version schemas.
    CHECK (schema_namespace <> '')
);

-- Primary resolution query: namespace + applicable levels
CREATE INDEX IF NOT EXISTS idx_config_entries_resolve
    ON config_entries (namespace, level, level_id);

COMMENT ON TABLE config_entries IS 'Hierarchical config values scoped to GLOBAL/JURISDICTION/TENANT/USER/SESSION';
COMMENT ON COLUMN config_entries.level_id IS 'Scope identifier: "global" for GLOBAL, ISO code for JURISDICTION, UUID for TENANT/USER/SESSION';
