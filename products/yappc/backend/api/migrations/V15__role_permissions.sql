-- V15: Role-permission registry table
-- Replaces hardcoded InMemoryRolePermissionRegistry with durable PostgreSQL storage.
-- Default roles are seeded here so the table is never empty on first boot.

CREATE TABLE IF NOT EXISTS role_permissions (
    role_name   VARCHAR(255) NOT NULL PRIMARY KEY,
    permissions JSONB        NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  role_permissions            IS 'System-wide RBAC role-to-permission mappings';
COMMENT ON COLUMN role_permissions.permissions IS 'JSON array of permission strings for this role';

-- ============================================================
-- Seed default roles matching DevelopmentModule.createDefaultRegistry()
-- These can be overridden at runtime via JdbcRolePermissionRegistry.registerRole()
-- ============================================================

INSERT INTO role_permissions (role_name, permissions) VALUES
    ('OWNER', '[
        "workspace:create","workspace:read","workspace:update","workspace:delete","workspace:manage_members",
        "project:create","project:read","project:update","project:delete",
        "requirement:create","requirement:read","requirement:update","requirement:delete","requirement:approve",
        "ai_suggestion:request","ai_suggestion:feedback",
        "user:manage","role:assign","admin:system"
    ]'::jsonb),
    ('ADMIN', '[
        "workspace:create","workspace:read","workspace:update","workspace:delete","workspace:manage_members",
        "project:create","project:read","project:update","project:delete",
        "requirement:approve","user:manage","role:assign","ai_suggestion:request"
    ]'::jsonb),
    ('MEMBER', '[
        "workspace:read",
        "project:create","project:read","project:update",
        "requirement:create","requirement:read","requirement:update","requirement:delete",
        "ai_suggestion:request","ai_suggestion:feedback"
    ]'::jsonb),
    ('VIEWER', '[
        "workspace:read","project:read","requirement:read"
    ]'::jsonb),
    ('EDITOR', '[
        "workspace:read",
        "project:create","project:read","project:update",
        "requirement:create","requirement:read","requirement:update","requirement:delete",
        "ai_suggestion:request","ai_suggestion:feedback"
    ]'::jsonb),
    ('USER', '[
        "workspace:read",
        "project:create","project:read","project:update",
        "requirement:create","requirement:read","requirement:update","requirement:delete",
        "ai_suggestion:request","ai_suggestion:feedback"
    ]'::jsonb)
ON CONFLICT (role_name) DO NOTHING;
