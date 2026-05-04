-- Migration V17: Add database integrity constraints (P1-6)
-- This migration adds tenant_id, FK relationships, check constraints, lifecycle fields, and unique constraints

-- Add tenant_id to campaigns table (if not exists)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'dmos_campaigns' AND column_name = 'tenant_id'
    ) THEN
        ALTER TABLE dmos_campaigns ADD COLUMN tenant_id VARCHAR(255);
        ALTER TABLE dmos_campaigns ADD CONSTRAINT fk_campaigns_tenant
            FOREIGN KEY (tenant_id) REFERENCES dmos_workspaces(tenant_id);
        CREATE INDEX idx_campaigns_tenant ON dmos_campaigns(tenant_id);
    END IF;
END $$;

-- Add lifecycle fields (created_at, updated_at, deleted_at) to campaigns
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'dmos_campaigns' AND column_name = 'created_at'
    ) THEN
        ALTER TABLE dmos_campaigns ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
        ALTER TABLE dmos_campaigns ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
        ALTER TABLE dmos_campaigns ADD COLUMN deleted_at TIMESTAMP;
        
        -- Add trigger to update updated_at
        CREATE OR REPLACE FUNCTION update_updated_at_column()
        RETURNS TRIGGER AS $$
        BEGIN
            NEW.updated_at = CURRENT_TIMESTAMP;
            RETURN NEW;
        END;
        $$ language 'plpgsql';
        
        CREATE TRIGGER update_campaigns_updated_at
            BEFORE UPDATE ON dmos_campaigns
            FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;

-- Add check constraints for campaign status
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'dmos_campaigns' AND constraint_name = 'ck_campaigns_status'
    ) THEN
        ALTER TABLE dmos_campaigns ADD CONSTRAINT ck_campaigns_status
            CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'COMPLETED', 'CANCELLED'));
    END IF;
END $$;

-- Add unique constraint on campaign name within workspace
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'dmos_campaigns' AND constraint_name = 'uk_campaigns_workspace_name'
    ) THEN
        ALTER TABLE dmos_campaigns ADD CONSTRAINT uk_campaigns_workspace_name
            UNIQUE (workspace_id, name);
    END IF;
END $$;

-- Add tenant_id to approval snapshots
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'dmos_approval_snapshots' AND column_name = 'tenant_id'
    ) THEN
        ALTER TABLE dmos_approval_snapshots ADD COLUMN tenant_id VARCHAR(255);
        ALTER TABLE dmos_approval_snapshots ADD CONSTRAINT fk_approvals_tenant
            FOREIGN KEY (tenant_id) REFERENCES dmos_workspaces(tenant_id);
        CREATE INDEX idx_approvals_tenant ON dmos_approval_snapshots(tenant_id);
    END IF;
END $$;

-- Add lifecycle fields to approval snapshots
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'dmos_approval_snapshots' AND column_name = 'created_at'
    ) THEN
        ALTER TABLE dmos_approval_snapshots ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
        ALTER TABLE dmos_approval_snapshots ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
        ALTER TABLE dmos_approval_snapshots ADD COLUMN deleted_at TIMESTAMP;
        
        CREATE TRIGGER update_approvals_updated_at
            BEFORE UPDATE ON dmos_approval_snapshots
            FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;

-- Add check constraint for approval status
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'dmos_approval_snapshots' AND constraint_name = 'ck_approvals_status'
    ) THEN
        ALTER TABLE dmos_approval_snapshots ADD CONSTRAINT ck_approvals_status
            CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'));
    END IF;
END $$;

-- Add tenant_id to AI action log
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'dmos_ai_action_log' AND column_name = 'tenant_id'
    ) THEN
        ALTER TABLE dmos_ai_action_log ADD COLUMN tenant_id VARCHAR(255);
        ALTER TABLE dmos_ai_action_log ADD CONSTRAINT fk_ai_actions_tenant
            FOREIGN KEY (tenant_id) REFERENCES dmos_workspaces(tenant_id);
        CREATE INDEX idx_ai_actions_tenant ON dmos_ai_action_log(tenant_id);
    END IF;
END $$;

-- Add lifecycle fields to AI action log
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'dmos_ai_action_log' AND column_name = 'created_at'
    ) THEN
        ALTER TABLE dmos_ai_action_log ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
        ALTER TABLE dmos_ai_action_log ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
        ALTER TABLE dmos_ai_action_log ADD COLUMN deleted_at TIMESTAMP;
        
        CREATE TRIGGER update_ai_actions_updated_at
            BEFORE UPDATE ON dmos_ai_action_log
            FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;

-- Add check constraint for AI action status
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'dmos_ai_action_log' AND constraint_name = 'ck_ai_actions_status'
    ) THEN
        ALTER TABLE dmos_ai_action_log ADD CONSTRAINT ck_ai_actions_status
            CHECK (status IN ('INITIATED', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'CANCELLED'));
    END IF;
END $$;

-- Add foreign key from campaigns to workspaces
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'dmos_campaigns' AND constraint_name = 'fk_campaigns_workspace'
    ) THEN
        ALTER TABLE dmos_campaigns ADD CONSTRAINT fk_campaigns_workspace
            FOREIGN KEY (workspace_id) REFERENCES dmos_workspaces(workspace_id)
            ON DELETE CASCADE;
    END IF;
END $$;

-- Add foreign key from approval snapshots to workspaces
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'dmos_approval_snapshots' AND constraint_name = 'fk_approvals_workspace'
    ) THEN
        ALTER TABLE dmos_approval_snapshots ADD CONSTRAINT fk_approvals_workspace
            FOREIGN KEY (workspace_id) REFERENCES dmos_workspaces(workspace_id)
            ON DELETE CASCADE;
    END IF;
END $$;

-- Add foreign key from AI action log to workspaces
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_name = 'dmos_ai_action_log' AND constraint_name = 'fk_ai_actions_workspace'
    ) THEN
        ALTER TABLE dmos_ai_action_log ADD CONSTRAINT fk_ai_actions_workspace
            FOREIGN KEY (workspace_id) REFERENCES dmos_workspaces(workspace_id)
            ON DELETE CASCADE;
    END IF;
END $$;
