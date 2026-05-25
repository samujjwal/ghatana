-- YAPPC-007: Seed first PHR reusable assets
-- This migration registers the initial set of reusable assets from PHR for promotion across the product family.

INSERT INTO product_family_registry (
    family_id,
    family_name,
    description,
    owner_team,
    lifecycle_profile,
    tenant_id
) VALUES (
    'phr',
    'Personal Health Record',
    'Healthcare product family reusable assets and release governance.',
    'phr',
    'regulated',
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
    'phr',
    'phr-consent-ui-component',
    'PHR Consent Management UI Component',
    'component',
    'ui',
    'Reusable consent management UI component with consent capture, revocation, and history display. Supports GDPR/HIPAA consent requirements.',
    'phr',
    'approved',
    'system'
),
(
    'phr',
    'phr-audit-trail-service',
    'PHR Audit Trail Service',
    'service',
    'service',
    'Audit trail service for healthcare operations with immutable logging, tamper detection, and compliance reporting. Meets healthcare audit requirements.',
    'phr',
    'approved',
    'system'
),
(
    'phr',
    'phr-fhir-export-workflow',
    'PHR FHIR Export Workflow',
    'workflow',
    'service',
    'FHIR R4 data export workflow with patient data bundle generation, consent filtering, and interoperability standards compliance.',
    'phr',
    'approved',
    'system'
),
(
    'phr',
    'phr-emergency-access-policy',
    'PHR Emergency Access Policy',
    'policy',
    'validation',
    'Emergency access policy for healthcare data with break-glass procedures, audit logging, and time-limited access controls.',
    'phr',
    'approved',
    'system'
),
(
    'phr',
    'phr-distributed-cache-integration',
    'PHR Distributed Cache Integration',
    'service',
    'service',
    'Redis-backed distributed cache integration with consent invalidation propagation, cache warming, and multi-node synchronization.',
    'phr',
    'approved',
    'system'
),
(
    'phr',
    'phr-tenant-isolation-middleware',
    'PHR Tenant Isolation Middleware',
    'service',
    'validation',
    'Tenant isolation middleware with strict mode enforcement, signed JWT validation, and cross-tenant data leakage prevention.',
    'phr',
    'approved',
    'system'
),
(
    'phr',
    'phr-rollback-policy',
    'PHR Rollback Policy',
    'policy',
    'validation',
    'Healthcare-specific rollback policy with manifest history requirements, post-rollback verification gates, and approval contracts.',
    'phr',
    'approved',
    'system'
)
ON CONFLICT (asset_id, tenant_id) DO NOTHING;

COMMENT ON TABLE product_family_reusable_assets IS 'Stores reusable assets that can be promoted across products in the family. Initial PHR assets seeded in V021.';
