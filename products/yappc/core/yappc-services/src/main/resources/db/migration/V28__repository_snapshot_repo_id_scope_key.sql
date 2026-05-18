-- V28__repository_snapshot_repo_id_scope_key.sql
-- Align repository_snapshots scoped uniqueness with RepositorySnapshotRepository upsert key.
-- Canonical scoped key: (tenant_id, workspace_id, project_id, provider, repo_id, commit_sha)

-- Remove conflicting scoped key variants that drifted from runtime SQL.
ALTER TABLE repository_snapshots
DROP CONSTRAINT IF EXISTS uk_repository_snapshot_scope;

-- Ensure canonical scoped key exists for ON CONFLICT in RepositorySnapshotRepository.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_repository_snapshot_provider_repo_scope'
    ) THEN
        ALTER TABLE repository_snapshots
        ADD CONSTRAINT uk_repository_snapshot_provider_repo_scope
        UNIQUE (tenant_id, workspace_id, project_id, provider, repo_id, commit_sha);
    END IF;
END $$;

COMMENT ON CONSTRAINT uk_repository_snapshot_provider_repo_scope ON repository_snapshots IS
    'Scoped uniqueness for durable snapshot identity: tenant/workspace/project/provider/repo/commit.';
