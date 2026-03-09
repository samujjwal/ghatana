-- 0004_correlation_tables.sql
-- Implements the cross-source correlation storage schema for correlating
-- anomaly events from agents with latency events from extensions

-- Correlated incidents table
CREATE TABLE IF NOT EXISTS correlated_incidents (
    incident_id String CODEC(ZSTD(1)),
    created_at DateTime64(9, 'UTC') CODEC(DoubleDelta, ZSTD(1)),
    updated_at DateTime64(9, 'UTC') CODEC(DoubleDelta, ZSTD(1)),
    tenant_id String CODEC(ZSTD(1)),
    device_id String CODEC(ZSTD(1)),
    host_id String CODEC(ZSTD(1)),
    
    -- Time window for correlation
    window_start DateTime64(9, 'UTC') CODEC(DoubleDelta, ZSTD(1)),
    window_end DateTime64(9, 'UTC') CODEC(DoubleDelta, ZSTD(1)),
    
    -- Correlation metadata
    correlation_score Float64 CODEC(Gorilla, ZSTD(1)),
    confidence Float64 CODEC(Gorilla, ZSTD(1)),
    incident_type Enum8('performance' = 1, 'availability' = 2, 'resource' = 3, 'mixed' = 4) CODEC(ZSTD(1)),
    severity Enum8('low' = 1, 'medium' = 2, 'high' = 3, 'critical' = 4) CODEC(ZSTD(1)),
    
    -- Source event references
    anomaly_event_ids Array(String) CODEC(ZSTD(1)),
    web_event_ids Array(String) CODEC(ZSTD(1)),
    
    -- Aggregated metrics
    anomaly_count UInt32 CODEC(ZSTD(1)),
    web_event_count UInt32 CODEC(ZSTD(1)),
    avg_cpu_anomaly_score Float64 CODEC(Gorilla, ZSTD(1)),
    avg_memory_anomaly_score Float64 CODEC(Gorilla, ZSTD(1)),
    avg_latency_ms Float64 CODEC(Gorilla, ZSTD(1)),
    affected_domains Array(String) CODEC(ZSTD(1)),
    
    -- Status tracking
    status Enum8('active' = 1, 'resolved' = 2, 'investigating' = 3, 'acknowledged' = 4) CODEC(ZSTD(1)),
    resolved_at Nullable(DateTime64(9, 'UTC')) CODEC(DoubleDelta, ZSTD(1)),
    
    -- Additional context
    labels Map(String, String) CODEC(ZSTD(1)),
    
    -- Indexes for efficient querying
    INDEX idx_tenant_id tenant_id TYPE bloom_filter GRANULARITY 3,
    INDEX idx_device_id device_id TYPE bloom_filter GRANULARITY 3,
    INDEX idx_host_id host_id TYPE bloom_filter GRANULARITY 3,
    INDEX idx_incident_type incident_type TYPE bloom_filter GRANULARITY 3,
    INDEX idx_severity severity TYPE bloom_filter GRANULARITY 3,
    INDEX idx_status status TYPE bloom_filter GRANULARITY 3,
    INDEX idx_created_at created_at TYPE minmax GRANULARITY 3,
    INDEX idx_window_start window_start TYPE minmax GRANULARITY 3,
    INDEX idx_correlation_score correlation_score TYPE minmax GRANULARITY 3
) ENGINE = MergeTree()
PARTITION BY toStartOfDay(created_at)
ORDER BY (tenant_id, device_id, toStartOfDay(created_at), incident_type, severity);

-- Correlation events table for detailed event association
CREATE TABLE IF NOT EXISTS correlation_events (
    correlation_id String CODEC(ZSTD(1)),
    incident_id String CODEC(ZSTD(1)),
    event_id String CODEC(ZSTD(1)),
    event_timestamp DateTime64(9, 'UTC') CODEC(DoubleDelta, ZSTD(1)),
    event_source Enum8('anomaly' = 1, 'web' = 2) CODEC(ZSTD(1)),
    event_type String CODEC(ZSTD(1)),
    correlation_weight Float64 CODEC(Gorilla, ZSTD(1)),
    
    -- Event-specific fields (stored as JSON for flexibility)
    event_payload String CODEC(ZSTD(1)),
    
    -- Indexes
    INDEX idx_incident_id incident_id TYPE bloom_filter GRANULARITY 3,
    INDEX idx_event_id event_id TYPE bloom_filter GRANULARITY 3,
    INDEX idx_event_source event_source TYPE bloom_filter GRANULARITY 3,
    INDEX idx_event_timestamp event_timestamp TYPE minmax GRANULARITY 3
) ENGINE = MergeTree()
PARTITION BY toStartOfDay(event_timestamp)
ORDER BY (incident_id, event_timestamp, event_source);

-- View for quick incident summaries
CREATE VIEW IF NOT EXISTS incident_summary AS
SELECT 
    incident_id,
    created_at,
    tenant_id,
    device_id,
    host_id,
    incident_type,
    severity,
    status,
    correlation_score,
    confidence,
    anomaly_count,
    web_event_count,
    avg_latency_ms,
    affected_domains,
    window_start,
    window_end,
    dateDiff('second', window_start, window_end) as duration_seconds
FROM correlated_incidents
ORDER BY created_at DESC;