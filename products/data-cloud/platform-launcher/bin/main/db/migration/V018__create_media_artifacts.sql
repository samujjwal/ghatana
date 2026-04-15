-- V018: Create media_artifacts table for Data Cloud media storage
--
-- Stores metadata for audio, video, and image artifacts produced by or ingested
-- by AV-domain agents (AudioTranscriptionAgent, MultimodalAnalysisAgent, etc.).
-- Artifact bytes are stored in blob storage; this table holds metadata only.

CREATE TABLE IF NOT EXISTS media_artifacts (
    id                  BIGSERIAL       PRIMARY KEY,
    artifact_id         VARCHAR(255)    NOT NULL,
    tenant_id           VARCHAR(255)    NOT NULL,
    agent_id            VARCHAR(255)    NOT NULL,
    media_type          VARCHAR(255)    NOT NULL,
    storage_uri         TEXT            NOT NULL,
    size_bytes          BIGINT          NOT NULL CHECK (size_bytes >= 0),
    checksum            VARCHAR(64),
    duration_ms         BIGINT          NOT NULL DEFAULT 0 CHECK (duration_ms >= 0),
    origin_tool_id      VARCHAR(255),
    correlation_id      VARCHAR(255),
    metadata            JSONB           NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ     NOT NULL
);

-- Primary lookup: artifact by tenant-scoped ID (tenant isolation enforced here)
CREATE UNIQUE INDEX IF NOT EXISTS uidx_media_artifacts_artifact_id
    ON media_artifacts (tenant_id, artifact_id);

-- Secondary index: agent lookup within tenant
CREATE INDEX IF NOT EXISTS idx_media_artifacts_agent
    ON media_artifacts (tenant_id, agent_id, created_at DESC);

-- Secondary index: MIME type lookup within tenant
CREATE INDEX IF NOT EXISTS idx_media_artifacts_media_type
    ON media_artifacts (tenant_id, media_type, created_at DESC);

-- Secondary index: correlation ID for trace-based lookups
CREATE INDEX IF NOT EXISTS idx_media_artifacts_correlation
    ON media_artifacts (tenant_id, correlation_id)
    WHERE correlation_id IS NOT NULL;
