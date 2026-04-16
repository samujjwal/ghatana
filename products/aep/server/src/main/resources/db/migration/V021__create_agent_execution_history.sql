CREATE TABLE IF NOT EXISTS agent_execution_history (
    execution_id  TEXT PRIMARY KEY,
    agent_id      TEXT NOT NULL,
    status        TEXT NOT NULL,
    input_payload TEXT,
    output_payload TEXT,
    duration_ms   BIGINT NOT NULL DEFAULT 0,
    executed_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agent_execution_history_agent_time
    ON agent_execution_history (agent_id, executed_at DESC);
