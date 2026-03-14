-- Flyway V004: Event Schema Registry
--
-- Stores versioned JSON Schema definitions for event types.
-- One active schema per event_type at any time; older versions are retained for
-- historical validation and compatibility checking.
--
-- Status lifecycle:  DRAFT → ACTIVE → DEPRECATED → BROKEN (if compat check fails)
-- Compatibility:     NONE | BACKWARD | FORWARD | FULL
--   BACKWARD  = new schema can read data written with old schema
--   FORWARD   = old schema can read data written with new schema
--   FULL      = both BACKWARD and FORWARD

CREATE TYPE event_schema_status AS ENUM ('DRAFT', 'ACTIVE', 'DEPRECATED', 'BROKEN');
CREATE TYPE event_schema_compat AS ENUM ('NONE', 'BACKWARD', 'FORWARD', 'FULL');

CREATE TABLE IF NOT EXISTS event_schema_registry (
    event_type        VARCHAR(255)            NOT NULL,
    version           INTEGER                 NOT NULL,
    json_schema       JSONB                   NOT NULL,
    status            event_schema_status     NOT NULL DEFAULT 'DRAFT',
    compat_type       event_schema_compat     NOT NULL DEFAULT 'BACKWARD',
    description       TEXT,
    created_at        TIMESTAMPTZ             NOT NULL DEFAULT NOW(),
    activated_at      TIMESTAMPTZ,
    deprecated_at     TIMESTAMPTZ,

    CONSTRAINT pk_event_schema_registry PRIMARY KEY (event_type, version),
    CONSTRAINT chk_version_positive CHECK (version > 0)
);

-- Lookup active schema for an event type
CREATE INDEX idx_event_schema_active
    ON event_schema_registry (event_type, status)
    WHERE status = 'ACTIVE';

-- Version history lookup
CREATE INDEX idx_event_schema_type_ver
    ON event_schema_registry (event_type, version DESC);

COMMENT ON TABLE event_schema_registry IS
    'Versioned JSON Schema registry for aggregate event types. '
    'Supports backward/forward compatibility checking before activation. '
    'Only one schema per event_type may carry status=ACTIVE at once.';

COMMENT ON COLUMN event_schema_registry.event_type IS
    'Logical event type name (e.g. OrderPlaced, TradeExecuted).';
COMMENT ON COLUMN event_schema_registry.version IS
    'Monotonically increasing integer version per event_type. '
    'Increment MINOR for backward-compatible additions, MAJOR for breaking changes.';
COMMENT ON COLUMN event_schema_registry.json_schema IS
    'JSON Schema (draft-07) that event data payloads must conform to.';
COMMENT ON COLUMN event_schema_registry.status IS
    'DRAFT: not yet enforced. ACTIVE: currently enforced. '
    'DEPRECATED: superseded by newer version. BROKEN: failed compat check.';
COMMENT ON COLUMN event_schema_registry.compat_type IS
    'Declared compatibility relative to the previous active version.';
