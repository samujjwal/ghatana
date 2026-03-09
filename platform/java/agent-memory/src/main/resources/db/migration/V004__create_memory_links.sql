-- ============================================================================
-- V004: Create memory_links table
-- Cross-referencing table for links between memory items.
-- Enables graph traversal across memory tiers.
-- ============================================================================

CREATE TABLE IF NOT EXISTS memory_links (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id       UUID NOT NULL REFERENCES memory_items(id) ON DELETE CASCADE,
    target_id       UUID NOT NULL REFERENCES memory_items(id) ON DELETE CASCADE,
    link_type       VARCHAR(50) NOT NULL,
    strength        DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    metadata        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- Prevent duplicate links
    CONSTRAINT uq_memory_link UNIQUE (source_id, target_id, link_type),

    CONSTRAINT chk_link_type CHECK (link_type IN (
        'DERIVED_FROM', 'CONTRADICTS', 'SUPPORTS', 'SUPERSEDES',
        'RELATES_TO', 'CAUSED_BY', 'LEARNED_FROM'
    ))
);

-- Indexes for graph traversal
CREATE INDEX IF NOT EXISTS idx_memory_links_source ON memory_links (source_id);
CREATE INDEX IF NOT EXISTS idx_memory_links_target ON memory_links (target_id);
CREATE INDEX IF NOT EXISTS idx_memory_links_type ON memory_links (link_type);

-- Bidirectional link lookup
CREATE INDEX IF NOT EXISTS idx_memory_links_bidirectional ON memory_links (source_id, target_id);
