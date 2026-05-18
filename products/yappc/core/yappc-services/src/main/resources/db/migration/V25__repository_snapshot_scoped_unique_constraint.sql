-- V25__repository_snapshot_scoped_unique_constraint.sql
-- P0: Add scoped unique constraint to repository_snapshots for proper conflict resolution
-- This ensures the ON CONFLICT clause in RepositorySnapshotRepository uses the correct scoped key
-- to prevent cross-tenant conflicts and ensure proper tenant/workspace/project isolation

-- Drop any existing unscoped unique constraint on snapshot_id if it exists
DROP INDEX IF EXISTS uk_repository_snapshots_snapshot_id;

-- Add scoped unique constraint matching the ON CONFLICT clause in RepositorySnapshotRepository
-- This ensures that conflicts are scoped to tenant/workspace/project scope
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint 
        WHERE conname = 'uk_repository_snapshot_scope'
    ) THEN
        ALTER TABLE repository_snapshots
        ADD CONSTRAINT uk_repository_snapshot_scope
        UNIQUE (tenant_id, workspace_id, project_id, repository_id, commit_sha);
    END IF;
END $$;

-- Add comment to document the scoped constraint
COMMENT ON CONSTRAINT uk_repository_snapshot_scope ON repository_snapshots IS
    'P0: Scoped unique constraint to prevent cross-tenant snapshot conflicts. 
     Ensures that (tenant_id, workspace_id, project_id, repository_id, commit_sha) is unique.
     Used by RepositorySnapshotRepository ON CONFLICT clause for safe upserts.';
