-- =============================================================================
-- V001__create_reference_data_schema.sql
-- Reference Data (D11) schema: instruments, entities, benchmarks, audit log.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Instrument master — SCD Type-2 (effective_to IS NULL = current row)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS instrument_master (
    id                UUID         NOT NULL,
    symbol            VARCHAR(32)  NOT NULL,
    exchange          VARCHAR(32)  NOT NULL,
    isin              VARCHAR(16),
    name              VARCHAR(256) NOT NULL,
    type              VARCHAR(32)  NOT NULL,        -- InstrumentType enum value
    status            VARCHAR(32)  NOT NULL,        -- InstrumentStatus enum value
    sector            VARCHAR(128),
    lot_size          INTEGER      NOT NULL DEFAULT 1,
    tick_size         NUMERIC(20, 8) NOT NULL DEFAULT 0.01,
    currency          VARCHAR(3)   NOT NULL DEFAULT 'NPR',
    effective_from    DATE         NOT NULL,
    effective_to      DATE,                         -- NULL means current version
    created_at_utc    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at_bs     VARCHAR(12),                  -- Bikram Sambat date string
    metadata          JSONB        NOT NULL DEFAULT '{}'
);

-- Fast lookup of the live record per instrument id
CREATE UNIQUE INDEX IF NOT EXISTS uix_instrument_current
    ON instrument_master (id)
    WHERE effective_to IS NULL;

-- Temporal range queries for SCD Type-2 as-of lookups
CREATE INDEX IF NOT EXISTS idx_instrument_temporal
    ON instrument_master (id, effective_from, effective_to);

-- Symbol + exchange uniqueness among live records
CREATE UNIQUE INDEX IF NOT EXISTS uix_instrument_symbol_exchange_current
    ON instrument_master (symbol, exchange)
    WHERE effective_to IS NULL;

-- Full-text search across symbol, isin, name
CREATE INDEX IF NOT EXISTS idx_instrument_search
    ON instrument_master (lower(symbol), lower(isin), lower(name))
    WHERE effective_to IS NULL;

-- ---------------------------------------------------------------------------
-- 2. Entity master — market participants (SCD Type-2)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS entity_master (
    id                   UUID         NOT NULL,
    entity_type          VARCHAR(32)  NOT NULL,    -- EntityType enum
    name                 VARCHAR(256) NOT NULL,
    short_name           VARCHAR(64),
    registration_number  VARCHAR(128),
    country              VARCHAR(64),
    status               VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    effective_from       DATE         NOT NULL,
    effective_to         DATE,
    created_at_utc       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    metadata             JSONB        NOT NULL DEFAULT '{}'
);

CREATE UNIQUE INDEX IF NOT EXISTS uix_entity_current
    ON entity_master (id)
    WHERE effective_to IS NULL;

CREATE INDEX IF NOT EXISTS idx_entity_type
    ON entity_master (entity_type)
    WHERE effective_to IS NULL;

-- ---------------------------------------------------------------------------
-- 3. Entity relationships — directed graph edges (SCD Type-2)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS entity_relationships (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    parent_entity_id     UUID         NOT NULL REFERENCES entity_master (id),
    child_entity_id      UUID         NOT NULL REFERENCES entity_master (id),
    relationship_type    VARCHAR(32)  NOT NULL,    -- RelationshipType enum
    effective_from       DATE         NOT NULL,
    effective_to         DATE                      -- NULL = currently active
);

CREATE INDEX IF NOT EXISTS idx_entity_rel_parent
    ON entity_relationships (parent_entity_id)
    WHERE effective_to IS NULL;

CREATE INDEX IF NOT EXISTS idx_entity_rel_child
    ON entity_relationships (child_entity_id)
    WHERE effective_to IS NULL;

-- ---------------------------------------------------------------------------
-- 4. Benchmark definitions
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS benchmark_definitions (
    id                   UUID          NOT NULL PRIMARY KEY,
    name                 VARCHAR(128)  NOT NULL,
    type                 VARCHAR(32)   NOT NULL,   -- BenchmarkType enum
    base_date            DATE          NOT NULL,
    base_value           NUMERIC(20,8) NOT NULL,
    currency             VARCHAR(3)    NOT NULL DEFAULT 'NPR',
    calculation_method   VARCHAR(128),
    status               VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE'
);

CREATE UNIQUE INDEX IF NOT EXISTS uix_benchmark_name
    ON benchmark_definitions (lower(name))
    WHERE status != 'DELETED';

-- ---------------------------------------------------------------------------
-- 5. Benchmark constituents — weighted components (SCD Type-2 on weight)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS benchmark_constituents (
    benchmark_id         UUID          NOT NULL REFERENCES benchmark_definitions (id),
    instrument_id        UUID          NOT NULL,   -- FK to instrument_master (no FK for perf)
    weight               NUMERIC(8,6)  NOT NULL,   -- 0.000001 – 1.000000
    effective_from       DATE          NOT NULL,
    effective_to         DATE                      -- NULL = currently active
);

CREATE INDEX IF NOT EXISTS idx_benchmark_constituents_current
    ON benchmark_constituents (benchmark_id)
    WHERE effective_to IS NULL;

-- ---------------------------------------------------------------------------
-- 6. Benchmark values — OHLCV timeseries per benchmark per day
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS benchmark_values (
    benchmark_id         UUID          NOT NULL REFERENCES benchmark_definitions (id),
    date_utc             DATE          NOT NULL,
    date_bs              VARCHAR(12),              -- Bikram Sambat date
    open_value           NUMERIC(20,8),
    high_value           NUMERIC(20,8),
    low_value            NUMERIC(20,8),
    close_value          NUMERIC(20,8),
    volume               BIGINT,
    daily_return         NUMERIC(20,8),
    CONSTRAINT pk_benchmark_values PRIMARY KEY (benchmark_id, date_utc)
);

CREATE INDEX IF NOT EXISTS idx_benchmark_values_range
    ON benchmark_values (benchmark_id, date_utc DESC);

-- ---------------------------------------------------------------------------
-- 7. Reference data snapshots — EOD point-in-time archive (D11-010)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS refdata_snapshots (
    snapshot_date        DATE         NOT NULL,
    snapshot_date_bs     VARCHAR(12),
    instrument_count     INTEGER      NOT NULL DEFAULT 0,
    entity_count         INTEGER      NOT NULL DEFAULT 0,
    snapshot_json        JSONB        NOT NULL DEFAULT '{}',
    created_at_utc       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_refdata_snapshots PRIMARY KEY (snapshot_date)
) PARTITION BY RANGE (snapshot_date);

-- Default partition covering all current dates; add yearly partitions as needed
CREATE TABLE IF NOT EXISTS refdata_snapshots_default PARTITION OF refdata_snapshots DEFAULT;

-- ---------------------------------------------------------------------------
-- 8. Reference data audit log — change trail (D11-011)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS refdata_audit_log (
    id                   UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    entity_type          VARCHAR(32)  NOT NULL,    -- "INSTRUMENT" | "MARKET_ENTITY"
    entity_id            UUID         NOT NULL,
    change_type          VARCHAR(32)  NOT NULL,    -- "CREATE" | "UPDATE" | "STATUS_CHANGE"
    before_json          JSONB,
    after_json           JSONB,
    actor_id             VARCHAR(256),
    occurred_at_utc      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_entity
    ON refdata_audit_log (entity_id, occurred_at_utc DESC);

CREATE INDEX IF NOT EXISTS idx_audit_occurred
    ON refdata_audit_log (occurred_at_utc DESC);
