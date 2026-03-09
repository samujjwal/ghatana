-- Batch 17: Desktop Telemetry Schema
-- Enables WebSocket-based real-time event ingestion from agent-desktop
-- Date: November 13, 2025

-- Desktop Events (raw telemetry)
-- Stores individual events streamed from agent-desktop
-- Retention: 7 days (configured in BATCH_18)
CREATE TABLE IF NOT EXISTS desktop_events (
    id SERIAL PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    -- Event types: WINDOW_FOCUS, PROCESS_START, PROCESS_END, IDLE_CHANGED, SESSION_START, SESSION_END
    
    timestamp BIGINT NOT NULL,
    -- Unix timestamp in milliseconds (from agent-desktop)
    
    window_title VARCHAR(500),
    -- Title of focused window (if applicable)
    
    process_name VARCHAR(255),
    -- Name of running process
    
    process_path VARCHAR(500),
    -- Full path to executable
    
    app_category VARCHAR(100),
    -- App category from desktop_app_categories (e.g., 'productivity', 'entertainment')
    
    session_id VARCHAR(36),
    -- Unique session identifier for grouping related events
    
    is_idle BOOLEAN DEFAULT FALSE,
    -- Whether device was idle during this event
    
    idle_seconds INT,
    -- Seconds idle (if applicable)
    
    data JSONB DEFAULT '{}',
    -- Additional metadata (extensible for future event types)
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Desktop Sessions (daily aggregates)
-- Summarizes daily activity for faster queries
-- Retention: 90 days (configured in BATCH_18)
CREATE TABLE IF NOT EXISTS desktop_sessions (
    id SERIAL PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    
    total_events INT NOT NULL DEFAULT 0,
    -- Count of events for this day
    
    active_time_seconds INT NOT NULL DEFAULT 0,
    -- Total seconds spent in active use (non-idle)
    
    idle_time_seconds INT NOT NULL DEFAULT 0,
    -- Total seconds spent idle
    
    most_used_app VARCHAR(255),
    -- Single most-used application
    
    categories_seen JSONB DEFAULT '[]',
    -- Array of app categories seen: ['productivity', 'entertainment', ...]
    
    app_breakdown JSONB DEFAULT '{}',
    -- Object with app usage in seconds: {'chrome': 1800, 'vscode': 3600, ...}
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(device_id, date)
);

-- Desktop App Categories (ML-driven categorization)
-- Persistent lookup table for app classification
-- Updated by BATCH_20 learning pipeline
CREATE TABLE IF NOT EXISTS desktop_app_categories (
    id SERIAL PRIMARY KEY,
    app_name VARCHAR(255) NOT NULL,
    -- Application name (e.g., 'Google Chrome', 'Visual Studio Code')
    
    category VARCHAR(100) NOT NULL,
    -- Category: 'productivity', 'entertainment', 'social', 'education', 'uncategorized'
    
    subcategory VARCHAR(100),
    -- Subcategory for finer classification (optional)
    
    confidence_score FLOAT DEFAULT 0.0,
    -- Confidence of categorization (0.0-1.0), used by ML learning
    
    is_productive BOOLEAN DEFAULT TRUE,
    -- Whether this app should count toward productivity metrics
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(app_name)
);

-- Desktop Weekly Aggregates (rolling stats)
-- Summary of weekly trends for dashboard visualization
-- Retention: 1+ years (configured in BATCH_18)
CREATE TABLE IF NOT EXISTS desktop_weekly_aggregates (
    id SERIAL PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    week_start DATE NOT NULL,
    -- Monday of the week (ISO 8601)
    
    total_sessions INT NOT NULL DEFAULT 0,
    -- Count of unique sessions (days with activity)
    
    total_active_seconds INT NOT NULL DEFAULT 0,
    -- Total seconds active across all days
    
    avg_session_duration_seconds INT,
    -- Average session duration (total_active_seconds / total_sessions)
    
    most_used_app VARCHAR(255),
    -- Most-used app for the week
    
    top_apps JSONB DEFAULT '[]',
    -- Array of top 5 apps by usage time: [
    --   {'name': 'Chrome', 'seconds': 25200},
    --   {'name': 'VSCode', 'seconds': 18000}
    -- ]
    
    category_breakdown JSONB DEFAULT '{}',
    -- Breakdown by category: {'productivity': 28800, 'entertainment': 14400}
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(device_id, week_start)
);

-- Indexes for query performance
-- Key queries in BATCH_18+:
-- - Find recent events by device: SELECT * FROM desktop_events WHERE device_id = ? ORDER BY timestamp DESC LIMIT 100
-- - Daily stats: SELECT * FROM desktop_sessions WHERE device_id = ? AND date BETWEEN ? AND ?
-- - Weekly trends: SELECT * FROM desktop_weekly_aggregates WHERE device_id = ? ORDER BY week_start DESC LIMIT 12

CREATE INDEX IF NOT EXISTS idx_desktop_events_device_timestamp 
    ON desktop_events(device_id, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_desktop_events_created_at 
    ON desktop_events(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_desktop_events_session_id 
    ON desktop_events(session_id) WHERE session_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_desktop_sessions_device_date 
    ON desktop_sessions(device_id, date DESC);

CREATE INDEX IF NOT EXISTS idx_desktop_sessions_date 
    ON desktop_sessions(date DESC);

CREATE INDEX IF NOT EXISTS idx_desktop_weekly_aggregates_device 
    ON desktop_weekly_aggregates(device_id, week_start DESC);

CREATE INDEX IF NOT EXISTS idx_desktop_app_categories_name 
    ON desktop_app_categories(app_name);

-- Partitioning preparation for BATCH_18
-- NOTE: Monthly partitioning for desktop_events will be added in BATCH_18
--       to handle large event volume efficiently
-- Example partition (to be automated in BATCH_18):
-- ALTER TABLE desktop_events PARTITION BY RANGE (YEAR_MONTH(timestamp))
-- PARTITION p_2025_11 VALUES LESS THAN (202512),
-- PARTITION p_2025_12 VALUES LESS THAN (202601),
-- ...

-- Grants for application user
-- Adjust username based on your setup (typically: guardian_app)
-- GRANT SELECT, INSERT ON TABLE desktop_events TO guardian_app;
-- GRANT SELECT, INSERT, UPDATE ON TABLE desktop_sessions TO guardian_app;
-- GRANT SELECT ON TABLE desktop_app_categories TO guardian_app;
-- GRANT SELECT, INSERT, UPDATE ON TABLE desktop_weekly_aggregates TO guardian_app;

-- Verification: Check successful table creation
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('desktop_events', 'desktop_sessions', 'desktop_app_categories', 'desktop_weekly_aggregates')
ORDER BY table_name;
