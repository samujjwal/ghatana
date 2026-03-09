-- ============================================================================
-- V001: Create memory_items table
-- Stores all memory tiers in a single table with type discrimination.
-- Uses JSONB for flexible content, tsvector for full-text search.
-- ============================================================================

CREATE TABLE IF NOT EXISTS memory_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type            VARCHAR(50) NOT NULL,  -- EPISODE, FACT, PROCEDURE, PREFERENCE, ARTIFACT, TASK_STATE, WORKING
    tenant_id       VARCHAR(100) NOT NULL,
    sphere_id       VARCHAR(100),
    agent_id        VARCHAR(100) NOT NULL,

    -- Content (JSONB — schema varies by type)
    content         JSONB NOT NULL DEFAULT '{}',

    -- Provenance
    provenance      JSONB NOT NULL DEFAULT '{}',

    -- Validity
    validity        JSONB NOT NULL DEFAULT '{"status": "ACTIVE", "confidence": 1.0}',

    -- Links to other memory items
    links           JSONB NOT NULL DEFAULT '[]',

    -- Searchable labels
    labels          JSONB NOT NULL DEFAULT '{}',

    -- Data classification
    classification  VARCHAR(30) NOT NULL DEFAULT 'INTERNAL',

    -- Full-text search vector (auto-maintained by trigger)
    search_vector   tsvector,

    -- Timestamps
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ,
    deleted_at      TIMESTAMPTZ,  -- soft delete

    -- Consolidation tracking
    consolidated_at TIMESTAMPTZ,

    CONSTRAINT chk_type CHECK (type IN ('EPISODE', 'FACT', 'PROCEDURE', 'PREFERENCE', 'ARTIFACT', 'TASK_STATE', 'WORKING'))
);

-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_memory_items_tenant_type ON memory_items (tenant_id, type);
CREATE INDEX IF NOT EXISTS idx_memory_items_agent ON memory_items (agent_id);
CREATE INDEX IF NOT EXISTS idx_memory_items_created ON memory_items (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_memory_items_type_created ON memory_items (type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_memory_items_search ON memory_items USING GIN (search_vector);
CREATE INDEX IF NOT EXISTS idx_memory_items_labels ON memory_items USING GIN (labels);
CREATE INDEX IF NOT EXISTS idx_memory_items_content ON memory_items USING GIN (content);

-- Partial index for non-deleted items
CREATE INDEX IF NOT EXISTS idx_memory_items_active ON memory_items (tenant_id, type, created_at DESC)
    WHERE deleted_at IS NULL;

-- Auto-update search vector from content
CREATE OR REPLACE FUNCTION memory_items_search_update()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', COALESCE(NEW.content->>'input', '')), 'A') ||
        setweight(to_tsvector('english', COALESCE(NEW.content->>'output', '')), 'B') ||
        setweight(to_tsvector('english', COALESCE(NEW.content->>'subject', '')), 'A') ||
        setweight(to_tsvector('english', COALESCE(NEW.content->>'predicate', '')), 'B') ||
        setweight(to_tsvector('english', COALESCE(NEW.content->>'object', '')), 'B') ||
        setweight(to_tsvector('english', COALESCE(NEW.content->>'situation', '')), 'A') ||
        setweight(to_tsvector('english', COALESCE(NEW.content->>'action', '')), 'B');
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE TRIGGER trg_memory_items_search
    BEFORE INSERT OR UPDATE ON memory_items
    FOR EACH ROW EXECUTE FUNCTION memory_items_search_update();
