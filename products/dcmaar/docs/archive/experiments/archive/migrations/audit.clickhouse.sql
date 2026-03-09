-- Example ClickHouse schema for append-only audit events
CREATE TABLE IF NOT EXISTS audit_events
(
  time DateTime,
  subject String,
  action String,
  target String,
  result String,
  details String
)
ENGINE = MergeTree
ORDER BY (time, subject);

