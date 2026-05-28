# PHR Release Checklist

This checklist must be completed before any PHR production release. It ensures that no documentation claims completeness without corresponding code, tests, and evidence.

## Pre-Release Requirements

### Documentation Completeness

- [ ] All IA use cases marked as "implemented" in `phr-usecase-baseline.json` have:
  - [ ] Web route implementation in `phrRouteContracts.ts`
  - [ ] Mobile screen implementation (if applicable)
  - [ ] Backend API route handler
  - [ ] Unit tests for core logic
  - [ ] Integration tests for API endpoints
  - [ ] E2E test coverage for critical user journeys

- [ ] Doc/code mismatch evidence shows zero discrepancies
  - [ ] Run `node scripts/generate-phr-doc-code-mismatch-evidence.mjs`
  - [ ] Review generated `.kernel/evidence/phr/doc-code-mismatch.json`
  - [ ] Fix any mismatches before proceeding

- [ ] IA coverage documentation is up to date
  - [ ] Run `node scripts/generate-phr-ia-coverage-doc.mjs`
  - [ ] Review generated `products/phr/docs/IA_COVERAGE.md`
  - [ ] Ensure all implemented features are documented

### Code Quality

- [ ] All TypeScript files pass `tsc --noEmit` with strict mode
- [ ] All Java files pass Gradle build with zero warnings
- [ ] ESLint passes with zero warnings
- [ ] Prettier formatting applied to all files
- [ ] Test coverage meets minimum thresholds:
  - [ ] Backend: 80% for critical paths
  - [ ] Web: 70% for user-facing code
  - [ ] Mobile: 70% for user-facing code

### Security & Privacy

- [ ] PHI log safety check passes
  - [ ] Run `node scripts/check-phr-phi-log-safety.mjs`
  - [ ] Zero PHI leakage in logs/diagnostics

- [ ] i18n conformance check passes
  - [ ] Run `node scripts/check-phr-i18n-conformance.mjs`
  - [ ] Zero raw user-visible strings in production code

- [ ] Mobile PHI storage hardened
  - [ ] Key rotation implemented (90-day threshold)
  - [ ] Biometric policy support enabled
  - [ ] Device install ID tracking active
  - [ ] Tamper detection functional

- [ ] Backend policy gates enforced
  - [ ] Emergency break-glass requires 20+ char justification
  - [ ] Patient scope validation active
  - [ ] Audit trail generation for all PHI access

### Release Readiness

- [ ] Release readiness endpoint accessible to admin
- [ ] All required sections return fresh evidence
- [ ] Evidence outbox health check passes
- [ ] Kernel runtime API integration verified
- [ ] No deprecated APIs or patterns in use

### Route States

- [ ] `phr-route-contract.json` route states reviewed
- [ ] Hidden routes render not-found on direct links
- [ ] Blocked routes render forbidden on direct links
- [ ] Stable routes include `apiEndpoint`, `policyId`, and `testId`

## Release-Specific Checks

### For MVP-Current Features

- [ ] All MVP-current use cases implemented
- [ ] Critical user journeys tested end-to-end
- [ ] Mobile offline cache functional
- [ ] Session management tested (login, expiry, logout)
- [ ] Consent revocation clears PHI cache

### For Hidden or Blocked Features

- [ ] Route state documented in `phr-route-contract.json`
- [ ] Rollback procedure tested
- [ ] User impact assessment completed
- [ ] Monitoring/alerting configured

### For Deferred/Phase-2 Features

- [ ] Clearly marked as not production-ready
- [ ] No accidental exposure in production
- [ ] Roadmap timeline documented

## Post-Release Verification

- [ ] Smoke tests pass in production environment
- [ ] Error rates within acceptable thresholds
- [ ] Performance metrics meet baselines
- [ ] Security scan results reviewed
- [ ] User feedback channels monitored

## Sign-Off

**Engineering Lead:** ____________________ Date: ________

**Product Manager:** ____________________ Date: ________

**Security Review:** ____________________ Date: ________

**QA Lead:** ____________________ Date: ________

---

*This checklist is part of the PHR governance process. All items must be completed and signed off before production release.*
