-- 0002_add_extension_tables.sql
-- Create extensions and extension_events tables
-- Note: Use additive migrations; do not drop existing tables.

BEGIN;

CREATE TABLE IF NOT EXISTS extensions (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    api_key_hash TEXT NOT NULL,
    client_cert_fingerprint TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS extension_events (
    event_id TEXT PRIMARY KEY,
    extension_id TEXT NOT NULL REFERENCES extensions(id) ON DELETE CASCADE,
    tab_id TEXT,
    url TEXT,
    domain TEXT,
    event_type TEXT,
    latency DOUBLE PRECISION,
    status_code INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_extension_events_extension_id ON extension_events(extension_id);
CREATE INDEX IF NOT EXISTS idx_extension_events_domain ON extension_events(domain);
CREATE INDEX IF NOT EXISTS idx_extension_events_event_type ON extension_events(event_type);

COMMIT;
