-- V018: Create media_artifacts table for Data Cloud media storage
--
-- Stores metadata for audio, video, and image artifacts produced by or ingested
-- by AV-domain agents (AudioTranscriptionAgent, MultimodalAnalysisAgent, etc.).
-- Artifact bytes are stored in blob storage; this table holds metadata only.
--
-- WS3-7: Extended with comprehensive governance and lifecycle fields for
-- first-class media modality with consent, retention, classification, and
-- processing job tracking.

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
    status              VARCHAR(50)     NOT NULL DEFAULT 'ACTIVE',
    processing_state    VARCHAR(50)     NOT NULL DEFAULT 'REGISTERED',
    content_class       VARCHAR(50),
    privacy_class       VARCHAR(50)     NOT NULL DEFAULT 'INTERNAL',
    consent_status      VARCHAR(50),
    retention_policy    VARCHAR(255),
    retention_until     TIMESTAMPTZ,
    storage_provider    VARCHAR(255),
    lineage_ref         TEXT,
    policy_context      VARCHAR(255),
    redaction_state     VARCHAR(50)     NOT NULL DEFAULT 'NONE',
    owner_id            VARCHAR(255),
    source_system       VARCHAR(255)    NOT NULL DEFAULT 'media-artifact-service',
    metadata            JSONB           NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ     NOT NULL,
    updated_at          TIMESTAMPTZ     NOT NULL,
    processing_job_id   VARCHAR(255),
    transcript_id       VARCHAR(255),
    frame_index_id      VARCHAR(255),
    last_error          TEXT,
    created_by          VARCHAR(255)    NOT NULL,
    updated_by          VARCHAR(255),
    deleted_at          TIMESTAMPTZ
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

-- WS3-7: Secondary index: processing state for job queue management
CREATE INDEX IF NOT EXISTS idx_media_artifacts_processing_state
    ON media_artifacts (tenant_id, processing_state, created_at DESC);

-- WS3-7: Secondary index: consent status for consent management
CREATE INDEX IF NOT EXISTS idx_media_artifacts_consent_status
    ON media_artifacts (tenant_id, consent_status, created_at DESC)
    WHERE consent_status IS NOT NULL;

-- WS3-7: Secondary index: retention expiration for cleanup jobs
CREATE INDEX IF NOT EXISTS idx_media_artifacts_retention_until
    ON media_artifacts (tenant_id, retention_until)
    WHERE retention_until IS NOT NULL;

-- WS3-7: Secondary index: soft delete tracking
CREATE INDEX IF NOT EXISTS idx_media_artifacts_deleted_at
    ON media_artifacts (tenant_id, deleted_at)
    WHERE deleted_at IS NOT NULL;

-- WS3-7: Enable Row Level Security for tenant isolation
ALTER TABLE media_artifacts ENABLE ROW LEVEL SECURITY;

-- WS3-7: RLS policy: tenants can only see their own artifacts
CREATE POLICY media_artifacts_tenant_isolation ON media_artifacts
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant_id', true));

-- WS3-7: RLS policy: allow service role to bypass RLS for administrative operations
CREATE POLICY media_artifacts_service_bypass ON media_artifacts
    FOR ALL
    TO service_role
    USING (true)
    WITH CHECK (true);
