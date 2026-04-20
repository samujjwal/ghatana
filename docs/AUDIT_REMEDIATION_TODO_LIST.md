# Audit Remediation To-Do List

**Generated:** 2026-04-19  
**Based on:** Data Cloud, AEP, and YAPPC product audits  
**Scope:** P0 and P1 remediation tasks + Copilot guideline compliance

---

## Executive Summary

- **Total P0 Tasks:** 14 (6 fixed, 5 partial, 3 not fixed)
- **Total P1 Tasks:** 19 (9 fixed, 6 partial, 4 not verified)
- **Critical Blockers:** 7 tasks requiring immediate action
- **Production Trust Items:** 4 tasks requiring completion before production deployment

---

## Priority Legend

- 🔴 **CRITICAL** - P0: Must fix immediately (production blocker)
- 🟡 **HIGH** - P1: Required for production trust
- 🟢 **MEDIUM** - P2/P3: Simplification and strategic improvements
- 🔵 **COPILOT** - Copilot guideline compliance

---

## Data Cloud Remediation Tasks

### 🔴 P0: Critical Blockers

#### DC-P0-1: Fix shared TypeScript package wiring for `@ghatana/design-system` and `@ghatana/data-grid`

**Status:** PARTIALLY FIXED  
**Severity:** Critical  
**Evidence:** `platform/typescript/design-system/package.json` line 105 still has `@ghatana/data-grid` in peerDependencies, creating a circular dependency. Comment says "removed from dependencies" but peerDependency still creates cycle.

**Impact:**
- Representative Data Cloud UI test suites do not start
- Blocks all UI product validation and release confidence
- Breaks platform reuse credibility

**Action Items:**
- [ ] Remove `@ghatana/data-grid` from peerDependencies in design-system/package.json
- [ ] Either make `@ghatana/data-grid` depend only on lower-level packages, OR
- [ ] Move the re-export behind a proper dependency declaration and build boundary
- [ ] Rerun the three failed UI test suites
- [ ] Run broader Data Cloud UI smoke tests to verify fix

**Files to Modify:**
- `platform/typescript/design-system/package.json`
- `platform/typescript/design-system/src/index.ts` (line 104 comment)

---

#### DC-P0-2: Stop returning HTTP 200 for invalid request scenarios

**Status:** NOT FIXED  
**Severity:** Critical  
**Evidence:** `products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/VoiceErrorHandlingTest.java` line 329 explicitly tests "GET /api/v1/voice/intents always returns HTTP 200", indicating the anti-pattern persists.

**Impact:**
- Breaks client correctness
- Breaks alerting and dashboards
- Breaks retries
- Breaks operator trust
- Governance, AI assist, voice, and other envelope-based routes affected

**Action Items:**
- [ ] Audit all launcher handlers for envelope-first error pattern
- [ ] Change HTTP responses from 200 to proper 4xx/5xx statuses
- [ ] Preserve structured error bodies in response
- [ ] Update OpenAPI spec to reflect correct status codes
- [ ] Add HTTP contract tests for error scenarios
- [ ] Update UI service error handling tests
- [ ] Remove or update VoiceErrorHandlingTest.java line 329

**Files to Modify:**
- All launcher handlers in `products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/`
- `products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/VoiceErrorHandlingTest.java`
- OpenAPI spec for Data Cloud

---

#### DC-P0-3: Reclassify misleading tests and replace placeholders with real boundary tests

**Status:** NOT FIXED  
**Severity:** Critical  
**Evidence:** Tests identified as placeholder-level in audit (DATA_CLOUD_PRODUCT_REALITY_AUDIT_2026-04-19.md lines 418-421):
- `products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/FailureRecoveryTest.java`
- `products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/MultiTenantIsolationTest.java`
- `products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/EndToEndWorkflowTest.java`
- `products/data-cloud/ui/src/__tests__/e2e/CriticalPathJourney.test.tsx`
- `products/data-cloud/ui/tests/e2e/workflow.e2e.test.ts`

**Impact:**
- Current evidence overstates production readiness
- Release trust compromised
- Architecture decisions based on false evidence
- Auditability compromised

**Action Items:**
- [ ] Rename synthetic tests to reflect their actual scope (e.g., "unit" instead of "integration")
- [ ] Rebuild tests against launcher plus real durable providers where applicable
- [ ] Create real launcher-backed integration suite
- [ ] Add browser-level smoke pack
- [ ] Remove or reclassify FailureRecoveryTest (placeholder-level)
- [ ] Remove or reclassify MultiTenantIsolationTest (uses custom in-memory store)
- [ ] Remove or reclassify EndToEndWorkflowTest (placeholder)

**Files to Modify:**
- `products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/FailureRecoveryTest.java`
- `products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/MultiTenantIsolationTest.java`
- `products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/EndToEndWorkflowTest.java`
- `products/data-cloud/ui/src/__tests__/e2e/CriticalPathJourney.test.tsx`
- `products/data-cloud/ui/tests/e2e/workflow.e2e.test.ts`

---

### 🟡 P1: Required for Production Trust

#### DC-P1-1: Reconcile product truth across docs, route matrix, and UI boundaries

**Status:** NOT VERIFIED  
**Severity:** High  
**Evidence:** Requires manual review

**Action Items:**
- [ ] Audit all Data Cloud documentation
- [ ] Compare documentation against route matrix
- [ ] Compare documentation against UI boundaries
- [ ] Create single canonical source of truth
- [ ] Update docs to match implementation OR
- [ ] Update implementation to match docs
- [ ] Ensure consistency across all three sources

---

#### DC-P1-2: Remove or demote boundary-only pages from product framing until APIs exist

**Status:** NOT VERIFIED  
**Severity:** High  
**Evidence:** Requires UI boundary analysis

**Action Items:**
- [ ] Identify all boundary-only pages in UI
- [ ] Check which have backend APIs
- [ ] Remove or demote pages without APIs from product framing
- [ ] Add "preview" or "coming soon" labels where appropriate
- [ ] Update navigation to hide non-functional pages

---

#### DC-P1-3: Raise coverage thresholds materially above 0.20 instruction / 0.10 branch

**Status:** NOT FIXED  
**Severity:** High  
**Evidence:** Audit explicitly mentions low coverage thresholds for platform-launcher (line 472)

**Action Items:**
- [ ] Measure current coverage for platform-launcher
- [ ] Set new target thresholds (e.g., 0.50 instruction / 0.30 branch)
- [ ] Update build.gradle.kts with new thresholds
- [ ] Add coverage enforcement in CI
- [ ] Write additional tests to meet new thresholds
- [ ] Monitor coverage in CI/CD pipeline

**Files to Modify:**
- `products/data-cloud/platform-launcher/build.gradle.kts` (or relevant build file)

---

#### DC-P1-4: Prove durable workflow execution, recovery, and tenant isolation against real providers

**Status:** NOT VERIFIED  
**Severity:** High  
**Evidence:** Requires integration testing verification

**Action Items:**
- [ ] Create integration test for durable workflow execution
- [ ] Test workflow recovery after failure
- [ ] Test tenant isolation with real Data Cloud provider
- [ ] Test cross-tenant data leakage prevention
- [ ] Add tests to CI pipeline
- [ ] Document evidence of durable execution

---

#### DC-P1-5: Strengthen auth/session posture on the frontend

**Status:** NOT VERIFIED  
**Severity:** High  
**Evidence:** Requires frontend auth implementation review

**Action Items:**
- [ ] Audit frontend auth/session implementation
- [ ] Remove shell role as quasi-product mode
- [ ] Add clearer wording for shell role if kept
- [ ] Strengthen session validation
- [ ] Add proper error handling for auth failures
- [ ] Test auth flows end-to-end

---

## AEP Remediation Tasks

### 🔴 P0: Critical Blockers

#### AEP-P0-3: In-memory run history lost on restart

**Status:** PARTIALLY FIXED  
**Severity:** Critical  
**Evidence:** `recentRuns` Deque still exists in AepHttpServer.java line 201 (MAX_RECENT_RUNS = 1,000). RunLedgerService added at line 179 for durable storage when DataCloud is available, but startup warning not verified.

**Impact:**
- No production run history without Data Cloud
- Operators cannot diagnose post-restart issues

**Action Items:**
- [ ] Document prominently that Data Cloud is required for production
- [ ] Add startup warning when runLedger is null
- [ ] Add health check indicator for run history availability
- [ ] Consider removing recentRuns entirely if runLedger is available
- [ ] Update documentation to clarify Data Cloud requirement

**Files to Modify:**
- `products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java`
- AEP documentation

---

### 🟡 P1: Required for Production Trust

#### AEP-P1-10: GDPR erasure depth unverified

**Status:** PARTIALLY FIXED  
**Severity:** High  
**Evidence:** GDPR erasure endpoint exists (ComplianceController.java line 69 handleGdprErasure), but integration test verifying deletion from dc_memory, EventLogStore, and all caches not found.

**Impact:**
- Compliance obligation not verified by tests
- Risk of incomplete data deletion

**Action Items:**
- [ ] Add integration test for GDPR erasure
- [ ] Verify data deleted from dc_memory collection
- [ ] Verify data deleted from EventLogStore
- [ ] Verify data deleted from all caches
- [ ] Test with real Data Cloud provider
- [ ] Add to CI pipeline

**Files to Create:**
- `products/aep/server/src/test/java/com/ghatana/aep/server/http/controllers/GdprErasureDepthTest.java`

---

## YAPPC Remediation Tasks

### 🔴 P0: Critical Blockers

#### YAPPC-P0-2: Insecure defaults: `dev-key`, `default-tenant`, `change-me-in-production`

**Status:** PARTIALLY FIXED  
**Severity:** Critical  
**Evidence:** YappcEnvironmentConfig.java validation added (line 52 defines INSECURE_DEFAULT_KEY), but references still exist in code:
- TenantContextFilterTest.java line 41 uses "default-tenant"
- auth-session.ts line 107 rejects default-tenant but references still in tests

**Impact:**
- Auth/tenant isolation silently degrades in production
- Production auth cannot be trusted

**Action Items:**
- [ ] Remove all remaining references to "dev-key" in production code paths
- [ ] Remove all remaining references to "default-tenant" in production code paths
- [ ] Remove all remaining references to "change-me-in-production"
- [ ] Update TenantContextFilterTest to not use default-tenant
- [ ] Fail startup if placeholder values detected in non-dev environments
- [ ] Add YappcEnvironmentConfigTest validation

**Files to Modify:**
- Test files using default-tenant
- Any remaining production code with insecure defaults

---

#### YAPPC-P0-3: JWT secret alignment between Node BFF and Java service

**Status:** NOT VERIFIED  
**Severity:** Critical  
**Evidence:** Requires JWT configuration comparison

**Impact:**
- Credential confusion leads to auth bypass
- JWT tokens may not validate correctly across services

**Action Items:**
- [ ] Document single JWT authority
- [ ] Confirm same JWT secret source for Node BFF and Java service
- [ ] Confirm same algorithm for both services
- [ ] Add integration test for JWT validation across services
- [ ] Document JWT configuration in deployment guide

**Files to Review:**
- Node BFF JWT configuration
- Java LifecycleService JWT configuration
- Environment variables for JWT secrets

---

#### YAPPC-P0-5: Create `yappc-ci.yml` dedicated CI workflow

**Status:** NOT FIXED  
**Severity:** Critical  
**Evidence:** No yappc-ci.yml found in `.gitea/workflows/yappc/`

**Impact:**
- Product is not continuously verified
- No dedicated CI for YAPPC builds

**Action Items:**
- [ ] Create `.gitea/workflows/yappc/yappc-ci.yml`
- [ ] Add Gradle build step
- [ ] Add frontend build step
- [ ] Add critical journey E2E tests
- [ ] Upload yappc-release-evidence-bundle
- [ ] Configure failure notifications
- [ ] Add coverage reporting

**Files to Create:**
- `.gitea/workflows/yappc/yappc-ci.yml`

---

### 🟡 P1: Required for Production Trust

#### YAPPC-P1-1: JWT in `localStorage` (XSS risk)

**Status:** NOT FIXED  
**Severity:** High  
**Evidence:** Extensive localStorage usage for auth tokens:
- auth-session.ts lines 71, 87, 95
- localStorage.getItem('auth-session')
- localStorage.setItem('auth-session', ...)

**Impact:**
- Token theft via injected scripts (XSS vulnerability)

**Action Items:**
- [ ] Migrate access token to httpOnly secure cookie
- [ ] Keep refresh token server-side only
- [ ] Update auth-session.ts to use cookies instead of localStorage
- [ ] Update AuthProvider to handle cookie-based auth
- [ ] Add SameSite and Secure flags to cookies
- [ ] Test XSS resistance
- [ ] Update documentation

**Files to Modify:**
- `products/yappc/frontend/web/src/providers/auth-session.ts`
- `products/yappc/frontend/apps/api/src/middleware/devAuth.ts`
- Auth middleware in Node BFF

---

#### YAPPC-P1-2: `devAuth.ts` bypass — env leakage risk

**Status:** PARTIALLY FIXED  
**Severity:** High  
**Evidence:** Validation added but devAuth.ts still exists:
- check-auth-policy.js line 7 lists devAuth.ts as allowed
- devAuth.ts bypass exists in codebase

**Impact:**
- Auth bypass in CI/staging if env misconfigured

**Action Items:**
- [ ] Gate devAuth bypass on `NODE_ENV === 'development'`
- [ ] Add required `ALLOW_DEV_AUTH=true` env var check
- [ ] Never activate devAuth in test or staging
- [ ] Add warning logs when devAuth is active
- [ ] Document devAuth bypass risks
- [ ] Consider removing devAuth entirely in favor of proper test auth

**Files to Modify:**
- `products/yappc/frontend/apps/api/src/middleware/devAuth.ts`
- `products/yappc/frontend/scripts/check-auth-policy.js`

---

#### YAPPC-P1-3: Complete Javalin removal from scaffold modules

**Status:** PARTIALLY FIXED  
**Severity:** High  
**Evidence:** Platform layer Javalin removed, but scaffold modules still have 17 files using Javalin (JAVALIN_MIGRATION_GUIDE.md)

**Impact:**
- ADR-004 violation (ActiveJ-only requirement)
- Two web frameworks in classpath

**Action Items:**
- [ ] Identify all 17 scaffold module files using Javalin
- [ ] Migrate each Javalin route to platform:java:http
- [ ] Remove Javalin dependencies from scaffold module build files
- [ ] Update JAVALIN_MIGRATION_GUIDE.md with progress
- [ ] Test all migrated routes
- [ ] Remove Javalin from version catalog

**Reference:**
- `products/yappc/docs/JAVALIN_MIGRATION_GUIDE.md`

---

#### YAPPC-P1-4: `e2e-tests.yml` toolchain alignment

**Status:** NOT VERIFIED  
**Severity:** High  
**Evidence:** Requires e2e-tests.yml verification

**Impact:**
- CI E2E tests may fail silently with wrong Node/pnpm versions

**Action Items:**
- [ ] Verify e2e-tests.yml exists
- [ ] Check Node version (should be 20)
- [ ] Check pnpm version (should be 10.28.2)
- [ ] Add `corepack enable && corepack prepare pnpm@10.28.2 --activate`
- [ ] Test E2E pipeline locally
- [ ] Verify Playwright version compatibility

**Files to Review:**
- `.gitea/workflows/yappc/e2e-tests.yml` (or equivalent)

---

#### YAPPC-P1-5: DataCloud query correctness in repositories

**Status:** NOT VERIFIED  
**Severity:** High  
**Evidence:** Requires query semantics review

**Impact:**
- Silent data corruption
- Incorrect query results

**Action Items:**
- [ ] Review YappcDataCloudRepository query semantics
- [ ] Review ProjectRepository query semantics
- [ ] Fix any identified query issues
- [ ] Add integration tests for query correctness
- [ ] Test with real Data Cloud provider
- [ ] Add query logging for debugging

**Files to Review:**
- `products/yappc/infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/adapter/YappcDataCloudRepository.java`
- `products/yappc/infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/repository/ProjectRepository.java`

---

#### YAPPC-P1-7: `default-tenant` fallback in `TenantContextFilter`

**Status:** PARTIALLY FIXED  
**Severity:** High  
**Evidence:** Validation added (DataCloudArtifactStore.java line 186 rejects default-tenant), but test references still exist:
- TenantContextFilterTest.java line 41
- AuthProvider.test.tsx lines 57, 62

**Impact:**
- Tenant data leakage risk
- Silent fallback to default-tenant

**Action Items:**
- [ ] Update TenantContextFilterTest to not use default-tenant
- [ ] Update AuthProvider.test.tsx to not use default-tenant
- [ ] Remove default-tenant fallback in non-dev environments
- [ ] Add integration test for tenant rejection
- [ ] Document tenant validation requirements

**Files to Modify:**
- `products/yappc/core/refactorer/api/src/test/java/com/ghatana/refactorer/server/auth/TenantContextFilterTest.java`
- `products/yappc/frontend/web/src/providers/__tests__/AuthProvider.behavior.test.tsx`

---

## Copilot Guideline Compliance Tasks

### 🔵 TypeScript Type Safety

#### COP-TS-1: Reduce `any` type usage in TypeScript code

**Status:** VIOLATIONS FOUND  
**Severity:** Medium  
**Evidence:** Extensive use of `any` type in test files and some production code:
- AEP UI tests: PipelineCanvas.test.tsx, VoiceInput.test.tsx
- YAPPC frontend tests: multiple test files
- VoiceInput.tsx line 23-24: `SpeechRecognition: any`, `webkitSpeechRecognition: any`

**Impact:**
- Violates copilot guideline: "Type safety is implementation-time, not later"
- Reduces type safety and IDE support
- Increases runtime error risk

**Action Items:**
- [ ] Audit all TypeScript files for `any` usage
- [ ] Replace `any` with proper types in production code
- [ ] Replace `any` with proper types in test files where feasible
- [ ] Use `unknown` with type guards for untyped data
- [ ] Add ESLint rule to ban `any` (with exceptions for test mocks)
- [ ] Enable strict TypeScript mode if not already

**Priority Files:**
- `products/aep/ui/src/components/voice/VoiceInput.tsx`
- All test files with excessive `any` usage

---

### 🔵 Test Coverage

#### COP-TEST-1: Improve test coverage for Data Cloud

**Status:** CONCERNS  
**Severity:** Medium  
**Evidence:** Coverage thresholds at 0.20 instruction / 0.10 branch (very low)

**Impact:**
- Insufficient test evidence
- Violates copilot guideline: "Tests are part of the change"

**Action Items:**
- [ ] Raise coverage thresholds to 0.50 instruction / 0.30 branch
- [ ] Write additional unit tests
- [ ] Write additional integration tests
- [ ] Add coverage enforcement in CI
- [ ] Monitor coverage trends

---

### 🔵 Documentation

#### COP-DOC-1: Ensure Java documentation tags are complete

**Status:** GOOD  
**Severity:** Low  
**Evidence:** AEP server code consistently uses `@doc.type`, `@doc.purpose`, `@doc.layer`, `@doc.pattern` tags

**Action Items:**
- [ ] Audit Data Cloud Java code for missing @doc tags
- [ ] Audit YAPPC Java code for missing @doc tags
- [ ] Add missing tags where needed
- [ ] Ensure consistency across all products

---

## Implementation Timeline

### Sprint 1 (Immediate - Next 2 Weeks)

**Critical P0 Blockers:**
1. DC-P0-1: Fix design-system/data-grid cycle
2. DC-P0-2: Fix HTTP 200 anti-pattern
3. DC-P0-3: Replace misleading tests
4. YAPPC-P0-5: Create yappc-ci.yml
5. YAPPC-P0-2: Remove insecure defaults references

**High Priority P1:**
1. YAPPC-P1-1: Migrate JWT from localStorage to httpOnly cookie
2. YAPPC-P1-3: Complete Javalin removal in scaffold modules

---

### Sprint 2 (Short-Term - 2-4 Weeks)

**P1 Production Trust:**
1. DC-P1-3: Raise coverage thresholds
2. AEP-P1-10: Add GDPR erasure depth test
3. YAPPC-P1-4: Fix e2e-tests.yml toolchain
4. YAPPC-P1-5: Verify DataCloud query correctness
5. YAPPC-P1-7: Remove default-tenant fallback

**Copilot Compliance:**
1. COP-TS-1: Reduce `any` type usage
2. COP-TEST-1: Improve test coverage

---

### Sprint 3+ (Long-Term - 1-3 Months)

**Strategic P1/P2:**
1. DC-P1-1: Reconcile product truth
2. DC-P1-2: Remove boundary-only pages
3. DC-P1-4: Prove durable workflow execution
4. DC-P1-5: Strengthen auth/session posture
5. AEP-P0-3: Complete in-memory run history fix
6. YAPPC-P0-3: Verify JWT secret alignment

**Documentation:**
1. COP-DOC-1: Complete Java documentation tags

---

## Success Criteria

### Definition of Done for Each Task

- [ ] Code changes implemented
- [ ] Tests added/updated
- [ ] Documentation updated
- [ ] CI pipeline passes
- [ ] Manual verification completed
- [ ] No regressions introduced

### Overall Success Metrics

- **P0 Tasks:** 100% complete (0 partial, 0 not fixed)
- **P1 Tasks:** 90%+ complete
- **Copilot Compliance:** 95%+ adherence
- **Test Coverage:** Above 0.50 instruction / 0.30 branch
- **Type Safety:** Zero `any` in production code, minimal in tests

---

## Notes

- This to-do list is based on the comprehensive gap report generated on 2026-04-19
- Priorities may shift based on business needs
- Some tasks may require additional investigation before implementation
- Coordinate with product owners before removing or changing user-facing features
- Consider creating feature branches for each major task group

---

## References

- Data Cloud Audit: `products/data-cloud/DATA_CLOUD_PRODUCT_REALITY_AUDIT_2026-04-19.md`
- AEP Audit: `docs/AEP_PRODUCT_AUDIT_2026-04-19.md`
- YAPPC Audit: `docs/YAPPC_PRODUCT_AUDIT_2026-04-19.md`
- Copilot Instructions: `.github/copilot-instructions.md`
- YAPPC Remediation Progress: `products/yappc/docs/AUDIT_REMEDIATION_PROGRESS.md`
