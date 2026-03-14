-- V001: Config schema registry
-- Stores versioned JSON Schema definitions for configuration namespaces.

CREATE TABLE IF NOT EXISTS config_schemas (
    namespace    TEXT         NOT NULL,
    version      TEXT         NOT NULL,
    json_schema  JSONB        NOT NULL,
    description  TEXT         NOT NULL DEFAULT '',
    defaults     JSONB        NOT NULL DEFAULT '{}',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_config_schemas PRIMARY KEY (namespace, version)
);

COMMENT ON TABLE config_schemas IS 'Versioned JSON Schema definitions for config namespaces';
COMMENT ON COLUMN config_schemas.namespace IS 'Logical grouping of config keys (e.g. payments, kyc)';
COMMENT ON COLUMN config_schemas.version   IS 'Semantic version of the schema (e.g. 1.0.0)';
COMMENT ON COLUMN config_schemas.defaults  IS 'Default key/value map applied when no entry exists at any level';
