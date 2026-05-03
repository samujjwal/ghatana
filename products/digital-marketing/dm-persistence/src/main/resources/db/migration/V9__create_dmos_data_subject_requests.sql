-- Migration V9: Create data subject requests table (DMOS-P1-017)
-- Stores privacy compliance requests for data subject rights (GDPR/CCPA).

CREATE TABLE dmos_data_subject_requests (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    request_type VARCHAR(64) NOT NULL,
    contact_point_hash VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    submitted_at TIMESTAMP NOT NULL,
    submitted_by VARCHAR(64) NOT NULL,
    completed_at TIMESTAMP,
    completed_by VARCHAR(64),
    rejection_reason TEXT,
    evidence_location TEXT
);

-- Index for tenant/workspace scoping (DMOS-P1-017)
CREATE INDEX idx_dsr_tenant_workspace ON dmos_data_subject_requests(tenant_id, workspace_id);

-- Index for contact point lookup (DMOS-P1-017)
CREATE INDEX idx_dsr_contact_point ON dmos_data_subject_requests(contact_point_hash);

-- Index for status filtering (DMOS-P1-017)
CREATE INDEX idx_dsr_status ON dmos_data_subject_requests(status);

-- Index for pending requests (DMOS-P1-017)
CREATE INDEX idx_dsr_pending ON dmos_data_subject_requests(submitted_at) WHERE status = 'PENDING';
