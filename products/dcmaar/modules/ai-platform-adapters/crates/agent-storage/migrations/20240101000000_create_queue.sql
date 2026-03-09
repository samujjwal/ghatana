-- Create the queue_items table
CREATE TABLE IF NOT EXISTS queue_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    data BLOB NOT NULL,
    status TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    
    -- Add indexes for common queries
    CONSTRAINT fk_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'DEAD_LETTER'))
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_queue_items_status ON queue_items(status);
CREATE INDEX IF NOT EXISTS idx_queue_items_created_at ON queue_items(created_at);
CREATE INDEX IF NOT EXISTS idx_queue_items_updated_at ON queue_items(updated_at);

-- Create a view for pending items
CREATE VIEW IF NOT EXISTS pending_queue_items AS
SELECT * FROM queue_items WHERE status = 'PENDING';

-- Create a view for failed items that can be retried
CREATE VIEW IF NOT EXISTS retryable_failed_items AS
SELECT * FROM queue_items 
WHERE status = 'FAILED' 
AND (retry_count < 3 OR retry_count IS NULL);

-- Create a view for dead letter items
CREATE VIEW IF NOT EXISTS dead_letter_items AS
SELECT * FROM queue_items WHERE status = 'DEAD_LETTER';

-- Create a trigger to automatically update the updated_at timestamp
CREATE TRIGGER IF NOT EXISTS update_queue_items_updated_at
AFTER UPDATE ON queue_items
BEGIN
    UPDATE queue_items 
    SET updated_at = CAST((julianday('now') - 2440587.5) * 86400.0 AS INTEGER)
    WHERE id = NEW.id;
END;
