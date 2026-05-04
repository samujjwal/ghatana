-- Copyright (c) 2025 Ghatana Platform Contributors
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- Create page_artifacts table for durable page artifact persistence
-- Supports tenant/workspace/project scoping, optimistic concurrency, and historical versions

CREATE TABLE IF NOT EXISTS page_artifacts (
    -- Primary key with tenant/workspace/project scoping
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    project_id VARCHAR(255) NOT NULL,
    artifact_id VARCHAR(255) NOT NULL,

    -- Document identifier for optimistic concurrency (ETag)
    document_id VARCHAR(255) NOT NULL,

    -- Artifact metadata
    name VARCHAR(500) NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Sync and trust metadata
    sync_status VARCHAR(50) NOT NULL DEFAULT 'SYNCED',
    trust_level VARCHAR(50) NOT NULL DEFAULT 'UNKNOWN',
    data_classification VARCHAR(50) NOT NULL DEFAULT 'UNCLASSIFIED',
    source VARCHAR(255),

    -- Builder document (JSON payload)
    builder_document JSONB NOT NULL,

    -- Validation summary (JSON)
    validation_summary JSONB,

    -- Governance records (JSON array)
    ai_change_records JSONB,

    -- Compiler/decompiler metrics
    residual_island_count INTEGER NOT NULL DEFAULT 0,
    round_trip_fidelity DOUBLE PRECISION NOT NULL DEFAULT 1.0,

    -- Constraints
    CONSTRAINT uq_tenant_workspace_project_artifact UNIQUE (tenant_id, workspace_id, project_id, artifact_id),
    CONSTRAINT chk_sync_status CHECK (sync_status IN ('SYNCED', 'PENDING', 'CONFLICT', 'ERROR')),
    CONSTRAINT chk_trust_level CHECK (trust_level IN ('TRUSTED', 'UNTRUSTED', 'UNKNOWN', 'REVIEW_REQUIRED')),
    CONSTRAINT chk_data_classification CHECK (data_classification IN ('UNCLASSIFIED', 'PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED')),
    CONSTRAINT chk_round_trip_fidelity CHECK (round_trip_fidelity >= 0.0 AND round_trip_fidelity <= 1.0),
    CONSTRAINT chk_residual_island_count CHECK (residual_island_count >= 0)
);

-- Create indexes for common query patterns
CREATE INDEX idx_page_artifacts_tenant ON page_artifacts(tenant_id);
CREATE INDEX idx_page_artifacts_workspace ON page_artifacts(workspace_id);
CREATE INDEX idx_page_artifacts_project ON page_artifacts(project_id);
CREATE INDEX idx_page_artifacts_artifact ON page_artifacts(artifact_id);
CREATE INDEX idx_page_artifacts_document_id ON page_artifacts(document_id);
CREATE INDEX idx_page_artifacts_sync_status ON page_artifacts(sync_status);
CREATE INDEX idx_page_artifacts_trust_level ON page_artifacts(trust_level);
CREATE INDEX idx_page_artifacts_updated_at ON page_artifacts(updated_at DESC);

-- Create historical versions table for rollback support
CREATE TABLE IF NOT EXISTS page_artifact_versions (
    id BIGSERIAL PRIMARY KEY,
    page_artifact_id BIGINT NOT NULL REFERENCES page_artifacts(id) ON DELETE CASCADE,
    
    tenant_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    project_id VARCHAR(255) NOT NULL,
    artifact_id VARCHAR(255) NOT NULL,
    document_id VARCHAR(255) NOT NULL,
    
    name VARCHAR(500) NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    
    sync_status VARCHAR(50) NOT NULL,
    trust_level VARCHAR(50) NOT NULL,
    data_classification VARCHAR(50) NOT NULL,
    source VARCHAR(255),
    
    builder_document JSONB NOT NULL,
    validation_summary JSONB,
    ai_change_records JSONB,
    
    residual_island_count INTEGER NOT NULL,
    round_trip_fidelity DOUBLE PRECISION NOT NULL,
    
    version_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version_reason VARCHAR(500)
);

CREATE INDEX idx_page_artifact_versions_artifact ON page_artifact_versions(artifact_id);
CREATE INDEX idx_page_artifact_versions_document_id ON page_artifact_versions(document_id);
CREATE INDEX idx_page_artifact_versions_created_at ON page_artifact_versions(version_created_at DESC);
