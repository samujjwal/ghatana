-- YAPPC-001: Create canonical Data Cloud schema for product-family control-plane collections
-- This migration adds tables for managing product release readiness and reusable assets across the product family

-- Product Release Readiness table
CREATE TABLE IF NOT EXISTS product_release_readiness (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id VARCHAR(255) NOT NULL,
    product_version VARCHAR(100) NOT NULL,
    release_target VARCHAR(50) NOT NULL, -- e.g., 'production', 'staging', 'development'
    release_verdict VARCHAR(20) NOT NULL, -- 'pass' or 'fail'
    average_score DECIMAL(3,2),
    release_target_score DECIMAL(3,2),
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    evidence JSONB NOT NULL,
    blocking_gaps JSONB NOT NULL DEFAULT '[]'::jsonb,
    below_target_dimensions JSONB NOT NULL DEFAULT '[]'::jsonb,
    tenant_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    CONSTRAINT uq_product_release UNIQUE (product_id, product_version, release_target)
);

-- Indexes for product_release_readiness
CREATE INDEX IF NOT EXISTS idx_product_release_readiness_product_id ON product_release_readiness(product_id);
CREATE INDEX IF NOT EXISTS idx_product_release_readiness_release_target ON product_release_readiness(release_target);
CREATE INDEX IF NOT EXISTS idx_product_release_readiness_verdict ON product_release_readiness(release_verdict);
CREATE INDEX IF NOT EXISTS idx_product_release_readiness_tenant_id ON product_release_readiness(tenant_id);
CREATE INDEX IF NOT EXISTS idx_product_release_readiness_generated_at ON product_release_readiness(generated_at DESC);

-- Product Family Assets table
CREATE TABLE IF NOT EXISTS product_family_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id VARCHAR(255) NOT NULL UNIQUE,
    asset_name VARCHAR(255) NOT NULL,
    asset_type VARCHAR(100) NOT NULL, -- e.g., 'ui-component', 'service', 'workflow', 'policy'
    source_product_id VARCHAR(255) NOT NULL, -- Product that contributed the asset
    description TEXT,
    tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    maturity_level VARCHAR(50) NOT NULL, -- e.g., 'stable', 'beta', 'experimental'
    usage_count INTEGER NOT NULL DEFAULT 0,
    last_used_at TIMESTAMP WITH TIME ZONE,
    promotion_status VARCHAR(50) NOT NULL DEFAULT 'draft', -- 'draft', 'proposed', 'approved', 'rejected'
    promotion_evidence JSONB,
    tenant_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    CONSTRAINT chk_promotion_status CHECK (promotion_status IN ('draft', 'proposed', 'approved', 'rejected'))
);

-- Indexes for product_family_assets
CREATE INDEX IF NOT EXISTS idx_product_family_assets_asset_id ON product_family_assets(asset_id);
CREATE INDEX IF NOT EXISTS idx_product_family_assets_source_product_id ON product_family_assets(source_product_id);
CREATE INDEX IF NOT EXISTS idx_product_family_assets_asset_type ON product_family_assets(asset_type);
CREATE INDEX IF NOT EXISTS idx_product_family_assets_promotion_status ON product_family_assets(promotion_status);
CREATE INDEX IF NOT EXISTS idx_product_family_assets_tenant_id ON product_family_assets(tenant_id);
CREATE INDEX IF NOT EXISTS idx_product_family_assets_tags ON product_family_assets USING GIN(tags);

-- Asset Promotion History table
CREATE TABLE IF NOT EXISTS asset_promotion_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id VARCHAR(255) NOT NULL,
    source_product_id VARCHAR(255) NOT NULL,
    target_product_id VARCHAR(255) NOT NULL,
    promotion_status VARCHAR(50) NOT NULL,
    promoted_by VARCHAR(255) NOT NULL,
    promoted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    review_comments TEXT,
    evidence JSONB,
    tenant_id VARCHAR(255) NOT NULL,
    
    CONSTRAINT fk_asset_promotion_asset FOREIGN KEY (asset_id) REFERENCES product_family_assets(asset_id) ON DELETE CASCADE
);

-- Indexes for asset_promotion_history
CREATE INDEX IF NOT EXISTS idx_asset_promotion_history_asset_id ON asset_promotion_history(asset_id);
CREATE INDEX IF NOT EXISTS idx_asset_promotion_history_source_product_id ON asset_promotion_history(source_product_id);
CREATE INDEX IF NOT EXISTS idx_asset_promotion_history_target_product_id ON asset_promotion_history(target_product_id);
CREATE INDEX IF NOT EXISTS idx_asset_promotion_history_promoted_at ON asset_promotion_history(promoted_at DESC);
CREATE INDEX IF NOT EXISTS idx_asset_promotion_history_tenant_id ON asset_promotion_history(tenant_id);

-- Product Family Asset Promotion Policy table
CREATE TABLE IF NOT EXISTS product_family_asset_promotion_policy (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_id VARCHAR(255) NOT NULL UNIQUE,
    policy_name VARCHAR(255) NOT NULL,
    policy_type VARCHAR(100) NOT NULL, -- e.g., 'automatic', 'manual', 'gated'
    source_product_id VARCHAR(255) NOT NULL,
    target_product_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    asset_types JSONB NOT NULL DEFAULT '[]'::jsonb,
    required_maturity_level VARCHAR(50),
    approval_workflow_id VARCHAR(255),
    policy_rules JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_active BOOLEAN NOT NULL DEFAULT true,
    tenant_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Indexes for product_family_asset_promotion_policy
CREATE INDEX IF NOT EXISTS idx_asset_promotion_policy_policy_id ON product_family_asset_promotion_policy(policy_id);
CREATE INDEX IF NOT EXISTS idx_asset_promotion_policy_source_product_id ON product_family_asset_promotion_policy(source_product_id);
CREATE INDEX IF NOT EXISTS idx_asset_promotion_policy_is_active ON product_family_asset_promotion_policy(is_active);
CREATE INDEX IF NOT EXISTS idx_asset_promotion_policy_tenant_id ON product_family_asset_promotion_policy(tenant_id);

-- Row Level Security (RLS) for tenant isolation
ALTER TABLE product_release_readiness ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_family_assets ENABLE ROW LEVEL SECURITY;
ALTER TABLE asset_promotion_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_family_asset_promotion_policy ENABLE ROW LEVEL SECURITY;

-- RLS policies for product_release_readiness
CREATE POLICY product_release_readiness_tenant_isolation ON product_release_readiness
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant_id', true));

-- RLS policies for product_family_assets
CREATE POLICY product_family_assets_tenant_isolation ON product_family_assets
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant_id', true));

-- RLS policies for asset_promotion_history
CREATE POLICY asset_promotion_history_tenant_isolation ON asset_promotion_history
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant_id', true));

-- RLS policies for product_family_asset_promotion_policy
CREATE POLICY product_family_asset_promotion_policy_tenant_isolation ON product_family_asset_promotion_policy
    FOR ALL
    USING (tenant_id = current_setting('app.current_tenant_id', true));

-- Trigger for updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_product_release_readiness_updated_at
    BEFORE UPDATE ON product_release_readiness
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_update_product_family_assets_updated_at
    BEFORE UPDATE ON product_family_assets
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trigger_update_product_family_asset_promotion_policy_updated_at
    BEFORE UPDATE ON product_family_asset_promotion_policy
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Comments for documentation
COMMENT ON TABLE product_release_readiness IS 'Stores release readiness evidence and scores for products across the product family';
COMMENT ON TABLE product_family_assets IS 'Stores reusable assets that can be promoted across products in the family';
COMMENT ON TABLE asset_promotion_history IS 'Tracks the history of asset promotions between products';
COMMENT ON TABLE product_family_asset_promotion_policy IS 'Defines policies governing asset promotion between products';
