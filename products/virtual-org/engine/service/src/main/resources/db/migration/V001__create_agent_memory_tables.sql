-- Virtual Organization: Agent Memory Schema
--
-- This schema supports the PgVectorAgentMemory implementation.
-- It stores agent memories with vector embeddings for semantic search.

-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Agent memory table
CREATE TABLE agent_memory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536),  -- OpenAI text-embedding-ada-002 dimension
    task_type VARCHAR(100),
    success BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB,

    -- Indexes
    CONSTRAINT agent_memory_agent_id_check CHECK (agent_id IS NOT NULL AND agent_id <> '')
);

-- Create indexes for efficient querying
CREATE INDEX idx_agent_memory_agent_id ON agent_memory(agent_id);
CREATE INDEX idx_agent_memory_created_at ON agent_memory(created_at DESC);
CREATE INDEX idx_agent_memory_task_type ON agent_memory(task_type);
CREATE INDEX idx_agent_memory_success ON agent_memory(success);

-- Create IVFFlat index for vector similarity search
-- This uses cosine distance for similarity
CREATE INDEX idx_agent_memory_embedding ON agent_memory
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

-- Metadata index for JSONB queries
CREATE INDEX idx_agent_memory_metadata ON agent_memory USING gin(metadata);

-- Comments
COMMENT ON TABLE agent_memory IS 'Stores agent task memories with vector embeddings for semantic search';
COMMENT ON COLUMN agent_memory.id IS 'Unique identifier for the memory entry';
COMMENT ON COLUMN agent_memory.agent_id IS 'ID of the agent that owns this memory';
COMMENT ON COLUMN agent_memory.content IS 'Textual content of the memory (task + response)';
COMMENT ON COLUMN agent_memory.embedding IS 'Vector embedding of the content for semantic search';
COMMENT ON COLUMN agent_memory.task_type IS 'Type of task (e.g., FEATURE_IMPLEMENTATION)';
COMMENT ON COLUMN agent_memory.success IS 'Whether the task was successful';
COMMENT ON COLUMN agent_memory.created_at IS 'When this memory was created';
COMMENT ON COLUMN agent_memory.metadata IS 'Additional metadata as JSON';

-- Function to search similar memories
CREATE OR REPLACE FUNCTION search_similar_memories(
    p_agent_id VARCHAR(255),
    p_embedding vector(1536),
    p_limit INT DEFAULT 10,
    p_min_similarity FLOAT DEFAULT 0.7
)
RETURNS TABLE (
    id UUID,
    content TEXT,
    task_type VARCHAR(100),
    success BOOLEAN,
    created_at TIMESTAMP,
    metadata JSONB,
    similarity FLOAT
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        m.id,
        m.content,
        m.task_type,
        m.success,
        m.created_at,
        m.metadata,
        1 - (m.embedding <=> p_embedding) as similarity
    FROM agent_memory m
    WHERE m.agent_id = p_agent_id
        AND 1 - (m.embedding <=> p_embedding) >= p_min_similarity
    ORDER BY m.embedding <=> p_embedding
    LIMIT p_limit;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION search_similar_memories IS 'Searches for similar memories using cosine similarity';

-- Agent configuration table (for storing agent settings)
CREATE TABLE agent_config (
    agent_id VARCHAR(255) PRIMARY KEY,
    role VARCHAR(100) NOT NULL,
    llm_provider VARCHAR(50),
    llm_model VARCHAR(100),
    llm_temperature FLOAT,
    llm_max_tokens INT,
    memory_short_term_size INT DEFAULT 100,
    memory_long_term_enabled BOOLEAN DEFAULT true,
    enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    config JSONB
);

CREATE INDEX idx_agent_config_role ON agent_config(role);
CREATE INDEX idx_agent_config_enabled ON agent_config(enabled);

COMMENT ON TABLE agent_config IS 'Stores agent configuration and settings';

-- Agent performance metrics table
CREATE TABLE agent_metrics (
    id BIGSERIAL PRIMARY KEY,
    agent_id VARCHAR(255) NOT NULL,
    tasks_completed BIGINT DEFAULT 0,
    tasks_failed BIGINT DEFAULT 0,
    avg_completion_time_ms FLOAT,
    success_rate FLOAT,
    total_tokens_used BIGINT DEFAULT 0,
    total_tool_calls BIGINT DEFAULT 0,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_agent_metrics_agent_id ON agent_metrics(agent_id);
CREATE INDEX idx_agent_metrics_recorded_at ON agent_metrics(recorded_at DESC);

COMMENT ON TABLE agent_metrics IS 'Stores agent performance metrics over time';

-- Task execution history table
CREATE TABLE task_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id VARCHAR(255) NOT NULL,
    agent_id VARCHAR(255) NOT NULL,
    task_type VARCHAR(100),
    priority VARCHAR(50),
    status VARCHAR(50),
    success BOOLEAN,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    duration_ms BIGINT,
    tokens_used INT,
    tool_calls_count INT,
    result_summary TEXT,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_task_history_task_id ON task_history(task_id);
CREATE INDEX idx_task_history_agent_id ON task_history(agent_id);
CREATE INDEX idx_task_history_created_at ON task_history(created_at DESC);
CREATE INDEX idx_task_history_success ON task_history(success);

COMMENT ON TABLE task_history IS 'Audit log of all task executions';

-- Decision history table
CREATE TABLE decision_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    decision_id VARCHAR(255) NOT NULL,
    agent_id VARCHAR(255) NOT NULL,
    task_id VARCHAR(255),
    decision_type VARCHAR(100) NOT NULL,
    chosen_option TEXT,
    reasoning TEXT,
    confidence FLOAT,
    required_approval BOOLEAN DEFAULT false,
    approved_by VARCHAR(255),
    approved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB
);

CREATE INDEX idx_decision_history_decision_id ON decision_history(decision_id);
CREATE INDEX idx_decision_history_agent_id ON decision_history(agent_id);
CREATE INDEX idx_decision_history_task_id ON decision_history(task_id);
CREATE INDEX idx_decision_history_created_at ON decision_history(created_at DESC);

COMMENT ON TABLE decision_history IS 'Audit log of all agent decisions';

-- Grants (adjust as needed for your security model)
-- GRANT SELECT, INSERT, UPDATE ON agent_memory TO virtual_org_app;
-- GRANT SELECT, INSERT, UPDATE ON agent_config TO virtual_org_app;
-- GRANT SELECT, INSERT ON agent_metrics TO virtual_org_app;
-- GRANT SELECT, INSERT ON task_history TO virtual_org_app;
-- GRANT SELECT, INSERT ON decision_history TO virtual_org_app;
