-- ============================================================================
-- V002: Create memory_embeddings table
-- Stores vector embeddings for memory items. Uses pgvector for similarity search.
-- ============================================================================

-- Ensure pgvector extension is available
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS memory_embeddings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    memory_item_id  UUID NOT NULL REFERENCES memory_items(id) ON DELETE CASCADE,
    model_name      VARCHAR(100) NOT NULL DEFAULT 'text-embedding-3-small',
    embedding       vector(1536) NOT NULL,  -- OpenAI text-embedding-3-small dimension
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- One embedding per (item, model) pair
    CONSTRAINT uq_embedding_item_model UNIQUE (memory_item_id, model_name)
);

-- HNSW index for approximate nearest neighbour search (cosine distance)
CREATE INDEX IF NOT EXISTS idx_embeddings_hnsw ON memory_embeddings
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- Index for joining back to memory_items
CREATE INDEX IF NOT EXISTS idx_embeddings_item ON memory_embeddings (memory_item_id);

-- Semantic search function: finds top-k similar embeddings
CREATE OR REPLACE FUNCTION search_memory_semantic(
    query_embedding vector(1536),
    tenant_filter   VARCHAR(100),
    type_filter     VARCHAR(50)[] DEFAULT NULL,
    k               INT DEFAULT 10,
    time_start      TIMESTAMPTZ DEFAULT NULL,
    time_end        TIMESTAMPTZ DEFAULT NULL
)
RETURNS TABLE (
    memory_item_id UUID,
    similarity     FLOAT8
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        me.memory_item_id,
        1 - (me.embedding <=> query_embedding) AS similarity
    FROM memory_embeddings me
    JOIN memory_items mi ON mi.id = me.memory_item_id
    WHERE mi.tenant_id = tenant_filter
      AND mi.deleted_at IS NULL
      AND (type_filter IS NULL OR mi.type = ANY(type_filter))
      AND (time_start IS NULL OR mi.created_at >= time_start)
      AND (time_end IS NULL OR mi.created_at <= time_end)
    ORDER BY me.embedding <=> query_embedding
    LIMIT k;
END;
$$ LANGUAGE plpgsql;
