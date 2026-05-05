# Release Truth Checklist

**ARCH-P1-004** — Verification checklist to ensure release artifacts are canonical, consistent, and production-ready.

## Purpose

This checklist ensures that all release artifacts across the Ghatana monorepo are:
- **Canonical** — Single source of truth for contracts, schemas, and documentation
- **Consistent** — Version-aligned across all products and platform modules
- **Complete** — All required artifacts present and validated
- **Production-ready** — Security, compliance, and operational readiness verified

## Pre-Release Verification

### 1. Version Consistency

- [ ] All `package.json` version fields aligned across TypeScript packages
- [ ] All `gradle.properties` version fields aligned across Java modules
- [ ] Platform module versions match product dependencies
- [ ] Release tag follows semantic versioning (e.g., `v1.2.3`)
- [ ] CHANGELOG.md entries exist for all changed components

### 2. Contract Canonicality

- [ ] OpenAPI contracts canonical: `node scripts/check-openapi-contract-canonical.mjs`
- [ ] Data Cloud API spec matches generated docs
- [ ] AEP contract spec matches server runtime spec
- [ ] Required endpoints present in all canonical contracts
- [ ] Schema definitions consistent across platform and products

### 3. Documentation Alignment

- [ ] API documentation generated from OpenAPI contracts
- [ ] README.md files reflect current version and features
- [ ] Architecture docs (ADR) updated for breaking changes
- [ ] Migration guides provided for breaking changes
- [ ] Public contracts in `docs/architecture/` are current

### 4. Security & Dependency Validation

- [ ] Dependency check passes: `./gradlew dependencyCheckAnalyze`
- [ ] No CRITICAL or HIGH severity vulnerabilities
- [ ] License compliance verified for all dependencies
- [ ] Security CI/CD checks pass (CodeQL, Trivy, TruffleHog, OWASP ZAP)
- [ ] Secrets not committed (verified by secret scanner)

### 5. Test Coverage & Quality

- [ ] Unit tests pass across all modules: `./gradlew test`
- [ ] Integration tests pass: `./gradlew integrationTest`
- [ ] Code coverage meets minimum thresholds (80% for critical paths)
- [ ] Architecture tests pass: `./gradlew :testing:architecture-tests:test`
- [ ] No linting or formatting violations (Spotless, ESLint, Prettier)

### 6. Database & Migration Validation

- [ ] Flyway migrations applied: `./gradlew flywayInfo` (no PENDING)
- [ ] Migration scripts are idempotent
- [ ] Rollback scripts available for breaking migrations
- [ ] Schema validation passes

### 7. Build & Distribution

- [ ] Clean build succeeds: `./gradlew clean build -x test`
- [ ] Distribution artifacts generated correctly
- [ ] Docker images build successfully
- [ ] Docker images tagged with correct version
- [ ] Container scans pass (Trivy)

### 8. Platform Contract Validation

- [ ] Platform Java contracts validated via `ContractValidationGate`
- [ ] Platform TypeScript API helpers match server contracts
- [ ] Shared design system version aligned
- [ ] No contract drift between platform and products

## Post-Release Verification

### 1. Deployment Smoke Tests

- [ ] Health endpoints return 200: `/health`, `/ready`
- [ ] Metrics endpoint accessible: `/metrics`
- [ ] All services start without errors
- [ ] Database connections healthy
- [ ] Message queues connected

### 2. Functional Validation

- [ ] Core user journeys work end-to-end
- [ ] Authentication/authorization functional
- [ ] Tenant isolation verified
- [ ] SSE/WebSocket connections functional
- [ ] Background jobs processing

### 3. Observability Validation

- [ ] Structured logs emitted for critical flows
- [ ] Metrics published for key operations
- [ ] Traces correlated across services
- [ ] Alert rules firing correctly
- [ ] Dashboards reflecting current state

### 4. Performance Validation

- [ ] Response times within SLA
- [ ] No memory leaks detected
- [ ] Database query performance acceptable
- [ ] Cache hit ratios healthy
- [ ] No resource exhaustion

## Rollback Readiness

- [ ] Previous release artifacts preserved
- [ ] Database rollback scripts tested
- [ ] Feature flags ready for quick disable
- [ ] Monitoring alerts configured for rollback triggers
- [ ] Communication plan prepared for rollback notification

## Release Sign-Off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Release Manager | | | |
| Tech Lead | | | |
| Security Reviewer | | | |
| QA Lead | | | |

## Automation

This checklist can be automated via:

```bash
# Run full release truth validation
./scripts/release/release-truth-check.sh
```

The automated script should:
1. Run all verification steps
2. Fail fast on any violation
3. Generate a report with pass/fail status per item
4. Exit with non-zero code if any check fails
