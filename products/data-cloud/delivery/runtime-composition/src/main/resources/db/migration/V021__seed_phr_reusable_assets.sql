-- YAPPC-007: Seed first PHR reusable assets
-- This migration registers the initial set of reusable assets from PHR for promotion across the product family

-- PHR Consent Management UI Component
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
    'phr-consent-ui-component',
    'PHR Consent Management UI Component',
    'ui-component',
    'phr',
    'Reusable consent management UI component with consent capture, revocation, and history display. Supports GDPR/HIPAA consent requirements.',
    ARRAY['consent', 'ui', 'healthcare', 'gdpr', 'hipaa'],
    'stable',
    0,
    'approved',
    'system'
) ON CONFLICT (asset_id) DO NOTHING;

-- PHR Audit Trail Service
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
    'phr-audit-trail-service',
    'PHR Audit Trail Service',
    'service',
    'phr',
    'Audit trail service for healthcare operations with immutable logging, tamper detection, and compliance reporting. Meets healthcare audit requirements.',
    ARRAY['audit', 'service', 'healthcare', 'compliance', 'immutable'],
    'stable',
    0,
    'approved',
    'system'
) ON CONFLICT (asset_id) DO NOTHING;

-- PHR FHIR Export Workflow
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
    'phr-fhir-export-workflow',
    'PHR FHIR Export Workflow',
    'workflow',
    'phr',
    'FHIR R4 data export workflow with patient data bundle generation, consent filtering, and interoperability standards compliance.',
    ARRAY['fhir', 'workflow', 'healthcare', 'interoperability', 'export'],
    'stable',
    0,
    'approved',
    'system'
) ON CONFLICT (asset_id) DO NOTHING;

-- PHR Emergency Access Policy
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
    'phr-emergency-access-policy',
    'PHR Emergency Access Policy',
    'policy',
    'phr',
    'Emergency access policy for healthcare data with break-glass procedures, audit logging, and time-limited access controls.',
    ARRAY['policy', 'healthcare', 'emergency', 'access-control', 'break-glass'],
    'stable',
    0,
    'approved',
    'system'
) ON CONFLICT (asset_id) DO NOTHING;

-- PHR Distributed Cache Integration
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
    'phr-distributed-cache-integration',
    'PHR Distributed Cache Integration',
    'service',
    'phr',
    'Redis-backed distributed cache integration with consent invalidation propagation, cache warming, and multi-node synchronization.',
    ARRAY['cache', 'service', 'redis', 'distributed', 'consent'],
    'stable',
    0,
    'approved',
    'system'
) ON CONFLICT (asset_id) DO NOTHING;

-- PHR Tenant Isolation Middleware
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
    'phr-tenant-isolation-middleware',
    'PHR Tenant Isolation Middleware',
    'service',
    'phr',
    'Tenant isolation middleware with strict mode enforcement, signed JWT validation, and cross-tenant data leakage prevention.',
    ARRAY['tenant', 'middleware', 'security', 'isolation', 'jwt'],
    'stable',
    0,
    'approved',
    'system'
) ON CONFLICT (asset_id) DO NOTHING;

-- PHR Rollback Policy
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
    'phr-rollback-policy',
    'PHR Rollback Policy',
    'policy',
    'phr',
    'Healthcare-specific rollback policy with manifest history requirements, post-rollback verification gates, and approval contracts.',
    ARRAY['policy', 'healthcare', 'rollback', 'deployment', 'approval'],
    'stable',
    0,
    'approved',
    'system'
) ON CONFLICT (asset_id) DO NOTHING;

-- Comment on the seeded assets
COMMENT ON TABLE product_family_assets IS 'Stores reusable assets that can be promoted across products in the family. Initial PHR assets seeded in V021.';
