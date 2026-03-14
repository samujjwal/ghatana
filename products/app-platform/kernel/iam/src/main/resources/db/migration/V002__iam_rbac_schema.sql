-- V002: RBAC roles, permissions, assignments (K01-RBAC)

-- Platform and tenant-scoped roles
CREATE TABLE iam_roles (
    role_id     VARCHAR(64)  PRIMARY KEY,
    role_name   VARCHAR(128) NOT NULL,
    tenant_id   VARCHAR(64),          -- NULL = platform-wide role
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_role_tenant UNIQUE (tenant_id, role_name)
);

-- Fine-grained permissions per role
CREATE TABLE iam_permissions (
    id        BIGSERIAL    PRIMARY KEY,
    role_id   VARCHAR(64)  NOT NULL REFERENCES iam_roles(role_id) ON DELETE CASCADE,
    resource  VARCHAR(128) NOT NULL,  -- e.g. "ledger", "payment"
    action    VARCHAR(64)  NOT NULL,  -- e.g. "read", "write", "approve"

    CONSTRAINT uq_perm UNIQUE (role_id, resource, action)
);

-- Principal (user / service) to role assignments
CREATE TABLE iam_role_assignments (
    id           BIGSERIAL    PRIMARY KEY,
    tenant_id    VARCHAR(64)  NOT NULL,
    principal_id VARCHAR(128) NOT NULL,
    role_name    VARCHAR(128) NOT NULL,
    assigned_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_assignment UNIQUE (tenant_id, principal_id, role_name)
);

-- Indexes
CREATE INDEX idx_iam_roles_tenant     ON iam_roles (tenant_id);
CREATE INDEX idx_iam_assignments_prin ON iam_role_assignments (tenant_id, principal_id);

-- RLS: tenants see only their own roles and assignments
ALTER TABLE iam_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE iam_role_assignments ENABLE ROW LEVEL SECURITY;

CREATE POLICY rls_iam_roles ON iam_roles
    USING (tenant_id IS NULL OR tenant_id = current_setting('app.current_tenant', TRUE));

CREATE POLICY rls_iam_assignments ON iam_role_assignments
    USING (tenant_id = current_setting('app.current_tenant', TRUE));

COMMENT ON TABLE iam_roles        IS 'RBAC roles scoped to tenant or platform-wide (K01)';
COMMENT ON TABLE iam_permissions  IS 'Fine-grained permissions per role (K01)';
COMMENT ON TABLE iam_role_assignments IS 'Principal-to-role assignments (K01)';
