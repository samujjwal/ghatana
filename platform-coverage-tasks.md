# Platform Library 100% Coverage Task List

**Generated:** 2026-04-20  
**Based on:** platform-lib-audit-report.md  
**Goal:** Achieve 100% meaningful coverage across all platform libraries and modules  
**Scope:** `platform/` directory (contracts, agent-catalog, java, typescript, shared-services, testing, kernel)

---

## Priority Legend
- **P0** - Critical security/production risks, must fix immediately
- **P1** - High impact, fix within 1-2 weeks
- **P2** - Medium impact, fix within 1 month
- **P3** - Low impact, technical debt cleanup

---

## P0 - Critical Security & Production Risks

### 1. Replace All Fake Tests with Real Tests (P0)
**Location:** `platform/testing/` (9 files) and `platform/shared-services/testing/` (3 files)  
**Issue:** All tests are object-literal theatre - they assert on hardcoded literals without invoking any real subject-under-test  
**Impact:** Zero actual coverage, misleading CI signal

**Tasks:**
- [ ] Delete or rewrite `platform/testing/advanced-accessibility-patterns.test.ts`
- [ ] Delete or rewrite `platform/testing/advanced-api-versioning.test.ts`
- [ ] Delete or rewrite `platform/testing/advanced-database-patterns.test.ts`
- [ ] Delete or rewrite `platform/testing/advanced-network-resilience.test.ts`
- [ ] Delete or rewrite `platform/testing/advanced-performance-memory.test.ts`
- [ ] Delete or rewrite `platform/testing/advanced-security-edge-cases.test.ts`
- [ ] Delete or rewrite `platform/testing/cross-library-integration.test.ts`
- [ ] Delete or rewrite `platform/testing/deployment-operations.test.ts`
- [ ] Delete or rewrite `platform/testing/e2e-workflows.test.ts`
- [ ] Delete or rewrite `platform/shared-services/testing/cross-service-integration.test.ts`
- [ ] Delete or rewrite `platform/shared-services/testing/http-layer.test.ts`
- [ ] Delete or rewrite `platform/shared-services/testing/security-boundary.test.ts`

**Replacement Strategy:**
- For each test file, either:
  - **Option A:** Delete entirely if the scenarios are already covered elsewhere
  - **Option B:** Rewrite with real subjects-under-test using Vitest setup with spawned servers
- Real cross-tier flow tests should use docker-compose fixtures
- Tests must invoke actual code paths, not assert on hardcoded expectations

**Verification:** Run test suite and confirm all tests exercise real code

---

### 2. Wire Integration Tests Module (P0)
**Location:** `platform/java/integration-tests/`  
**Issue:** Module has 3 real ActiveJ tests but no `build.gradle.kts` and not included in `settings.gradle.kts`  
**Impact:** Integration tests never run, zero coverage for cross-module integration

**Tasks:**
- [ ] Create `platform/java/integration-tests/build.gradle.kts` using `id("java-module")`
- [ ] Add `platform/java/integration-tests` to `settings.gradle.kts`
- [ ] Verify `SecurityIntegrationTest` passes
- [ ] Verify `ObservabilityIntegrationTest` passes
- [ ] Verify `DatabaseIntegrationTest` passes
- [ ] Add integration test execution to CI pipeline

**Verification:** Run `./gradlew :platform:java:integration-tests:test` and confirm all tests pass

---

### 3. Rewrite OpaClient with Proper Implementation (P0)
**Location:** `platform/java/policy-as-code/src/main/java/com/ghatana/platform/pac/OpaClient.java`  
**Issue:** Stub implementation with hand-written JSON serialization (`mapToJsonUnsafe`) and substring match for allow/deny decision  
**Impact:** Security vulnerability, bypassable by crafted OPA responses, production unsafe

**Tasks:**
- [ ] Replace `mapToJsonUnsafe()` with Jackson `ObjectMapper`
- [ ] Create typed `OpaResponse` record with proper structure:
  ```java
  record OpaResponse(OpaResult result) {}
  record OpaResult(boolean allow, Map<String, Object> data) {}
  ```
- [ ] Parse OPA response using Jackson deserialization
- [ ] Replace `response.body().contains("\"allow\":true")` with explicit boolean check on typed response
- [ ] Harden status-code handling (handle 4xx, 5xx, timeouts)
- [ ] Add integration test against real OPA Testcontainer
- [ ] Add unit tests for malformed JSON, missing fields, network errors

**Test Scenarios:**
- [ ] Test allow=true response
- [ ] Test allow=false response
- [ ] Test allow field missing
- [ ] Test malformed JSON response
- [ ] Test HTTP 5xx errors
- [ ] Test timeout scenarios
- [ ] Test large nested input

**Verification:** All new tests pass, security review approved

---

## P1 - High Impact Issues

### 4. Reconcile Governance Documents (P1)
**Locations:** 
- `.github/copilot-instructions.md §17`
- `platform/typescript/LIBRARY_GOVERNANCE.md`
- `platform/typescript/PACKAGE_NAMING_STANDARD.md`

**Issue:** Conflicting canonical TS package lists between documents  
**Impact:** Contributors get conflicting guidance, inconsistent package usage

**Tasks:**
- [ ] Audit all three documents for package list differences
- [ ] Identify single canonical list (likely LIBRARY_GOVERNANCE.md)
- [ ] Update `.github/copilot-instructions.md` to reference canonical list
- [ ] Have LIBRARY_GOVERNANCE.md and PACKAGE_NAMING_STANDARD.md reference copilot-instructions as authoritative entry point
- [ ] Remove `@ghatana/accessibility-audit` from instructions (directory doesn't exist)
- [ ] Add missing packages to instructions: `@ghatana/code-editor`, `@ghatana/config`, `@ghatana/state`, `@ghatana/forms`, `@ghatana/data-grid`, `@ghatana/wizard`, `@ghatana/events`, `@ghatana/domain-components`

**Verification:** All three documents are consistent and cross-reference correctly

---

### 5. Execute Fix-Forward Deprecation Cleanup (P1)
**Issue:** Multiple deprecated packages, classes, and fields kept for backward compatibility, violating Section 25 (fix-forward, no aliases, no backward compatibility)

**Tasks:**
- [ ] Delete `platform/typescript/canvas-core` (deprecated re-export facade)
- [ ] Delete `platform/typescript/canvas-plugins` (deprecated re-export facade)
- [ ] Delete `platform/typescript/canvas-tools` (deprecated re-export facade)
- [ ] Migrate all consumers to use `@ghatana/canvas` directly
- [ ] Remove `AgentType.LLM` from `platform/java/agent-core` (legacy, kept until 3.0.0)
- [ ] Remove legacy `Agent` interface from `platform/java/agent-core`
- [ ] Remove `AgentCapabilities` from `platform/java/agent-core`
- [ ] Delete `platform/java/agent-core/DEPRECATION_GUIDE.md` after cleanup
- [ ] Remove `_deprecated` and `_migratedTo` fields from `platform/agent-catalog/schema-migration.ts`
- [ ] Remove `@Deprecated(forRemoval = true)` members:
  - [ ] `com.ghatana.platform.core.validation.ValidationResult`
  - [ ] `com.ghatana.platform.aiintegration.LLMService.generate(String)`
  - [ ] `com.ghatana.platform.toolruntime.DefaultToolExecutor` (legacy constructor)
  - [ ] `com.ghatana.platform.security.model.User`
  - [ ] Other deprecated members across codebase

**Verification:** No deprecated code remains, all consumers migrated

---

### 6. Fix Root package.json Dependency Drift (P1)
**Location:** `platform/typescript/package.json`  
**Issue:** Root package.json declares outdated versions (TS 4.9.4, vitest 0.28.1, React 18.x, Node 16, npm) while actual packages use modern versions (TS 6.0.2, vitest 4.1.4, React 19.2.4, Node 25.6.0, pnpm)

**Tasks:**
- [ ] Update `typescript` to `^6.0.2`
- [ ] Update `vitest` to `^4.1.4`
- [ ] Update `@types/react` to match React 19.x
- [ ] Update `@types/node` to `^25.6.0`
- [ ] Update `engines.node` to `>=20` (or match actual usage)
- [ ] Replace `npm run --workspaces` with `pnpm` commands
- [ ] Populate `workspaces` field or remove if relying on root pnpm-workspace
- [ ] Verify all packages can build with updated root config

**Verification:** Root package.json matches actual package tooling, builds pass

---

### 7. Delete or Restore Orphan UI Shells (P1)
**Locations:** 
- `platform/typescript/audit-ui/`
- `platform/typescript/nlp-ui/`
- `platform/typescript/privacy-ui/`
- `platform/typescript/security-ui/`
- `platform/typescript/voice-ui/`
- `platform/typescript/selection-ui/`
- `platform/typescript/browser-events/`

**Issue:** Single-file `src/index.ts` folders with no package.json/tsconfig.json/README.md  
**Impact:** Dead code, confusing structure, not consumable

**Tasks:**
- [ ] Determine if any of these are still needed
- [ ] If not needed: Delete all orphan UI shell directories
- [ ] If needed: Restore to real packages with proper package.json, tsconfig.json, README.md
- [ ] Verify design-system exports map covers any needed functionality

**Verification:** No orphan single-file folders remain, or all are proper packages

---

### 8. Relocate Product-Coupled Shared Services (P1)
**Locations:**
- `platform/shared-services/aep-kernel-bridge/`
- `platform/shared-services/data-cloud-kernel-bridge/`
- `platform/shared-services/yappc-kernel-bridge/`

**Issue:** Product-specific bridges live in platform/ namespace, violating "platform modules should stay generic and product-agnostic" rule  
**Impact:** Architectural boundary violation, confusing dependency direction

**Tasks:**
- [ ] Move `aep-kernel-bridge` to `products/aep/kernel-bridge/`
- [ ] Move `data-cloud-kernel-bridge` to `products/data-cloud/kernel-bridge/`
- [ ] Move `yappc-kernel-bridge` to `products/yappc/kernel-bridge/`
- [ ] Update all import statements in consuming code
- [ ] Update Gradle module references in settings.gradle.kts
- [ ] Update build.gradle.kts files for moved modules
- [ ] Alternatively, move to top-level `shared-services/` outside `platform/`

**Verification:** No product-specific code in platform/, all builds pass

---

### 9. Promote HTTP Filters to Platform (P1)
**Issue:** `TenantContextFilter`, `ApiKeyAuthFilter`, `JwtAuthFilter` documented in instructions but implementations sit in product code  
**Impact:** Code duplication, inconsistent auth patterns across products

**Tasks:**
- [ ] Audit product code for existing filter implementations
- [ ] Promote `TenantContextFilter` to `platform/java/http`
- [ ] Promote `ApiKeyAuthFilter` to `platform/java/http`
- [ ] Promote `JwtAuthFilter` to `platform/java/http`
- [ ] Ensure filters follow ActiveJ patterns
- [ ] Add comprehensive tests for each filter
- [ ] Update product code to use platform filters
- [ ] Update instructions to reference platform implementations

**Verification:** All products use platform HTTP filters, tests pass

---

### 10. Add Schema Validation for Agent Catalog (P1)
**Location:** `platform/agent-catalog/`  
**Issue:** No CI-time validation that loaded YAMLs conform to `catalog-schema.yaml`  
**Impact:** Invalid catalog data could be committed

**Tasks:**
- [ ] Create CI validation script using schema validator
- [ ] Add validation to GitHub Actions workflow
- [ ] Validate all YAML files against `catalog-schema.yaml`
- [ ] Fail PR if validation fails
- [ ] Add tests for schema validation logic

**Verification:** CI validates all catalog YAMLs on PR

---

## P2 - Medium Impact Issues

### 11. Centralize Inline Dependency Coordinates (P2)
**Location:** `platform/contracts/build.gradle.kts`  
**Issue:** Pinned versions for codemodel, jsonschema2pojo, mustache.java, jsoup, protobuf-java-util are hardcoded strings instead of in libs.versions.toml

**Tasks:**
- [ ] Extract all inline version coordinates to `gradle/libs.versions.toml`
- [ ] Update `platform/contracts/build.gradle.kts` to use version catalog
- [ ] Verify build still works after changes

**Verification:** No inline versions in contracts build.gradle.kts

---

### 12. Flatten Foundation Platform Utils (P2)
**Location:** `platform/typescript/foundation/platform-utils/`  
**Issue:** Lives one level deeper than siblings, inconsistent with pnpm-workspace conventions

**Tasks:**
- [ ] Move `platform/typescript/foundation/platform-utils/` to `platform/typescript/platform-utils/`
- [ ] Update pnpm-workspace.yaml to reflect new location
- [ ] Update all import statements
- [ ] Delete empty `foundation/` directory

**Verification:** Platform utils at consistent depth, all imports work

---

### 13. Remove Committed bin/ Directories (P2)
**Locations:**
- `platform/java/agent-core/bin/`
- `platform/java/core/bin/`

**Issue:** Eclipse output directories committed, should be gitignored  
**Impact:** Repository bloat, potential conflicts

**Tasks:**
- [ ] Add `**/bin/` to root `.gitignore`
- [ ] Remove tracked `platform/java/agent-core/bin/` from git
- [ ] Remove tracked `platform/java/core/bin/` from git
- [ ] Remove `.disabled` test file from agent-core/bin/
- [ ] Verify no other bin/ directories are tracked

**Verification:** No bin/ directories in git, gitignore updated

---

### 14. Resolve platform/kernel/ Confusion (P2)
**Locations:** `platform/kernel/docs/` vs `platform-kernel/`  
**Issue:** platform/kernel/ only contains docs, actual code in platform-kernel/, naming collision confusing

**Tasks:**
- [ ] Choose one of:
  - **Option A:** Move `platform/kernel/docs/` to `platform-kernel/docs/` and delete `platform/kernel/`
  - **Option B:** Rename `platform/kernel/` to `platform/kernel-docs/` and document relationship
- [ ] Update all references to docs location
- [ ] Update README files if needed

**Verification:** Single source of truth for kernel docs, no confusion

---

### 15. Enable or Remove Disabled Architecture Rules (P2)
**Location:** `platform/java/testing/src/test/java/com/ghatana/platform/testing/architecture/ArchitectureRulesTest.java`  
**Issue:** 6 architectural assertions disabled with TODO comments  
**Impact:** Architectural enforcement not active, misleading

**Tasks:**
- [ ] Review each disabled assertion
- [ ] If still valid: Enable and fix any violations
- [ ] If no longer valid: Delete assertion
- [ ] Remove TODO comments
- [ ] Ensure all enabled rules pass

**Verification:** All architecture rules enabled and passing, or removed

---

### 16. Convert Schema Migration to TypeScript (P2)
**Location:** `platform/agent-catalog/schema-migration.ts`  
**Issue:** Migration script is JS not TS, lives outside pnpm package, has no tests  
**Impact:** Type safety gap, inconsistent tooling

**Tasks:**
- [ ] Convert `schema-migration.ts` to TypeScript
- [ ] Move to appropriate pnpm package or create new package
- [ ] Add type definitions
- [ ] Add tests for migration logic
- [ ] Update CI to use TS version
- [ ] Remove _deprecated pattern per fix-forward policy

**Verification:** Migration script is TypeScript with tests

---

### 17. Remove Production `any` Types (P2)
**Locations:** 
- `platform/typescript/canvas/src/elements/live-react.ts` (lines 97, 185)
- Test files with ~76 `as any` casts

**Tasks:**
- [ ] Replace `React.ComponentType<any>` with proper generic types
- [ ] Audit all test files for unjustified `as any` casts
- [ ] Replace `as any` with proper type guards or typed helpers
- [ ] Keep only justified boundary adapter mocks with typed wrappers
- [ ] Add eslint rule to prevent new `any` types in production code

**Verification:** No production `any` types, tests use typed helpers

---

### 18. Remove Duplicate Java Packages (P2)
**Location:** `platform/java/core/`  
**Issue:** Ships both `com.ghatana.platform.core.*` (canonical) and legacy `com.ghatana.platform.validation.ValidationResult`

**Tasks:**
- [ ] Delete legacy `com.ghatana.platform.validation.ValidationResult`
- [ ] Move to clearly retired-only directory if needed for reference
- [ ] Update all consumers to use canonical location
- [ ] Verify no package conflicts

**Verification:** Single canonical package structure

---

### 19. Fix Build File Duplication (P2)
**Location:** `platform/java/policy-as-code/build.gradle.kts`  
**Issue:** Lists `libs.bundles.testing.containers` twice (lines 24-25)

**Tasks:**
- [ ] Remove duplicate `libs.bundles.testing.containers` entry
- [ ] Verify build still works

**Verification:** No duplicate dependencies in build.gradle.kts

---

### 20. Resolve UI Package Deprecation Conflict (P2)
**Location:** `platform/typescript/ui/`  
**Issue:** Listed as deprecated by governance, but README says it's backing implementation for design-system

**Tasks:**
- [ ] Determine actual status of `@ghatana/ui` package
- [ ] If backing implementation: Remove from deprecated list in governance
- [ ] If truly deprecated: Fold into design-system and deprecate package
- [ ] Update documentation to match reality

**Verification:** Governance and package documentation agree

---

## P3 - Low Impact / Technical Debt

### 21. Add Audit Integrity Guarantees (P3)
**Location:** `platform/java/audit/`  
**Issue:** No append-only/hash-chain integrity assurance for audit storage

**Tasks:**
- [ ] Evaluate if append-only/hash-chain is needed for audit store
- [ ] If needed: Implement hash-chain integrity verification
- [ ] Add tests for integrity guarantees
- [ ] Document choice (if not implementing)

**Verification:** Audit integrity either implemented or documented as not required

---

### 22. Complete Observability TODOs (P3)
**Location:** `platform/java/observability/src/main/java/com/ghatana/platform/observability/http/TraceHttpService.java`  
**Issue:** Two TODOs about migrating to `core:http-server` ResponseBuilder

**Tasks:**
- [ ] Complete migration to `core:http-server` ResponseBuilder
- [ ] Remove TODO comments
- [ ] Add tests for migrated code

**Verification:** No TODOs in TraceHttpService, all tests pass

---

### 23. Add Performance Benchmarks (P3)
**Locations:** `platform/java/database`, `platform/java/http`, `platform/java/messaging`, `platform/java/observability`  
**Issue:** No JMH benchmarks except agent-core (which declares but has no source)

**Tasks:**
- [ ] Add JMH benchmarks for database operations
- [ ] Add JMH benchmarks for HTTP layer
- [ ] Add JMH benchmarks for messaging throughput
- [ ] Add JMH benchmarks for observability ingest
- [ ] Either add agent-core JMH sources or remove JMH dependency
- [ ] Add benchmark execution to CI (nightly)

**Verification:** Performance baselines established for critical paths

---

### 24. Add Contract Tests (P3)
**Location:** `platform/contracts/`  
**Issue:** No schema evolution/backward-compat assertions for published proto files

**Tasks:**
- [ ] Add `buf breaking` check for protobuf schemas
- [ ] Add OpenAPI consumer-driven contract tests for products
- [ ] Add breaking change check to CI
- [ ] Fail PR if breaking changes detected

**Verification:** CI validates schema backward compatibility

---

### 25. Add Cross-Module Contract Tests (P3)
**Scope:** Platform-wide  
**Issue:** No cross-module contract tests beyond OpenAPI generation

**Tasks:**
- [ ] Identify critical module boundaries
- [ ] Add contract tests for each boundary
- [ ] Use Pact or similar for consumer-driven contracts
- [ ] Add to CI pipeline

**Verification:** Critical boundaries have contract tests

---

### 26. Deep Audit for Partial Modules (P3)
**Locations:** Modules marked "PARTIAL" in audit report  
**Issue:** Several modules need deeper test coverage review

**Tasks:**
- [ ] Deep audit `platform/java/identity` test coverage
- [ ] Deep audit `platform/java/cache` test coverage
- [ ] Deep audit `platform/java/config` test coverage
- [ ] Deep audit `platform/java/data-governance` test coverage
- [ ] Deep audit `platform/java/ds-cli` test coverage
- [ ] Add missing tests to reach meaningful coverage

**Verification:** All partial modules have adequate test coverage

---

### 27. Verify Testcontainers Utilities (P3)
**Location:** `platform/java/database/testcontainers/`  
**Issue:** Need to verify if this is a separate Gradle subproject or wired for reuse

**Tasks:**
- [ ] Verify Testcontainers utilities are properly wired
- [ ] If separate subproject: Ensure in settings.gradle.kts
- [ ] If not: Wire for reuse by other modules
- [ ] Document usage pattern

**Verification:** Testcontainers utilities accessible to all modules

---

### 28. Add AI/ML Evaluation Harness (P3)
**Location:** `platform/java/ai-integration/`  
**Issue:** No evaluation harness (no eval datasets, acceptance thresholds, drift metrics)

**Tasks:**
- [ ] Create evaluation dataset structure
- [ ] Add acceptance threshold definitions
- [ ] Implement drift metric tracking
- [ ] Add prompt/version registry beyond YAML
- [ ] Add eval tests to CI

**Verification:** AI/ML features have eval harness with thresholds

---

### 29. Add Policy Guard Rail Before LLM Call (P3)
**Location:** `platform/java/ai-integration/`  
**Issue:** No policy guard rail before LLM call (OpaClient would be natural place after fix)

**Tasks:**
- [ ] Integrate OpaClient (after P0 fix) into LLM call flow
- [ ] Add policy check before LLM invocation
- [ ] Add tests for policy guard rail
- [ ] Document policy evaluation pattern

**Verification:** All LLM calls pass through policy evaluation

---

### 30. Verify Deprecated aep-connectors Removal (P3)
**Location:** `platform/java/messaging/`  
**Issue:** Module merged from connectors + aep-connectors, need to verify all deprecated references removed

**Tasks:**
- [ ] Search codebase for any remaining `aep-connectors` references
- [ ] Remove any found references
- [ ] Update documentation
- [ ] Verify products use unified module

**Verification:** No deprecated connector references remain

---

## Test Coverage Requirements by Module

### Java Modules
For each Java module, ensure:
- [ ] Unit tests for all business logic
- [ ] Integration tests for database/external dependencies (using Testcontainers)
- [ ] Contract tests for API boundaries
- [ ] ActiveJ async tests extend `EventloopTestBase` and use `runPromise(...)`
- [ ] Minimum 80% line coverage for critical paths
- [ ] 100% coverage for security-sensitive code

### TypeScript Packages
For each TypeScript package, ensure:
- [ ] Unit tests in `__tests__/` co-located
- [ ] Vitest configuration with strict mode
- [ ] No `any` types in production code
- [ ] Type safety with `tsc --noEmit` in CI
- [ ] Minimum 80% line coverage for critical paths
- [ ] 100% coverage for security-sensitive code

### Cross-Cutting Concerns
- [ ] Security tests for all auth/authz code
- [ ] Performance tests for high-throughput paths
- [ ] Resilience tests for external dependencies
- [ ] Accessibility tests for UI components (Axe CI gating)
- [ ] Visual regression tests for canvas components

---

## Execution Strategy

### Phase 1: P0 Critical Fixes (Week 1)
1. Replace all fake tests (Task 1)
2. Wire integration tests module (Task 2)
3. Rewrite OpaClient (Task 3)

### Phase 2: P1 High Impact (Weeks 2-3)
4. Reconcile governance documents (Task 4)
5. Execute fix-forward deprecation (Task 5)
6. Fix root package.json drift (Task 6)
7. Delete orphan UI shells (Task 7)
8. Relocate product-coupled services (Task 8)
9. Promote HTTP filters (Task 9)
10. Add schema validation (Task 10)

### Phase 3: P2 Medium Impact (Weeks 4-5)
11-20. Complete all P2 tasks

### Phase 4: P3 Technical Debt (Weeks 6-8)
21-30. Complete all P3 tasks

### CI Integration
- [ ] Add test execution to PR checks (unit + contract)
- [ ] Add integration test execution to nightly builds
- [ ] Add performance benchmarks to weekly runs
- [ ] Add visual regression to release pipeline
- [ ] Add accessibility audit to PR checks

---

## Success Criteria

- [ ] All P0 tasks completed
- [ ] All P1 tasks completed
- [ ] All P2 tasks completed
- [ ] All P3 tasks completed
- [ ] 100% of tests exercise real code (no object-literal theatre)
- [ ] All integration tests run in CI
- [ ] All security vulnerabilities addressed
- [ ] All deprecated code removed
- [ ] All governance documents consistent
- [ ] All dependencies centralized
- [ ] All orphan code removed or restored
- [ ] All architectural boundaries enforced
- [ ] Minimum 80% coverage across all modules
- [ ] 100% coverage for security-sensitive code
- [ ] CI pipeline validates all quality gates
- [ ] Performance baselines established
- [ ] AI/ML eval harness operational

---

## Notes

- This task list is based on the comprehensive audit performed on 2026-04-20
- Priorities align with security risk and production readiness impact
- Some tasks may have dependencies on others - adjust order as needed
- Regular progress reviews recommended to track completion
- Update this file as tasks are completed or new issues discovered
