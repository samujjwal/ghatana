-- V15__create_repository_snapshots.sql
-- Durable source snapshot manifests for Java-owned source import orchestration.

CREATE TABLE IF NOT EXISTS repository_snapshots (
    id                    VARCHAR(255) PRIMARY KEY,
    tenant_id             VARCHAR(255) NOT NULL,
    workspace_id          VARCHAR(255) NOT NULL,
    project_id            VARCHAR(255) NOT NULL,
    provider              VARCHAR(64) NOT NULL,
    repo_id               VARCHAR(1024) NOT NULL,
    commit_sha            VARCHAR(255),
    branch                VARCHAR(255),
    local_root_path       TEXT NOT NULL,
    content_checksum      VARCHAR(64),
    diagnostics_json      JSONB,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, workspace_id, project_id, provider, repo_id, commit_sha)
);

CREATE TABLE IF NOT EXISTS repository_snapshot_files (
    id                    BIGSERIAL PRIMARY KEY,
    snapshot_id           VARCHAR(255) NOT NULL REFERENCES repository_snapshots(id) ON DELETE CASCADE,
    relative_path         TEXT NOT NULL,
    absolute_path         TEXT,
    size_bytes            BIGINT NOT NULL,
    last_modified_at      TIMESTAMPTZ,
    materialized          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (snapshot_id, relative_path)
);

CREATE INDEX IF NOT EXISTS idx_repository_snapshots_scope
ON repository_snapshots(tenant_id, workspace_id, project_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_repository_snapshot_files_snapshot
ON repository_snapshot_files(snapshot_id);
