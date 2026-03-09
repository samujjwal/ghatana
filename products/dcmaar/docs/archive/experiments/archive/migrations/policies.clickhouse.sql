-- Example ClickHouse schema for policies table
CREATE TABLE IF NOT EXISTS policies
(
  subject String,
  resource String DEFAULT '*',
  version String,
  data String,
  schema_version UInt32,
  updated_at DateTime DEFAULT now()
)
ENGINE = MergeTree
ORDER BY (subject, resource);
