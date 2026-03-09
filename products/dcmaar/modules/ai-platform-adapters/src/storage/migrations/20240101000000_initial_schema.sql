-- Create metrics table
CREATE TABLE IF NOT EXISTS metrics (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    metric_type TEXT NOT NULL,
    hostname TEXT NOT NULL,
    data TEXT NOT NULL,
    timestamp DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Indexes for common queries
    INDEX idx_metrics_metric_type (metric_type),
    INDEX idx_metrics_hostname (hostname),
    INDEX idx_metrics_timestamp (timestamp)
);

-- Create events table
CREATE TABLE IF NOT EXISTS events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_type TEXT NOT NULL,
    severity TEXT NOT NULL,
    message TEXT NOT NULL,
    data TEXT,
    timestamp DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Indexes for common queries
    INDEX idx_events_event_type (event_type),
    INDEX idx_events_severity (severity),
    INDEX idx_events_timestamp (timestamp)
);

-- Create configuration table
CREATE TABLE IF NOT EXISTS config (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Insert initial configuration
INSERT OR IGNORE INTO config (key, value) VALUES ('schema_version', '1');
