-- ============================================================================
-- Ghatana AI Requirements Service - Database Initialization
-- ============================================================================
-- PostgreSQL setup

-- Note: pgvector extension requires custom PostgreSQL build
-- For development, using standard postgres:16-alpine image
-- Vector functionality can be added by building custom image or using:
-- docker run -d pgvector/pgvector:latest (when available in your registry)

-- Enable extensions (pgvector omitted for standard PostgreSQL image)
-- Uncomment the line below if using pgvector-enabled PostgreSQL image
-- CREATE EXTENSION IF NOT EXISTS pgvector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- ============================================================================
-- REQUIREMENTS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS requirements (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(255) NOT NULL,
    project_id UUID NOT NULL,
    title VARCHAR(500) NOT NULL,
    description TEXT NOT NULL,
    requirement_type VARCHAR(50) NOT NULL DEFAULT 'FUNCTIONAL',
    classification VARCHAR(100),
    priority VARCHAR(20) DEFAULT 'MEDIUM',
    status VARCHAR(50) DEFAULT 'DRAFT',
    quality_score NUMERIC(3,2),
    confidence_score NUMERIC(3,2),
    acceptance_criteria TEXT[],
    tags TEXT[],
    created_by UUID,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    archived_at TIMESTAMP,
    metadata JSONB,
    version INTEGER DEFAULT 1,
    CONSTRAINT valid_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT valid_status CHECK (status IN ('DRAFT', 'REVIEW', 'APPROVED', 'IMPLEMENTED', 'DEPRECATED'))
);

CREATE INDEX idx_requirements_tenant_id ON requirements(tenant_id);
CREATE INDEX idx_requirements_project_id ON requirements(project_id);
CREATE INDEX idx_requirements_status ON requirements(status);
CREATE INDEX idx_requirements_created_at ON requirements(created_at DESC);
CREATE INDEX idx_requirements_quality_score ON requirements(quality_score DESC);
CREATE INDEX idx_requirements_tags ON requirements USING GIN(tags);

-- ============================================================================
-- REQUIREMENT EMBEDDINGS TABLE (Vector Storage)
-- ============================================================================
-- Note: Using bytea instead of pgvector's vector type for standard PostgreSQL
-- If pgvector is available, this can be migrated to use vector(1536) type
CREATE TABLE IF NOT EXISTS requirement_embeddings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    requirement_id UUID NOT NULL REFERENCES requirements(id) ON DELETE CASCADE,
    tenant_id VARCHAR(255) NOT NULL,
    embedding_model VARCHAR(100) NOT NULL,
    embedding_dimension INTEGER NOT NULL,
    vector bytea,  -- Store vector as binary data - can be JSON array or binary format
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_requirement_model UNIQUE(requirement_id, embedding_model)
);

CREATE INDEX idx_embeddings_requirement_id ON requirement_embeddings(requirement_id);
CREATE INDEX idx_embeddings_tenant_id ON requirement_embeddings(tenant_id);
-- Vector index would be: CREATE INDEX idx_embeddings_vector ON requirement_embeddings USING ivfflat(vector vector_cosine_ops) WITH (lists = 100);
-- (only available with pgvector extension)

-- ============================================================================
-- AI SUGGESTIONS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS ai_suggestions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(255) NOT NULL,
    requirement_id UUID NOT NULL REFERENCES requirements(id) ON DELETE CASCADE,
    suggestion_type VARCHAR(100) NOT NULL,
    suggestion_text TEXT NOT NULL,
    rationale TEXT,
    confidence_score NUMERIC(3,2) NOT NULL,
    accepted BOOLEAN DEFAULT FALSE,
    applied_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB
);

CREATE INDEX idx_suggestions_requirement_id ON ai_suggestions(requirement_id);
CREATE INDEX idx_suggestions_tenant_id ON ai_suggestions(tenant_id);
CREATE INDEX idx_suggestions_type ON ai_suggestions(suggestion_type);
CREATE INDEX idx_suggestions_accepted ON ai_suggestions(accepted);

-- ============================================================================
-- QUALITY ASSESSMENTS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS quality_assessments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(255) NOT NULL,
    requirement_id UUID NOT NULL REFERENCES requirements(id) ON DELETE CASCADE,
    overall_score NUMERIC(3,2) NOT NULL,
    clarity_score NUMERIC(3,2),
    completeness_score NUMERIC(3,2),
    specificity_score NUMERIC(3,2),
    testability_score NUMERIC(3,2),
    issues TEXT[],
    suggestions TEXT[],
    assessment_model VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT scores_range CHECK (
        overall_score >= 0 AND overall_score <= 1 AND
        clarity_score >= 0 AND clarity_score <= 1 AND
        completeness_score >= 0 AND completeness_score <= 1 AND
        specificity_score >= 0 AND specificity_score <= 1 AND
        testability_score >= 0 AND testability_score <= 1
    )
);

CREATE INDEX idx_quality_requirement_id ON quality_assessments(requirement_id);
CREATE INDEX idx_quality_tenant_id ON quality_assessments(tenant_id);
CREATE INDEX idx_quality_score ON quality_assessments(overall_score DESC);
CREATE INDEX idx_quality_created_at ON quality_assessments(created_at DESC);

-- ============================================================================
-- REQUIREMENT CLASSIFICATIONS TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS requirement_classifications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(255) NOT NULL,
    requirement_id UUID NOT NULL REFERENCES requirements(id) ON DELETE CASCADE,
    classification VARCHAR(100) NOT NULL,
    confidence_score NUMERIC(3,2) NOT NULL,
    model_version VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT valid_confidence CHECK (confidence_score >= 0 AND confidence_score <= 1),
    CONSTRAINT valid_classification CHECK (
        classification IN (
            'FUNCTIONAL', 'NON_FUNCTIONAL', 'SECURITY', 'COMPLIANCE',
            'PERFORMANCE', 'USABILITY', 'MAINTAINABILITY', 'DEFAULT'
        )
    )
);

CREATE INDEX idx_classifications_requirement_id ON requirement_classifications(requirement_id);
CREATE INDEX idx_classifications_tenant_id ON requirement_classifications(tenant_id);
CREATE INDEX idx_classifications_type ON requirement_classifications(classification);

-- ============================================================================
-- ACCEPTANCE CRITERIA TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS acceptance_criteria (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(255) NOT NULL,
    requirement_id UUID NOT NULL REFERENCES requirements(id) ON DELETE CASCADE,
    given_condition TEXT NOT NULL,
    when_action TEXT NOT NULL,
    then_outcome TEXT NOT NULL,
    priority VARCHAR(20) DEFAULT 'MEDIUM',
    status VARCHAR(50) DEFAULT 'DRAFT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_criteria_requirement_id ON acceptance_criteria(requirement_id);
CREATE INDEX idx_criteria_tenant_id ON acceptance_criteria(tenant_id);
CREATE INDEX idx_criteria_status ON acceptance_criteria(status);

-- ============================================================================
-- AI OPERATION LOGS TABLE (for auditing and monitoring)
-- ============================================================================
CREATE TABLE IF NOT EXISTS ai_operation_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(255) NOT NULL,
    operation_type VARCHAR(100) NOT NULL,
    requirement_id UUID REFERENCES requirements(id) ON DELETE SET NULL,
    operation_status VARCHAR(50) NOT NULL,
    model_used VARCHAR(100),
    input_tokens INTEGER,
    output_tokens INTEGER,
    total_tokens INTEGER,
    latency_ms INTEGER,
    cost_cents NUMERIC(10,2),
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_logs_tenant_id ON ai_operation_logs(tenant_id);
CREATE INDEX idx_logs_operation_type ON ai_operation_logs(operation_type);
CREATE INDEX idx_logs_created_at ON ai_operation_logs(created_at DESC);
CREATE INDEX idx_logs_status ON ai_operation_logs(operation_status);

-- ============================================================================
-- CACHE MANAGEMENT TABLE
-- ============================================================================
CREATE TABLE IF NOT EXISTS cache_invalidation_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id VARCHAR(255) NOT NULL,
    cache_key VARCHAR(500) NOT NULL,
    invalidation_reason VARCHAR(200),
    invalidated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_cache_tenant_id ON cache_invalidation_log(tenant_id);
CREATE INDEX idx_cache_invalidated_at ON cache_invalidation_log(invalidated_at DESC);

-- ============================================================================
-- MATERIALIZED VIEW: AI METRICS
-- ============================================================================
CREATE MATERIALIZED VIEW IF NOT EXISTS ai_metrics_summary AS
SELECT
    tenant_id,
    DATE(created_at) as date,
    operation_type,
    COUNT(*) as operation_count,
    AVG(latency_ms) as avg_latency_ms,
    MAX(latency_ms) as max_latency_ms,
    MIN(latency_ms) as min_latency_ms,
    PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY latency_ms) as p99_latency_ms,
    SUM(input_tokens) as total_input_tokens,
    SUM(output_tokens) as total_output_tokens,
    SUM(CASE WHEN operation_status = 'SUCCESS' THEN 1 ELSE 0 END) as success_count,
    SUM(CASE WHEN operation_status = 'FAILED' THEN 1 ELSE 0 END) as failure_count
FROM ai_operation_logs
GROUP BY tenant_id, DATE(created_at), operation_type;

CREATE INDEX idx_metrics_tenant_date ON ai_metrics_summary(tenant_id, date DESC);

-- ============================================================================
-- MATERIALIZED VIEW: QUALITY STATISTICS
-- ============================================================================
CREATE MATERIALIZED VIEW IF NOT EXISTS quality_statistics AS
SELECT
    tenant_id,
    requirement_type,
    COUNT(*) as total_requirements,
    AVG(quality_score) as avg_quality_score,
    MAX(quality_score) as max_quality_score,
    MIN(quality_score) as min_quality_score,
    PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY quality_score) as median_quality_score,
    SUM(CASE WHEN quality_score >= 0.8 THEN 1 ELSE 0 END) as high_quality_count,
    SUM(CASE WHEN quality_score < 0.5 THEN 1 ELSE 0 END) as low_quality_count
FROM requirements
WHERE archived_at IS NULL
GROUP BY tenant_id, requirement_type;

CREATE INDEX idx_quality_stats_tenant ON quality_statistics(tenant_id);

-- ============================================================================
-- FUNCTIONS FOR MAINTENANCE
-- ============================================================================

-- Function to update modified timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers for updated_at
CREATE TRIGGER update_requirements_updated_at BEFORE UPDATE ON requirements
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_requirement_embeddings_updated_at BEFORE UPDATE ON requirement_embeddings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_acceptance_criteria_updated_at BEFORE UPDATE ON acceptance_criteria
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- GRANTS (if needed)
-- ============================================================================
-- Grant permissions to application user
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO ghatana;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO ghatana;

-- ============================================================================
-- COMMENTS
-- ============================================================================
COMMENT ON TABLE requirements IS 'Core requirements table - stores all requirement records';
COMMENT ON TABLE requirement_embeddings IS 'Vector embeddings for similarity search';
COMMENT ON TABLE ai_suggestions IS 'AI-generated suggestions for requirements';
COMMENT ON TABLE quality_assessments IS 'Quality scores and assessments';
COMMENT ON TABLE ai_operation_logs IS 'Audit log of all AI operations for monitoring and billing';
COMMENT ON COLUMN requirement_embeddings.vector IS 'pgvector embedding - 1536 dimensions for OpenAI embeddings';

-- ============================================================================
-- INITIAL DATA (optional test data)
-- ============================================================================
-- Uncomment to add test project
-- INSERT INTO requirements (tenant_id, project_id, title, description, requirement_type)
-- VALUES (
--     'tenant-1',
--     uuid_generate_v4(),
--     'Sample Requirement',
--     'This is a sample requirement for testing',
--     'FUNCTIONAL'
-- );

-- ============================================================================
-- PERFORMANCE TUNING
-- ============================================================================
-- Analyze tables for query optimization
ANALYZE requirements;
ANALYZE requirement_embeddings;
ANALYZE ai_suggestions;
ANALYZE quality_assessments;

-- ============================================================================
-- DONE
-- ============================================================================
-- All tables and indexes created successfully
GRANT CONNECT ON DATABASE requirements_ai TO ghatana;
