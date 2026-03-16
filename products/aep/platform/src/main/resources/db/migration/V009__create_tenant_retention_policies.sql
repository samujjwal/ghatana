-- V009: AEP Tenant Retention Policies
-- Stores per-tenant, per-event-type data retention configuration including
-- GDPR Art.17 / CCPA §1798.105 right-to-erasure requests.

CREATE TABLE IF NOT EXISTS aep_tenant_retention_policies (
    id                 UUID            NOT NULL DEFAULT gen_random_uuid(),
    tenant_id          VARCHAR(255)    NOT NULL,
    event_type         VARCHAR(255)    NOT NULL DEFAULT 'DEFAULT',
    max_age_seconds    BIGINT          NOT NULL DEFAULT 2592000, -- 30 days
    max_bytes          BIGINT          NOT NULL DEFAULT 0,
    gdpr_erasure       BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_aep_tenant_retention_policies PRIMARY KEY (id),
    CONSTRAINT uq_aep_retention_tenant_type      UNIQUE (tenant_id, event_type)
);

-- Index for fast lookup by tenant during enforcement sweep
CREATE INDEX IF NOT EXISTS idx_aep_retention_tenant
    ON aep_tenant_retention_policies (tenant_id);

-- Index to quickly find pending GDPR erasure requests
CREATE INDEX IF NOT EXISTS idx_aep_retention_gdpr_erasure
    ON aep_tenant_retention_policies (gdpr_erasure)
    WHERE gdpr_erasure = TRUE;

-- Retention enforcement audit log — immutable append-only
CREATE TABLE IF NOT EXISTS aep_retention_audit (
    id              BIGSERIAL       NOT NULL,
    tenant_id       VARCHAR(255)    NOT NULL,
    event_type      VARCHAR(255)    NOT NULL,
    policy_id       UUID,
    run_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    events_purged   BIGINT          NOT NULL DEFAULT 0,
    bytes_freed     BIGINT          NOT NULL DEFAULT 0,
    gdpr_erasure    BOOLEAN         NOT NULL DEFAULT FALSE,
    duration_ms     BIGINT,
    error_message   TEXT,

    CONSTRAINT pk_aep_retention_audit PRIMARY KEY (id)
);

-- Fast query-by-tenant for compliance audits
CREATE INDEX IF NOT EXISTS idx_aep_retention_audit_tenant
    ON aep_retention_audit (tenant_id, run_at DESC);
