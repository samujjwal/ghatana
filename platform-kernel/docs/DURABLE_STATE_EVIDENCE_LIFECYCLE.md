# Durable State and Evidence for All Lifecycle Phases

## Purpose

This document defines the canonical durable state and evidence requirements for all product lifecycle phases in the Ghatana platform. It ensures that every lifecycle phase produces persistent, queryable evidence for release readiness, compliance, and operational observability.

## Lifecycle Phases

### 1. Build Phase

**Purpose**: Compile and package the product artifacts.

**Durable State**:
- `build_artifacts` table in Data Cloud
- Fields: build_id, git_commit, branch, timestamp, artifacts (JSON), quality_metrics (JSON), tenant_id

**Evidence Produced**:
- `artifact-manifest.json` - Artifact metadata and checksums
- `lifecycle-result.json` - Build execution result
- Quality metrics (test coverage, build duration, success rate)

**Storage Location**:
- `.kernel/out/products/{productId}/build/{timestamp}/lifecycle-result.json`
- `.kernel/out/products/{productId}/build/{timestamp}/{productId}/{timestamp}/build/artifact-manifest.json`

**Validation Gates**:
- Build success
- Test coverage threshold (≥80% for critical paths)
- No security vulnerabilities in artifacts
- Artifact checksums verified

---

### 2. Test Phase

**Purpose**: Execute unit, integration, and E2E tests.

**Durable State**:
- `test_results` table in Data Cloud
- Fields: test_suite_id, build_id, test_type, total_tests, passed, failed, skipped, duration_ms, coverage_report (JSON), tenant_id

**Evidence Produced**:
- `lifecycle-health-snapshot.json` - Test execution summary
- Coverage reports (line, branch, function)
- Test execution logs
- Failure reports with stack traces

**Storage Location**:
- `.kernel/out/products/{productId}/test/{timestamp}/lifecycle-health-snapshot.json`
- Coverage reports in `.kernel/out/products/{productId}/test/{timestamp}/coverage/`

**Validation Gates**:
- All tests pass
- Coverage thresholds met
- No flaky tests
- Test execution time within SLA

---

### 3. Deploy Phase

**Purpose**: Deploy artifacts to target environment.

**Durable State**:
- `deployment_manifest` table in Data Cloud
- Fields: deployment_id, build_id, environment, artifact_id, deployment_status, health_status, rollback_available, deployed_at, tenant_id

**Evidence Produced**:
- `deployment-manifest.json` - Deployment metadata
- Health check results
- Deployment logs
- Rollback availability status

**Storage Location**:
- `.kernel/out/products/{productId}/deploy/{timestamp}/deployment-manifest.json`
- Health check results in Data Cloud

**Validation Gates**:
- Deployment successful
- Health checks pass
- Rollback available
- No deployment errors

---

### 4. Verify Phase

**Purpose**: Verify deployment in target environment.

**Durable State**:
- `verification_results` table in Data Cloud
- Fields: verification_id, deployment_id, environment, verification_type, status, results (JSON), verified_at, tenant_id

**Evidence Produced**:
- `verify-health-report.json` - Verification results
- Smoke test results
- Integration test results
- Performance metrics

**Storage Location**:
- `.kernel/out/products/{productId}/verify/{timestamp}/verify-health-report.json`

**Validation Gates**:
- All verifications pass
- Performance metrics within SLA
- No regressions detected
- Security scans pass

---

### 5. Release Phase

**Purpose**: Promote verified deployment to production.

**Durable State**:
- `product_family_release_readiness` table in Data Cloud
- Fields: family_id, environment, status, overall_score, blocking_issues (JSON), warnings (JSON), checked_at, tenant_id

**Evidence Produced**:
- Release readiness evidence (JSON)
- Gate validation results
- Approval records
- Release notes

**Storage Location**:
- `.kernel/evidence/{productId}/{productId}-release-readiness.json`
- Data Cloud release readiness table

**Validation Gates**:
- All mandatory gates pass
- No blocking issues
- Overall score ≥8.0 for production
- Approvals granted

---

## Evidence Categories

### Build Evidence
- **Table**: `build_artifacts`
- **Schema**: build_id, git_commit, branch, timestamp, artifacts, quality_metrics, tenant_id
- **Retention**: 90 days for non-production, 365 days for production

### Test Evidence
- **Table**: `test_results`
- **Schema**: test_suite_id, build_id, test_type, total, passed, failed, skipped, duration_ms, coverage_report, tenant_id
- **Retention**: 90 days for non-production, 365 days for production

### Deployment Evidence
- **Table**: `deployment_manifest`
- **Schema**: deployment_id, build_id, environment, artifact_id, status, health_status, rollback_available, deployed_at, tenant_id
- **Retention**: 365 days for all environments

### Verification Evidence
- **Table**: `verification_results`
- **Schema**: verification_id, deployment_id, environment, verification_type, status, results, verified_at, tenant_id
- **Retention**: 365 days for all environments

### Release Readiness Evidence
- **Table**: `product_family_release_readiness`
- **Schema**: family_id, environment, status, overall_score, blocking_issues, warnings, checked_at, tenant_id
- **Retention**: Indefinite for production, 180 days for non-production

## Evidence Linking

### Trace-Evidence Linking
- Each lifecycle phase generates a unique `traceId`
- All evidence produced during the phase is linked to this `traceId`
- `KernelTimelineEventProducer` records timeline events with trace linking
- `TraceLinker` maintains bidirectional links between traces and evidence

### Cross-Phase Linking
- Build phase `build_id` links to test phase
- Test phase `test_suite_id` links to deployment phase
- Deployment phase `deployment_id` links to verification phase
- Verification phase `verification_id` links to release phase

### Evidence Graph
- Evidence forms a directed acyclic graph (DAG)
- Each node represents an evidence artifact
- Edges represent dependencies between evidence
- Graph traversal enables impact analysis

## Query Patterns

### Product Release Readiness Query
```sql
SELECT 
    family_id,
    environment,
    status,
    overall_score,
    blocking_issues,
    warnings,
    checked_at
FROM product_family_release_readiness
WHERE family_id = ? AND environment = ?
ORDER BY checked_at DESC
LIMIT 1;
```

### Build Evidence Query
```sql
SELECT 
    build_id,
    git_commit,
    branch,
    timestamp,
    artifacts,
    quality_metrics
FROM build_artifacts
WHERE build_id = ? AND tenant_id = current_setting('app.current_tenant')::VARCHAR;
```

### Deployment History Query
```sql
SELECT 
    deployment_id,
    build_id,
    environment,
    deployment_status,
    health_status,
    rollback_available,
    deployed_at
FROM deployment_manifest
WHERE build_id = ? AND tenant_id = current_setting('app.current_tenant')::VARCHAR
ORDER BY deployed_at DESC;
```

## Tenant Isolation

All evidence tables enforce tenant isolation via Row Level Security (RLS):

```sql
ALTER TABLE build_artifacts ENABLE ROW LEVEL SECURITY;
CREATE POLICY build_artifacts_tenant_isolation 
    ON build_artifacts
    FOR ALL USING (tenant_id = current_setting('app.current_tenant')::VARCHAR);

ALTER TABLE test_results ENABLE ROW LEVEL SECURITY;
CREATE POLICY test_results_tenant_isolation 
    ON test_results
    FOR ALL USING (tenant_id = current_setting('app.current_tenant')::VARCHAR);

ALTER TABLE deployment_manifest ENABLE ROW LEVEL SECURITY;
CREATE POLICY deployment_manifest_tenant_isolation 
    ON deployment_manifest
    FOR ALL USING (tenant_id = current_setting('app.current_tenant')::VARCHAR);

ALTER TABLE verification_results ENABLE ROW LEVEL SECURITY;
CREATE POLICY verification_results_tenant_isolation 
    ON verification_results
    FOR ALL USING (tenant_id = current_setting('app.current_tenant')::VARCHAR);

ALTER TABLE product_family_release_readiness ENABLE ROW LEVEL SECURITY;
CREATE POLICY product_family_release_readiness_tenant_isolation 
    ON product_family_release_readiness
    FOR ALL USING (tenant_id = current_setting('app.current_tenant')::VARCHAR);
```

## Observability

### Metrics
- Evidence collection success rate per phase
- Evidence validation failure rate
- Phase transition duration
- Evidence storage size
- Query performance

### Logging
- Structured logs for all evidence operations
- Audit trail for evidence mutations
- Error logs for validation failures
- Performance logs for slow queries

### Tracing
- Distributed tracing for cross-phase operations
- Span annotations for evidence collection
- Correlation IDs for evidence linking
- Trace context propagation

## Compliance

### SOC2
- Evidence retention policies enforced
- Audit trail for all evidence mutations
- Access controls for evidence operations
- Evidence integrity verification

### Healthcare Compliance (for healthcare products)
- PHI-free evidence storage
- Encrypted evidence at rest
- Audit trail for PHI access
- Data sovereignty enforcement

### Data Privacy Compliance
- Data minimization in evidence
- Right to erasure support
- Data portability support
- Consent tracking

## References

- `products/data-cloud/docs/CANONICAL_EVIDENCE_COLLECTIONS.md`
- `platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/release/ProductReleaseReadinessProducer.java`
- `platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/timeline/KernelTimelineEventProducer.java`
- Migration V020: `create_product_family_control_plane.sql`
