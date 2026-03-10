-- V20: Agent memory_items table
-- Provides durable PostgreSQL-backed storage for the PersistentMemoryPlane.
-- Replaces the in-memory EventLogMemoryStore for production deployments.
--
-- Content type column discriminates memory tier:
--   EPISODE     — agent turn episodic memory
--   FACT        — semantic memory (subject-predicate-object triples)
--   PROCEDURE   — procedural memory (learned policies)
--   PREFERENCE  — preference memory (key-value per namespace)
--   ARTIFACT    — long-lived artifacts referenced by multiple episodes
--   TASK_STATE  — durable task checkpoint (used by JdbcTaskStateStore)
--   WORKING     — promoted working-memory entry

CREATE TABLE IF NOT EXISTS memory_items (
    id              TEXT PRIMARY KEY,
    type            VARCHAR(50)  NOT NULL,
    tenant_id       VARCHAR(100) NOT NULL,
    sphere_id       VARCHAR(100),
    agent_id        VARCHAR(100) NOT NULL DEFAULT '',

    -- Content (JSONB — schema varies by type)
    content         JSONB        NOT NULL DEFAULT '{}',

    -- Provenance metadata
    provenance      JSONB        NOT NULL DEFAULT '{}',

    -- Validity tracking (status, confidence)
    validity        JSONB        NOT NULL DEFAULT '{"status": "ACTIVE", "confidence": 1.0}',

    -- Links to other memory items
    links           JSONB        NOT NULL DEFAULT '[]',

    -- Searchable labels / tags
    labels          JSONB        NOT NULL DEFAULT '{}',

    -- Data classification (UNCLASSIFIED, INTERNAL, CONFIDENTIAL, RESTRICTED)
    classification  VARCHAR(30)  NOT NULL DEFAULT 'INTERNAL',

    -- Full-text search vector (maintained by trigger below)
    search_vector   tsvector,

    -- Timestamps
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,           -- soft delete

    -- Consolidation tracking
    consolidated_at TIMESTAMPTZ,

    CONSTRAINT chk_memory_item_type CHECK (
        type IN ('EPISODE', 'FACT', 'PROCEDURE', 'PREFERENCE', 'ARTIFACT', 'TASK_STATE', 'WORKING')
    )
);

-- ─── Indexes ─────────────────────────────────────────────────────────────────

-- Primary access pattern: tenant + type (listing per memory tier)
CREATE INDEX IF NOT EXISTS idx_memory_items_tenant_type
    ON memory_items (tenant_id, type);

-- Per-agent queries
CREATE INDEX IF NOT EXISTS idx_memory_items_agent
    ON memory_items (agent_id);

-- Chronological listing (recent-first)
CREATE INDEX IF NOT EXISTS idx_memory_items_created
    ON memory_items (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_memory_items_type_created
    ON memory_items (type, created_at DESC);

-- Full-text search
CREATE INDEX IF NOT EXISTS idx_memory_items_search
    ON memory_items USING GIN (search_vector);

-- Label-based filtering
CREATE INDEX IF NOT EXISTS idx_memory_items_labels
    ON memory_items USING GIN (labels);

-- JSONB content queries
CREATE INDEX IF NOT EXISTS idx_memory_items_content
    ON memory_items USING GIN (content);

-- Partial index for non-deleted items (most common query scope)
CREATE INDEX IF NOT EXISTS idx_memory_items_active
    ON memory_items (tenant_id, type, created_at DESC)
    WHERE deleted_at IS NULL;

-- ─── Trigger: auto-update search_vector on insert/update ─────────────────────

CREATE OR REPLACE FUNCTION memory_items_search_vector_update()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', COALESCE(NEW.content::text, '')), 'A') ||
        setweight(to_tsvector('english', COALESCE(NEW.labels::text, '')), 'B');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_memory_items_search_vector ON memory_items;
CREATE TRIGGER trg_memory_items_search_vector
    BEFORE INSERT OR UPDATE ON memory_items
    FOR EACH ROW EXECUTE FUNCTION memory_items_search_vector_update();
