-- Enable WAL mode for better concurrency
PRAGMA journal_mode=WAL;

-- Create a table for storing agent state
CREATE TABLE IF NOT EXISTS agent_state (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    key TEXT NOT NULL UNIQUE,
    value BLOB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create an index on the key for faster lookups
CREATE INDEX IF NOT EXISTS idx_agent_state_key ON agent_state(key);

-- Create a table for storing events
CREATE TABLE IF NOT EXISTS events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_type TEXT NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create an index on event type and creation time
CREATE INDEX IF NOT EXISTS idx_events_type_created ON events(event_type, created_at);
