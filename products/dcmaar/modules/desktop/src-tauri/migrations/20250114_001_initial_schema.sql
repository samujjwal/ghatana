-- Initial database schema for DCMaar Desktop
-- Follows WSRF-DES-003 (WAL/queue pattern) and WSRF-ARCH-001 (contracts first)

-- Enable WAL mode for better concurrency
PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
PRAGMA foreign_keys = ON;

-- Metrics table
CREATE TABLE IF NOT EXISTS metrics (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    metric_id TEXT NOT NULL,
    name TEXT NOT NULL,
    value REAL NOT NULL,
    metric_type TEXT NOT NULL CHECK(metric_type IN ('GAUGE', 'COUNTER', 'HISTOGRAM', 'SUMMARY')),
    unit TEXT,
    labels TEXT, -- JSON
    timestamp INTEGER NOT NULL, -- Unix timestamp in milliseconds
    source TEXT NOT NULL,
    tenant_id TEXT NOT NULL,
    device_id TEXT NOT NULL,
    session_id TEXT NOT NULL,
    schema_version TEXT NOT NULL,
    metadata TEXT, -- JSON
    created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000),
    UNIQUE(metric_id, timestamp)
);

CREATE INDEX idx_metrics_timestamp ON metrics(timestamp DESC);
CREATE INDEX idx_metrics_name ON metrics(name);
CREATE INDEX idx_metrics_source ON metrics(source);
CREATE INDEX idx_metrics_tenant_device ON metrics(tenant_id, device_id);

-- Events table
CREATE TABLE IF NOT EXISTS events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_id TEXT NOT NULL UNIQUE,
    event_type TEXT NOT NULL,
    activity_type TEXT NOT NULL,
    severity TEXT NOT NULL,
    source TEXT NOT NULL,
    application TEXT,
    window_title TEXT,
    duration_ms INTEGER,
    data BLOB, -- Binary event data
    metadata TEXT, -- JSON
    timestamp INTEGER NOT NULL, -- Unix timestamp in milliseconds
    tenant_id TEXT NOT NULL,
    device_id TEXT NOT NULL,
    session_id TEXT NOT NULL,
    schema_version TEXT NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000),
    updated_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000)
);

CREATE INDEX idx_events_timestamp ON events(timestamp DESC);
CREATE INDEX idx_events_type ON events(event_type);
CREATE INDEX idx_events_activity_type ON events(activity_type);
CREATE INDEX idx_events_severity ON events(severity);
CREATE INDEX idx_events_processed ON events(processed);
CREATE INDEX idx_events_tenant_device ON events(tenant_id, device_id);

-- Actions table
CREATE TABLE IF NOT EXISTS actions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    action_id TEXT NOT NULL UNIQUE,
    action_type TEXT NOT NULL,
    status TEXT NOT NULL CHECK(status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    command TEXT NOT NULL,
    args TEXT, -- JSON array
    working_dir TEXT,
    env TEXT, -- JSON object
    timeout_ms INTEGER,
    exit_code INTEGER,
    stdout TEXT,
    stderr TEXT,
    error TEXT,
    duration_ms INTEGER,
    started_at INTEGER,
    completed_at INTEGER,
    metadata TEXT, -- JSON
    created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000),
    updated_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000)
);

CREATE INDEX idx_actions_status ON actions(status);
CREATE INDEX idx_actions_type ON actions(action_type);
CREATE INDEX idx_actions_created_at ON actions(created_at DESC);

-- Agent configurations table
CREATE TABLE IF NOT EXISTS agent_configs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    agent_id TEXT NOT NULL UNIQUE,
    version TEXT NOT NULL,
    config TEXT NOT NULL, -- JSON
    is_active BOOLEAN NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000),
    updated_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000)
);

CREATE INDEX idx_agent_configs_active ON agent_configs(is_active);

-- Sync state table for tracking last sync timestamps
CREATE TABLE IF NOT EXISTS sync_state (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    source TEXT NOT NULL UNIQUE,
    last_sync_at INTEGER NOT NULL,
    last_metric_timestamp INTEGER,
    last_event_timestamp INTEGER,
    status TEXT NOT NULL CHECK(status IN ('IDLE', 'SYNCING', 'ERROR')),
    error_message TEXT,
    metadata TEXT, -- JSON
    created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000),
    updated_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000)
);

-- Queue table for failed operations (WAL pattern per WSRF-DES-003)
CREATE TABLE IF NOT EXISTS operation_queue (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    operation_type TEXT NOT NULL CHECK(operation_type IN ('METRIC', 'EVENT', 'ACTION', 'CONFIG')),
    operation_data TEXT NOT NULL, -- JSON
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    next_retry_at INTEGER,
    last_error TEXT,
    status TEXT NOT NULL CHECK(status IN ('PENDING', 'PROCESSING', 'FAILED', 'COMPLETED')),
    created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000),
    updated_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000)
);

CREATE INDEX idx_queue_status ON operation_queue(status);
CREATE INDEX idx_queue_next_retry ON operation_queue(next_retry_at);

-- Audit log table for compliance (WSRF-SEC-003)
CREATE TABLE IF NOT EXISTS audit_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_type TEXT NOT NULL,
    actor TEXT NOT NULL,
    action TEXT NOT NULL,
    resource TEXT,
    result TEXT NOT NULL CHECK(result IN ('SUCCESS', 'FAILURE', 'DENIED')),
    details TEXT, -- JSON
    ip_address TEXT,
    user_agent TEXT,
    timestamp INTEGER NOT NULL DEFAULT (strftime('%s', 'now') * 1000)
);

CREATE INDEX idx_audit_timestamp ON audit_log(timestamp DESC);
CREATE INDEX idx_audit_actor ON audit_log(actor);
CREATE INDEX idx_audit_action ON audit_log(action);

-- Triggers for updated_at timestamps
CREATE TRIGGER IF NOT EXISTS update_events_timestamp 
AFTER UPDATE ON events
BEGIN
    UPDATE events SET updated_at = strftime('%s', 'now') * 1000 WHERE id = NEW.id;
END;

CREATE TRIGGER IF NOT EXISTS update_actions_timestamp 
AFTER UPDATE ON actions
BEGIN
    UPDATE actions SET updated_at = strftime('%s', 'now') * 1000 WHERE id = NEW.id;
END;

CREATE TRIGGER IF NOT EXISTS update_agent_configs_timestamp 
AFTER UPDATE ON agent_configs
BEGIN
    UPDATE agent_configs SET updated_at = strftime('%s', 'now') * 1000 WHERE id = NEW.id;
END;

CREATE TRIGGER IF NOT EXISTS update_sync_state_timestamp 
AFTER UPDATE ON sync_state
BEGIN
    UPDATE sync_state SET updated_at = strftime('%s', 'now') * 1000 WHERE id = NEW.id;
END;

CREATE TRIGGER IF NOT EXISTS update_operation_queue_timestamp 
AFTER UPDATE ON operation_queue
BEGIN
    UPDATE operation_queue SET updated_at = strftime('%s', 'now') * 1000 WHERE id = NEW.id;
END;
