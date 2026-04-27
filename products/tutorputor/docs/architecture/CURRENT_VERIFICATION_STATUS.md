# TutorPutor Current Verification Status

> **Document type**: CI-Backed Verification Status  
> **Generation method**: Generated from local clean-build verification run.  
> Regenerate with `./scripts/run-typecheck.sh` or the commands listed in each section.  
> **Commit SHA**: `4a5e8c6dc468b518e83448bce6c64f45b462e0e4`  
> **Date**: 2026-04-27  

---

## Build and TypeScript Status

| Package | Command | Status | Errors |
|---|---|---|---|
| `@tutorputor/contracts` | `pnpm exec tsc -p tsconfig.json` | ✅ PASS | 0 |
| `@tutorputor/platform` | `pnpm exec tsc --noEmit` | ✅ PASS | 0 |

**Note**: `@tutorputor/contracts` must be compiled before `@tutorputor/platform`.  
The `dist/` folder is not committed and must be generated as part of the build pipeline.

Previously stale `tsc_probe.txt` has been archived to `docs/archive/tsc_probe_2026-04-19_archived.txt`.  
Machine-readable status: [`docs/architecture/latest-typecheck-status.json`](latest-typecheck-status.json)

---

## Unit Test Status

> Run: `pnpm exec vitest run` inside `products/tutorputor/services/tutorputor-platform/`

| Test Suite | Status | Pass | Notes |
|---|---|---|---|
| `src/modules/ai/__tests__/routes.test.ts` | ✅ PASS | 30 | Strict auth contract enforced |
| `src/__tests__/setupPlatform.integration.test.ts` | ✅ PASS | 43 | Full consent + auth flow |
| `src/modules/learning/__tests__/routes.test.ts` | ✅ PASS | 25 | Dashboard auth enforced |
| `src/api/__tests__/tutorputorClient.test.ts` | ✅ PASS | 9 | Strict tenant context |
| `src/modules/content/studio/__tests__/service.test.ts` | ✅ PASS | 62 | Independent validator gating |
| `src/core/middleware/__tests__/consent-enforcement.test.ts` | ✅ PASS | - | Integration consent routes covered |
| `src/__tests__/auth-token-required.integration.test.ts` | ✅ PASS | - | Missing token, expired token, wrong tenant |
| `src/__tests__/consent-enforcement.integration.test.ts` | ✅ PASS | - | No consent, revoked consent, minor consent |

---

## Integration Test Status

| Test Suite | Status | Notes |
|---|---|---|
| `tests/integration/comprehensive.test.ts` | ✅ PASS | DB + service integration |
| `tests/integration/DatabaseIntegration.test.ts` | ✅ PASS | Prisma adapter coverage |
| Auth fail-closed regression | ✅ PASS | Missing token → 401, wrong tenant → 403, wrong role → 403 |
| Consent enforcement | ✅ PASS | AI routes, analytics routes, integration routes |
| Cross-tenant isolation | ✅ PASS | Tenant X cannot access tenant Y resources |
| Trusted-proxy header rejection | ✅ PASS | Forged headers rejected without shared secret |

---

## E2E Test Status

> Run: `bash ./scripts/run-critical-journey-e2e.sh` from `products/tutorputor/`

| Spec | Status | Notes |
|---|---|---|
| `ContentStudio.spec.ts` | ✅ PASS | Real bearer auth + authoring lifecycle |
| `LearnerJourney.spec.ts` | ✅ PASS | Dashboard hydration, module navigation |
| `StudentOnboarding.spec.ts` | ✅ PASS | Login, first-visit dashboard, module exploration |
| `EducatorWorkflow.spec.ts` | ✅ PASS | Authoring workspace, analytics dashboard |

---

## Security Status

| Gate | Status | Notes |
|---|---|---|
| Trusted-proxy bypass disabled by default | ✅ PASS | Requires explicit TRUST_PROXY_AUTH_HEADERS + shared secret |
| JWT guard on all guarded routes | ✅ PASS | Missing token → 401 |
| Consent middleware on AI/analytics/integration routes | ✅ PASS | Missing consent → 451 |
| Cross-tenant resource isolation | ✅ PASS | Tenant-scoped helpers enforced |

---

## Known Skipped / Deferred Tests

| Area | Reason | Tracking |
|---|---|---|
| Mobile E2E | Requires live device or emulator | P2-3 |
| Stripe billing E2E | Requires Stripe test keys in CI | P2-4 |
| Golden dataset validators | Architecture in progress | P1-1 |
| Simulation deterministic replay | Harness in progress | P1-2 |

---

## How to Regenerate This Document

```bash
# 1. Build contracts
cd products/tutorputor/contracts
pnpm exec tsc -p tsconfig.json

# 2. Typecheck platform
cd products/tutorputor/services/tutorputor-platform
pnpm exec tsc --noEmit

# 3. Run unit tests
pnpm exec vitest run

# 4. Run E2E (requires live stack)
bash ./scripts/run-critical-journey-e2e.sh
```

Update `latest-typecheck-status.json` and this document after each run.
