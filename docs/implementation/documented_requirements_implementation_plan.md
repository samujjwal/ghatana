# Data Cloud Documented Requirements Implementation Plan

**Date:** 2026-05-09
**Purpose:** Detailed, consistent, correct, and production-grade implementation plan for 12 documented tasks requiring significant infrastructure work.

---

## Executive Summary

This document provides a comprehensive implementation plan for 12 Data Cloud TODO items that have been documented but require substantial infrastructure work. These tasks span frontend type generation, AI fallback implementation, platform dependency auditing, backend data integrity (idempotency, transactions, deletion lifecycle), UI/UX enhancements, shared library cleanup, and security testing.

**Estimated Effort:** 8-12 weeks for full implementation (depending on team size and parallelization)
**Risk Level:** Medium-High (significant architectural changes and infrastructure work)
**Dependencies:** Some tasks have dependencies on others (noted below)

---

## Task 1: DC-P1-006 - Frontend API Type Generation

### Current State
- No openapi-typescript infrastructure exists
- Frontend uses ad hoc TypeScript types defined in API service files
- OpenAPI specs exist: `products/data-cloud/contracts/openapi/action-plane.yaml`, `aep.yaml`, `data-cloud.yaml`

### Required Infrastructure
- openapi-typescript package integration
- Build pipeline integration to regenerate types on OpenAPI changes
- Type/contract tests to verify UI cannot compile against stale API types

### Implementation Plan

#### Phase 1: Tooling Setup (Week 1)
1. **Install openapi-typescript**
   ```bash
   pnpm add -D openapi-typescript @openapitools/openapi-generator-cli
   ```

2. **Create generation script**
   ```typescript
   // scripts/generate-api-types.ts
   import { generateApiClient } from 'openapi-typescript-codegen';
   
   generateApiClient({
     name: 'DataCloudApi',
     input: '../contracts/openapi/action-plane.yaml',
     output: './src/api/generated',
     exportCore: true,
     exportSchemas: true,
     exportServices: true,
     exportHooks: true,
   });
   ```

3. **Add to package.json scripts**
   ```json
   {
     "scripts": {
       "api:types:generate": "tsx scripts/generate-api-types.ts",
       "api:types:check": "tsx scripts/generate-api-types.ts --check"
     }
   }
   ```

#### Phase 2: Build Integration (Week 1-2)
1. **Add pre-commit hook**
   - Run `api:types:check` to detect drift
   - Fail commit if generated types are out of sync

2. **Add CI check**
   - Add step in GitHub Actions workflow
   - Fail PR if types are not regenerated

3. **Update pnpm workspace**
   - Add generated types to build dependencies
   - Ensure type checking includes generated types

#### Phase 3: Migration (Week 2-3)
1. **Audit current ad hoc types**
   - List all manual TypeScript interfaces in `src/api/`
   - Map to corresponding OpenAPI schemas

2. **Replace with generated types**
   - Update API service files to use generated types
   - Remove ad hoc interfaces
   - Update component props to use generated types

3. **Add type validation tests**
   ```typescript
   // src/__tests__/api/typesContract.test.ts
   describe('API types contract validation', () => {
     it('generated types match OpenAPI schema', () => {
       // Verify generated types are up to date
     });
   });
   ```

#### Phase 4: Documentation (Week 3)
1. **Update developer documentation**
   - Document type generation workflow
   - Add troubleshooting guide

2. **Update ARCHITECTURE.md**
   - Remove DC-P1-006 note (completed)

### Acceptance Criteria
- [ ] OpenAPI spec changes trigger automatic type regeneration
- [ ] CI fails if types are out of sync
- [ ] All API services use generated types
- [ ] No ad hoc TypeScript types for API responses
- [ ] Type validation tests pass

### Dependencies
- DC-UI-004 (generated clients) - should be coordinated
- OpenAPI specs (action-plane.yaml) must be stable

### Estimated Effort
- 2-3 weeks

---

## Task 2: DC-P1-011 - AI Heuristic Fallback Implementation

### Current State
- AI handlers exist: `AiAssistHandler`, `AIAssistService`, `AIAssistServiceImpl`
- No explicit heuristic fallback for degraded/preview behavior
- AI operations may fail silently or return errors

### Required Implementation
- Surface AI heuristic fallback as degraded/preview behavior
- Ensure AI failures don't block core workflows
- Add telemetry for AI fallback events

### Implementation Plan

#### Phase 1: Heuristic Detection (Week 1)
1. **Define heuristic quality metrics**
   ```java
   public enum AiQualityLevel {
       HIGH,    // AI response is confident and actionable
       MEDIUM,  // AI response is reasonable but may need review
       LOW,     // AI response is degraded, fallback to heuristics
       FALLBACK // AI unavailable, use pure heuristics
   }
   ```

2. **Implement quality scoring**
   - Add confidence score evaluation in `AIAssistServiceImpl`
   - Detect timeout, rate limit, or model unavailability
   - Score based on response quality indicators

#### Phase 2: Heuristic Fallback Logic (Week 2)
1. **Create heuristic fallback service**
   ```java
   public class HeuristicFallbackService {
       public List<EntitySuggestion> getEntitySuggestionsHeuristic(
           String collection, String query, int limit
       ) {
           // Implement rule-based suggestions without AI
           // - Prefix matching
           // - Recent entity frequency
           // - Collection schema hints
       }
   }
   ```

2. **Integrate with AiAssistHandler**
   - Check AI quality before returning results
   - Fall back to heuristics if quality is LOW or FALLBACK
   - Log fallback events for telemetry

#### Phase 3: Degraded/Preview Status (Week 2)
1. **Update Runtime Truth**
   - Add AI quality metric to Runtime Truth response
   - Surface status: LIVE, DEGRADED (using heuristics), PREVIEW (heuristic-only)

2. **Update DisabledSurfacePage**
   - Show AI degradation status when AI is unavailable
   - Display "Using heuristic suggestions" message

#### Phase 4: Testing (Week 3)
1. **Add fallback tests**
   ```java
   @Test
   void shouldUseHeuristicsWhenAiUnavailable() {
       // Test AI unavailability triggers heuristic fallback
   }
   
   @Test
   void shouldSurfaceDegradedStatusForHeuristicMode() {
       // Test Runtime Truth shows DEGRADED when using heuristics
   }
   ```

2. **Add telemetry validation**
   - Verify fallback events are logged
   - Verify metrics are emitted for AI quality

### Acceptance Criteria
- [ ] AI failures trigger heuristic fallback without blocking workflows
- [ ] Runtime Truth surfaces AI quality status (LIVE/DEGRADED/PREVIEW)
- [ ] Heuristic fallback is transparent to users (shows degraded status)
- [ ] Telemetry tracks AI quality and fallback events
- [ ] Tests verify fallback behavior

### Dependencies
- None (can be implemented independently)

### Estimated Effort
- 2-3 weeks

---

## Task 3: DC-BND-003 - Platform Module Dependency Audit

### Current State
- Platform has some data-cloud references (PlatformDataCloudSemanticBoundaryTest, data-cloud-openapi.yaml)
- Existing test enforces no product-specific semantics in platform modules
- Historical violations have been remediated

### Required Audit
- Comprehensive dependency audit of platform modules
- Identify any remaining product-specific semantics
- Ensure platform modules remain genuinely reusable

### Implementation Plan

#### Phase 1: Dependency Analysis (Week 1)
1. **Audit platform/java modules**
   - Scan all `platform/java/*/src/main/java` for data-cloud references
   - Check package names, class names, method names, constants
   - Check JavaDoc comments and error messages

2. **Audit platform/typescript modules**
   - Scan all `platform/typescript/*/src` for data-cloud references
   - Check TypeScript types, interfaces, enums

3. **Audit contracts**
   - Verify `contracts/src/test/resources/data-cloud-openapi.yaml` is test-only
   - Ensure no data-cloud-specific contracts in shared contracts

#### Phase 2: Remediation Planning (Week 1)
1. **Classify findings**
   - **Critical violations**: Compile-time dependencies on product classes
   - **Doc-only violations**: Comments or JavaDoc references (harmless but should be documented)
   - **Test-only violations**: Test fixtures using product classes (acceptable)

2. **Create remediation plan**
   - For critical violations: Move to product-specific module
   - For doc-only violations: Add inline documentation
   - For test-only violations: Document as intentional

#### Phase 3: Enforcement (Week 2)
1. **Enhance PlatformDataCloudSemanticBoundaryTest**
   - Add more comprehensive regex patterns
   - Add checks for TypeScript modules
   - Add checks for contracts

2. **Add CI guard**
   - Run PlatformDataCloudSemanticBoundaryTest in CI
   - Fail build if new violations are introduced

#### Phase 4: Documentation (Week 2)
1. **Update platform documentation**
   - Document allowed dependencies
   - Document test-only exceptions

2. **Update ARCHITECTURE.md**
   - Remove DC-BND-003 note (completed)

### Acceptance Criteria
- [ ] No compile-time dependencies on product classes in platform modules
- [ ] All doc-only violations are documented and justified
- [ ] PlatformDataCloudSemanticBoundaryTest covers all platform modules
- [ ] CI enforces boundary rules

### Dependencies
- None (audit and enforcement task)

### Estimated Effort
- 1-2 weeks

---

## Task 4: DC-BE-002 - Ensure Mutating Routes are Idempotent

### Current State
- Entity CRUD has idempotency via `EntityWriteIdempotencyStore` and `X-Idempotency-Key` header
- Other mutating routes (pipelines, events, governance, analytics) lack idempotency
- Documentation added to `EntityCrudHandler.java` noting requirements

### Required Implementation
- Add idempotency support to all mutating routes (POST, PUT, DELETE, PATCH)
- Document non-idempotent routes explicitly
- Add retry tests for idempotent routes

### Implementation Plan

#### Phase 1: Idempotency Infrastructure (Week 1)
1. **Extend idempotency store interface**
   ```java
   public interface WriteIdempotencyStore {
       Optional<Map<String, Object>> get(String tenantId, String scope, String idempotencyKey);
       void put(String tenantId, String scope, String idempotencyKey, Map<String, Object> responseBody);
   }
   ```

2. **Create generic idempotency helper**
   ```java
   public class IdempotencyHelper {
       public static Promise<HttpResponse> checkIdempotencyOrNull(
           WriteIdempotencyStore store,
           String tenantId,
           String scope,
           String idempotencyKey
       ) {
           // Reuse existing logic from EntityCrudHandler
       }
   }
   ```

#### Phase 2: Add Idempotency to Handlers (Week 2-3)
1. **Pipeline operations**
   - POST /api/v1/pipelines (pipeline creation)
   - POST /api/v1/pipelines/:pipelineId/execute (pipeline execution)
   - PUT /api/v1/pipelines/:pipelineId (pipeline update)

2. **Event operations**
   - POST /api/v1/events (event append)
   - POST /api/v1/events/batch (batch event append)

3. **Governance operations**
   - POST /api/v1/governance/policies (policy creation)
   - PUT /api/v1/governance/policies/:id (policy update)

4. **Analytics operations**
   - POST /api/v1/analytics/queries (query creation)
   - DELETE /api/v1/analytics/queries/:id (query cancellation)

#### Phase 3: Non-Idempotent Route Documentation (Week 3)
1. **Document explicitly non-idempotent routes**
   - Add OpenAPI `x-idempotency: false` extension
   - Add API documentation warning

2. **Add HTTP headers**
   - Return `Warning: 299 - This operation is not idempotent` header

#### Phase 4: Retry Tests (Week 4)
1. **Add retry test framework**
   ```java
   @Test
   void shouldRejectDuplicatePipelineCreation() {
       String idempotencyKey = UUID.randomUUID().toString();
       // Create pipeline with idempotency key
       // Retry with same key
       // Assert cached response is returned
   }
   ```

2. **Test all idempotent routes**
   - Entity CRUD (already tested)
   - Pipeline operations
   - Event operations
   - Governance operations
   - Analytics operations

### Acceptance Criteria
- [ ] All mutating routes support X-Idempotency-Key header
- [ ] Non-idempotent routes are documented with OpenAPI extension
- [ ] Retry tests verify idempotency for all mutating routes
- [ ] No data corruption on retry

### Dependencies
- EntityWriteIdempotencyStore infrastructure exists
- Requires coordination with DC-BE-003 (transaction boundaries)

### Estimated Effort
- 3-4 weeks

---

## Task 5: DC-BE-003 - Add Transaction Boundaries for Multi-Step Writes

### Current State
- No explicit transaction boundaries for multi-step writes
- Entity write + event append + audit logging are not atomic
- Partial writes could leave invalid state
- Documentation added to `DataCloudHttpServer.java` noting requirements

### Required Implementation
- Wrap write + event + audit flows atomically where required
- Ensure partial writes cannot leave invalid state
- Add failure injection tests

### Implementation Plan

#### Phase 1: Transaction Pattern Design (Week 1)
1. **Define transaction boundaries**
   - Entity CRUD: entity write + CDC event + audit log
   - Event append: event write + metadata update + audit log
   - Governance operations: policy write + event + audit log

2. **Design transaction API**
   ```java
   public interface TransactionManager {
       <T> Promise<T> executeInTransaction(Supplier<Promise<T>> operation);
   }
   ```

#### Phase 2: Transaction Implementation (Week 2-3)
1. **Implement transaction manager**
   ```java
   public class DataCloudTransactionManager implements TransactionManager {
       private final EntityStore entityStore;
       private final EventLogStore eventLogStore;
       private final AuditService auditService;
       
       public <T> Promise<T> executeInTransaction(Supplier<Promise<T>> operation) {
           // Begin transaction
           try {
               T result = operation.get();
               // Commit if all operations succeed
               return Promise.of(result);
           } catch (Exception e) {
               // Rollback all operations
               throw new TransactionException(e);
           }
       }
   }
   ```

2. **Integrate with EntityCrudHandler**
   - Wrap entity write + event + audit in transaction
   - Rollback on failure

3. **Integrate with EventHandler**
   - Wrap event append + metadata update in transaction
   - Rollback on failure

#### Phase 3: Failure Injection Tests (Week 3-4)
1. **Add failure injection framework**
   ```java
   @Test
   void shouldRollbackEntityWriteOnEventFailure() {
       // Inject failure after entity write
       // Verify entity is rolled back
   }
   
   @Test
   void shouldRollbackEventAppendOnAuditFailure() {
       // Inject failure after event write
       // Verify event is rolled back
   }
   ```

2. **Test all transaction boundaries**
   - Entity CRUD transaction tests
   - Event append transaction tests
   - Governance operation transaction tests

#### Phase 4: Documentation (Week 4)
1. **Update DataCloudHttpServer.java**
   - Remove DC-BE-003 note (completed)
   - Document transaction boundaries

### Acceptance Criteria
- [ ] Multi-step writes are atomic
- [ ] Partial writes cannot leave invalid state
- [ ] Failure injection tests verify rollback behavior
- [ ] Transaction boundaries are documented

### Dependencies
- Requires coordination with DC-BE-002 (idempotency)
- Requires coordination with DC-BE-004 (deletion lifecycle)

### Estimated Effort
- 3-4 weeks

---

## Task 6: DC-BE-004 - Standardize Deletion Lifecycle

### Current State
- Current implementation uses hard delete (immediate removal)
- Retention purge exists (RETENTION_PURGE in DataLifecycleHandler)
- No soft delete, archive, or retention purge policy
- Documentation added to `DataLifecycleHandler.java` noting requirements

### Required Implementation
- Define hard delete vs soft delete vs archive vs retention purge
- Implement deletion lifecycle policy
- Ensure deletes are deterministic and auditable
- Add delete lifecycle tests

### Implementation Plan

#### Phase 1: Deletion Policy Design (Week 1)
1. **Define deletion modes**
   ```java
   public enum DeletionMode {
       HARD_DELETE,      // Immediate removal from primary storage
       SOFT_DELETE,      // Mark as deleted with tombstone, retain for audit/restore
       ARCHIVE,          // Move to cold storage for long-term retention
       RETENTION_PURGE    // Automated deletion based on governance retention policies
   }
   ```

2. **Define deletion lifecycle policy**
   ```java
   public class DeletionLifecyclePolicy {
       public DeletionMode getDeletionMode(String collection, String deploymentMode) {
           // Production: SOFT_DELETE -> ARCHIVE -> RETENTION_PURGE
           // Development: HARD_DELETE
       }
       
       public Duration getRetentionPeriod(DeletionMode mode) {
           // SOFT_DELETE: 30 days
           // ARCHIVE: 1 year
           // RETENTION_PURGE: based on governance policy
       }
   }
   ```

#### Phase 2: Implementation (Week 2-3)
1. **Implement soft delete**
   - Add `deletedAt` timestamp to entities
   - Filter deleted entities in queries
   - Add restore operation

2. **Implement archive**
   - Move deleted entities to cold storage
   - Implement storage tier lifecycle

3. **Integrate with DataLifecycleHandler**
   - Add deletion mode parameter to purge endpoint
   - Implement lifecycle transitions

#### Phase 3: Delete Lifecycle Tests (Week 3-4)
1. **Add deletion lifecycle tests**
   ```java
   @Test
   void shouldSoftDeleteEntity() {
       // Verify entity is marked deleted but not removed
   }
   
   @Test
   void shouldArchiveAfterRetentionPeriod() {
       // Verify entity is moved to cold storage
   }
   
   @Test
   void shouldPurgeAfterGovernancePolicy() {
       // Verify entity is permanently deleted
   }
   ```

2. **Test determinism**
   - Verify delete operations are idempotent
   - Verify audit trail is complete

#### Phase 4: Documentation (Week 4)
1. **Update DataLifecycleHandler.java**
   - Remove DC-BE-004 note (completed)
   - Document deletion lifecycle policy

2. **Update API documentation**
   - Document deletion modes in OpenAPI
   - Add deletion lifecycle examples

### Acceptance Criteria
- [ ] Deletion lifecycle policy is defined and implemented
- [ ] Soft delete, archive, and retention purge are supported
- [ ] Deletes are deterministic and auditable
- [ ] Delete lifecycle tests verify behavior

### Dependencies
- Requires coordination with DC-BE-003 (transaction boundaries)
- Requires coordination with DC-BE-002 (idempotency)

### Estimated Effort
- 3-4 weeks

---

## Task 7: DC-UI-002 - Ensure Every Unavailable Surface Has Actionable Disabled UI

### Current State
- `DisabledSurfacePage.tsx` exists with basic messaging
- Shows surface name, description, action hint
- Lacks dependency, status, next action, and remediation/runbook links
- Documentation added to `DisabledSurfacePage.tsx` noting requirements

### Required Implementation
- Show dependency information (which service/dependency is missing or degraded)
- Show specific status (DEGRADED, UNAVAILABLE, MISCONFIGURED, DISABLED)
- Show next action (specific remediation steps)
- Show remediation/runbook link (documentation for enabling the surface)
- Add Playwright disabled-surface tests

### Implementation Plan

#### Phase 1: Runtime Truth Integration (Week 1)
1. **Extend Runtime Truth API response**
   ```typescript
   interface SurfaceStatus {
       id: string;
       name: string;
       status: 'LIVE' | 'DEGRADED' | 'DISABLED' | 'UNAVAILABLE' | 'MISCONFIGURED';
       maturity: 'active' | 'preview' | 'boundary' | 'deprecated';
       dependencies?: string[];
       missingDependencies?: string[];
       degradedDependencies?: string[];
       remediationUrl?: string;
       nextAction?: string;
   }
   ```

2. **Update surfaces.service.ts**
   - Fetch dependency information from Runtime Truth
   - Parse status and remediation data

#### Phase 2: Enhanced DisabledSurfacePage (Week 2)
1. **Add dependency display**
   ```tsx
   <div className="mt-4">
     <h3 className="text-sm font-medium">Dependencies</h3>
     <ul className="mt-2 space-y-1">
       {dependencies.map(dep => (
         <li key={dep} className={getStatusColor(dep)}>
           {dep.name}: {dep.status}
         </li>
       ))}
     </ul>
   </div>
   ```

2. **Add next action display**
   ```tsx
   <div className="mt-4 p-4 bg-amber-50 dark:bg-amber-900/20 rounded-lg">
     <h3 className="text-sm font-medium text-amber-900 dark:text-amber-100">
       Next Action
     </h3>
     <p className="mt-1 text-sm text-amber-700 dark:text-amber-300">
       {nextAction}
     </p>
   </div>
   ```

3. **Add runbook link**
   ```tsx
   <a
     href={remediationUrl}
     target="_blank"
     rel="noopener noreferrer"
     className="inline-flex items-center gap-2 mt-4 text-sm text-blue-600 hover:text-blue-700"
   >
     View Runbook
     <ExternalLink className="w-4 h-4" />
   </a>
   ```

#### Phase 3: Playwright Tests (Week 3)
1. **Add disabled surface tests**
   ```typescript
   test('should show dependency information for disabled surface', async ({ page }) => {
     // Navigate to disabled surface
     // Verify dependencies are shown
     // Verify status is displayed
   });
   
   test('should show next action and runbook link', async ({ page }) => {
     // Verify next action is displayed
     // Verify runbook link is clickable
   });
   ```

#### Phase 4: Documentation (Week 3)
1. **Update DisabledSurfacePage.tsx**
   - Remove DC-UI-002 note (completed)
   - Document new props and features

### Acceptance Criteria
- [ ] Disabled surface shows dependency information
- [ ] Disabled surface shows specific status (DEGRADED, UNAVAILABLE, etc.)
- [ ] Disabled surface shows next action
- [ ] Disabled surface shows remediation/runbook link
- [ ] Playwright tests verify disabled surface behavior

### Dependencies
- Runtime Truth API must provide dependency and remediation information
- Requires coordination with DC-BE-001 (RuntimeTruthStatus enum)

### Estimated Effort
- 2-3 weeks

---

## Task 8: DC-UI-003 - Remove Local Duplicate Buttons/Cards/Badges

### Current State
- `@data-cloud/ui-components` exists with Button, StatusBadge, LoadingState, BaseCard, KPICard
- Documentation added to `ui-components/README.md` noting requirements
- No comprehensive audit of duplicate components in UI

### Required Implementation
- Audit UI codebase for duplicate buttons, cards, badges
- Replace with `@data-cloud/ui-components` components
- Add import/lint scan to prevent future duplicates
- Ensure design system consistency

### Implementation Plan

#### Phase 1: Component Audit (Week 1)
1. **Scan for duplicate buttons**
   ```typescript
   // Search for inline button implementations
   // Search for button-like components not using @data-cloud/ui-components/common/Button
   ```

2. **Scan for duplicate cards**
   ```typescript
   // Search for inline card implementations
   // Search for card-like components not using @data-cloud/ui-components/cards/BaseCard
   ```

3. **Scan for duplicate badges**
   ```typescript
   // Search for inline badge implementations
   // Search for badge-like components not using @data-cloud/ui-components/common/StatusBadge
   ```

#### Phase 2: Migration (Week 2-3)
1. **Replace duplicates with ui-components**
   - Update imports to use `@data-cloud/ui-components`
   - Update component props to match ui-components API
   - Remove duplicate implementations

2. **Add lint rule**
   ```typescript
   // eslint-plugin-data-cloud
   {
     rules: {
       'no-duplicate-buttons': 'error',
       'no-duplicate-cards': 'error',
       'no-duplicate-badges': 'error'
     }
   }
   ```

#### Phase 3: Documentation (Week 3)
1. **Update ui-components/README.md**
   - Remove DC-UI-003 note (completed)
   - Document component inventory
   - Add migration guide

2. **Update ARCHITECTURE.md**
   - Document component usage guidelines

### Acceptance Criteria
- [ ] No duplicate button implementations
- [ ] No duplicate card implementations
- [ ] No duplicate badge implementations
- [ ] All UI components use `@data-cloud/ui-components`
- [ ] Lint rule prevents future duplicates

### Dependencies
- None (can be implemented independently)

### Estimated Effort
- 2-3 weeks

---

## Task 9: DC-UI-004 - Ensure UI Services Use Generated/Validated Clients

### Current State
- No openapi-typescript infrastructure exists (see DC-P1-006)
- Frontend uses ad hoc response types defined in API service files
- Documentation added to `ARCHITECTURE.md` noting requirements

### Required Implementation
- Replace ad hoc response types with generated/contract-validated types
- Ensure UI cannot compile against stale API types
- Add type/contract tests

### Implementation Plan

**This task should be coordinated with DC-P1-006 (frontend API type generation) as they share the same infrastructure.**

#### Phase 1: Tooling Setup (Week 1)
- Same as DC-P1-006 Phase 1

#### Phase 2: Build Integration (Week 1-2)
- Same as DC-P1-006 Phase 2

#### Phase 3: Migration (Week 2-3)
1. **Audit current API service types**
   - List all manual TypeScript interfaces in `src/api/`
   - Map to corresponding OpenAPI schemas

2. **Replace with generated types**
   - Update API service files to use generated types
   - Remove ad hoc interfaces
   - Update component props to use generated types

3. **Add type validation tests**
   - Same as DC-P1-006 Phase 3

#### Phase 4: Documentation (Week 3)
1. **Update ARCHITECTURE.md**
   - Remove DC-UI-004 note (completed)

### Acceptance Criteria
- Same as DC-P1-006

### Dependencies
- DC-P1-006 (should be implemented together)

### Estimated Effort
- 2-3 weeks (coordinated with DC-P1-006)

---

## Task 10: DC-SHARED-002 - Validate Direct Shared UI/Platform Dependencies

### Current State
- UI package.json has many workspace dependencies
- Documentation added to `package.json` noting requirements
- No comprehensive dependency audit

### Required Implementation
- Audit shared UI/platform dependencies
- Remove unused workspace dependencies
- Avoid library sprawl
- Add unused dependency scan

### Implementation Plan

#### Phase 1: Dependency Audit (Week 1)
1. **Analyze UI dependencies**
   - List all `@ghatana/*` workspace dependencies
   - Check usage in codebase (Grep for imports)
   - Identify unused dependencies

2. **Analyze platform dependencies**
   - Check `@data-cloud/ui` imports from platform modules
   - Identify circular dependencies

#### Phase 2: Cleanup (Week 1-2)
1. **Remove unused dependencies**
   ```bash
   # After audit, remove unused packages
   pnpm remove @ghatana/unused-package
   ```

2. **Consolidate overlapping dependencies**
   - Identify packages with overlapping functionality
   - Choose canonical package
   - Remove duplicates

#### Phase 3: Dependency Scan (Week 2)
1. **Add dependency scan tool**
   ```typescript
   // scripts/validate-dependencies.ts
   import { execSync } from 'node:child_process';
   
   const dependencies = Object.keys(packageJson.dependencies);
   const unused = dependencies.filter(dep => {
       // Check if dep is imported in source code
       return !isImported(dep);
   });
   
   if (unused.length > 0) {
       console.error('Unused dependencies:', unused);
       process.exit(1);
   }
   ```

2. **Add to CI**
   - Run dependency scan in CI
   - Fail PR if unused dependencies are detected

#### Phase 4: Documentation (Week 2)
1. **Update package.json**
   - Remove DC-SHARED-002 note (completed)

2. **Document dependency policy**
   - Add guidelines for adding new dependencies
   - Document dependency review process

### Acceptance Criteria
- [ ] No unused workspace dependencies
- [ ] No overlapping dependencies
- [ ] Dependency scan fails if unused dependencies are detected
- [ ] Dependency list is minimal and justified

### Dependencies
- None (can be implemented independently)

### Estimated Effort
- 1-2 weeks

---

## Task 11: DC-SHARED-003 - Move Data Cloud-Specific Utilities Out of Generic Shared Libraries

### Current State
- Platform has some data-cloud references (PlatformDataCloudSemanticBoundaryTest)
- Existing test enforces no product-specific semantics in platform modules
- Documentation added to `ARCHITECTURE.md` noting requirements

### Required Implementation
- Audit shared libraries for Data Cloud-specific semantics
- Move product-specific utilities to `products/data-cloud`
- Add dependency/API audit tests to verify shared libraries remain generic

### Implementation Plan

#### Phase 1: Audit (Week 1)
1. **Audit platform/java modules**
   - Scan for data-cloud-specific utilities
   - Check `@ghatana/platform/*` for product-specific code

2. **Audit platform/typescript modules**
   - Scan for data-cloud-specific utilities
   - Check `@ghatana/canvas`, `@ghatana/design-system`, etc.

#### Phase 2: Migration (Week 1-2)
1. **Move product-specific utilities**
   - Move to `products/data-cloud/libs/` or `products/data-cloud/delivery/`
   - Update imports
   - Update package.json dependencies

2. **Verify shared libraries are generic**
   - Ensure no product-specific semantics remain
   - Ensure shared libraries are reusable across unrelated products

#### Phase 3: Tests (Week 2)
1. **Add dependency/API audit tests**
   ```java
   @Test
   void platformModulesContainNoProductSemantics() {
       // Verify no data-cloud-specific code in platform modules
   }
   ```

2. **Run PlatformDataCloudSemanticBoundaryTest**
   - Verify test passes after migration

#### Phase 4: Documentation (Week 2)
1. **Update ARCHITECTURE.md**
   - Remove DC-SHARED-003 note (completed)

### Acceptance Criteria
- [ ] No Data Cloud-specific utilities in platform modules
- [ ] Product-specific utilities moved to `products/data-cloud`
- [ ] Shared libraries are reusable across unrelated products
- [ ] Dependency/API audit tests pass

### Dependencies
- DC-BND-003 (platform module dependency audit) - should be coordinated

### Estimated Effort
- 1-2 weeks

---

## Task 12: DC-SEC-001 - Add Full Tenant Isolation Test Matrix

### Current State
- Some tenant isolation tests exist (AnalyticsUiContractTest, CollectionsUiContractTest)
- No comprehensive tenant isolation test matrix
- Documentation added to `AdminApiUiContractTest.java` noting requirements

### Required Implementation
- Verify every route group rejects cross-tenant access
- Ensure no cross-tenant data leak
- Add tenant isolation tests for all route groups

### Implementation Plan

#### Phase 1: Route Group Inventory (Week 1)
1. **List all route groups**
   - Entity CRUD: `/api/v1/entities/*`
   - Event append: `/api/v1/events/*`
   - Pipelines: `/api/v1/pipelines/*`
   - Governance: `/api/v1/governance/*`
   - Analytics: `/api/v1/analytics/*`
   - Runtime Truth: `/api/v1/truth/*`
   - AI assist: `/api/v1/ai/*`
   - Webhooks: `/api/v1/webhooks/*`

#### Phase 2: Test Matrix Implementation (Week 2-3)
1. **Create tenant isolation test base class**
   ```java
   public abstract class TenantIsolationTestBase extends EventloopTestBase {
       protected void assertCrossTenantAccessRejected(String endpoint, String tenant1, String tenant2) {
           // Test that tenant1 cannot access tenant2's data
       }
       
       protected void assertTenantDataIsolation(String endpoint, String tenant1, String tenant2) {
           // Test that tenant1 and tenant2 data are isolated
       }
   }
   ```

2. **Implement test matrix**
   - `EntityCrudTenantIsolationTest.java`
   - `EventAppendTenantIsolationTest.java`
   - `PipelineTenantIsolationTest.java`
   - `GovernanceTenantIsolationTest.java`
   - `AnalyticsTenantIsolationTest.java` (enhance existing)
   - `RuntimeTruthTenantIsolationTest.java`
   - `AiAssistTenantIsolationTest.java`
   - `WebhookTenantIsolationTest.java`

#### Phase 3: Cross-Tenant Leak Tests (Week 3)
1. **Add cross-tenant data leak tests**
   ```java
   @Test
   void shouldPreventCrossTenantEntityRead() {
       // Create entity in tenant1
       // Try to read from tenant2
       // Assert 403 or 404
   }
   
   @Test
   void shouldPreventCrossTenantEntityWrite() {
       // Try to write to tenant2 from tenant1
       // Assert 403
   }
   ```

#### Phase 4: Documentation (Week 3)
1. **Update AdminApiUiContractTest.java**
   - Remove DC-SEC-001 note (completed)

2. **Document test matrix**
   - Add tenant isolation test documentation
   - Document test coverage

### Acceptance Criteria
- [ ] Every route group has tenant isolation tests
- [ ] Cross-tenant access is rejected
- [ ] No cross-tenant data leak
- [ ] Test matrix covers all route groups

### Dependencies
- DC-P0-007 (enforce tenant isolation for every API group) - already implemented

### Estimated Effort
- 2-3 weeks

---

## Implementation Timeline

### Sequential Order (Recommended)

1. **Week 1-2:** DC-P1-006 + DC-UI-004 (coordinated frontend type generation)
2. **Week 1-2:** DC-BND-003 + DC-SHARED-003 (coordinated platform audits)
3. **Week 2-3:** DC-SHARED-002 (dependency cleanup)
4. **Week 2-3:** DC-P1-011 (AI heuristic fallback)
5. **Week 2-3:** DC-SEC-001 (tenant isolation test matrix)
6. **Week 3-4:** DC-UI-003 (component deduplication)
7. **Week 3-4:** DC-UI-002 (disabled UI enhancement)
8. **Week 3-6:** DC-BE-002 (idempotency)
9. **Week 3-6:** DC-BE-003 (transaction boundaries)
10. **Week 3-6:** DC-BE-004 (deletion lifecycle)

**Total:** 8-12 weeks

### Parallelization Opportunities

- DC-P1-006 + DC-UI-004: Same infrastructure, implement together
- DC-BND-003 + DC-SHARED-003: Platform audits, implement together
- DC-BE-002 + DC-BE-003 + DC-BE-004: Backend data integrity, can be parallelized with careful coordination
- DC-UI-002 + DC-UI-003: UI enhancements, can be parallelized

**Parallelized Timeline:** 6-8 weeks with 2-3 developers

---

## Risk Assessment

### High Risk
- **DC-BE-003 (Transaction Boundaries):** Significant architectural change, requires careful testing
- **DC-BE-004 (Deletion Lifecycle):** Data loss risk if not implemented correctly
- **DC-BE-002 (Idempotency):** Requires coordination with transaction boundaries

### Medium Risk
- **DC-P1-006 + DC-UI-004 (Type Generation):** Build infrastructure changes, affects entire frontend
- **DC-BND-003 + DC-SHARED-003 (Platform Audits):** May require significant refactoring if violations found

### Low Risk
- **DC-P1-011 (AI Fallback):** Feature enhancement, low risk to existing functionality
- **DC-SEC-001 (Tenant Isolation Tests):** Test-only task, no production code changes
- **DC-UI-002 (Disabled UI):** UI enhancement, low risk
- **DC-UI-003 (Component Deduplication):** Refactoring task, low risk
- **DC-SHARED-002 (Dependency Cleanup):** Dependency removal, low risk

---

## Success Metrics

### Quantitative Metrics
- **Type Generation:** 100% of API services use generated types
- **AI Fallback:** 0% AI failures block core workflows
- **Platform Audits:** 0% product-specific semantics in platform modules
- **Idempotency:** 100% of mutating routes support idempotency
- **Transactions:** 100% of multi-step writes are atomic
- **Deletion Lifecycle:** 100% of deletes follow lifecycle policy
- **Disabled UI:** 100% of unavailable surfaces show actionable disabled UI
- **Component Deduplication:** 0% duplicate buttons/cards/badges
- **Dependency Cleanup:** 0% unused workspace dependencies
- **Tenant Isolation:** 100% of route groups have tenant isolation tests

### Qualitative Metrics
- Frontend type safety improved
- AI resilience improved
- Platform reusability improved
- Data integrity improved
- User experience improved for unavailable features
- Code maintainability improved
- Test coverage improved

---

## Next Steps

1. **Review this plan** with engineering team and stakeholders
2. **Prioritize tasks** based on business impact and risk
3. **Assign resources** and create sprint backlog
4. **Begin implementation** following the recommended sequential order
5. **Track progress** using this plan as a reference
6. **Update plan** as needed based on implementation learnings

---

## Appendix: Related Documentation

- Data Cloud TODO List: `docs/audit/data_cloud_todo_list_20260509_160436.md`
- Coding Instructions: `copilot-instructions.md`
- Platform Architecture: `platform/java/core/src/test/java/com/ghatana/platform/architecture/PlatformDataCloudSemanticBoundaryTest.java`
- UI Architecture: `products/data-cloud/delivery/ui/ARCHITECTURE.md`
- OpenAPI Specs: `products/data-cloud/contracts/openapi/`

---

**Document Version:** 1.0
**Last Updated:** 2026-05-09
**Maintained By:** Data Cloud Engineering Team
