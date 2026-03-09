-- 0006_policy_simulation.sql
-- Implements storage schema for policy simulation sandbox functionality

-- Policy simulations table for tracking simulation runs
CREATE TABLE IF NOT EXISTS policy_simulations (
    simulation_id String CODEC(ZSTD(1)),
    created_at DateTime64(9, 'UTC') DEFAULT now64() CODEC(DoubleDelta, ZSTD(1)),
    updated_at DateTime64(9, 'UTC') DEFAULT now64() CODEC(DoubleDelta, ZSTD(1)),
    
    -- Simulation metadata
    name String CODEC(ZSTD(1)),
    description String CODEC(ZSTD(1)),
    requested_by String CODEC(ZSTD(1)),
    requested_at DateTime64(9, 'UTC') CODEC(DoubleDelta, ZSTD(1)),
    
    -- Execution details
    status Enum8('pending' = 1, 'running' = 2, 'completed' = 3, 'failed' = 4, 'timeout' = 5, 'cancelled' = 6) CODEC(ZSTD(1)),
    started_at Nullable(DateTime64(9, 'UTC')) CODEC(DoubleDelta, ZSTD(1)),
    completed_at Nullable(DateTime64(9, 'UTC')) CODEC(DoubleDelta, ZSTD(1)),
    duration_ms UInt64 CODEC(ZSTD(1)),
    
    -- Input parameters
    time_range_start DateTime64(9, 'UTC') CODEC(DoubleDelta, ZSTD(1)),
    time_range_end DateTime64(9, 'UTC') CODEC(DoubleDelta, ZSTD(1)),
    policy_bundle String CODEC(ZSTD(1)), -- JSON policy configuration
    event_filters String CODEC(ZSTD(1)), -- JSON event filters
    simulation_config String CODEC(ZSTD(1)), -- JSON simulation config
    
    -- Results summary
    events_processed UInt32 CODEC(ZSTD(1)),
    events_filtered UInt32 CODEC(ZSTD(1)),
    anomalies_detected UInt32 CODEC(ZSTD(1)),
    anomalies_prevented UInt32 CODEC(ZSTD(1)),
    incidents_created UInt32 CODEC(ZSTD(1)),
    incidents_prevented UInt32 CODEC(ZSTD(1)),
    
    -- Performance metrics
    events_per_second Float64 CODEC(Gorilla, ZSTD(1)),
    avg_processing_time_ms Float64 CODEC(Gorilla, ZSTD(1)),
    peak_memory_usage_mb Float64 CODEC(Gorilla, ZSTD(1)),
    database_queries UInt32 CODEC(ZSTD(1)),
    query_time_ms Float64 CODEC(Gorilla, ZSTD(1)),
    
    -- Comparison with baseline
    baseline_incidents UInt32 CODEC(ZSTD(1)),
    simulated_incidents UInt32 CODEC(ZSTD(1)),
    incident_delta Int32 CODEC(ZSTD(1)),
    baseline_anomalies UInt32 CODEC(ZSTD(1)),
    simulated_anomalies UInt32 CODEC(ZSTD(1)),
    anomaly_delta Int32 CODEC(ZSTD(1)),
    
    -- Accuracy metrics
    true_positives UInt32 CODEC(ZSTD(1)),
    false_positives UInt32 CODEC(ZSTD(1)),
    true_negatives UInt32 CODEC(ZSTD(1)),
    false_negatives UInt32 CODEC(ZSTD(1)),
    precision Float64 CODEC(Gorilla, ZSTD(1)),
    recall Float64 CODEC(Gorilla, ZSTD(1)),
    f1_score Float64 CODEC(Gorilla, ZSTD(1)),
    
    -- Impact assessment
    impact_assessment String CODEC(ZSTD(1)),
    
    -- Error tracking
    error_count UInt32 CODEC(ZSTD(1)),
    warning_count UInt32 CODEC(ZSTD(1)),
    errors String CODEC(ZSTD(1)), -- JSON array of errors
    warnings String CODEC(ZSTD(1)), -- JSON array of warnings
    
    -- Indexes for efficient querying
    INDEX idx_simulation_id simulation_id TYPE bloom_filter GRANULARITY 3,
    INDEX idx_requested_by requested_by TYPE bloom_filter GRANULARITY 3,
    INDEX idx_status status TYPE bloom_filter GRANULARITY 3,
    INDEX idx_created_at created_at TYPE minmax GRANULARITY 3,
    INDEX idx_time_range_start time_range_start TYPE minmax GRANULARITY 3,
    INDEX idx_duration duration_ms TYPE minmax GRANULARITY 3
) ENGINE = MergeTree()
PARTITION BY toStartOfDay(created_at)
ORDER BY (requested_by, created_at, simulation_id);

-- Policy effects table for detailed tracking of individual policy impacts
CREATE TABLE IF NOT EXISTS policy_effects (
    effect_id String DEFAULT generateUUIDv4() CODEC(ZSTD(1)),
    simulation_id String CODEC(ZSTD(1)),
    created_at DateTime64(9, 'UTC') DEFAULT now64() CODEC(DoubleDelta, ZSTD(1)),
    
    -- Policy identification
    policy_id String CODEC(ZSTD(1)),
    policy_type Enum8('anomaly_policy' = 1, 'filter_rule' = 2, 'threshold_rule' = 3, 'allowlist' = 4, 'denylist' = 5) CODEC(ZSTD(1)),
    policy_name String CODEC(ZSTD(1)),
    
    -- Effect details
    events_affected UInt32 CODEC(ZSTD(1)),
    action String CODEC(ZSTD(1)), -- allow, deny, modify, flag
    impact_summary String CODEC(ZSTD(1)),
    
    -- Sample events affected
    example_event_ids Array(String) CODEC(ZSTD(1)),
    
    -- Performance impact
    processing_time_ms Float64 CODEC(Gorilla, ZSTD(1)),
    cpu_usage_percent Float64 CODEC(Gorilla, ZSTD(1)),
    
    -- Indexes
    INDEX idx_simulation_id simulation_id TYPE bloom_filter GRANULARITY 3,
    INDEX idx_policy_id policy_id TYPE bloom_filter GRANULARITY 3,
    INDEX idx_policy_type policy_type TYPE bloom_filter GRANULARITY 3,
    INDEX idx_events_affected events_affected TYPE minmax GRANULARITY 3
) ENGINE = MergeTree()
PARTITION BY toStartOfDay(created_at)
ORDER BY (simulation_id, policy_type, events_affected DESC);

-- Simulation events table for storing processed events during simulation (optional, for detailed analysis)
CREATE TABLE IF NOT EXISTS simulation_events (
    event_id String CODEC(ZSTD(1)),
    simulation_id String CODEC(ZSTD(1)),
    original_event_id String CODEC(ZSTD(1)),
    timestamp DateTime64(9, 'UTC') CODEC(DoubleDelta, ZSTD(1)),
    
    -- Event details
    source_type Enum8('agent' = 1, 'extension' = 2, 'desktop' = 3, 'simulation' = 4) CODEC(ZSTD(1)),
    event_type String CODEC(ZSTD(1)),
    device_id String CODEC(ZSTD(1)),
    tenant_id String CODEC(ZSTD(1)),
    
    -- Processing results
    processed Bool CODEC(ZSTD(1)),
    filtered Bool CODEC(ZSTD(1)),
    policies_applied Array(String) CODEC(ZSTD(1)),
    
    -- Event payload (limited for storage efficiency)
    payload_summary String CODEC(ZSTD(1)), -- Key metrics only
    labels String CODEC(ZSTD(1)), -- JSON labels
    
    -- Simulation-specific data
    is_baseline Bool DEFAULT false CODEC(ZSTD(1)),
    simulation_result String CODEC(ZSTD(1)), -- anomaly, incident, normal
    confidence_score Float64 CODEC(Gorilla, ZSTD(1)),
    
    -- Indexes
    INDEX idx_simulation_id simulation_id TYPE bloom_filter GRANULARITY 3,
    INDEX idx_original_id original_event_id TYPE bloom_filter GRANULARITY 3,
    INDEX idx_device_id device_id TYPE bloom_filter GRANULARITY 3,
    INDEX idx_event_type event_type TYPE bloom_filter GRANULARITY 3,
    INDEX idx_timestamp timestamp TYPE minmax GRANULARITY 3,
    INDEX idx_processed processed TYPE bloom_filter GRANULARITY 3
) ENGINE = MergeTree()
PARTITION BY (simulation_id, toStartOfDay(timestamp))
ORDER BY (simulation_id, device_id, timestamp, event_type);

-- Policy bundles table for storing reusable policy configurations
CREATE TABLE IF NOT EXISTS policy_bundles (
    bundle_id String DEFAULT generateUUIDv4() CODEC(ZSTD(1)),
    created_at DateTime64(9, 'UTC') DEFAULT now64() CODEC(DoubleDelta, ZSTD(1)),
    updated_at DateTime64(9, 'UTC') DEFAULT now64() CODEC(DoubleDelta, ZSTD(1)),
    
    -- Bundle metadata
    name String CODEC(ZSTD(1)),
    description String CODEC(ZSTD(1)),
    version String CODEC(ZSTD(1)),
    created_by String CODEC(ZSTD(1)),
    
    -- Policy content
    policy_bundle String CODEC(ZSTD(1)), -- Complete JSON policy bundle
    
    -- Usage tracking
    usage_count UInt32 DEFAULT 0 CODEC(ZSTD(1)),
    last_used_at Nullable(DateTime64(9, 'UTC')) CODEC(DoubleDelta, ZSTD(1)),
    
    -- Status
    status Enum8('draft' = 1, 'active' = 2, 'deprecated' = 3, 'archived' = 4) DEFAULT 'draft' CODEC(ZSTD(1)),
    
    -- Validation
    is_valid Bool DEFAULT true CODEC(ZSTD(1)),
    validation_errors String CODEC(ZSTD(1)), -- JSON array of validation issues
    
    -- Indexes
    INDEX idx_bundle_id bundle_id TYPE bloom_filter GRANULARITY 3,
    INDEX idx_name name TYPE bloom_filter GRANULARITY 3,
    INDEX idx_created_by created_by TYPE bloom_filter GRANULARITY 3,
    INDEX idx_status status TYPE bloom_filter GRANULARITY 3,
    INDEX idx_version version TYPE bloom_filter GRANULARITY 3
) ENGINE = MergeTree()
PARTITION BY toStartOfDay(created_at)
ORDER BY (created_by, status, created_at DESC);

-- Views for simulation analytics

-- Simulation performance summary
CREATE VIEW IF NOT EXISTS simulation_performance_summary AS
SELECT 
    toStartOfDay(created_at) as date,
    requested_by,
    count() as total_simulations,
    countIf(status = 'completed') as completed_simulations,
    countIf(status = 'failed') as failed_simulations,
    countIf(status = 'timeout') as timeout_simulations,
    
    -- Performance metrics
    avg(duration_ms) as avg_duration_ms,
    avg(events_processed) as avg_events_processed,
    avg(events_per_second) as avg_events_per_second,
    avg(peak_memory_usage_mb) as avg_memory_usage_mb,
    
    -- Impact metrics
    avg(incident_delta) as avg_incident_delta,
    avg(anomaly_delta) as avg_anomaly_delta,
    avg(precision) as avg_precision,
    avg(recall) as avg_recall,
    avg(f1_score) as avg_f1_score
    
FROM policy_simulations
GROUP BY date, requested_by
ORDER BY date DESC, requested_by;

-- Policy effectiveness analysis
CREATE VIEW IF NOT EXISTS policy_effectiveness_analysis AS
SELECT 
    pe.policy_type,
    pe.policy_id,
    pe.policy_name,
    count() as usage_count,
    sum(pe.events_affected) as total_events_affected,
    avg(pe.events_affected) as avg_events_per_simulation,
    avg(pe.processing_time_ms) as avg_processing_time,
    
    -- Simulation outcomes
    avg(ps.precision) as avg_precision,
    avg(ps.recall) as avg_recall,
    avg(ps.incident_delta) as avg_incident_impact,
    
    -- Recent usage
    max(pe.created_at) as last_used
    
FROM policy_effects pe
JOIN policy_simulations ps ON pe.simulation_id = ps.simulation_id
WHERE ps.status = 'completed'
GROUP BY pe.policy_type, pe.policy_id, pe.policy_name
ORDER BY total_events_affected DESC, avg_precision DESC;