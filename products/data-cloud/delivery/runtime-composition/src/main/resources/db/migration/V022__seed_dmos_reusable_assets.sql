-- YAPPC-008: Seed first DMOS reusable assets
-- This migration registers the initial set of reusable assets from Digital Marketing for promotion across the product family

-- DMOS Google Ads Connector
INSERT INTO product_family_assets (
    asset_id,
    asset_name,
    asset_type,
    source_product_id,
    description,
    tags,
    maturity_level,
    usage_count,
    promotion_status,
    tenant_id
) VALUES (
    'dmos-google-ads-connector',
    'DMOS Google Ads Connector',
    'service',
    'digital-marketing',
    'Google Ads API connector with OAuth authentication, idempotent campaign operations, retry logic, dead-letter queue, compensation actions, and external ID persistence.',
    ARRAY['connector', 'google-ads', 'oauth', 'idempotency', 'retry'],
    'stable',
    0,
    'approved',
    'system'
) ON CONFLICT (asset_id) DO NOTHING;

-- DMOS Marketing Consent Boundary Service
INSERT INTO product_family_assets (
    asset_id,
    asset_name,
    asset_type,
    source_product_id,
    description,
    tags,
    maturity_level,
    usage_count,
    promotion_status,
    tenant_id
) VALUES (
    'dmos-marketing-consent-boundary',
    'DMOS Marketing Consent Boundary Service',
    'service',
    'digital-marketing',
    'Marketing consent boundary enforcement service ensuring contact data does not flow downstream without explicit consent evidence. Supports GDPR marketing consent requirements.',
    ARRAY['consent', 'marketing', 'gdpr', 'boundary', 'enforcement'],
    'stable',
    0,
    'approved',
    'system'
) ON CONFLICT (asset_id) DO NOTHING;

-- DMOS Campaign Budget Management Workflow
INSERT INTO product_family_assets (
    asset_id,
    asset_name,
    asset_type,
    source_product_id,
    description,
    tags,
    maturity_level,
    usage_count,
    promotion_status,
    tenant_id
) VALUES (
    'dmos-campaign-budget-workflow',
    'DMOS Campaign Budget Management Workflow',
    'workflow',
    'digital-marketing',
    'Campaign budget management workflow with allocation, tracking, overspend prevention, and rollback capabilities for budget adjustments.',
    ARRAY['workflow', 'budget', 'campaign', 'finance', 'rollback'],
    'stable',
    0,
    'approved',
    'system'
) ON CONFLICT (asset_id) DO NOTHING;

-- DMOS Lifecycle Readiness Gate Pack
INSERT INTO product_family_assets (
    asset_id,
    asset_name,
    asset_type,
    source_product_id,
    description,
    tags,
    maturity_level,
    usage_count,
    promotion_status,
    tenant_id
) VALUES (
    'dmos-lifecycle-readiness-gate-pack',
    'DMOS Lifecycle Readiness Gate Pack',
    'policy',
    'digital-marketing',
    'Lifecycle readiness gate pack for Digital Marketing with registry validation, manifest validation, lifecycle contract validation, bridge compliance, marketing consent boundary, persistence proof, and connector proof gates.',
    ARRAY['policy', 'lifecycle', 'readiness', 'gates', 'validation'],
    'stable',
    0,
    'approved',
    'system'
) ON CONFLICT (asset_id) DO NOTHING;

-- DMOS Production Bootstrap Validator
INSERT INTO product_family_assets (
    asset_id,
    asset_name,
    asset_type,
    source_product_id,
    description,
    tags,
    maturity_level,
    usage_count,
    promotion_status,
    tenant_id
) VALUES (
    'dmos-production-bootstrap-validator',
    'DMOS Production Bootstrap Validator',
    'service',
    'digital-marketing',
    'Production bootstrap validation service checking PostgreSQL connectivity, migrations, connector credentials, secrets, OTLP endpoint, rate limits, feature flags, and kill switches.',
    ARRAY['bootstrap', 'validation', 'production', 'connectivity', 'security'],
    'stable',
    0,
    'approved',
    'system'
) ON CONFLICT (asset_id) DO NOTHING;

-- DMOS Marketing Analytics Dashboard UI Component
INSERT INTO product_family_assets (
    asset_id,
    asset_name,
    asset_type,
    source_product_id,
    description,
    tags,
    maturity_level,
    usage_count,
    promotion_status,
    tenant_id
) VALUES (
    'dmos-marketing-analytics-dashboard',
    'DMOS Marketing Analytics Dashboard UI Component',
    'ui-component',
    'digital-marketing',
    'Reusable marketing analytics dashboard UI component with campaign performance metrics, ROI tracking, audience insights, and trend visualization.',
    ARRAY['ui', 'analytics', 'dashboard', 'marketing', 'visualization'],
    'stable',
    0,
    'approved',
    'system'
) ON CONFLICT (asset_id) DO NOTHING;

-- DMOS Rate Limiting Policy
INSERT INTO product_family_assets (
    asset_id,
    asset_name,
    asset_type,
    source_product_id,
    description,
    tags,
    maturity_level,
    usage_count,
    promotion_status,
    tenant_id
) VALUES (
    'dmos-rate-limiting-policy',
    'DMOS Rate Limiting Policy',
    'policy',
    'digital-marketing',
    'Rate limiting policy for marketing API endpoints with tenant-specific limits, burst capacity, and graceful degradation under load.',
    ARRAY['policy', 'rate-limiting', 'api', 'performance', 'degradation'],
    'stable',
    0,
    'approved',
    'system'
) ON CONFLICT (asset_id) DO NOTHING;

-- Comment on the seeded assets
COMMENT ON TABLE product_family_assets IS 'Stores reusable assets that can be promoted across products in the family. Initial DMOS assets seeded in V022.';
