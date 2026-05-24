-- YAPPC-011: Create canonical Data Cloud schema for Kernel lifecycle truth
-- This migration adds the kernel_lifecycle_truth table for storing durable state/evidence for all lifecycle phases

CREATE TABLE IF NOT EXISTS kernel_lifecycle_truth (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_unit_id VARCHAR(255) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    phase VARCHAR(100) NOT NULL, -- e.g., 'validate', 'test', 'build', 'package', 'deploy', 'verify', 'rollback'
    phase_status VARCHAR(50) NOT NULL, -- e.g., 'started', 'completed', 'failed', 'blocked'
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    phase_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    manifest_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
    evidence_refs JSONB NOT NULL DEFAULT '[]'::jsonb,
    rollback_available BOOLEAN NOT NULL DEFAULT false,
    rollback_artifact_id VARCHAR(255),
    rollback_manifest_digest VARCHAR(255),
    executed_by VARCHAR(255) NOT NULL DEFAULT 'kernel',
    correlation_id VARCHAR(255),
    tenant_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    CONSTRAINT uq_kernel_lifecycle_phase UNIQUE (product_unit_id, phase, occurred_at)
);

-- Indexes for kernel_lifecycle_truth
CREATE INDEX IF NOT EXISTS idx_kernel_lifecycle_truth_product_unit_id ON kernel_lifecycle_truth(product_unit_id);
CREATE INDEX IF NOT EXISTS idx_kernel_lifecycle_truth_product_id ON kernel_lifecycle_truth(product_id);
CREATE INDEX IF NOT EXISTS idx_kernel_lifecycle_truth_phase ON kernel_lifecycle_truth(phase);
CREATE INDEX IF NOT EXISTS idx_kernel_lifecycle_truth_phase_status ON kernel_lifecycle_truth(phase_status);
CREATE INDEX IF NOT EXISTS idx_kernel_lifecycle_truth_occurred_at ON kernel_lifecycle_truth(occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_kernel_lifecycle_truth_rollback_available ON kernel_lifecycle_truth(rollback_available);
CREATE INDEX IF NOT EXISTS idx_kernel_lifecycle_truth_tenant_id ON kernel_lifecycle_truth(tenant_id);
CREATE INDEX IF NOT EXISTS idx_kernel_lifecycle_truth_correlation_id ON kernel_lifecycle_truth(correlation_id);

-- Row Level Security (RLS) for tenant isolation
ALTER TABLE kernel_lifecycle_truth ENABLE ROW LEVEL SECURITY;

-- RLS policy for kernel_lifecycle_truth
CREATE POLICY kernel_lifecycle_truth_tenant_isolation ON kernel_lifecycle_truth
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant_id', true));

-- Comment for documentation
COMMENT ON TABLE kernel_lifecycle_truth IS 'Stores durable state and evidence for all Kernel lifecycle phases including rollback visibility';
