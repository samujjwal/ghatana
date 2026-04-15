-- Flyway V003: Create timeseries table for TimeSeriesRecord
-- Maps to: com.ghatana.datacloud.TimeSeriesRecord (@Entity, table = "timeseries")
-- Parent: com.ghatana.datacloud.DataRecord (@MappedSuperclass)

CREATE TABLE IF NOT EXISTS timeseries (
    -- DataRecord base fields
    id                UUID            NOT NULL,
    tenant_id         VARCHAR(255)    NOT NULL,
    collection_name   VARCHAR(255)    NOT NULL,
    record_type       VARCHAR(50)     NOT NULL,
    data              JSONB           DEFAULT '{}'::jsonb,
    metadata          JSONB           DEFAULT '{}'::jsonb,
    created_at        TIMESTAMPTZ,
    created_by        VARCHAR(255),

    -- TimeSeriesRecord-specific fields
    metric_name       VARCHAR(255)    NOT NULL,
    "timestamp"       TIMESTAMPTZ     NOT NULL,
    value             DOUBLE PRECISION,
    tags              JSONB           DEFAULT '{}'::jsonb,

    CONSTRAINT pk_timeseries PRIMARY KEY (id),
    CONSTRAINT chk_timeseries_record_type CHECK (record_type IN ('ENTITY', 'EVENT', 'TIMESERIES', 'DOCUMENT', 'GRAPH'))
);

-- Performance indexes (DESC on timestamp for recent-first queries)
CREATE INDEX idx_timeseries_tenant ON timeseries (tenant_id);
CREATE INDEX idx_timeseries_metric ON timeseries (tenant_id, collection_name, metric_name);
CREATE INDEX idx_timeseries_time ON timeseries (tenant_id, collection_name, metric_name, "timestamp" DESC);

-- Documentation
COMMENT ON TABLE timeseries IS 'Time-series metric store for TimeSeriesRecord. Optimized for temporal range queries on metric data.';
COMMENT ON COLUMN timeseries.metric_name IS 'Name of the metric being recorded';
COMMENT ON COLUMN timeseries."timestamp" IS 'Primary ordering field — the time at which the metric value was observed';
COMMENT ON COLUMN timeseries.value IS 'Numeric metric value (nullable for event-style markers)';
COMMENT ON COLUMN timeseries.tags IS 'Dimension tags as JSONB for flexible grouping and filtering';
COMMENT ON COLUMN timeseries.data IS 'Additional metric payload as JSONB';
