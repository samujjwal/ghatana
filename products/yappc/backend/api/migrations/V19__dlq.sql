-- V19: Dead-Letter Queue (DLQ) for failed pipeline events
-- Stores operator pipeline failures so they can be inspected and retried.
-- DlqPublisher writes rows on OperatorResult.failed(); DLQ API exposes management endpoints.

CREATE TABLE IF NOT EXISTS yappc_dlq (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       TEXT        NOT NULL,
    pipeline_id     TEXT        NOT NULL,
    node_id         TEXT        NOT NULL,                 -- operator node that failed
    event_type      TEXT        NOT NULL,
    event_payload   JSONB       NOT NULL DEFAULT '{}'::jsonb,
    failure_reason  TEXT        NOT NULL,
    retry_count     INT         NOT NULL DEFAULT 0,
    status          TEXT        NOT NULL DEFAULT 'PENDING',  -- PENDING | RETRYING | RESOLVED | ABANDONED
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at     TIMESTAMPTZ,
    correlation_id  TEXT,

    CONSTRAINT pk_yappc_dlq PRIMARY KEY (id),
    CONSTRAINT chk_dlq_status CHECK (status IN ('PENDING', 'RETRYING', 'RESOLVED', 'ABANDONED'))
);

CREATE INDEX IF NOT EXISTS idx_yappc_dlq_tenant_status
    ON yappc_dlq (tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_yappc_dlq_pipeline
    ON yappc_dlq (pipeline_id, node_id);

CREATE INDEX IF NOT EXISTS idx_yappc_dlq_created
    ON yappc_dlq (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_yappc_dlq_correlation
    ON yappc_dlq (correlation_id)
    WHERE correlation_id IS NOT NULL;

COMMENT ON TABLE yappc_dlq IS
    'Dead-Letter Queue for YAPPC AEP pipeline operator failures. '
    'Written by DlqPublisher; managed via GET /api/v1/dlq and POST /api/v1/dlq/{id}/retry.';

COMMENT ON COLUMN yappc_dlq.node_id IS
    'The pipeline node ID of the operator that produced the failure.';

COMMENT ON COLUMN yappc_dlq.event_payload IS
    'Full event payload snapshot at the time of failure.';

COMMENT ON COLUMN yappc_dlq.retry_count IS
    'Number of retry attempts made for this DLQ entry.';

COMMENT ON COLUMN yappc_dlq.status IS
    'PENDING = not yet retried; RETRYING = retry in progress; RESOLVED = retry succeeded; ABANDONED = max retries exhausted.';
