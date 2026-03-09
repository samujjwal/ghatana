-- ClickHouse Schema for Distributed Tracing Storage
-- Created for Ghatana Platform Observability System
-- Version: 1.0.0

-- Database creation (optional, may already exist)
-- CREATE DATABASE IF NOT EXISTS observability;

-- Spans table: Individual unit of work within a trace
CREATE TABLE IF NOT EXISTS observability.spans (
    -- Core identifiers
    traceId String NOT NULL,
    spanId String NOT NULL,
    parentSpanId Nullable(String),
    
    -- Metadata
    serviceName String NOT NULL,
    operationName String NOT NULL,
    spanName String,
    
    -- Timing (nanosecond precision)
    startTime DateTime64(9) NOT NULL,
    endTime DateTime64(9) NOT NULL,
    durationNs UInt64 NOT NULL,
    
    -- Status (OK, ERROR, UNSET per OpenTelemetry spec)
    status String DEFAULT 'UNSET',
    statusMessage Nullable(String),
    
    -- Tags and logs (key-value pairs, stored as JSON)
    tags String DEFAULT '{}',
    logs String DEFAULT '{}',
    
    -- Denormalized fields for common queries
    httpMethod Nullable(String),
    httpStatusCode Nullable(UInt16),
    dbSystem Nullable(String),
    dbStatement Nullable(String),
    
    -- Timestamp for partitioning and TTL
    timestamp DateTime DEFAULT now()
)
ENGINE = MergeTree()
ORDER BY (traceId, spanId)
PARTITION BY toDate(timestamp)
PRIMARY KEY (traceId, spanId)
TTL timestamp + INTERVAL 30 DAY
SETTINGS 
    index_granularity = 8192,
    ttl_only_drop_parts = 1;

-- Traces table: Aggregated trace-level view
CREATE TABLE IF NOT EXISTS observability.traces (
    -- Identifiers
    traceId String NOT NULL,
    
    -- Aggregated metadata
    serviceName String NOT NULL,
    operationName String,
    
    -- Timing
    startTime DateTime64(9) NOT NULL,
    endTime DateTime64(9) NOT NULL,
    durationMs UInt64 NOT NULL,
    
    -- Status aggregation
    status String DEFAULT 'OK',
    errorCount UInt32 DEFAULT 0,
    hasError UInt8 DEFAULT 0,
    
    -- Span aggregation
    spanCount UInt32 NOT NULL,
    
    -- Tags (JSON)
    tags String DEFAULT '{}',
    
    -- Timestamp for partitioning and TTL
    timestamp DateTime DEFAULT now()
)
ENGINE = MergeTree()
ORDER BY (traceId)
PARTITION BY toDate(timestamp)
PRIMARY KEY traceId
TTL timestamp + INTERVAL 30 DAY
SETTINGS 
    index_granularity = 8192,
    ttl_only_drop_parts = 1;

-- Materialized view: Automatically update traces table from spans
CREATE MATERIALIZED VIEW IF NOT EXISTS observability.traces_mv TO observability.traces AS
SELECT 
    traceId,
    min(serviceName) as serviceName,
    argMax(operationName, startTime) as operationName,
    min(startTime) as startTime,
    max(endTime) as endTime,
    (max(endTime) - min(startTime)) / 1000000000 as durationMs,
    if(maxIf(status, status = 'ERROR') = 'ERROR', 'ERROR', 'OK') as status,
    countIf(status = 'ERROR') as errorCount,
    if(maxIf(status, status = 'ERROR') = 'ERROR', 1, 0) as hasError,
    count() as spanCount,
    '{}' as tags,
    now() as timestamp
FROM observability.spans
GROUP BY traceId;

-- Index table for fast service/operation lookups
CREATE TABLE IF NOT EXISTS observability.service_operation_index (
    serviceName String NOT NULL,
    operationName String NOT NULL,
    lastSeen DateTime DEFAULT now()
)
ENGINE = ReplacingMergeTree(lastSeen)
ORDER BY (serviceName, operationName)
PARTITION BY toYYYYMM(lastSeen)
TTL lastSeen + INTERVAL 90 DAY;

-- Materialized view: Index services and operations
CREATE MATERIALIZED VIEW IF NOT EXISTS observability.service_operation_index_mv 
TO observability.service_operation_index AS
SELECT DISTINCT
    serviceName,
    operationName,
    now() as lastSeen
FROM observability.spans;

-- Dictionary for fast service/operation lookups (optional, for optimization)
CREATE DICTIONARY IF NOT EXISTS observability.service_dict (
    serviceName String,
    firstSeen DateTime,
    lastSeen DateTime
)
PRIMARY KEY serviceName
SOURCE(CLICKHOUSE(
    HOST 'localhost'
    PORT 9000
    USER 'default'
    PASSWORD ''
    DB 'observability'
    TABLE 'service_operation_index'
))
LIFETIME(MIN 60 MAX 300)
LAYOUT(flat());

-- Helper functions for common queries

-- Function: Calculate percentiles for duration
CREATE FUNCTION IF NOT EXISTS observability.percentile_duration AS 
(traceId String, percentile Float32) -> Float32 
RETURNS
SELECT quantile(percentile)(durationNs / 1000000.0)
FROM observability.spans
WHERE traceId = traceId
GROUP BY traceId;

-- Function: Count error spans in trace
CREATE FUNCTION IF NOT EXISTS observability.count_errors AS
(traceId String) -> UInt32
RETURNS
SELECT countIf(status = 'ERROR')
FROM observability.spans
WHERE traceId = traceId;

-- Comments for schema documentation
ALTER TABLE observability.spans COMMENT 'Individual spans within distributed traces, partitioned by date for efficient queries';
ALTER TABLE observability.traces COMMENT 'Aggregated trace-level view for quick trace retrieval';
ALTER TABLE observability.service_operation_index COMMENT 'Index of services and operations for discovery';
