-- V001__create_regulatory_reporting_schema.sql
-- D-10 Regulatory Reporting — Core Schema
-- Covers D10-001 through D10-015 primary persistence needs

-- ─── Report Definitions (D10-001 to D10-003) ─────────────────────────────────
CREATE TABLE IF NOT EXISTS report_definitions (
    definition_id       TEXT        PRIMARY KEY,
    report_code         TEXT        NOT NULL,
    report_name         TEXT        NOT NULL,
    regulator           TEXT        NOT NULL,   -- 'SEBON', 'NRB', 'CDSC', 'FIC'
    report_category     TEXT        NOT NULL,   -- 'TRADE', 'POSITION', 'AML', 'CUSTODY'
    frequency           TEXT        NOT NULL,   -- 'DAILY', 'WEEKLY', 'MONTHLY', 'ON_DEMAND'
    format              TEXT        NOT NULL,   -- 'PDF', 'CSV', 'XBRL', 'XML', 'JSON'
    schema_version      TEXT        NOT NULL,
    status              TEXT        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DEPRECATED', 'DRAFT')),

    -- Schedule configuration (CRON expression for scheduled reports)
    cron_expression     TEXT,
    timezone            TEXT        NOT NULL DEFAULT 'Asia/Kathmandu',

    -- Retention policy (years)
    retention_years     INT         NOT NULL DEFAULT 7,

    created_by          TEXT        NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_report_def_code_active
    ON report_definitions (report_code) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_report_def_regulator ON report_definitions (regulator);
CREATE INDEX IF NOT EXISTS idx_report_def_frequency  ON report_definitions (frequency);

-- ─── Report Templates (D10-002: versioned template registry) ─────────────────
CREATE TABLE IF NOT EXISTS report_templates (
    template_id         TEXT        PRIMARY KEY,
    report_code         TEXT        NOT NULL,
    version_major       INT         NOT NULL,
    version_minor       INT         NOT NULL DEFAULT 0,
    template_type       TEXT        NOT NULL,   -- 'JINJA2', 'JASPER', 'XBRL_TAXONOMY'
    template_content    TEXT        NOT NULL,   -- Jinja2/Jasper XML / XBRL taxonomy path
    active              BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (report_code, version_major, version_minor)
);

CREATE INDEX IF NOT EXISTS idx_report_templates_code ON report_templates (report_code, active);

-- ─── Trade Reports (D10-004 to D10-005: real-time trade reporting) ────────────
CREATE TABLE IF NOT EXISTS trade_reports (
    report_id           TEXT        PRIMARY KEY,
    trade_id            TEXT        NOT NULL,
    definition_id       TEXT        NOT NULL REFERENCES report_definitions(definition_id),
    tenant_id           TEXT        NOT NULL,
    report_date         DATE        NOT NULL,
    report_date_bs      TEXT,                   -- Bikram Sambat date (K-15)
    status              TEXT        NOT NULL DEFAULT 'PENDING' CHECK (status IN (
                            'PENDING', 'GENERATED', 'SUBMITTED', 'ACKNOWLEDGED', 'REJECTED', 'FAILED')),
    payload             JSONB,                  -- report data snapshot
    generated_at        TIMESTAMPTZ,
    submitted_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_trade_reports_trade_id     ON trade_reports (trade_id);
CREATE INDEX IF NOT EXISTS idx_trade_reports_status       ON trade_reports (status);
CREATE INDEX IF NOT EXISTS idx_trade_reports_report_date  ON trade_reports (report_date DESC);
CREATE INDEX IF NOT EXISTS idx_trade_reports_tenant       ON trade_reports (tenant_id);

-- ─── Regulator Submissions (D10-009 to D10-010: submission lifecycle) ─────────
CREATE TABLE IF NOT EXISTS regulator_submissions (
    submission_id       TEXT        PRIMARY KEY,
    report_id           TEXT        NOT NULL,
    definition_id       TEXT        NOT NULL REFERENCES report_definitions(definition_id),
    tenant_id           TEXT        NOT NULL,
    regulator           TEXT        NOT NULL,
    report_code         TEXT        NOT NULL,
    submission_ref      TEXT,                   -- Regulator-assigned submission reference
    status              TEXT        NOT NULL DEFAULT 'PENDING' CHECK (status IN (
                            'PENDING', 'SUBMITTED', 'ACKNOWLEDGED', 'REJECTED', 'FAILED')),
    retry_count         INT         NOT NULL DEFAULT 0,
    payload_bytes       BIGINT,
    checksum_sha256     TEXT,
    submitted_at        TIMESTAMPTZ,
    ack_received_at     TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reg_submissions_report_id   ON regulator_submissions (report_id);
CREATE INDEX IF NOT EXISTS idx_reg_submissions_status      ON regulator_submissions (status);
CREATE INDEX IF NOT EXISTS idx_reg_submissions_regulator   ON regulator_submissions (regulator, status);
CREATE INDEX IF NOT EXISTS idx_reg_submissions_tenant      ON regulator_submissions (tenant_id);

-- ─── ACK/NACK Errors (D10-010: regulator rejection details) ──────────────────
CREATE TABLE IF NOT EXISTS submission_nack_errors (
    error_id            TEXT        PRIMARY KEY,
    submission_id       TEXT        NOT NULL REFERENCES regulator_submissions(submission_id),
    field_name          TEXT,                   -- nullable — some errors are document-level
    error_code          TEXT        NOT NULL,
    message             TEXT        NOT NULL,
    severity            TEXT        NOT NULL DEFAULT 'ERROR' CHECK (severity IN ('ERROR', 'WARNING')),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_nack_errors_submission ON submission_nack_errors (submission_id);

-- ─── Submission Audit Trail (D10-011) ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS submission_audit_trail (
    entry_id            TEXT        PRIMARY KEY,
    submission_id       TEXT        NOT NULL REFERENCES regulator_submissions(submission_id),
    report_id           TEXT        NOT NULL,
    event_type          TEXT        NOT NULL,   -- 'CREATED', 'SUBMITTED', 'ACK', 'NACK', 'RETRY', 'ESCALATED'
    actor_id            TEXT,
    detail              JSONB,
    occurred_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sub_audit_submission ON submission_audit_trail (submission_id);
CREATE INDEX IF NOT EXISTS idx_sub_audit_report     ON submission_audit_trail (report_id);

-- ─── Generated File Artifacts: PDF, CSV/Excel, XBRL ─────────────────────────
-- Used by PdfReportRendererService, CsvExcelRendererService, XbrlRendererService

CREATE TABLE IF NOT EXISTS report_pdf_artifacts (
    artifact_id         TEXT        PRIMARY KEY,
    submission_id       TEXT        NOT NULL REFERENCES regulator_submissions(submission_id),
    report_code         TEXT        NOT NULL,
    file_name           TEXT        NOT NULL,
    file_size_bytes     BIGINT      NOT NULL,
    checksum_sha256     TEXT        NOT NULL,
    storage_uri         TEXT        NOT NULL,   -- S3 / Data Cloud path
    generated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pdf_artifacts_submission ON report_pdf_artifacts (submission_id);

CREATE TABLE IF NOT EXISTS report_file_artifacts (
    artifact_id         TEXT        PRIMARY KEY,
    submission_id       TEXT        NOT NULL REFERENCES regulator_submissions(submission_id),
    report_code         TEXT        NOT NULL,
    format              TEXT        NOT NULL,   -- 'CSV', 'XLSX'
    file_name           TEXT        NOT NULL,
    file_size_bytes     BIGINT      NOT NULL,
    checksum_sha256     TEXT        NOT NULL,
    storage_uri         TEXT        NOT NULL,
    generated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_file_artifacts_submission ON report_file_artifacts (submission_id);

CREATE TABLE IF NOT EXISTS report_xbrl_artifacts (
    artifact_id         TEXT        PRIMARY KEY,
    submission_id       TEXT        NOT NULL REFERENCES regulator_submissions(submission_id),
    report_code         TEXT        NOT NULL,
    taxonomy_version    TEXT        NOT NULL,
    file_name           TEXT        NOT NULL,
    file_size_bytes     BIGINT      NOT NULL,
    checksum_sha256     TEXT        NOT NULL,
    storage_uri         TEXT        NOT NULL,
    generated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_xbrl_artifacts_submission ON report_xbrl_artifacts (submission_id);

-- ─── Trade Report Reconciliation Breaks (D10-013) ─────────────────────────────
CREATE TABLE IF NOT EXISTS trade_report_breaks (
    break_id            TEXT        PRIMARY KEY,
    trade_id            TEXT        NOT NULL,
    report_code         TEXT        NOT NULL,
    tenant_id           TEXT        NOT NULL,
    break_reason        TEXT        NOT NULL,   -- 'MISSING_REPORT', 'DATA_MISMATCH', 'LATE_SUBMISSION'
    status              TEXT        NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED', 'WAIVED')),
    detected_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    resolved_at         TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_trade_breaks_trade_id ON trade_report_breaks (trade_id);
CREATE INDEX IF NOT EXISTS idx_trade_breaks_status   ON trade_report_breaks (status);
CREATE INDEX IF NOT EXISTS idx_trade_breaks_tenant   ON trade_report_breaks (tenant_id);
