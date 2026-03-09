-- Migration 0007: Drift Monitoring Tables
-- This migration creates comprehensive drift monitoring infrastructure
-- for tracking policy performance and detecting configuration drift over time

-- Policy Drift Metrics: Store performance metrics for each policy over time
CREATE TABLE IF NOT EXISTS policy_drift_metrics (
    policy_id String,
    policy_version String,
    measurement_time DateTime,
    window_start DateTime,
    window_end DateTime,
    
    -- Performance metrics
    accuracy Float64,
    precision Float64,
    recall Float64,
    f1_score Float64,
    volume UInt64,
    
    -- Drift indicators
    drift_score Float64,
    drift_status Enum8('healthy' = 1, 'warning' = 2, 'critical' = 3, 'unknown' = 4),
    
    -- Analysis metadata
    baseline_period String,
    measurement_source String DEFAULT 'automated',
    confidence Float64,
    
    -- Event aggregations
    true_positives UInt64,
    false_positives UInt64,
    true_negatives UInt64,
    false_negatives UInt64,
    
    -- Temporal context
    created_at DateTime DEFAULT now(),
    tenant_id String,
    environment String DEFAULT 'production'
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(measurement_time)
ORDER BY (policy_id, measurement_time, tenant_id)
TTL measurement_time + INTERVAL 90 DAY
SETTINGS index_granularity = 8192;

-- Drift Alerts: Track all drift detection alerts and their lifecycle
CREATE TABLE IF NOT EXISTS drift_alerts (
    alert_id String,
    policy_id String,
    alert_type String,
    severity Enum8('low' = 1, 'medium' = 2, 'high' = 3, 'critical' = 4),
    
    -- Alert content
    title String,
    description String,
    drift_score Float64,
    affected_metrics Array(String),
    drift_reasons Array(String),
    
    -- Lifecycle tracking
    status Enum8('open' = 1, 'acknowledged' = 2, 'resolved' = 3, 'suppressed' = 4),
    acknowledged_by String,
    acknowledged_at Nullable(DateTime),
    resolved_by String,
    resolved_at Nullable(DateTime),
    resolution_notes String,
    
    -- Notification tracking
    slack_sent Bool DEFAULT false,
    email_sent Bool DEFAULT false,
    last_notified Nullable(DateTime),
    notification_count UInt32 DEFAULT 0,
    
    -- Context
    tenant_id String,
    environment String DEFAULT 'production',
    created_at DateTime DEFAULT now(),
    updated_at DateTime DEFAULT now()
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(created_at)
ORDER BY (policy_id, created_at, alert_id)
TTL created_at + INTERVAL 180 DAY
SETTINGS index_granularity = 8192;

-- Baseline Statistics: Store baseline performance for comparison
CREATE TABLE IF NOT EXISTS policy_baselines (
    policy_id String,
    baseline_version String,
    training_period_start DateTime,
    training_period_end DateTime,
    
    -- Statistical baselines
    mean_accuracy Float64,
    std_accuracy Float64,
    mean_precision Float64,
    std_precision Float64,
    mean_recall Float64,
    std_recall Float64,
    mean_f1 Float64,
    std_f1 Float64,
    mean_volume Float64,
    std_volume Float64,
    
    -- Distribution characteristics
    accuracy_percentiles Array(Float64), -- 5th, 25th, 50th, 75th, 95th percentiles
    volume_percentiles Array(Float64),
    
    -- Seasonality patterns
    hourly_patterns Array(Float64), -- 24 values for each hour
    daily_patterns Array(Float64),  -- 7 values for each day of week
    monthly_patterns Array(Float64), -- 12 values for each month
    
    -- Correlation matrix (flattened)
    metric_correlations Map(String, Float64),
    
    -- Metadata
    sample_size UInt64,
    confidence_level Float64 DEFAULT 0.95,
    calculation_method String DEFAULT 'statistical',
    
    -- Lifecycle
    created_at DateTime DEFAULT now(),
    updated_at DateTime DEFAULT now(),
    tenant_id String,
    environment String DEFAULT 'production',
    is_active Bool DEFAULT true
)
ENGINE = ReplacingMergeTree(updated_at)
PARTITION BY policy_id
ORDER BY (policy_id, baseline_version, tenant_id)
SETTINGS index_granularity = 8192;

-- Drift Events: Detailed event-level drift analysis
CREATE TABLE IF NOT EXISTS drift_events (
    event_id String,
    policy_id String,
    drift_type String, -- 'performance', 'volume', 'pattern', 'novelty'
    detection_time DateTime,
    
    -- Event details
    metric_name String,
    baseline_value Float64,
    current_value Float64,
    deviation_score Float64,
    statistical_significance Float64,
    
    -- Context
    window_size_hours UInt32,
    comparison_period String,
    affected_devices Array(String),
    affected_tenants Array(String),
    
    -- Analysis
    trend_direction Enum8('improving' = 1, 'stable' = 2, 'declining' = 3),
    seasonality_adjusted Bool DEFAULT false,
    novelty_score Float64,
    confidence Float64,
    
    -- Metadata
    tenant_id String,
    environment String DEFAULT 'production',
    created_at DateTime DEFAULT now()
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(detection_time)
ORDER BY (policy_id, detection_time, drift_type)
TTL detection_time + INTERVAL 60 DAY
SETTINGS index_granularity = 8192;

-- Recommended Actions: Store and track recommended remediation actions
CREATE TABLE IF NOT EXISTS drift_recommendations (
    recommendation_id String,
    alert_id String,
    policy_id String,
    
    -- Action details
    action_type String,
    priority Enum8('low' = 1, 'medium' = 2, 'high' = 3, 'critical' = 4),
    title String,
    description String,
    impact_description String,
    effort_estimate String,
    
    -- Execution tracking
    status Enum8('pending' = 1, 'approved' = 2, 'in_progress' = 3, 'completed' = 4, 'rejected' = 5),
    assigned_to String,
    deadline DateTime,
    auto_approved Bool DEFAULT false,
    execution_notes String,
    
    -- Results tracking
    executed_at Nullable(DateTime),
    execution_duration_minutes Nullable(UInt32),
    success_rate Float64,
    impact_measured Float64,
    rollback_available Bool DEFAULT false,
    
    -- Context
    tenant_id String,
    environment String DEFAULT 'production',
    created_at DateTime DEFAULT now(),
    updated_at DateTime DEFAULT now()
)
ENGINE = ReplacingMergeTree(updated_at)
PARTITION BY toYYYYMM(created_at)
ORDER BY (policy_id, alert_id, recommendation_id)
TTL created_at + INTERVAL 365 DAY
SETTINGS index_granularity = 8192;

-- Drift Monitor Status: Track monitoring service health and performance
CREATE TABLE IF NOT EXISTS drift_monitor_status (
    monitor_instance String,
    check_time DateTime,
    
    -- Performance metrics
    checks_performed UInt64,
    policies_monitored UInt32,
    alerts_generated UInt32,
    processing_duration_ms Float64,
    memory_usage_mb Float64,
    
    -- Health indicators
    health_score Float64, -- 0-1, higher is better
    error_rate Float64,
    success_rate Float64,
    avg_response_time_ms Float64,
    
    -- Resource utilization
    cpu_usage Float64,
    database_connections UInt32,
    concurrent_checks UInt32,
    queue_depth UInt32,
    
    -- Configuration
    check_interval_minutes UInt32,
    window_size_hours UInt32,
    alert_threshold Float64,
    max_concurrent_checks UInt32,
    
    -- Context
    version String,
    environment String DEFAULT 'production',
    tenant_id String,
    created_at DateTime DEFAULT now()
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(check_time)
ORDER BY (monitor_instance, check_time)
TTL check_time + INTERVAL 30 DAY
SETTINGS index_granularity = 8192;

-- Materialized Views for Analytics

-- Policy Health Dashboard: Real-time policy health overview
CREATE MATERIALIZED VIEW IF NOT EXISTS policy_health_dashboard_mv
TO policy_health_dashboard
AS SELECT
    policy_id,
    tenant_id,
    environment,
    argMax(drift_status, measurement_time) as current_status,
    argMax(accuracy, measurement_time) as current_accuracy,
    argMax(precision, measurement_time) as current_precision,
    argMax(recall, measurement_time) as current_recall,
    argMax(drift_score, measurement_time) as current_drift_score,
    count() as measurement_count,
    max(measurement_time) as last_measured,
    avg(accuracy) as avg_accuracy_24h,
    min(accuracy) as min_accuracy_24h,
    max(accuracy) as max_accuracy_24h
FROM policy_drift_metrics
WHERE measurement_time >= now() - INTERVAL 24 HOUR
GROUP BY policy_id, tenant_id, environment;

CREATE TABLE IF NOT EXISTS policy_health_dashboard (
    policy_id String,
    tenant_id String,
    environment String,
    current_status Enum8('healthy' = 1, 'warning' = 2, 'critical' = 3, 'unknown' = 4),
    current_accuracy Float64,
    current_precision Float64,
    current_recall Float64,
    current_drift_score Float64,
    measurement_count UInt64,
    last_measured DateTime,
    avg_accuracy_24h Float64,
    min_accuracy_24h Float64,
    max_accuracy_24h Float64
)
ENGINE = SummingMergeTree()
ORDER BY (policy_id, tenant_id, environment);

-- Alert Summary: Hourly alert aggregations
CREATE MATERIALIZED VIEW IF NOT EXISTS alert_summary_mv
TO alert_summary
AS SELECT
    toStartOfHour(created_at) as hour,
    policy_id,
    severity,
    tenant_id,
    environment,
    count() as alert_count,
    countIf(status = 'resolved') as resolved_count,
    countIf(status = 'open') as open_count,
    avg(drift_score) as avg_drift_score,
    max(drift_score) as max_drift_score
FROM drift_alerts
GROUP BY hour, policy_id, severity, tenant_id, environment;

CREATE TABLE IF NOT EXISTS alert_summary (
    hour DateTime,
    policy_id String,
    severity Enum8('low' = 1, 'medium' = 2, 'high' = 3, 'critical' = 4),
    tenant_id String,
    environment String,
    alert_count UInt64,
    resolved_count UInt64,
    open_count UInt64,
    avg_drift_score Float64,
    max_drift_score Float64
)
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(hour)
ORDER BY (hour, policy_id, severity);

-- Performance Trends: Policy performance over time
CREATE MATERIALIZED VIEW IF NOT EXISTS performance_trends_mv
TO performance_trends
AS SELECT
    toStartOfHour(measurement_time) as hour,
    policy_id,
    tenant_id,
    environment,
    avg(accuracy) as avg_accuracy,
    avg(precision) as avg_precision,
    avg(recall) as avg_recall,
    avg(f1_score) as avg_f1_score,
    sum(volume) as total_volume,
    avg(drift_score) as avg_drift_score,
    count() as sample_count
FROM policy_drift_metrics
GROUP BY hour, policy_id, tenant_id, environment;

CREATE TABLE IF NOT EXISTS performance_trends (
    hour DateTime,
    policy_id String,
    tenant_id String,
    environment String,
    avg_accuracy Float64,
    avg_precision Float64,
    avg_recall Float64,
    avg_f1_score Float64,
    total_volume UInt64,
    avg_drift_score Float64,
    sample_count UInt64
)
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(hour)
ORDER BY (hour, policy_id, tenant_id);

-- Indexes for improved query performance
-- These are automatically created with ORDER BY, but explicit indexes for specific queries

-- Index for policy lookup by status
ALTER TABLE policy_drift_metrics ADD INDEX idx_drift_status (drift_status) TYPE set(100) GRANULARITY 1;

-- Index for alert status queries
ALTER TABLE drift_alerts ADD INDEX idx_alert_status (status) TYPE set(10) GRANULARITY 1;

-- Index for severity-based queries
ALTER TABLE drift_alerts ADD INDEX idx_severity (severity) TYPE set(10) GRANULARITY 1;

-- Index for time-based performance queries
ALTER TABLE policy_drift_metrics ADD INDEX idx_measurement_time (measurement_time) TYPE minmax GRANULARITY 1;

-- Index for drift score analysis
ALTER TABLE policy_drift_metrics ADD INDEX idx_drift_score (drift_score) TYPE minmax GRANULARITY 1;

-- Comments for documentation
ALTER TABLE policy_drift_metrics COMMENT 'Stores policy performance metrics over time for drift detection';
ALTER TABLE drift_alerts COMMENT 'Tracks drift detection alerts and their resolution lifecycle';
ALTER TABLE policy_baselines COMMENT 'Maintains baseline performance statistics for drift comparison';
ALTER TABLE drift_events COMMENT 'Detailed event-level drift analysis and pattern detection';
ALTER TABLE drift_recommendations COMMENT 'Recommended actions for addressing detected drift';
ALTER TABLE drift_monitor_status COMMENT 'Health and performance metrics for the drift monitoring service';