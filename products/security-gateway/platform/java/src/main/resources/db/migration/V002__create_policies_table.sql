-- V002: Create policies tables for RBAC
-- Description: Creates the policies and policy_permissions tables for storing RBAC policies
-- Author: Ghatana Platform Team

-- Policies table stores policy metadata
CREATE TABLE IF NOT EXISTS policies (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    role VARCHAR(128) NOT NULL,
    resource VARCHAR(512) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for efficient role lookups
CREATE INDEX IF NOT EXISTS idx_policies_role ON policies(role);

-- Index for efficient resource lookups
CREATE INDEX IF NOT EXISTS idx_policies_resource ON policies(resource);

-- Policy permissions table stores permissions for each policy
CREATE TABLE IF NOT EXISTS policy_permissions (
    policy_id VARCHAR(64) NOT NULL,
    permission VARCHAR(128) NOT NULL,
    PRIMARY KEY (policy_id, permission),
    FOREIGN KEY (policy_id) REFERENCES policies(id) ON DELETE CASCADE
);

-- Index for efficient policy_id lookups
CREATE INDEX IF NOT EXISTS idx_policy_permissions_policy_id ON policy_permissions(policy_id);

-- Insert default admin policy
INSERT INTO policies (id, name, description, role, resource, enabled)
VALUES ('default-admin-policy', 'Default Admin Policy', 'Grants full access to admin role', 'admin', '*', TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO policy_permissions (policy_id, permission)
VALUES 
    ('default-admin-policy', 'read'),
    ('default-admin-policy', 'write'),
    ('default-admin-policy', 'delete'),
    ('default-admin-policy', 'admin')
ON CONFLICT (policy_id, permission) DO NOTHING;

-- Audit trigger for tracking policy changes
CREATE OR REPLACE FUNCTION update_policies_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_policies_updated_at ON policies;
CREATE TRIGGER trigger_policies_updated_at
    BEFORE UPDATE ON policies
    FOR EACH ROW
    EXECUTE FUNCTION update_policies_updated_at();

-- Add comments for documentation
COMMENT ON TABLE policies IS 'RBAC policies defining role-resource-permission mappings';
COMMENT ON COLUMN policies.id IS 'Unique policy identifier';
COMMENT ON COLUMN policies.name IS 'Human-readable policy name';
COMMENT ON COLUMN policies.description IS 'Detailed description of the policy';
COMMENT ON COLUMN policies.role IS 'Role to which this policy applies';
COMMENT ON COLUMN policies.resource IS 'Resource pattern (supports * wildcard)';
COMMENT ON COLUMN policies.enabled IS 'Whether this policy is active';

COMMENT ON TABLE policy_permissions IS 'Permissions granted by each policy';
COMMENT ON COLUMN policy_permissions.policy_id IS 'Reference to parent policy';
COMMENT ON COLUMN policy_permissions.permission IS 'Permission action (e.g., read, write, delete)';
