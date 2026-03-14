-- Flyway V003: Audit retention policies and export tracking tables.

CREATE TABLE IF NOT EXISTS audit_retention_policies (
    policy_id         VARCHAR(255)  NOT NULL,
    tenant_id         VARCHAR(255)  NOT NULL,
    -- Glob pattern matched against 'action' (e.g. 'ORDER_*', '*')
    action_pattern    VARCHAR(255)  NOT NULL,
    retention_days    INT           NOT NULL CHECK (retention_days > 0),
    -- If set: archive to cold storage after this many days, then delete
    archive_after_days INT,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_audit_retention PRIMARY KEY (policy_id),
    -- First-match-wins: unique per (tenant, pattern)
    CONSTRAINT uq_audit_retention_tenant_pattern UNIQUE (tenant_id, action_pattern)
);

COMMENT ON TABLE audit_retention_policies IS
    'Per-tenant audit log retention configuration. Glob patterns matched against the action field. '
    'Default when no policy matches: 10-year retention per regulatory requirements.';
COMMENT ON COLUMN audit_retention_policies.action_pattern IS
    'Glob pattern for action matching. Examples: ''ORDER_*'', ''LOGIN'', ''*''. First match wins.';

-- -------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS audit_exports (
    export_id       VARCHAR(255)  NOT NULL,
    tenant_id       VARCHAR(255)  NOT NULL,
    from_date       TIMESTAMPTZ   NOT NULL,
    to_date         TIMESTAMPTZ   NOT NULL,
    format          VARCHAR(20)   NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PROCESSING',
    file_url        VARCHAR(500),
    requested_by    VARCHAR(255)  NOT NULL,
    requested_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,

    CONSTRAINT pk_audit_exports PRIMARY KEY (export_id),
    CONSTRAINT chk_audit_export_format CHECK (format IN ('CSV','JSON','PDF')),
    CONSTRAINT chk_audit_export_status CHECK (status IN ('PROCESSING','COMPLETED','FAILED'))
);

CREATE INDEX idx_audit_exports_tenant
    ON audit_exports (tenant_id, requested_at DESC);

COMMENT ON TABLE audit_exports IS
    'Tracks async evidence export jobs for regulatory submission. '
    'Exports are streamed to S3/object storage and linked via file_url.';
