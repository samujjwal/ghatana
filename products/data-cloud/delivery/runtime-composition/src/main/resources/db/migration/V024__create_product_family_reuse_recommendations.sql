-- YAPPC-012: Create canonical Data Cloud schema for product-family reuse recommendations
-- This migration adds the product_family_reuse_recommendations table for guided reuse recommendations

CREATE TABLE IF NOT EXISTS product_family_reuse_recommendations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    target_product VARCHAR(255) NOT NULL,
    recommended_asset_id VARCHAR(255) NOT NULL,
    recommendation_type VARCHAR(100) NOT NULL, -- 'ui-component', 'service', 'workflow', 'policy', 'connector'
    confidence_score DECIMAL(3,2) NOT NULL, -- 0.0 to 1.0
    rationale TEXT NOT NULL,
    adaptation_notes TEXT,
    estimated_effort_hours INTEGER,
    benefit_category VARCHAR(100), -- 'ui-consistency', 'security', 'compliance', 'performance', 'maintainability'
    is_active BOOLEAN NOT NULL DEFAULT true,
    tenant_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    CONSTRAINT uq_target_asset UNIQUE (target_product, recommended_asset_id)
);

-- Indexes for product_family_reuse_recommendations
CREATE INDEX IF NOT EXISTS idx_reuse_recommendations_target_product ON product_family_reuse_recommendations(target_product);
CREATE INDEX IF NOT EXISTS idx_reuse_recommendations_recommended_asset_id ON product_family_reuse_recommendations(recommended_asset_id);
CREATE INDEX IF NOT EXISTS idx_reuse_recommendations_type ON product_family_reuse_recommendations(recommendation_type);
CREATE INDEX IF NOT EXISTS idx_reuse_recommendations_confidence ON product_family_reuse_recommendations(confidence_score DESC);
CREATE INDEX IF NOT EXISTS idx_reuse_recommendations_active ON product_family_reuse_recommendations(is_active);
CREATE INDEX IF NOT EXISTS idx_reuse_recommendations_tenant_id ON product_family_reuse_recommendations(tenant_id);

-- Row Level Security (RLS) for tenant isolation
ALTER TABLE product_family_reuse_recommendations ENABLE ROW LEVEL SECURITY;

-- RLS policy for product_family_reuse_recommendations
CREATE POLICY product_family_reuse_recommendations_tenant_isolation ON product_family_reuse_recommendations
    FOR ALL
    USING (tenant_id = tenant_security.get_current_tenant());

-- Shared trigger function for updated_at management.
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for updated_at
CREATE TRIGGER trigger_update_product_family_reuse_recommendations_updated_at
    BEFORE UPDATE ON product_family_reuse_recommendations
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Comment for documentation
COMMENT ON TABLE product_family_reuse_recommendations IS 'Stores guided reuse recommendations for target products based on existing product-family assets';

-- Seed Tutorputor reuse recommendations
INSERT INTO product_family_reuse_recommendations (
    target_product,
    recommended_asset_id,
    recommendation_type,
    confidence_score,
    rationale,
    adaptation_notes,
    estimated_effort_hours,
    benefit_category,
    tenant_id
) VALUES
(
    'tutorputor',
    'phr-consent-ui-component',
    'ui-component',
    0.85,
    'Tutorputor requires student consent for data collection and analytics. PHR consent UI component provides GDPR-compliant consent capture and revocation.',
    'Adapt healthcare-specific language to education context. Remove HIPAA references, add FERPA compliance notes.',
    8,
    'compliance',
    'system'
),
(
    'tutorputor',
    'phr-audit-trail-service',
    'service',
    0.90,
    'Tutorputor requires immutable audit logging for educational content changes and student data access. PHR audit trail service provides tamper detection and compliance reporting.',
    'Adapt healthcare audit categories to education context (e.g., curriculum changes, grade modifications).',
    6,
    'compliance',
    'system'
),
(
    'tutorputor',
    'dmos-marketing-analytics-dashboard',
    'ui-component',
    0.75,
    'Tutorputor needs student engagement analytics dashboard. DMOS marketing analytics dashboard provides visualization patterns for metrics and trends.',
    'Replace marketing metrics with education metrics (engagement time, quiz scores, completion rates).',
    12,
    'ui-consistency',
    'system'
)
ON CONFLICT (target_product, recommended_asset_id) DO NOTHING;

-- Seed FlashIt reuse recommendations
INSERT INTO product_family_reuse_recommendations (
    target_product,
    recommended_asset_id,
    recommendation_type,
    confidence_score,
    rationale,
    adaptation_notes,
    estimated_effort_hours,
    benefit_category,
    tenant_id
) VALUES
(
    'flashit',
    'phr-tenant-isolation-middleware',
    'service',
    0.95,
    'FlashIt requires strict tenant isolation for multi-tenant flashcard decks. PHR tenant isolation middleware provides signed JWT validation and cross-tenant data leakage prevention.',
    'Minimal adaptation needed. Tenant model is compatible with flashcard deck isolation requirements.',
    4,
    'security',
    'system'
),
(
    'flashit',
    'dmos-rate-limiting-policy',
    'policy',
    0.80,
    'FlashIt needs rate limiting for public flashcard deck access to prevent abuse. DMOS rate limiting policy provides tenant-specific limits and graceful degradation.',
    'Adjust rate limits for flashcard deck access patterns (read-heavy, write-light).',
    6,
    'performance',
    'system'
),
(
    'flashit',
    'phr-distributed-cache-integration',
    'service',
    0.85,
    'FlashIt requires caching for frequently accessed flashcard decks. PHR distributed cache integration provides Redis-backed caching with multi-node synchronization.',
    'Adapt cache invalidation strategy for flashcard deck updates instead of consent changes.',
    8,
    'performance',
    'system'
)
ON CONFLICT (target_product, recommended_asset_id) DO NOTHING;
