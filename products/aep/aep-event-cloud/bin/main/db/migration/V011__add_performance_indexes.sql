-- Flyway V011: Database Performance Indexes for AEP Query Optimization (AEP-004.1)
-- Purpose: Add composite performance indexes to support high-frequency query patterns
-- Target: Query execution time <100ms for 95% of queries under production load
-- Generated: 2026-04-06

-- ============================================================================
-- pipeline_checkpoints — optimize tenant + status lookups (most frequent pattern)
-- ============================================================================

-- Index: tenant+status for active pipeline monitoring queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pipeline_checkpoints_tenant_status
    ON pipeline_checkpoints (tenant_id, status)
    WHERE status IN ('RUNNING', 'STEP_SUCCESS', 'STEP_FAILED');

-- Index: tenant+created_at for time-range dashboard queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pipeline_checkpoints_tenant_created_at
    ON pipeline_checkpoints (tenant_id, created_at DESC);

-- Index: pipeline_id for pipeline-specific status queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pipeline_checkpoints_pipeline_id
    ON pipeline_checkpoints (pipeline_id, tenant_id, status);

-- Index: idempotency_key for deduplication lookups (write path)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pipeline_checkpoints_idempotency_key
    ON pipeline_checkpoints (idempotency_key)
    WHERE status NOT IN ('COMPLETED', 'CANCELLED', 'FAILED');

-- ============================================================================
-- aep_event_checkpoints — checkpoint read/write hot path (AEP-004.3)
-- ============================================================================

-- Index: tenant+operator+window for checkpoint restore
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_aep_event_checkpoints_tenant_operator_window
    ON aep_event_checkpoints (tenant_id, operator_id, window_id);

-- Index: expires_at for expired checkpoint cleanup job
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_aep_event_checkpoints_expires_at
    ON aep_event_checkpoints (expires_at)
    WHERE expires_at IS NOT NULL;

-- ============================================================================
-- patterns — pattern evaluation hot path
-- ============================================================================

-- Index: tenant+enabled for active pattern evaluation
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_patterns_tenant_enabled
    ON patterns (tenant_id, enabled)
    WHERE enabled = true;

-- Index: pattern_type for operator-specific queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_patterns_tenant_type
    ON patterns (tenant_id, pattern_type);

-- ============================================================================
-- agent_registry — agent lookup hot path
-- ============================================================================

-- Index: status+tenant for active agent queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_agent_registry_status_tenant
    ON agent_registry (status, tenant_id)
    WHERE status = 'ACTIVE';

-- Index: agent_type for type-based routing
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_agent_registry_type_tenant
    ON agent_registry (agent_type, tenant_id, status);

-- ============================================================================
-- pipeline_registry — pipeline resolution hot path
-- ============================================================================

-- Index: tenant+status for active pipeline lookup
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_pipeline_registry_tenant_status
    ON pipeline_registry (tenant_id, status)
    WHERE status = 'ACTIVE';

-- ============================================================================
-- audit_trail — compliance query hot path
-- ============================================================================

-- Index: tenant+timestamp for time-bounded compliance queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_trail_tenant_timestamp
    ON audit_trail (tenant_id, event_timestamp DESC);

-- Index: event_type for type-filtered compliance queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_trail_event_type_tenant
    ON audit_trail (event_type, tenant_id, event_timestamp DESC);

