-- Flyway Migration: V1__Initial_YAPPC_Schema.sql
-- YAPPC Agentic Platform — Core Entity Tables
-- Created: 2026-03-15
-- Purpose: Initialize PostgreSQL schema for YAPPC platform persistence layer

-- ================================================
-- Schema: yappc (YAPPC platform namespace)
-- ================================================

CREATE SCHEMA IF NOT EXISTS yappc;

-- ================================================
-- 1. AGENT REGISTRY & HEALTH MONITORING
-- ================================================

-- yappc.agent_registry — Central registry of all YAPPC agents
-- Schema: (id, agent_id, name, agent_type, capabilities, tenant_id, version, config, last_heartbeat, created_at, updated_at)
CREATE TABLE IF NOT EXISTS yappc.agent_registry (
    id TEXT PRIMARY KEY,
    agent_id TEXT NOT NULL,
    name TEXT NOT NULL,
    agent_type TEXT NOT NULL,
    capabilities TEXT NOT NULL,  -- JSON array: ["capability1", "capability2"]
    tenant_id TEXT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    config JSONB,
    last_heartbeat TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agent_registry_tenant_id ON yappc.agent_registry(tenant_id);
CREATE INDEX IF NOT EXISTS idx_agent_registry_agent_id ON yappc.agent_registry(agent_id, tenant_id);
CREATE INDEX IF NOT EXISTS idx_agent_registry_agent_type ON yappc.agent_registry(agent_type, tenant_id);


-- yappc.agent_health_events — Historical health monitoring events (for audit)
CREATE TABLE IF NOT EXISTS yappc.agent_health_events (
    id TEXT PRIMARY KEY,
    agent_id TEXT NOT NULL,
    tenant_id TEXT NOT NULL,
    status TEXT NOT NULL,  -- RUNNING, FAILED, STARTING, STOPPING
    failure_count INT DEFAULT 0,
    alert_triggered BOOLEAN DEFAULT FALSE,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agent_health_events_tenant_agent ON yappc.agent_health_events(tenant_id, agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_health_events_status ON yappc.agent_health_events(status, recorded_at);

-- ================================================
-- 2. EXECUTION HISTORY & TASK STATE
-- ================================================

-- yappc.task_states — Agent task execution state and history
-- Schema: (id, turn_id, agent_id, input, output, duration_ms, reflection, tenant_id, created_at)
CREATE TABLE IF NOT EXISTS yappc.task_states (
    id TEXT PRIMARY KEY,
    turn_id TEXT NOT NULL,
    agent_id TEXT NOT NULL,
    input TEXT,
    output TEXT,
    duration_ms BIGINT,
    reflection TEXT,
    tenant_id TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_task_states_tenant_agent ON yappc.task_states(tenant_id, agent_id);
CREATE INDEX IF NOT EXISTS idx_task_states_turn_id ON yappc.task_states(turn_id, tenant_id);
CREATE INDEX IF NOT EXISTS idx_task_states_created_at ON yappc.task_states(created_at DESC);

-- ================================================
-- 3. PROCEDURAL MEMORY & POLICY LEARNING
-- ================================================

-- yappc.learned_policies — Learned procedural policies with confidence scores
-- Schema: (id, agent_id, name, description, procedure, confidence, source, version, tenant_id, created_at, updated_at)
CREATE TABLE IF NOT EXISTS yappc.learned_policies (
    id TEXT PRIMARY KEY,
    agent_id TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    procedure TEXT NOT NULL,  -- JSON serialized EnhancedProcedure
    confidence FLOAT NOT NULL DEFAULT 0.0,
    source TEXT,
    version INT DEFAULT 1,
    tenant_id TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_learned_policies_tenant_agent ON yappc.learned_policies(tenant_id, agent_id);
CREATE INDEX IF NOT EXISTS idx_learned_policies_confidence ON yappc.learned_policies(tenant_id, confidence DESC);
CREATE INDEX IF NOT EXISTS idx_learned_policies_created_at ON yappc.learned_policies(created_at DESC);

-- ================================================
-- 4. LIFECYCLE & PHASE MANAGEMENT
-- ================================================

-- yappc.lifecycle_transitions — Recorded phase transitions for workflows
-- Schema: (id, workflow_id, from_phase, to_phase, triggered_by, reason, timestamp, tenant_id)
CREATE TABLE IF NOT EXISTS yappc.lifecycle_transitions (
    id TEXT PRIMARY KEY,
    workflow_id TEXT NOT NULL,
    from_phase TEXT NOT NULL,
    to_phase TEXT NOT NULL,
    triggered_by TEXT,  -- e.g., "agent-1", "system", "user"
    reason TEXT,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    tenant_id TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_lifecycle_transitions_tenant_workflow ON yappc.lifecycle_transitions(tenant_id, workflow_id);
CREATE INDEX IF NOT EXISTS idx_lifecycle_transitions_phases ON yappc.lifecycle_transitions(from_phase, to_phase);
CREATE INDEX IF NOT EXISTS idx_lifecycle_transitions_timestamp ON yappc.lifecycle_transitions(timestamp DESC);

-- ================================================
-- 5. CONFIGURATION & POLICIES
-- ================================================

-- yappc.policy_configurations — Hot-reloadable policy definitions
-- Schema: (id, version, description, rules, enabled, tenant_id, created_at)
CREATE TABLE IF NOT EXISTS yappc.policy_configurations (
    id TEXT PRIMARY KEY,
    version INT NOT NULL,
    description TEXT,
    rules JSONB NOT NULL,  -- Array of rule objects
    enabled BOOLEAN DEFAULT TRUE,
    tenant_id TEXT,  -- NULL for global policies
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_policy_configurations_enabled ON yappc.policy_configurations(enabled);
CREATE INDEX IF NOT EXISTS idx_policy_configurations_tenant ON yappc.policy_configurations(tenant_id);

-- yappc.policy_reload_log — Audit log for policy hot-reloads
CREATE TABLE IF NOT EXISTS yappc.policy_reload_log (
    id TEXT PRIMARY KEY,
    policy_id TEXT NOT NULL,
    old_version INT,
    new_version INT,
    reload_timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    status TEXT,  -- SUCCESS, FAILURE
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_policy_reload_log_policy_id ON yappc.policy_reload_log(policy_id);
CREATE INDEX IF NOT EXISTS idx_policy_reload_log_timestamp ON yappc.policy_reload_log(reload_timestamp DESC);

-- ================================================
-- 6. AUDIT & OBSERVABILITY
-- ================================================

-- yappc.audit_events — Comprehensive audit trail
-- Schema: (id, entity_type, entity_id, action, actor_id, changes, tenant_id, timestamp)
CREATE TABLE IF NOT EXISTS yappc.audit_events (
    id TEXT PRIMARY KEY,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    action TEXT NOT NULL,  -- CREATE, UPDATE, DELETE
    actor_id TEXT,
    changes JSONB,
    tenant_id TEXT NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_events_tenant_entity ON yappc.audit_events(tenant_id, entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_events_timestamp ON yappc.audit_events(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_audit_events_action ON yappc.audit_events(action, timestamp DESC);

-- ================================================
-- 7. DLQ (DEAD LETTER QUEUE) FOR FAILED OPERATIONS
-- ================================================

-- yappc.dlq_events — Failed events for retry/analysis
-- Schema: (id, event_type, payload, error_reason, retry_count, tenant_id, created_at)
CREATE TABLE IF NOT EXISTS yappc.dlq_events (
    id TEXT PRIMARY KEY,
    event_type TEXT NOT NULL,
    payload JSONB NOT NULL,
    error_reason TEXT,
    retry_count INT DEFAULT 0,
    tenant_id TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dlq_events_tenant_event_type ON yappc.dlq_events(tenant_id, event_type);
CREATE INDEX IF NOT EXISTS idx_dlq_events_created_at ON yappc.dlq_events(created_at ASC);
CREATE INDEX IF NOT EXISTS idx_dlq_events_retry_count ON yappc.dlq_events(retry_count);

-- ================================================
-- 8. WORKFLOW STATE & CHECKPOINTS
-- ================================================

-- yappc.durable_workflow_state — State snapshots for checkpoint recovery
-- Schema: (id, workflow_id, state_json, last_checkpoint, tenant_id, created_at)
CREATE TABLE IF NOT EXISTS yappc.durable_workflow_state (
    id TEXT PRIMARY KEY,
    workflow_id TEXT NOT NULL,
    state_json JSONB NOT NULL,
    last_checkpoint TIMESTAMP WITH TIME ZONE,
    tenant_id TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_durable_workflow_state_tenant_workflow ON yappc.durable_workflow_state(tenant_id, workflow_id);

-- ================================================
-- 9. OUTBOX FOR RELIABLE EVENT PUBLISHING
-- ================================================

-- yappc.outbox_events — Transactional outbox for reliable event publishing
-- Schema: (id, event_type, payload, published, published_at, created_at)
CREATE TABLE IF NOT EXISTS yappc.outbox_events (
    id TEXT PRIMARY KEY,
    event_type TEXT NOT NULL,
    payload JSONB NOT NULL,
    published BOOLEAN DEFAULT FALSE,
    published_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_outbox_events_published ON yappc.outbox_events(published, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_events_event_type ON yappc.outbox_events(event_type, published);

-- ================================================
-- 10. MEMORY & EPISODIC STORAGE
-- ================================================

-- yappc.memory_items — Episodic, semantic, and procedural memory storage
-- Schema: (id, memory_type, content, confidence, tenant_id, agent_id, created_at)
CREATE TABLE IF NOT EXISTS yappc.memory_items (
    id TEXT PRIMARY KEY,
    memory_type TEXT NOT NULL,  -- EPISODIC, SEMANTIC, PROCEDURAL
    content JSONB NOT NULL,
    confidence FLOAT DEFAULT 1.0,
    tenant_id TEXT NOT NULL,
    agent_id TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_memory_items_tenant_type ON yappc.memory_items(tenant_id, memory_type);
CREATE INDEX IF NOT EXISTS idx_memory_items_agent_id ON yappc.memory_items(tenant_id, agent_id, memory_type);
CREATE INDEX IF NOT EXISTS idx_memory_items_created_at ON yappc.memory_items(created_at DESC);

-- ================================================
-- GRANTS & PERMISSIONS
-- ================================================

-- Grant access to yampc application user if exists
-- ALTER ROLE yappc_app WITH PASSWORD 'xxx';  -- Set in environment
-- GRANT USAGE ON SCHEMA yappc TO yappc_app;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA yappc TO yappc_app;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA yappc TO yappc_app;

-- ================================================
-- End of V1__Initial_YAPPC_Schema.sql
-- ================================================
