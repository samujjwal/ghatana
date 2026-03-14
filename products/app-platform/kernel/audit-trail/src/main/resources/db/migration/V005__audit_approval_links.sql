-- V005: Maker-checker approval links for four-eyes principle audit (K07-013/014)

CREATE TABLE audit_approval_links (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         VARCHAR(64)  NOT NULL,
    maker_audit_id    VARCHAR(64)  NOT NULL REFERENCES audit_logs(audit_id),
    checker_audit_id  VARCHAR(64)  NOT NULL REFERENCES audit_logs(audit_id),
    approval_outcome  VARCHAR(20)  NOT NULL CHECK (approval_outcome IN ('APPROVED', 'REJECTED')),
    linked_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_maker_checker UNIQUE (maker_audit_id, checker_audit_id)
);

-- Prevent tampering with existing links
REVOKE UPDATE, DELETE ON audit_approval_links FROM PUBLIC;

CREATE INDEX idx_approval_links_tenant ON audit_approval_links (tenant_id);
CREATE INDEX idx_approval_links_maker  ON audit_approval_links (maker_audit_id);

COMMENT ON TABLE audit_approval_links IS
  'Links maker (initiator) and checker (approver) audit entries for four-eyes verification (K07-013)';
