-- YAPPC Lifecycle: Approval Requests Persistence
-- Migration: V2_0_0__YAPPC_APPROVAL_REQUESTS.sql
-- Creates the durable approval_requests table for HumanApprovalService JDBC persistence.

CREATE TABLE IF NOT EXISTS approval_requests (
    id                   VARCHAR(255)  NOT NULL PRIMARY KEY,
    tenant_id            VARCHAR(255)  NOT NULL,
    project_id           VARCHAR(255)  NOT NULL,
    requesting_agent_id  VARCHAR(255),
    approval_type        VARCHAR(64)   NOT NULL,
    status               VARCHAR(32)   NOT NULL DEFAULT 'PENDING',
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    decided_at           TIMESTAMPTZ,
    decided_by           VARCHAR(255),
    expires_at           TIMESTAMPTZ,
    -- Structured context stored as JSONB for schema evolution flexibility
    context              JSONB
);

-- Index for the most common query pattern: pending requests per project/tenant
CREATE INDEX IF NOT EXISTS idx_approval_requests_tenant_project_status
    ON approval_requests (tenant_id, project_id, status);

-- Index for listing all pending requests for a tenant
CREATE INDEX IF NOT EXISTS idx_approval_requests_tenant_status
    ON approval_requests (tenant_id, status);
