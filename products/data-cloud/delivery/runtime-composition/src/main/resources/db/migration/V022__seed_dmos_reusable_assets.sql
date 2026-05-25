-- YAPPC-008: Seed first DMOS reusable assets
-- This migration registers the initial set of reusable assets from Digital Marketing for promotion across the product family.

INSERT INTO product_family_registry (
    family_id,
    family_name,
    description,
    owner_team,
    lifecycle_profile,
    tenant_id
) VALUES (
    'digital-marketing',
    'Digital Marketing',
    'Digital marketing product family reusable assets and release governance.',
    'digital-marketing',
    'growth',
    'system'
) ON CONFLICT (family_id) DO NOTHING;

INSERT INTO product_family_reusable_assets (
    family_id,
    asset_id,
    asset_name,
    asset_type,
    asset_category,
    description,
    source_product_id,
    promotion_status,
    tenant_id
) VALUES
(
    'digital-marketing',
    'dmos-google-ads-connector',
    'DMOS Google Ads Connector',
    'service',
    'gateway',
    'Google Ads API connector with OAuth authentication, idempotent campaign operations, retry logic, dead-letter queue, compensation actions, and external ID persistence.',
    'digital-marketing',
    'approved',
    'system'
),
(
    'digital-marketing',
    'dmos-marketing-consent-boundary',
    'DMOS Marketing Consent Boundary Service',
    'service',
    'validation',
    'Marketing consent boundary enforcement service ensuring contact data does not flow downstream without explicit consent evidence. Supports GDPR marketing consent requirements.',
    'digital-marketing',
    'approved',
    'system'
),
(
    'digital-marketing',
    'dmos-campaign-budget-workflow',
    'DMOS Campaign Budget Management Workflow',
    'workflow',
    'service',
    'Campaign budget management workflow with allocation, tracking, overspend prevention, and rollback capabilities for budget adjustments.',
    'digital-marketing',
    'approved',
    'system'
),
(
    'digital-marketing',
    'dmos-lifecycle-readiness-gate-pack',
    'DMOS Lifecycle Readiness Gate Pack',
    'policy',
    'validation',
    'Lifecycle readiness gate pack for Digital Marketing with registry validation, manifest validation, lifecycle contract validation, bridge compliance, marketing consent boundary, persistence proof, and connector proof gates.',
    'digital-marketing',
    'approved',
    'system'
),
(
    'digital-marketing',
    'dmos-production-bootstrap-validator',
    'DMOS Production Bootstrap Validator',
    'service',
    'validation',
    'Production bootstrap validation service checking PostgreSQL connectivity, migrations, connector credentials, secrets, OTLP endpoint, rate limits, feature flags, and kill switches.',
    'digital-marketing',
    'approved',
    'system'
),
(
    'digital-marketing',
    'dmos-marketing-analytics-dashboard',
    'DMOS Marketing Analytics Dashboard UI Component',
    'component',
    'ui',
    'Reusable marketing analytics dashboard UI component with campaign performance metrics, ROI tracking, audience insights, and trend visualization.',
    'digital-marketing',
    'approved',
    'system'
),
(
    'digital-marketing',
    'dmos-rate-limiting-policy',
    'DMOS Rate Limiting Policy',
    'policy',
    'validation',
    'Rate limiting policy for marketing API endpoints with tenant-specific limits, burst capacity, and graceful degradation under load.',
    'digital-marketing',
    'approved',
    'system'
)
ON CONFLICT (asset_id, tenant_id) DO NOTHING;

COMMENT ON TABLE product_family_reusable_assets IS 'Stores reusable assets that can be promoted across products in the family. Initial DMOS assets seeded in V022.';
