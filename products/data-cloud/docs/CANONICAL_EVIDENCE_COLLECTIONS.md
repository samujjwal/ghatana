# Canonical Data Cloud Evidence Collections

## Purpose

This document establishes the canonical patterns for using Data Cloud evidence collections for release readiness and reusable assets across all products (PHR, Digital Marketing, YAPPC, etc.).

## Evidence Collection Schema

### Release Readiness Evidence

The canonical release readiness evidence is stored in the `product_family_release_readiness` table (migration V020):

```sql
CREATE TABLE product_family_release_readiness (
    id BIGSERIAL PRIMARY KEY,
    family_id VARCHAR(255) NOT NULL,
    environment VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    overall_score DECIMAL(3,1),
    blocking_issues JSONB,
    warnings JSONB,
    checked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    tenant_id VARCHAR(255) NOT NULL,
    CONSTRAINT uq_family_environment UNIQUE (family_id, environment, tenant_id)
);
```

**Canonical Usage Pattern:**

1. **Producer**: Use `ProductReleaseReadinessProducer` in `platform-kernel/kernel-core`
2. **Collector**: Implement `ReleaseEvidenceCollector` interface for product-specific evidence
3. **Scorecard**: Implement `ReleaseScorecardCalculator` interface for product-specific scoring
4. **Consumer**: Query via Data Cloud API `/api/product-family/{familyId}/release-readiness/{environment}`

### Reusable Assets Evidence

The canonical reusable assets evidence is stored in the `product_family_reusable_assets` table (migration V020):

```sql
CREATE TABLE product_family_reusable_assets (
    id BIGSERIAL PRIMARY KEY,
    family_id VARCHAR(255) NOT NULL,
    asset_id VARCHAR(255) NOT NULL,
    asset_name VARCHAR(255) NOT NULL,
    asset_type VARCHAR(100) NOT NULL,
    asset_category VARCHAR(100) NOT NULL,
    description TEXT,
    source_product_id VARCHAR(255),
    source_file_path TEXT,
    promoted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    promotion_status VARCHAR(50) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL,
    CONSTRAINT uq_family_asset UNIQUE (family_id, asset_id, tenant_id)
);
```

**Canonical Usage Pattern:**

1. **Registration**: Insert asset record with `promotion_status = 'draft'`
2. **Validation**: Run asset schema validation gate (YAPPC-006)
3. **Promotion**: Update `promotion_status` to `'approved'` after gate passes
4. **Adoption**: Track adoption in `product_family_asset_adoption` table

### Promotion Evidence

The canonical promotion evidence is stored in the `promotion_evidence` table (migration V017):

```sql
CREATE TABLE promotion_evidence (
    id BIGSERIAL PRIMARY KEY,
    source_artifact_id VARCHAR(255) NOT NULL,
    target_artifact_id VARCHAR(255) NOT NULL,
    promotion_type VARCHAR(100) NOT NULL,
    promotion_status VARCHAR(50) NOT NULL,
    promotion_metadata JSONB,
    promoted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    promoted_by VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL
);
```

**Canonical Usage Pattern:**

1. **Pre-Promotion**: Create evidence record with `promotion_status = 'pending'`
2. **Validation**: Run promotion validation checks
3. **Post-Promotion**: Update `promotion_status` to `'completed'` or `'failed'`
4. **Audit**: Query via `DataCloudPromotionEvidenceRepository`

### Evaluation Results

The canonical evaluation results are stored in the `evaluation_results` table (migration V015):

```sql
CREATE TABLE evaluation_results (
    id BIGSERIAL PRIMARY KEY,
    evaluation_pack_id VARCHAR(255) NOT NULL,
    asset_id VARCHAR(255) NOT NULL,
    evaluation_type VARCHAR(100) NOT NULL,
    evaluation_score DECIMAL(5,2),
    evaluation_details JSONB,
    evaluated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    evaluated_by VARCHAR(255) NOT NULL,
    tenant_id VARCHAR(255) NOT NULL
);
```

**Canonical Usage Pattern:**

1. **Evaluation**: Run evaluation pack against asset
2. **Recording**: Insert result with score and details
3. **Aggregation**: Query for aggregate scores across assets
4. **Governance**: Use for mastery and learning delta calculations

## Evidence Categories

### Build Evidence
- **Table**: `build_artifacts` (via artifact-manifest)
- **Fields**: build_id, git_commit, branch, timestamp, artifacts, quality_metrics
- **Status**: `passed`, `failed`, `partial`, `pending`

### Test Evidence
- **Table**: `test_results` (via lifecycle-health-snapshot)
- **Fields**: test_suite, total_tests, passed, failed, coverage, duration
- **Status**: `passed`, `failed`, `partial`, `pending`

### API Evidence
- **Table**: `api_contract_conformance` (via OpenAPI validation)
- **Fields**: contract_version, parity_status, schema_validation, endpoint_coverage
- **Status**: `passed`, `failed`, `partial`, `pending`

### Rollback Evidence
- **Table**: `rollback_readiness` (via rollback-manifest)
- **Fields**: deployment_history, artifact_selection, post_rollback_gates, approval_status
- **Status**: `ready`, `blocked`, `partial`, `pending`

### Deployment Evidence
- **Table**: `deployment_manifest` (via deployment-manifest)
- **Fields**: environment, deployment_id, artifact_id, health_status, rollback_available
- **Status**: `deployed`, `failed`, `partial`, `pending`

## API Endpoints

### Release Readiness
- `GET /api/product-family/{familyId}/release-readiness/{environment}` - Get release readiness
- `POST /api/product-family/{familyId}/release-readiness` - Generate release readiness
- `PUT /api/product-family/{familyId}/release-readiness/{environment}` - Update release readiness

### Reusable Assets
- `GET /api/product-family/{familyId}/reusable-assets` - List reusable assets
- `POST /api/product-family/{familyId}/reusable-assets` - Register reusable asset
- `PUT /api/product-family/{familyId}/reusable-assets/{assetId}` - Update asset status
- `GET /api/product-family/{familyId}/reusable-assets/{assetId}/adoption` - Get adoption status

### Promotion Evidence
- `GET /api/promotion-evidence/{sourceArtifactId}` - Get promotion evidence
- `POST /api/promotion-evidence` - Create promotion evidence
- `PUT /api/promotion-evidence/{id}` - Update promotion evidence

### Evaluation Results
- `GET /api/evaluation-results/{evaluationPackId}/{assetId}` - Get evaluation results
- `POST /api/evaluation-results` - Record evaluation result
- `GET /api/evaluation-results/aggregate/{evaluationPackId}` - Get aggregate scores

## Tenant Isolation

All evidence collections enforce tenant isolation via Row Level Security (RLS):

```sql
ALTER TABLE product_family_release_readiness ENABLE ROW LEVEL SECURITY;
CREATE POLICY product_family_release_readiness_tenant_isolation 
    ON product_family_release_readiness
    FOR ALL USING (tenant_id = current_setting('app.current_tenant')::VARCHAR);
```

**Canonical Usage Pattern:**
- Set tenant context via `TenantContext.scope(principal)` before queries
- Use `TenantExtractor.fromHttpOrThrow(request)` for user-facing paths
- Use `TenantExtractionFilter.strict()` for HTTP servers

## Evidence Lifecycle

1. **Collection**: Evidence collected during lifecycle phases (build, test, deploy, verify)
2. **Validation**: Evidence validated against schema and quality gates
3. **Storage**: Evidence stored in canonical Data Cloud collections
4. **Aggregation**: Evidence aggregated for release readiness scoring
5. **Promotion**: Validated evidence promoted to reusable assets
6. **Retention**: Evidence retained per retention policy (configurable per tenant)

## Integration Points

### Kernel Integration
- `ProductReleaseReadinessProducer` generates release readiness evidence
- `ReleaseEvidenceCollector` collects product-specific evidence
- `ReleaseScorecardCalculator` calculates overall scores

### Plugin Integration
- `ComplianceEvidenceRegistry` registers compliance evidence
- `DataCloudPolicyEvidenceProvider` provides policy evidence
- `DataCloudProductInteractionEvidenceProvider` provides interaction evidence

### UI Integration
- `ReleaseTruthDashboardPage` displays release readiness
- `TrustCenter` displays evidence and compliance status
- `MasteryPage` displays mastery and learning evidence

## Quality Gates

### Schema Validation
- All evidence must conform to canonical schema
- Schema version tracked in `schemaVersion` field
- Migration scripts ensure schema evolution

### Data Quality
- Required fields must be non-null
- Foreign key constraints enforced
- Unique constraints prevent duplicates

### Access Control
- Tenant isolation enforced via RLS
- Role-based access control (RBAC) for evidence operations
- Audit trail for all evidence mutations

## Monitoring and Observability

### Metrics
- Evidence collection success rate
- Evidence validation failure rate
- Release readiness score trends
- Asset promotion success rate

### Logging
- Structured logs for evidence collection
- Audit logs for evidence mutations
- Error logs for validation failures

### Tracing
- Correlation IDs for evidence collection workflows
- Distributed tracing for cross-service evidence flows
- Span annotations for evidence validation steps

## References

- Migration V015: `create_evaluation_results.sql`
- Migration V017: `create_promotion_evidence.sql`
- Migration V020: `create_product_family_control_plane.sql`
- `ComplianceEvidenceRegistry.java`
- `DataCloudPromotionEvidenceRepository.java`
- `DataCloudMasteryEvidenceRepository.java`
- `ProductReleaseReadinessProducer.java`
