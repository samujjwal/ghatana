-- DMOS P0: Tenant/workspace isolation hardening for campaigns and approval snapshots

ALTER TABLE dmos_campaigns
    ADD COLUMN IF NOT EXISTS tenant_id TEXT;

ALTER TABLE dmos_approval_snapshots
    ADD COLUMN IF NOT EXISTS tenant_id TEXT;

UPDATE dmos_campaigns c
SET tenant_id = w.tenant_id
FROM dmos_workspaces w
WHERE c.workspace_id = w.id
  AND c.tenant_id IS NULL;

UPDATE dmos_approval_snapshots s
SET tenant_id = w.tenant_id
FROM dmos_workspaces w
WHERE s.workspace_id = w.id
  AND s.tenant_id IS NULL;

ALTER TABLE dmos_campaigns
    ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE dmos_approval_snapshots
    ALTER COLUMN tenant_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS dmos_workspaces_id_tenant_uq
    ON dmos_workspaces (id, tenant_id);

DO $$
BEGIN
    ALTER TABLE dmos_campaigns
        ADD CONSTRAINT fk_campaigns_workspace_tenant
        FOREIGN KEY (workspace_id, tenant_id)
        REFERENCES dmos_workspaces (id, tenant_id)
        ON DELETE CASCADE;
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE dmos_approval_snapshots
        ADD CONSTRAINT fk_approval_snapshots_workspace_tenant
        FOREIGN KEY (workspace_id, tenant_id)
        REFERENCES dmos_workspaces (id, tenant_id)
        ON DELETE CASCADE;
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$
BEGIN
    ALTER TABLE dmos_approval_snapshots
        ADD CONSTRAINT fk_approval_snapshots_target_workspace_tenant
        FOREIGN KEY (target_workspace_id, tenant_id)
        REFERENCES dmos_workspaces (id, tenant_id)
        ON DELETE CASCADE;
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

CREATE INDEX IF NOT EXISTS dmos_campaigns_tenant_workspace_idx
    ON dmos_campaigns (tenant_id, workspace_id);

CREATE INDEX IF NOT EXISTS dmos_approval_snapshots_tenant_workspace_idx
    ON dmos_approval_snapshots (tenant_id, workspace_id);

CREATE INDEX IF NOT EXISTS dmos_approval_snapshots_tenant_target_workspace_idx
    ON dmos_approval_snapshots (tenant_id, target_workspace_id);
