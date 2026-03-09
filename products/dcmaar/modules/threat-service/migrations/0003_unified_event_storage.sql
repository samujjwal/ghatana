-- 0003_unified_event_storage.sql
-- Implements the unified event storage schema with source type tracking
-- and optimized browser events projection.

-- Unified events table for all event types
CREATE TABLE IF NOT EXISTS events (
    timestamp DateTime64(9, 'UTC') CODEC(DoubleDelta, ZSTD(1)),
    event_id String CODEC(ZSTD(1)),
    tenant_id String CODEC(ZSTD(1)),
    device_id String CODEC(ZSTD(1)),
    session_id String CODEC(ZSTD(1)),
    source_type Enum8('extension' = 1, 'desktop' = 2, 'agent' = 3) CODEC(ZSTD(1)),
    event_type String CODEC(ZSTD(1)),
    payload String CODEC(ZSTD(1)),
    labels Map(String, String) CODEC(ZSTD(1)),
    
    -- Indexes for common query patterns
    INDEX idx_tenant_id tenant_id TYPE bloom_filter GRANULARITY 3,
    INDEX idx_device_id device_id TYPE bloom_filter GRANULARITY 3,
    INDEX idx_session_id session_id TYPE bloom_filter GRANULARITY 3,
    INDEX idx_source_type source_type TYPE bloom_filter GRANULARITY 3,
    INDEX idx_event_type event_type TYPE bloom_filter GRANULARITY 3,
    INDEX idx_timestamp timestamp TYPE minmax GRANULARITY 3
) ENGINE = MergeTree()
PARTITION BY toStartOfDay(timestamp)
ORDER BY (tenant_id, device_id, toStartOfDay(timestamp), event_type, source_type);

-- Optimized projection for browser events
CREATE TABLE IF NOT EXISTS browser_events (
    timestamp DateTime64(9, 'UTC') CODEC(DoubleDelta, ZSTD(1)),
    event_id String CODEC(ZSTD(1)),
    tenant_id String CODEC(ZSTD(1)),
    device_id String CODEC(ZSTD(1)),
    session_id String CODEC(ZSTD(1)),
    source_type Enum8('extension' = 1, 'desktop' = 2) CODEC(ZSTD(1)),
    tab_id String CODEC(ZSTD(1)),
    url String CODEC(ZSTD(1)),
    domain String CODEC(ZSTD(1)),
    method String CODEC(ZSTD(1)),
    status_code Int32 CODEC(ZSTD(1)),
    latency_ms Float64 CODEC(Gorilla, ZSTD(1)),
    
    -- Indexes for common query patterns
    INDEX idx_tenant_id tenant_id TYPE bloom_filter GRANULARITY 3,
    INDEX idx_device_id device_id TYPE bloom_filter GRANULARITY 3,
    INDEX idx_session_id session_id TYPE bloom_filter GRANULARITY 3,
    INDEX idx_domain domain TYPE bloom_filter GRANULARITY 3,
    INDEX idx_url url TYPE bloom_filter GRANULARITY 3,
    INDEX idx_status_code status_code TYPE minmax GRANULARITY 3,
    INDEX idx_timestamp timestamp TYPE minmax GRANULARITY 3
) ENGINE = MergeTree()
PARTITION BY toStartOfDay(timestamp)
ORDER BY (tenant_id, device_id, toStartOfDay(timestamp), domain, source_type);

-- Materialized view to populate browser_events from the unified events table
-- This will automatically project browser events into the optimized table
CREATE MATERIALIZED VIEW IF NOT EXISTS browser_events_mv
TO browser_events
AS
SELECT 
    timestamp,
    event_id,
    tenant_id,
    device_id,
    session_id,
    source_type,
    JSONExtractString(payload, 'tab_id') as tab_id,
    JSONExtractString(payload, 'url') as url,
    JSONExtractString(payload, 'domain') as domain,
    JSONExtractString(payload, 'method') as method,
    toInt32OrNull(JSONExtractString(payload, 'status_code')) as status_code,
    toFloat64OrNull(JSONExtractString(payload, 'latency_ms')) as latency_ms
FROM events
WHERE event_type = 'browser';

-- Migration for existing data (if any)
-- This will backfill the browser_events table with existing browser events
-- from the extension_events table if it exists
-- Note: This is a placeholder - actual migration logic will depend on existing data structure
-- and should be tested thoroughly in a staging environment first
-- INSERT INTO browser_events
-- SELECT 
--     created_at as timestamp,
--     event_id,
--     'default' as tenant_id, -- Replace with actual tenant ID if available
--     extension_id as device_id, -- This assumes extension_id maps to device_id
--     '' as session_id, -- Session ID not available in old schema
--     'extension' as source_type,
--     tab_id,
--     url,
--     domain,
--     '' as method, -- Not available in old schema
--     status_code,
--     latency as latency_ms
-- FROM extension_events
-- WHERE domain IS NOT NULL AND domain != '';

-- Note: The extension_events table is kept for backward compatibility
-- It can be dropped in a future migration after verifying data migration
-- DROP TABLE IF EXISTS extension_events;
-- DROP TABLE IF EXISTS extensions;
