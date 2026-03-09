-- Flyway Migration: V1.0.0__Initial_Schema.sql
-- Initial ClickHouse schema creation for distributed tracing storage
-- Created: 2025-10-23
-- Database: observability

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

-- Service/Operation index table for discovery
CREATE TABLE IF NOT EXISTS observability.service_operation_index (
    serviceName String NOT NULL,
    operationName String NOT NULL,
    lastSeen DateTime DEFAULT now()
)
ENGINE = ReplacingMergeTree(lastSeen)
ORDER BY (serviceName, operationName)
PARTITION BY toYYYYMM(lastSeen)
TTL lastSeen + INTERVAL 90 DAY;
