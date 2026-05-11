-- Create generation_runs table for durable generation run storage with provenance
CREATE TABLE IF NOT EXISTS generation_runs (
    id VARCHAR(255) PRIMARY KEY,
    plan_id VARCHAR(255) NOT NULL,
    project_id VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    workspace_id VARCHAR(255) NOT NULL,
    intent JSONB NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('PENDING', 'GENERATING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    artifact_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    review_status VARCHAR(50) NOT NULL DEFAULT 'PENDING' CHECK (review_status IN ('PENDING', 'APPROVED', 'REJECTED', 'ROLLED_BACK')),
    preview_session_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    provenance JSONB NOT NULL DEFAULT '{}'::jsonb,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

-- Create indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_generation_runs_project_id ON generation_runs(project_id);
CREATE INDEX IF NOT EXISTS idx_generation_runs_plan_id ON generation_runs(plan_id);
CREATE INDEX IF NOT EXISTS idx_generation_runs_tenant_id ON generation_runs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_generation_runs_workspace_id ON generation_runs(workspace_id);
CREATE INDEX IF NOT EXISTS idx_generation_runs_status ON generation_runs(status);
CREATE INDEX IF NOT EXISTS idx_generation_runs_review_status ON generation_runs(review_status);
CREATE INDEX IF NOT EXISTS idx_generation_runs_created_at ON generation_runs(created_at DESC);

-- Add foreign key constraints if the referenced tables exist
-- Note: These constraints are optional and should be added when the referenced tables are available
-- ALTER TABLE generation_runs ADD CONSTRAINT fk_generation_runs_project_id 
--     FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE;
-- ALTER TABLE generation_runs ADD CONSTRAINT fk_generation_runs_tenant_id 
--     FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
-- ALTER TABLE generation_runs ADD CONSTRAINT fk_generation_runs_workspace_id 
--     FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE CASCADE;
