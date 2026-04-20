-- YAPPC development seed data
-- Safe idempotent inserts for local/dev environments.

INSERT INTO tenants (id, name, description, status, created_by, updated_by)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'YAPPC Dev Tenant',
    'Default development tenant for local workflows',
    'ACTIVE',
    'seed',
    'seed'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, tenant_id, email, username, first_name, last_name, status)
VALUES (
    '00000000-0000-0000-0000-000000000010',
    '00000000-0000-0000-0000-000000000001',
    'dev-admin@yappc.local',
    'dev-admin',
    'Dev',
    'Admin',
    'ACTIVE'
)
ON CONFLICT (email) DO NOTHING;

INSERT INTO workspaces (id, tenant_id, name, description, status, settings, created_by)
VALUES (
    '00000000-0000-0000-0000-000000000020',
    '00000000-0000-0000-0000-000000000001',
    'Default Workspace',
    'Development bootstrap workspace',
    'ACTIVE',
    '{"theme":"light","layout":"default"}'::jsonb,
    '00000000-0000-0000-0000-000000000010'
)
ON CONFLICT (tenant_id, name) DO NOTHING;

INSERT INTO projects (id, tenant_id, workspace_id, name, description, status, metadata, created_by)
VALUES (
    '00000000-0000-0000-0000-000000000030',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000020',
    'YAPPC Seed Project',
    'Seed project for lifecycle and integration testing',
    'PLANNING',
    '{"seeded":true,"source":"V1_0_0__YAPPC_DEV_SEED.sql"}'::jsonb,
    '00000000-0000-0000-0000-000000000010'
)
ON CONFLICT (tenant_id, name) DO NOTHING;

INSERT INTO metrics (id, tenant_id, project_id, name, description, type, value, unit, metadata, tags)
VALUES (
    '00000000-0000-0000-0000-000000000040',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000030',
    'seed.project.health',
    'Seed metric to verify backup and restore data presence',
    'GAUGE',
    1.0,
    'score',
    '{"seeded":true}'::jsonb,
    '["seed","health"]'::jsonb
)
ON CONFLICT (id) DO NOTHING;
