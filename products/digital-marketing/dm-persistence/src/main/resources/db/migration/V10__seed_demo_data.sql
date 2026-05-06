-- Migration V10: Seed demo data for local development (DMOS-P2-005)

DO $$
BEGIN
    IF to_regclass('public.dmos_tenants') IS NULL
        OR to_regclass('public.dmos_users') IS NULL
        OR to_regclass('public.dmos_approvals') IS NULL THEN
        RAISE NOTICE 'Skipping V10 demo seed migration: required demo tables are not present';
        RETURN;
    END IF;

    -- Seed demo tenant and workspace
    INSERT INTO dmos_tenants (id, name, created_at) VALUES ('tenant-demo', 'Demo Tenant', NOW())
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO dmos_workspaces (id, tenant_id, name, created_at) VALUES ('workspace-demo', 'tenant-demo', 'Demo Workspace', NOW())
    ON CONFLICT (id) DO NOTHING;

    -- Seed demo users with roles
    INSERT INTO dmos_users (id, tenant_id, workspace_id, email, roles, created_at) VALUES
        ('user-demo-admin', 'tenant-demo', 'workspace-demo', 'admin@demo.local', ARRAY['admin'], NOW()),
        ('user-demo-approver', 'tenant-demo', 'workspace-demo', 'approver@demo.local', ARRAY['approver'], NOW()),
        ('user-demo-user', 'tenant-demo', 'workspace-demo', 'user@demo.local', ARRAY['user'], NOW())
    ON CONFLICT (id) DO NOTHING;

    -- Seed demo approval
    INSERT INTO dmos_approvals (
        request_id, tenant_id, workspace_id, target_type, target_id, description,
        risk_level, required_approver_role, status, submitted_at, submitted_by,
        snapshot_summary, snapshot_at
    ) VALUES (
        'approval-demo-1', 'tenant-demo', 'workspace-demo', 'STRATEGY', 'strategy-demo-1',
        'Demo Q3 Strategy for local development', 2, 'marketing-director', 'PENDING',
        NOW(), 'user-demo-user', 'Demo Strategy Snapshot', NOW()
    ) ON CONFLICT (request_id) DO NOTHING;
END $$;
