-- NLQ Audit Table Schema
-- This table stores audit information for Natural Language Queries
CREATE TABLE IF NOT EXISTS nlq_audit (
    query_id String,
    user_id String,
    original_text String,
    generated_sql String,
    execution_time_ms UInt64,
    row_count UInt32,
    success Bool,
    error_message Nullable(String),
    timestamp DateTime DEFAULT now(),
    -- Additional metadata
    parser_confidence Float32 DEFAULT 0.0,
    query_intent String DEFAULT '',
    tables_accessed Array(String) DEFAULT [],
    functions_used Array(String) DEFAULT [],
    safety_violations Array(String) DEFAULT [],
    -- Performance tracking
    parse_time_ms UInt32 DEFAULT 0,
    generation_time_ms UInt32 DEFAULT 0,
    validation_time_ms UInt32 DEFAULT 0
) ENGINE = MergeTree()
ORDER BY (timestamp, user_id)
PARTITION BY toDate(timestamp)
TTL timestamp + INTERVAL 90 DAY DELETE;

-- Index for user queries
CREATE INDEX IF NOT EXISTS idx_nlq_user_id ON nlq_audit (user_id) TYPE bloom_filter GRANULARITY 1;

-- Index for query success/failure analysis
CREATE INDEX IF NOT EXISTS idx_nlq_success ON nlq_audit (success) TYPE bloom_filter GRANULARITY 1;

-- Sample queries for testing NLQ capabilities
INSERT INTO nlq_audit (query_id, user_id, original_text, generated_sql, execution_time_ms, row_count, success, timestamp) VALUES
('test-001', 'test-user', 'show error rate for service-api in last 24 hours', 'SELECT timestamp, error_rate FROM events WHERE service = ''service-api'' AND timestamp >= now() - INTERVAL 24 HOUR LIMIT 1000', 145, 324, true, now() - INTERVAL 1 DAY),
('test-002', 'test-user', 'average response time grouped by service in last week', 'SELECT service, avg(response_time) FROM metrics WHERE timestamp >= now() - INTERVAL 7 DAY GROUP BY service LIMIT 1000', 892, 12, true, now() - INTERVAL 2 DAY),
('test-003', 'test-user', 'cpu usage above 80% for production hosts', 'SELECT timestamp, host, cpu_usage FROM metrics WHERE cpu_usage > 0.8 AND environment = ''production'' ORDER BY timestamp DESC LIMIT 1000', 234, 67, true, now() - INTERVAL 3 DAY);