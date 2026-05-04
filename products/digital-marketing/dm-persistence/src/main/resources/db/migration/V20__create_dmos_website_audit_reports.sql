-- DMOS Website Audit Reports table
-- Schema migration V20: create dmos_website_audit_reports

CREATE TABLE IF NOT EXISTS dmos_website_audit_reports (
    report_id         TEXT        NOT NULL,
    workspace_id      TEXT        NOT NULL,
    website_url       TEXT        NOT NULL,
    overall_score     SMALLINT    NOT NULL CHECK (overall_score BETWEEN 0 AND 100),
    findings         JSONB       NOT NULL,
    summary          TEXT        NOT NULL,
    recommendations   TEXT        NOT NULL,
    model_version     TEXT        NOT NULL,
    audited_at        TIMESTAMPTZ NOT NULL,
    audited_by        TEXT        NOT NULL,
    CONSTRAINT dmos_website_audit_reports_pkey PRIMARY KEY (report_id, workspace_id)
);

CREATE INDEX IF NOT EXISTS dmos_website_audit_reports_workspace_idx ON dmos_website_audit_reports (workspace_id);
CREATE INDEX IF NOT EXISTS dmos_website_audit_reports_website_url_idx ON dmos_website_audit_reports (website_url);
CREATE INDEX IF NOT EXISTS dmos_website_audit_reports_audited_at_idx ON dmos_website_audit_reports (audited_at);
