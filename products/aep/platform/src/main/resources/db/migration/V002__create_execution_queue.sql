-- Flyway V002: Create execution queue for async pipeline job management
-- Maps to: com.ghatana.orchestrator.queue.impl.ExecutionQueueJobEntity

CREATE TABLE IF NOT EXISTS execution_queue (
    job_id            VARCHAR(100)    NOT NULL,
    tenant_id         VARCHAR(100)    NOT NULL,
    pipeline_id       VARCHAR(100)    NOT NULL,
    idempotency_key   VARCHAR(200)    NOT NULL,
    trigger_data      JSONB,
    status            VARCHAR(20)     NOT NULL,
    attempt_count     INTEGER         NOT NULL DEFAULT 0,
    max_attempts      INTEGER         NOT NULL DEFAULT 3,
    enqueued_at       TIMESTAMPTZ     NOT NULL,
    leased_at         TIMESTAMPTZ,
    lease_expires_at  TIMESTAMPTZ,
    leased_by         VARCHAR(100),
    completed_at      TIMESTAMPTZ,
    error_message     VARCHAR(2000),
    result            JSONB,
    next_retry_at     TIMESTAMPTZ,
    version           BIGINT,

    CONSTRAINT pk_execution_queue PRIMARY KEY (job_id),
    CONSTRAINT chk_execution_queue_status CHECK (status IN (
        'PENDING', 'LEASED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED', 'RETRYING'
    ))
);

-- Unique: prevent duplicate jobs per tenant + idempotency key
CREATE UNIQUE INDEX idx_execution_queue_idempotency
    ON execution_queue (tenant_id, idempotency_key);

-- Query patterns: poll for pending jobs, find by tenant+status, check expired leases
CREATE INDEX idx_execution_queue_tenant_status ON execution_queue (tenant_id, status);
CREATE INDEX idx_execution_queue_status_enqueued ON execution_queue (status, enqueued_at);
CREATE INDEX idx_execution_queue_lease_expires ON execution_queue (lease_expires_at);
CREATE INDEX idx_execution_queue_pipeline ON execution_queue (tenant_id, pipeline_id);

-- Partial index: only pending/retrying jobs for efficient polling
CREATE INDEX idx_execution_queue_pending_poll
    ON execution_queue (enqueued_at)
    WHERE status IN ('PENDING', 'RETRYING');

-- Documentation
COMMENT ON TABLE execution_queue IS 'Persistent execution queue for async pipeline job scheduling. Supports lease-based processing with retry and dead-letter semantics.';
COMMENT ON COLUMN execution_queue.version IS 'JPA @Version for optimistic concurrency control on lease operations';
COMMENT ON COLUMN execution_queue.leased_at IS 'Timestamp when a worker acquired the lease';
COMMENT ON COLUMN execution_queue.lease_expires_at IS 'Lease expiration for stale job recovery';
COMMENT ON COLUMN execution_queue.leased_by IS 'Worker identifier that currently holds the lease';
COMMENT ON COLUMN execution_queue.next_retry_at IS 'Scheduled time for next retry attempt (exponential backoff)';
COMMENT ON COLUMN execution_queue.trigger_data IS 'Pipeline trigger payload as JSONB';
