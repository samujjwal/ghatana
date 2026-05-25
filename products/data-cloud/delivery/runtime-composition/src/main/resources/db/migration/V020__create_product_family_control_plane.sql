-- Migration: Create product-family control-plane collections
-- Purpose: Canonical schema for product-family control-plane data (PHR, Digital Marketing, etc.)
-- Schema Version: 1.0.0
-- Author: Ghatana Kernel Team
-- Date: 2026-05-23

-- Product Family Registry
CREATE TABLE IF NOT EXISTS product_family_registry (
    id BIGSERIAL PRIMARY KEY,
    family_id VARCHAR(255) NOT NULL UNIQUE,
    family_name VARCHAR(255) NOT NULL,
    description TEXT,
    owner_team VARCHAR(255) NOT NULL,
    lifecycle_profile VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    tenant_id VARCHAR(255) NOT NULL
);

-- Product Family Members
CREATE TABLE IF NOT EXISTS product_family_members (
    id BIGSERIAL PRIMARY KEY,
    family_id VARCHAR(255) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    member_role VARCHAR(100) NOT NULL, -- 'primary', 'secondary', 'auxiliary'
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    tenant_id VARCHAR(255) NOT NULL,
    CONSTRAINT fk_family_members_family FOREIGN KEY (family_id) REFERENCES product_family_registry(family_id) ON DELETE CASCADE,
    CONSTRAINT uq_family_product UNIQUE (family_id, product_id, tenant_id)
);

-- Product Family Release Readiness
CREATE TABLE IF NOT EXISTS product_family_release_readiness (
    id BIGSERIAL PRIMARY KEY,
    family_id VARCHAR(255) NOT NULL,
    environment VARCHAR(50) NOT NULL, -- 'local', 'dev', 'staging', 'prod'
    status VARCHAR(50) NOT NULL, -- 'ready', 'blocked', 'partial', 'pending'
    overall_score DECIMAL(3,1),
    blocking_issues JSONB,
    warnings JSONB,
    checked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    tenant_id VARCHAR(255) NOT NULL,
    CONSTRAINT fk_release_readiness_family FOREIGN KEY (family_id) REFERENCES product_family_registry(family_id) ON DELETE CASCADE,
    CONSTRAINT uq_family_environment UNIQUE (family_id, environment, tenant_id)
);

-- Product Family Reusable Assets
CREATE TABLE IF NOT EXISTS product_family_reusable_assets (
    id BIGSERIAL PRIMARY KEY,
    family_id VARCHAR(255) NOT NULL,
    asset_id VARCHAR(255) NOT NULL,
    asset_name VARCHAR(255) NOT NULL,
    asset_type VARCHAR(100) NOT NULL, -- 'component', 'pattern', 'workflow', 'policy'
    asset_category VARCHAR(100) NOT NULL, -- 'ui', 'service', 'gateway', 'validation'
    description TEXT,
    source_product_id VARCHAR(255),
    source_file_path TEXT,
    promoted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    promotion_status VARCHAR(50) NOT NULL, -- 'draft', 'proposed', 'approved', 'rejected'
    tenant_id VARCHAR(255) NOT NULL,
    CONSTRAINT fk_reusable_assets_family FOREIGN KEY (family_id) REFERENCES product_family_registry(family_id) ON DELETE CASCADE,
    CONSTRAINT uq_reusable_assets_asset_tenant UNIQUE (asset_id, tenant_id),
    CONSTRAINT uq_family_asset UNIQUE (family_id, asset_id, tenant_id)
);

-- Product Family Asset Adoption
CREATE TABLE IF NOT EXISTS product_family_asset_adoption (
    id BIGSERIAL PRIMARY KEY,
    asset_id VARCHAR(255) NOT NULL,
    adopting_product_id VARCHAR(255) NOT NULL,
    adoption_status VARCHAR(50) NOT NULL, -- 'adopted', 'adapted', 'customized', 'rejected'
    adoption_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    adaptation_notes TEXT,
    tenant_id VARCHAR(255) NOT NULL,
    CONSTRAINT fk_asset_adoption_asset FOREIGN KEY (asset_id, tenant_id)
        REFERENCES product_family_reusable_assets(asset_id, tenant_id) ON DELETE CASCADE,
    CONSTRAINT uq_asset_product UNIQUE (asset_id, adopting_product_id, tenant_id)
);

-- Row Level Security Policies
ALTER TABLE product_family_registry ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_family_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_family_release_readiness ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_family_reusable_assets ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_family_asset_adoption ENABLE ROW LEVEL SECURITY;

-- RLS Policies: Tenant isolation
CREATE POLICY product_family_registry_tenant_isolation ON product_family_registry
    FOR ALL USING (tenant_id = tenant_security.get_current_tenant());

CREATE POLICY product_family_members_tenant_isolation ON product_family_members
    FOR ALL USING (tenant_id = tenant_security.get_current_tenant());

CREATE POLICY product_family_release_readiness_tenant_isolation ON product_family_release_readiness
    FOR ALL USING (tenant_id = tenant_security.get_current_tenant());

CREATE POLICY product_family_reusable_assets_tenant_isolation ON product_family_reusable_assets
    FOR ALL USING (tenant_id = tenant_security.get_current_tenant());

CREATE POLICY product_family_asset_adoption_tenant_isolation ON product_family_asset_adoption
    FOR ALL USING (tenant_id = tenant_security.get_current_tenant());

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_product_family_registry_tenant ON product_family_registry(tenant_id);
CREATE INDEX IF NOT EXISTS idx_product_family_members_family ON product_family_members(family_id);
CREATE INDEX IF NOT EXISTS idx_product_family_members_tenant ON product_family_members(tenant_id);
CREATE INDEX IF NOT EXISTS idx_product_family_release_readiness_family_env ON product_family_release_readiness(family_id, environment);
CREATE INDEX IF NOT EXISTS idx_product_family_release_readiness_tenant ON product_family_release_readiness(tenant_id);
CREATE INDEX IF NOT EXISTS idx_product_family_reusable_assets_family ON product_family_reusable_assets(family_id);
CREATE INDEX IF NOT EXISTS idx_product_family_reusable_assets_type ON product_family_reusable_assets(asset_type);
CREATE INDEX IF NOT EXISTS idx_product_family_reusable_assets_tenant ON product_family_reusable_assets(tenant_id);
CREATE INDEX IF NOT EXISTS idx_product_family_asset_adoption_asset ON product_family_asset_adoption(asset_id);
CREATE INDEX IF NOT EXISTS idx_product_family_asset_adoption_product ON product_family_asset_adoption(adopting_product_id);
CREATE INDEX IF NOT EXISTS idx_product_family_asset_adoption_tenant ON product_family_asset_adoption(tenant_id);

-- Triggers for updated_at
CREATE OR REPLACE FUNCTION update_product_family_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_product_family_registry_updated_at
    BEFORE UPDATE ON product_family_registry
    FOR EACH ROW EXECUTE FUNCTION update_product_family_updated_at();

-- Comments for documentation
COMMENT ON TABLE product_family_registry IS 'Registry of product families (PHR, Digital Marketing, etc.)';
COMMENT ON TABLE product_family_members IS 'Products that belong to each product family';
COMMENT ON TABLE product_family_release_readiness IS 'Release readiness status per family per environment';
COMMENT ON TABLE product_family_reusable_assets IS 'Reusable assets promoted from products to family level';
COMMENT ON TABLE product_family_asset_adoption IS 'Track which products have adopted which reusable assets';
