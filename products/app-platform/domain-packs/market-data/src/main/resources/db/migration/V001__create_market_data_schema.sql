-- =============================================================================
-- V001__create_market_data_schema.sql
-- Market data (D04) schema: normalized_ticks as TimescaleDB hypertable.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Normalized ticks table — hypertable partition key: timestamp_utc
--    TimescaleDB will manage chunk intervals automatically (1 hour default).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS normalized_ticks (
    instrument_id   VARCHAR(64)    NOT NULL,    -- refers to instrument_master.id (D11)
    timestamp_utc   TIMESTAMPTZ    NOT NULL,    -- hypertable partition key
    calendar_date   DATE,                       -- local (NPT) trading date for daily rollups
    bid             NUMERIC(20, 8),
    ask             NUMERIC(20, 8),
    last            NUMERIC(20, 8),
    volume          BIGINT         NOT NULL DEFAULT 0,
    open            NUMERIC(20, 8),
    high            NUMERIC(20, 8),
    low             NUMERIC(20, 8),
    close           NUMERIC(20, 8),
    source          VARCHAR(16)    NOT NULL,    -- TickSource enum
    sequence        BIGINT         NOT NULL DEFAULT 0,
    anomaly_flag    BOOLEAN        NOT NULL DEFAULT FALSE
);

-- Convert to TimescaleDB hypertable (1-hour chunks)
SELECT create_hypertable(
    'normalized_ticks',
    'timestamp_utc',
    chunk_time_interval => INTERVAL '1 hour',
    if_not_exists => TRUE
);

-- Composite index for the most common query: instrument over a time range
CREATE INDEX IF NOT EXISTS idx_ticks_instrument_time
    ON normalized_ticks (instrument_id, timestamp_utc DESC);

-- Index on anomaly_flag for review queries
CREATE INDEX IF NOT EXISTS idx_ticks_anomaly
    ON normalized_ticks (instrument_id, timestamp_utc DESC)
    WHERE anomaly_flag = TRUE;

-- ---------------------------------------------------------------------------
-- 2. TimescaleDB compression policy (compress chunks older than 1 day)
-- ---------------------------------------------------------------------------
ALTER TABLE normalized_ticks
    SET (
        timescaledb.compress,
        timescaledb.compress_orderby = 'timestamp_utc DESC',
        timescaledb.compress_segmentby = 'instrument_id'
    );

SELECT add_compression_policy(
    'normalized_ticks',
    INTERVAL '1 day',
    if_not_exists => TRUE
);

-- ---------------------------------------------------------------------------
-- 3. Continuous aggregate: 1-minute OHLCV bars per instrument
--    Provides fast candlestick queries without full raw-tick scans.
-- ---------------------------------------------------------------------------
CREATE MATERIALIZED VIEW IF NOT EXISTS ohlcv_1min
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 minute', timestamp_utc) AS bucket,
    instrument_id,
    first(last, timestamp_utc)  AS open,
    max(last)                   AS high,
    min(last)                   AS low,
    last(last, timestamp_utc)   AS close,
    sum(volume)                 AS volume
FROM normalized_ticks
WHERE anomaly_flag = FALSE
GROUP BY bucket, instrument_id
WITH NO DATA;

SELECT add_continuous_aggregate_policy(
    'ohlcv_1min',
    start_offset  => INTERVAL '10 minutes',
    end_offset    => INTERVAL '1 minute',
    schedule_interval => INTERVAL '1 minute',
    if_not_exists => TRUE
);
