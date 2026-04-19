# Ghatana Platform: Consolidated Implementation Plan
**Date:** 2026-04-18  
**Based on:** Platform Reality Audit + Java Reuse Consolidation Audit + TypeScript/JS Reuse Consolidation Plan  
**Scope:** All 14 products, 30+ platform modules, 5 shared services, integration layers  
**Objective:** Coherent, non-redundant task-by-task execution plan addressing production blockers, code consolidation, and platform health

---

## Executive Summary

This plan consolidates findings from three independent audits into a single prioritized execution roadmap:

1. **Platform Reality Audit** — Production readiness assessment of all 14 products, identifying P0/P1/P2 blockers
2. **Java Reuse Consolidation Audit** — 12 major Java code duplication clusters across platform, kernel, products
3. **TypeScript/JS Reuse Consolidation Plan** — 13 major TS/JS duplication clusters across products and platform packages

**Key Insight:** The highest-value work combines production unblockers with code consolidation — fixing broken execution paths while simultaneously eliminating duplicate implementations.

---

## Phase 1: P0 Production Blockers (Weeks 1-2)

**Goal:** Unblock core user-facing execution paths that are currently broken or misleading.

### Task 1.1: Wire Real Credential Verification in Security-Gateway
**Priority:** P0 | **Product:** Security-Gateway | **Effort:** 1 day  
**Audit Source:** Platform Reality Audit §7.12, §15

**Problem:** `AuthHttpHandler.handleLogin()` accepts any non-empty email/password and issues a JWT without calling `AuthenticationServiceImpl.authenticate()`.

**Action:**
1. Modify `AuthHttpHandler.handleLogin()` to call `authenticationService.authenticate(tenantId, email, password)`
2. Use returned `AuthResult` to issue tokens only on successful authentication
3. Add unit test for credential verification path
4. Add integration test that verifies invalid credentials are rejected

**Files:**
- `products/security-gateway/platform/java/src/main/java/com/ghatana/auth/http/AuthHttpHandler.java`

**Verification:**
- Unit test: `AuthHttpHandlerTest.testHandleLoginRejectsInvalidCredentials()`
- Integration test: `SecurityGatewayIntegrationTest.testLoginEndpoint()`

---

### Task 1.2: Implement Real CI/CD Adapter for YAPPC
**Priority:** P0 | **Product:** YAPPC | **Effort:** 1 sprint  
**Audit Source:** Platform Reality Audit §7.4, §15

**Problem:** `NoOpCiCdAdapter` is always wired; all CI/CD methods return `NOT_READY`. No `CiCdPort` implementation exists.

**Action:**
1. Implement `GitHubActionsCiCdAdapter` or `ArgoCDCiCdAdapter` as real `CiCdPort` implementation
2. Add production startup guard that fails fast if `ENABLE_TASK_EXECUTION=true` but no real adapter is configured
3. Implement `RunRepository` backed by Data-Cloud to persist run results
4. Add IAM/RBAC middleware on task execution endpoints
5. Update `RunServiceImpl` to use real adapter instead of `NoOpCiCdAdapter`

**Files:**
- `products/yappc/core/cicd/src/main/java/com/ghatana/yappc/cicd/adapter/GitHubActionsCiCdAdapter.java` (new)
- `products/yappc/core/runtime/src/main/java/com/ghatana/yappc/runtime/RunServiceImpl.java`
- `products/yappc/core/runtime/src/main/java/com/ghatana/yappc/runtime/repository/RunRepository.java` (new)
- `products/yappc/core/runtime/src/main/java/com/ghatana/yappc/runtime/launcher/YapcLauncher.java`

**Verification:**
- Integration test: `CiCdAdapterIntegrationTest.testRealBuildExecution()`
- End-to-end test: `YapcE2ETest.testTaskExecutionFlow()`

---

### Task 1.3: Fix Audio-Video STT or Declare LLM-Fallback as Supported Mode
**Priority:** P0 | **Product:** Audio-Video | **Effort:** 1 day  
**Audit Source:** Platform Reality Audit §7.2, §15

**Problem:** `WhisperTranscriptionEngine.decode()` returns deterministic fake string; `transcribeViaGrpc()` has `// TODO` and is never reached.

**Action (Option A - Fix):**
1. Implement real Whisper ONNX/JNI binding in `WhisperTranscriptionEngine`
2. Wire gRPC stubs so `transcribeViaGrpc()` is reachable
3. Add auth layer on STT gRPC service
4. Add tenant isolation in multimodal service

**Action (Option B - Declare):**
1. Remove `WhisperTranscriptionEngine` fake implementation
2. Document LLM_FALLBACK as the only supported STT mode
3. Update all documentation to reflect this limitation
4. Add feature flag to prevent GRPC mode from being selected

**Files:**
- `products/audio-video/stt/src/main/java/com/ghatana/audio/stt/WhisperTranscriptionEngine.java`
- `products/audio-video/stt/src/main/java/com/ghatana/audio/stt/adapter/GrpcSttClientAdapter.java`

**Verification:**
- If Option A: Unit test `WhisperTranscriptionEngineTest.testRealTranscription()`
- If Option B: Documentation review + feature flag test

---

### Task 1.4: Implement Real Finance Business Rules
**Priority:** P0 | **Product:** Finance | **Effort:** 1 sprint  
**Audit Source:** Platform Reality Audit §7.8, §15

**Problem:** `validateTradeRules()`, `checkComplianceRules()`, `calculateRiskLevel()`, `calculateRiskScore()` all return hardcoded placeholder values. No OPA `.rego` policy files exist.

**Action:**
1. Implement real business rule logic in `FinanceRulesService`
2. Create OPA `.rego` policy files for trading compliance, AML rules, risk rules, reporting rules
3. Add OPA policy loading and validation at startup
4. Remove `// Placeholder for demo` comments
5. Add contract tests for rule evaluation

**Files:**
- `products/finance/rules-engine/src/main/java/com/ghatana/finance/rules/FinanceRulesService.java`
- `products/finance/rules/src/main/resources/opa/trading-compliance.rego` (new)
- `products/finance/rules/src/main/resources/opa/aml-rules.rego` (new)
- `products/finance/rules/src/main/resources/opa/risk-rules.rego` (new)
- `products/finance/rules/src/main/resources/opa/reporting-rules.rego` (new)

**Verification:**
- Contract test: `FinanceRulesContractTest.testRuleEvaluation()`
- Integration test: `FinanceOpaIntegrationTest.testOpaPolicyEvaluation()`

---

### Task 1.5: Implement AEP Agent Registration Dialog
**Priority:** P0 | **Product:** AEP | **Effort:** 3 days  
**Audit Source:** Platform Reality Audit §7.1, §15

**Problem:** Agent registration dialog button is dead with `TODO: Open agent registration dialog` comment.

**Action:**
1. Implement agent registration modal dialog
2. Wire to `POST /api/v1/agents` endpoint
3. Add form validation for agent configuration
4. Add success/error feedback
5. Remove TODO comment

**Files:**
- `products/aep/web/src/components/agents/AgentRegistrationDialog.tsx` (new)
- `products/aep/web/src/components/agents/AgentRegistryPage.tsx`

**Verification:**
- E2E test: `AgentRegistrationE2ETest.testRegisterNewAgent()`

---

## Phase 2: P1 Code Consolidation — Exact Duplicates (Weeks 2-4)

**Goal:** Eliminate exact duplicate code with minimal semantic risk.

### Task 2.1: Remove Duplicate ActiveJ Test Support
**Priority:** P1 | **Domain:** Java Platform | **Effort:** 2 days  
**Audit Source:** Java Reuse Consolidation Audit §B.1

**Problem:** `EventloopTestBase` and `EventloopTestUtil` are exact duplicates between `platform/java/testing` and `platform-kernel/kernel-core`.

**Action:**
1. Replace all kernel-core test imports with platform-testing imports
2. Delete duplicate kernel test copies
3. Add boundary test preventing duplicate test base classes
4. Run kernel and platform-testing test suites

**Files:**
- Delete: `platform-kernel/kernel-core/src/test/java/com/ghatana/platform/testing/activej/EventloopTestBase.java`
- Delete: `platform-kernel/kernel-core/src/test/java/com/ghatana/platform/testing/activej/EventloopTestUtil.java`
- Update imports in all kernel test files

**Verification:**
- All kernel tests pass after import migration
- Boundary test: `NoDuplicateTestBaseTest.java`

---

### Task 2.2: Collapse AEP State-Store Duplicates
**Priority:** P1 | **Domain:** Java Platform | **Effort:** 3 days  
**Audit Source:** Java Reuse Consolidation Audit §B.2

**Problem:** `HybridStateStore` and `SyncStrategy` duplicated in AEP under `com.ghatana.core.state` and `statestore.hybrid`.

**Action:**
1. Replace AEP `com.ghatana.core.state` imports with platform core
2. Refactor AEP `statestore.hybrid` to extend or wrap platform abstraction
3. Delete AEP exact duplicate files
4. Add boundary test preventing state-store duplication

**Files:**
- Delete: `products/aep/aep-engine/src/main/java/com/ghatana/core/state/HybridStateStore.java`
- Delete: `products/aep/aep-engine/src/main/java/com/ghatana/core/state/SyncStrategy.java`
- Update: `products/aep/aep-engine/src/main/java/com/ghatana/statestore/hybrid/HybridStateStore.java` (extend platform)

**Verification:**
- AEP engine tests pass
- State-store behavior unchanged (contract test)

---

### Task 2.3: Collapse YAPPC JsonUtils
**Priority:** P1 | **Domain:** Java Product | **Effort:** 1 day  
**Audit Source:** Java Reuse Consolidation Audit §B.10

**Problem:** YAPPC local `JsonUtils` is a subset duplicate of platform `JsonUtils`.

**Action:**
1. Replace YAPPC imports with platform `JsonUtils`
2. Confirm YAPPC code does not rely on materially different mapper contract
3. Delete local utility
4. Add boundary test

**Files:**
- Delete: `products/yappc/core/refactorer/engine/src/main/java/com/ghatana/refactorer/languages/tsjs/util/JsonUtils.java`
- Update imports in refactorer engine

**Verification:**
- YAPPC refactorer tests pass
- JSON serialization behavior unchanged

---

### Task 2.4: Merge PHR Response Envelopes
**Priority:** P1 | **Domain:** Java Product | **Effort:** 1 day  
**Audit Source:** Java Reuse Consolidation Audit §B.6

**Problem:** `FhirApiResponse` and `NepalHieApiResponse` are exact duplicates.

**Action:**
1. Merge into one PHR-local response type `PhrApiResponse`
2. Update all PHR controller imports
3. Delete duplicate files
4. Add boundary test

**Files:**
- New: `products/phr/src/main/java/com/ghatana/phr/api/PhrApiResponse.java`
- Delete: `products/phr/src/main/java/com/ghatana/phr/api/FhirApiResponse.java`
- Delete: `products/phr/src/main/java/com/ghatana/phr/api/NepalHieApiResponse.java`
- Update: All PHR controller files

**Verification:**
- PHR API tests pass
- Response contracts unchanged (JSON shape test)

---

### Task 2.5: Migrate AEP HttpHelper to Platform HTTP
**Priority:** P1 | **Domain:** Java Platform | **Effort:** 2 days  
**Audit Source:** Java Reuse Consolidation Audit §B.6

**Problem:** AEP `HttpHelper` duplicates platform HTTP response building logic.

**Action:**
1. Refactor AEP `HttpHelper` to delegate to platform `ResponseBuilder` and `ErrorResponse`
2. Delete local helper methods that are now redundant
3. Update all AEP controller usages
4. Add boundary test

**Files:**
- Update: `products/aep/server/src/main/java/com/ghatana/aep/server/http/HttpHelper.java`
- Update: All AEP controller files using HttpHelper

**Verification:**
- AEP HTTP server tests pass
- Response format unchanged

---

### Task 2.6: Collapse YAPPC Plugin API Duplication
**Priority:** P1 | **Domain:** Java Product | **Effort:** 2 days  
**Audit Source:** Java Reuse Consolidation Audit §B.7

**Problem:** `PluginRegistry`, `PluginLoader`, `PluginContext` duplicated between yappc-shared and scaffold.

**Action:**
1. Move scaffold generators remaining live consumers onto yappc-shared plugin types
2. Delete scaffold-local duplicates
3. Add boundary test preventing new plugin API types under scaffold packages

**Files:**
- Delete: `products/yappc/core/scaffold/generators/src/main/java/com/ghatana/yappc/core/plugin/PluginRegistry.java`
- Delete: `products/yappc/core/scaffold/generators/src/main/java/com/ghatana/yappc/core/plugin/PluginLoader.java`
- Delete: `products/yappc/core/scaffold/generators/src/main/java/com/ghatana/yappc/core/plugin/PluginContext.java`
- Update imports in scaffold generators

**Verification:**
- YAPPC scaffold tests pass
- Plugin API behavior unchanged

---

### Task 2.7: Delete YAPPC Notification Duplicate
**Priority:** P1 | **Domain:** TypeScript Product | **Effort:** 1 day  
**Audit Source:** TypeScript/JS Reuse Consolidation Plan §B.4

**Problem:** Notification UI duplicated under `yappc-ai/src/messaging/notifications` and `yappc-ai/src/notifications`.

**Action:**
1. Redirect all imports from `messaging/notifications` to `notifications`
2. Delete duplicate directory
3. Update barrel exports
4. Run Storybook and component tests

**Files:**
- Delete: `products/yappc/frontend/libs/yappc-ai/src/messaging/notifications/`
- Update: `products/yappc/frontend/libs/yappc-ai/src/messaging/index.ts`
- Update: All imports referencing messaging/notifications

**Verification:**
- YAPPC frontend tests pass
- Storybook renders notification components

---

### Task 2.8: Delete YAPPC AEP Config Duplicate
**Priority:** P1 | **Domain:** TypeScript Product | **Effort:** 1 day  
**Audit Source:** TypeScript/JS Reuse Consolidation Plan §B.5

**Problem:** AEP client/config factory exists in both `yappc-ai/src/aep-config` and `libs/aep-config`.

**Action:**
1. Redirect all imports from `yappc-ai/src/aep-config` to `libs/aep-config`
2. Delete duplicate directory
3. Update barrel exports
4. Run tests

**Files:**
- Delete: `products/yappc/frontend/libs/yappc-ai/src/aep-config/`
- Update: All imports referencing yappc-ai/src/aep-config

**Verification:**
- YAPPC frontend tests pass
- AEP client configuration works

---

### Task 2.9: Delete YAPPC Shortcuts Duplicate
**Priority:** P1 | **Domain:** TypeScript Product | **Effort:** 1 day  
**Audit Source:** TypeScript/JS Reuse Consolidation Plan §B.6

**Problem:** Keyboard shortcut system duplicated in `yappc-ui/src/components/shortcuts` and `libs/shortcuts`.

**Action:**
1. Redirect all imports from `yappc-ui/src/components/shortcuts` to `libs/shortcuts`
2. Delete duplicate directory
3. Update barrel exports
4. Run tests

**Files:**
- Delete: `products/yappc/frontend/libs/yappc-ui/src/components/shortcuts/`
- Update: All imports referencing yappc-ui/src/components/shortcuts

**Verification:**
- YAPPC frontend tests pass
- Keyboard shortcuts work

---

### Task 2.10: Finish YAPPC CRDT Migration
**Priority:** P1 | **Domain:** TypeScript Product | **Effort:** 2 days  
**Audit Source:** TypeScript/JS Reuse Consolidation Plan §B.8

**Problem:** CRDT code lives under both `yappc-state/src/store/crdt` and `collab/src/crdt`. Migration partially done.

**Action:**
1. Redirect all remaining imports from `@yappc/state` CRDT paths to `@yappc/collab/crdt`
2. Delete `yappc-state/src/store/crdt/` ownership
3. Update internal state package references
4. Run tests

**Files:**
- Delete: `products/yappc/frontend/libs/yappc-state/src/store/crdt/`
- Update: All imports from @yappc/state CRDT paths

**Verification:**
- YAPPC canvas/IDE tests pass
- CRDT behavior unchanged

---

## Phase 3: P2 Platform Health & Medium-Risk Consolidations (Weeks 4-6)

**Goal:** Improve platform health through medium-risk consolidations and structural improvements.

### Task 3.1: Unify Platform ValidationResult
**Priority:** P2 | **Domain:** Java Platform | **Effort:** 3 days  
**Audit Source:** Java Reuse Consolidation Audit §B.5

**Problem:** Two platform-level `ValidationResult` types exist in different packages.

**Action:**
1. Choose canonical type: `platform.core.validation.ValidationResult` (violation-oriented with legacy bridge)
2. Add conversion helpers during migration
3. Update kernel compatibility wrapper
4. Migrate platform modules first, then product modules
5. Delete old type after migration
6. Add boundary test

**Files:**
- Keep: `platform/java/core/src/main/java/com/ghatana/platform/core/validation/ValidationResult.java`
- Delete: `platform/java/core/src/main/java/com/ghatana/platform/validation/ValidationResult.java`
- Update: All imports across platform and products

**Verification:**
- All platform tests pass
- JSON contract tests for validation responses

---

### Task 3.2: Extract Canonical TenantContext
**Priority:** P2 | **Domain:** Java Platform | **Effort:** 3 days  
**Audit Source:** Java Reuse Consolidation Audit §B.3

**Problem:** Multiple `TenantContext` types — exact duplicates between platform-domain and Data Cloud SPI; conceptual duplicates elsewhere.

**Action:**
1. Extract canonical immutable tenant-scope record with `tenantId`, optional `workspaceId`, metadata
2. Move both exact-record callers to it
3. Rename governance `TenantContext` to `TenantContextHolder` to clarify holder-style intent
4. Keep YAPPC auth record local (adds subject, roles, claims)
5. Add boundary test

**Files:**
- New canonical: `platform/java/domain/src/main/java/com/ghatana/platform/domain/TenantScope.java`
- Rename: `platform/java/governance/src/main/java/com/ghatana/platform/governance/security/TenantContextHolder.java`
- Update: Data Cloud SPI and event-store contracts
- Keep local: YAPPC auth TenantContext

**Verification:**
- Platform tests pass
- Tenant isolation still enforced

---

### Task 3.3: Unify SecurityContext
**Priority:** P2 | **Domain:** Java Platform | **Effort:** 4 days  
**Audit Source:** Java Reuse Consolidation Audit §B.4

**Problem:** `SecurityContext` duplicated across platform security, security-gateway, kernel, AEP registry.

**Action:**
1. Enrich platform security `SecurityContext` to cover stable superset needed by security-gateway
2. Migrate security-gateway code to it
3. Move holder logic into platform security if multiple modules need it
4. Leave kernel `SecurityContext` separate until compatibility mapping defined
5. Add boundary test

**Files:**
- Enhance: `platform/java/security/src/main/java/com/ghatana/platform/security/SecurityContext.java`
- Update: `products/security-gateway/platform/java/src/main/java/com/ghatana/auth/security/SecurityContext.java`
- Keep separate: kernel and AEP registry versions for now

**Verification:**
- Security-gateway tests pass
- Auth flow unchanged

---

### Task 3.4: Extract Repository Support to Platform Database
**Priority:** P2 | **Domain:** Java Platform | **Effort:** 3 days  
**Audit Source:** Java Reuse Consolidation Audit §B.9

**Problem:** Manual `EntityManager` transaction wrapper duplicated in AEP orchestrator.

**Action:**
1. Extract generic `execute` and transaction pattern into platform database
2. Split into CRUD-oriented `JpaRepository` and factory-backed `TransactionalRepositorySupport`
3. Make AEP orchestrator repositories extend or delegate to support class
4. Keep AEP-specific query methods local
5. Add boundary test

**Files:**
- New: `platform/java/database/src/main/java/com/ghatana/core/database/repository/TransactionalRepositorySupport.java`
- Update: `products/aep/orchestrator/src/main/java/com/ghatana/orchestrator/store/AbstractRepository.java`

**Verification:**
- AEP orchestrator tests pass
- Transaction behavior unchanged

---

### Task 3.5: Create DCMAAR Mobile Shared Library
**Priority:** P2 | **Domain:** TypeScript Product | **Effort:** 1 sprint  
**Audit Source:** TypeScript/JS Reuse Consolidation Plan §B.1

**Problem:** Parent and child mobile apps have identical services, hooks, fixtures, and test setup.

**Action:**
1. Create `products/dcmaar/libs/typescript/mobile-shared` package
2. Move duplicated services: `api.ts`, `storage.ts`, `notifications.ts`
3. Move duplicated hooks: `useApi.ts`
4. Move fixtures, mocks, navigation setup, test helpers
5. Parameterize persona-specific labels and app navigation composition
6. Update both mobile apps to import from shared library
7. Update jest config and metro resolution
8. Run mobile app tests

**Files:**
- New: `products/dcmaar/libs/typescript/mobile-shared/src/services/api.ts`
- New: `products/dcmaar/libs/typescript/mobile-shared/src/services/storage.ts`
- New: `products/dcmaar/libs/typescript/mobile-shared/src/services/notifications.ts`
- New: `products/dcmaar/libs/typescript/mobile-shared/src/hooks/useApi.ts`
- New: `products/dcmaar/libs/typescript/mobile-shared/src/fixtures/`
- Update: Both mobile apps to import from shared library

**Verification:**
- Both mobile app tests pass
- No regression in app behavior

---

### Task 3.6: Migrate DCMAAR Device-Health to Product Libraries
**Priority:** P2 | **Domain:** TypeScript Product | **Effort:** 1 sprint  
**Audit Source:** TypeScript/JS Reuse Consolidation Plan §B.2

**Problem:** Device-health app reimplements monitors, pipelines, and browser-extension infrastructure already in product libraries.

**Action:**
1. Replace app-local monitors with imports from `plugin-extension/src/CPUMonitor.ts`, etc.
2. Replace app-local pipeline with import from `browser-extension-core/src/pipeline/EventPipeline.ts`
3. Replace app-local controller with composition over `BaseExtensionController`
4. Replace app-local connectors with imports from `connectors/src/*`
5. Delete app-local copies
6. Update tests

**Files:**
- Delete: `products/dcmaar/apps/device-health/src/plugins/monitors/`
- Delete: `products/dcmaar/apps/device-health/src/app/background/pipeline/EventPipeline.ts`
- Delete: `products/dcmaar/apps/device-health/src/browser/controller/ExtensionController.ts`
- Update: Device-health app imports to use product libraries

**Verification:**
- Device-health app tests pass
- Extension runtime behavior unchanged

---

### Task 3.7: Resolve DCMAAR Parent-Dashboard Stale Forks
**Priority:** P2 | **Domain:** TypeScript Product | **Effort:** 3 days  
**Audit Source:** TypeScript/JS Reuse Consolidation Plan §B.3

**Problem:** Parent dashboard maintains legacy and `*New` component pairs in parallel.

**Action:**
1. For each feature pair (DeviceManagement, PolicyManagement, Analytics, Reports, UsageMonitor, BlockNotifications, Dashboard), choose the design-system-based `New` implementation
2. Update dashboard routes to use chosen implementation
3. Update tests under `src/test`
4. Update Storybook stories
5. Delete legacy versions
6. Add lint rule preventing `*New.tsx` naming pattern

**Files:**
- Delete: One side of each pair (choose legacy to delete)
- Update: Dashboard routes and tests
- Add: ESLint rule preventing `*New.tsx` pattern

**Verification:**
- Parent-dashboard tests pass
- Storybook renders correctly
- No broken routes

---

### Task 3.8: Consolidate Software Org Web Feedback Components
**Priority:** P2 | **Domain:** TypeScript Product | **Effort:** 2 days  
**Audit Source:** TypeScript/JS Reuse Consolidation Plan §B.10

**Problem:** Multiple local notification centers and loading state components with same intent.

**Action:**
1. Create app-shared `src/shared/ui/feedback` module
2. Consolidate `NotificationCenter` implementations into one
3. Consolidate loading states: `LoadingState`, `LoadingStates`, `PageStates` into one `PageState` component with variants
4. Move `ErrorBoundary` to shared
5. Update all feature imports
6. Delete duplicate implementations

**Files:**
- New: `products/software-org/client/web/src/shared/ui/feedback/NotificationCenter.tsx`
- New: `products/software-org/client/web/src/shared/ui/feedback/PageState.tsx`
- New: `products/software-org/client/web/src/shared/ui/feedback/ErrorBoundary.tsx`
- Delete: Duplicates under features and components

**Verification:**
- Software Org Web tests pass
- Visual consistency across pages

---

### Task 3.9: Consolidate Software Org Web Data Layer
**Priority:** P2 | **Domain:** TypeScript Product | **Effort:** 3 days  
**Audit Source:** TypeScript/JS Reuse Consolidation Plan §B.11

**Problem:** Overlapping hooks and API client wrappers across `services/api/*`, `hooks/use*Api.ts`, `lib/api/*`.

**Action:**
1. Create app-shared `src/shared/data` module
2. Keep one transport client
3. Create one domain client per bounded area
4. Create one query hook per domain resource built on that client
5. Update all page-level imports
6. Delete duplicate implementations

**Files:**
- New: `products/software-org/client/web/src/shared/data/transport/client.ts`
- New: Domain clients and query hooks per bounded area
- Delete: Duplicate implementations

**Verification:**
- Software Org Web tests pass
- Request lifecycle consistent

---

### Task 3.10: Unify Platform DataGrid
**Priority:** P2 | **Domain:** TypeScript Platform | **Effort:** 3 days  
**Audit Source:** TypeScript/JS Reuse Consolidation Plan §B.9

**Problem:** `DataGrid` exists in both `platform/typescript/data-grid` and `platform/typescript/design-system`.

**Action:**
1. Keep low-level implementation in `platform/typescript/data-grid`
2. Make `@ghatana/design-system` re-export or wrap it, not reimplement
3. Update design-system internals
4. Update DCMAAR parent-dashboard new components
5. Delete duplicate implementation
6. Add boundary test

**Files:**
- Keep: `platform/typescript/data-grid/src/DataGrid.tsx`
- Update: `platform/typescript/design-system/src/organisms/DataGrid.tsx` (re-export or wrap)
- Delete: Duplicate implementation if any
- Update: All direct `@ghatana/data-grid` consumers

**Verification:**
- Design system tests pass
- Grid behavior unchanged

---

## Phase 4: P3 Structural Improvements & Higher-Risk Consolidations (Weeks 6-8)

**Goal:** Address higher-risk consolidations and structural platform improvements.

### Task 4.1: Consolidate SecretProvider SPI
**Priority:** P3 | **Domain:** Java Platform | **Effort:** 1 sprint  
**Audit Source:** Java Reuse Consolidation Audit §B.8

**Problem:** `SecretProvider` SPI repeated across kernel, agent-core, and Data Cloud event SPI.

**Action:**
1. Compare three interfaces and extract minimal common operations
2. Create canonical SPI in `platform:java:security` or `platform:contracts`
3. Adapt kernel and Data Cloud implementations
4. Keep module-specific extensions local
5. Add boundary test

**Files:**
- New canonical: `platform/java/security/src/main/java/com/ghatana/platform/security/SecretProvider.java`
- Update: Kernel and Data Cloud implementations to adapt
- Delete: Duplicate interfaces

**Verification:**
- All module tests pass
- Secret access behavior unchanged

---

### Task 4.2: Converge Kernel Data Cloud Adapter DTOs
**Priority:** P3 | **Domain:** Java Platform | **Effort:** 1 sprint  
**Audit Source:** Java Reuse Consolidation Audit §B.11

**Problem:** Kernel-local Data Cloud transport DTOs parallel Data Cloud SPI types.

**Action:**
1. Identify exact overlapping operations
2. Extract or reuse Data Cloud-owned contracts
3. Update `DataCloudKernelAdapter` to consume them
4. Delete kernel-local DTOs
5. Add boundary test

**Files:**
- Delete: `platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/adapter/datacloud/DataReadRequest.java`
- Delete: `platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/adapter/datacloud/DataWriteRequest.java`
- Delete: `platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/adapter/datacloud/SchemaCreateRequest.java`
- Delete: `platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/adapter/datacloud/QueryResult.java`
- Update: `DataCloudKernelAdapter` to use Data Cloud-owned contracts

**Verification:**
- Kernel tests pass
- Integration tests pass
- Data Cloud adapter behavior unchanged

---

### Task 4.3: Consolidate Event Publishing Transport
**Priority:** P3 | **Domain:** Java Platform | **Effort:** 1 sprint  
**Audit Source:** Java Reuse Consolidation Audit §B.12

**Problem:** Event publishing transport logic reimplemented per product instead of anchored to platform messaging.

**Action:**
1. Add generic async event sink/publisher support to platform messaging
2. Migrate AEP and Data Cloud implementations to it
3. Keep `DomainEventPublisher` product-local as domain port
4. Add boundary test

**Files:**
- New: `platform/java/messaging/src/main/java/com/ghatana/platform/messaging/EventPublisher.java`
- Update: AEP and Data Cloud implementations to delegate
- Keep: Product-local domain event types

**Verification:**
- AEP and Data-Cloud tests pass
- Event publishing behavior unchanged

---

### Task 4.4: Migrate Flashit to Shared Library
**Priority:** P3 | **Domain:** TypeScript Product | **Effort:** 1 sprint  
**Audit Source:** TypeScript/JS Reuse Consolidation Plan §B.12

**Problem:** Flashit clients still duplicate product types, API access, and helpers despite existing shared package.

**Action:**
1. Move web client API client to `libs/ts/shared/src/api/client.ts`
2. Move shared domain types to `libs/ts/shared/src/types/`
3. Move validation schemas to `libs/ts/shared/src/validation/`
4. Update both web and mobile app data layer imports
5. Keep platform-specific media capture and upload primitives local
6. Add boundary test

**Files:**
- Update: `products/flashit/libs/ts/shared/src/api/client.ts`
- Update: `products/flashit/libs/ts/shared/src/types/`
- Update: `products/flashit/libs/ts/shared/src/validation/`
- Delete: Client-local duplicates
- Keep: Camera, recorder, haptics, mobile-native services local

**Verification:**
- Flashit web and mobile tests pass
- Backend compatibility updates easier

---

### Task 4.5: Consolidate YAPPC Theme/Token Trees
**Priority:** P3 | **Domain:** TypeScript Product | **Effort:** 1 sprint  
**Audit Source:** TypeScript/JS Reuse Consolidation Plan §B.7

**Problem:** YAPPC UI token trees overlap platform tokens and contain internal duplication.

**Action:**
1. Move global tokens to `platform/typescript/tokens/src/*`
2. Keep product-specific presets in `products/yappc/frontend/libs/yappc-product-theme/src/*`
3. Delete duplicate theme/token subtrees
4. Update all imports
5. Run Storybook and snapshot tests
6. Add boundary test

**Files:**
- Update: `platform/typescript/tokens/src/*` (global tokens)
- Keep: `products/yappc/frontend/libs/yappc-product-theme/src/*` (product presets)
- Delete: `products/yappc/frontend/libs/yappc-ui/src/components/theme/tokens/*`
- Delete: `products/yappc/frontend/libs/yappc-ui/src/components/tokens/*`
- Update: All imports

**Verification:**
- YAPPC Storybook passes
- Snapshot tests pass
- Visual rendering unchanged

---

### Task 4.6: Add Fail-Fast Guard for OAUTH2_DISCOVERY_URI
**Priority:** P3 | **Domain:** Shared Services | **Effort:** 1 day  
**Audit Source:** Platform Reality Audit §6

**Problem:** `OAUTH2_DISCOVERY_URI` defaults to empty string with no fail-fast guard.

**Action:**
1. Add validation in Auth Gateway startup that throws if `OAUTH2_DISCOVERY_URI` is empty in production profile
2. Add documentation for required environment variable
3. Add integration test

**Files:**
- Update: `shared-services/auth-gateway/src/main/java/com/ghatana/auth/gateway/AuthGatewayLauncher.java`

**Verification:**
- Auth Gateway fails fast in production without OAuth2 discovery URI
- Integration test passes

---

### Task 4.7: Implement Real Consent Enforcement in AEP
**Priority:** P3 | **Domain:** Java Product | **Effort:** 3 days  
**Audit Source:** Platform Reality Audit §7.1

**Problem:** `DefaultConsentService` allows all events; no real consent enforcement by default.

**Action:**
1. Implement real consent backend or explicit allow-all flag with audit
2. Wire real consent service in production profile
3. Add consent check integration test
4. Add audit logging for consent decisions

**Files:**
- New: `products/aep/aep-engine/src/main/java/com/ghatana/aep/consent/RealConsentService.java`
- Update: `products/aep/aep-engine/src/main/java/com/ghatana/aep/Aep.java`

**Verification:**
- Consent enforcement works in production
- Audit trail records consent decisions

---

### Task 4.8: Persist AEP Pipeline Run History
**Priority:** P3 | **Domain:** Java Product | **Effort:** 3 days  
**Audit Source:** Platform Reality Audit §7.1

**Problem:** Pipeline run history is bounded `ArrayDeque` (in-memory, not durable).

**Action:**
1. Persist runs to Data-Cloud `EventRunLedger` in all profiles
2. Add pagination to run history API
3. Add cleanup policy for old runs
4. Add integration test

**Files:**
- New: `products/aep/aep-engine/src/main/java/com/ghatana/aep/persistence/RunLedger.java`
- Update: `products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java`

**Verification:**
- Run history persists across restarts
- Pagination works

---

### Task 4.9: Implement Real Vector Search in Data-Cloud
**Priority:** P3 | **Domain:** Java Product | **Effort:** 1 sprint  
**Audit Source:** Platform Reality Audit §7.3

**Problem:** `SimilaritySearchCapability` is interface only; no production vector DB implementation.

**Action:**
1. Choose vector DB: pgvector, Pinecone, or Weaviate
2. Implement production `SimilaritySearchCapability` adapter
3. Add vector index management
4. Add integration tests
5. Update documentation

**Files:**
- New: `products/data-cloud/platform-vector/src/main/java/com/ghatana/datacloud/vector/PgvectorSimilaritySearchCapability.java` (or chosen DB)
- Update: Data Cloud configuration

**Verification:**
- Vector search integration test passes
- Performance acceptable

---

### Task 4.10: Implement PHR Clinical Decision Support
**Priority:** P3 | **Domain:** Java Product | **Effort:** 1 sprint  
**Audit Source:** Platform Reality Audit §7.9

**Problem:** `ClinicalDecisionSupportService` has empty constructor; no implementation.

**Action:**
1. Implement clinical decision support logic
2. Wire real implementation in `PhrKernelModule`
3. Add integration tests
4. Add documentation

**Files:**
- Update: `products/phr/src/main/java/com/ghatana/phr/kernel/service/ClinicalDecisionSupportService.java`
- Update: `products/phr/src/main/java/com/ghatana/phr/kernel/PhrKernelModule.java`

**Verification:**
- Clinical decision support integration test passes
- Decision logic documented

---

## Phase 5: P4 Cleanup & Enforcement (Weeks 8-10)

**Goal:** Remove generated output, add CI enforcement, clean up stale files.

### Task 5.1: Remove Checked-In Generated/Compiled Output
**Priority:** P4 | **Domain:** Structural | **Effort:** 2 days  
**Audit Source:** TypeScript/JS Reuse Consolidation Plan §B.13

**Problem:** Generated and built outputs create structural duplication noise.

**Action:**
1. Remove checked-in compiled `dist-ts` trees where source already exists
2. Remove generated router type output from review surface where not required as source
3. Update build scripts and ignore rules
4. Update import paths that point directly into generated output
5. Add lint rule preventing generated output commits

**Files:**
- Delete: `products/dcmaar/modules/ai-platform-adapters/dist-ts/*`
- Delete: Generated router types where not required
- Update: `.gitignore` and build scripts
- Add: ESLint rule

**Verification:**
- Build still regenerates output
- CI passes without checked-in output

---

### Task 5.2: Add Lint Rules for Duplicate Prevention
**Priority:** P4 | **Domain:** Structural | **Effort:** 3 days  
**Audit Source:** TypeScript/JS Reuse Consolidation Plan §F, Java Reuse Consolidation Audit §D

**Problem:** No automated prevention of duplicate patterns.

**Action:**
1. Add lint rule for exact duplicate package ownership in YAPPC shared libs
2. Add duplicate-file check for `*.old.*`, `*New.tsx`, known parallel library paths
3. Add source-scan guard preventing duplicate test base classes
4. Add boundary test for state-store, validation-result, tenant-context duplication
5. Add lint rule for platform DataGrid duplicate

**Files:**
- New: `eslint-rules/duplicate-package-ownership.js`
- New: `eslint-rules/no-stale-forks.js`
- New: Java boundary tests

**Verification:**
- CI fails on duplicate patterns
- New violations caught

---

### Task 5.3: Delete Stale and Archival Files
**Priority:** P4 | **Domain:** Structural | **Effort:** 1 day  
**Audit Source:** TypeScript/JS Reuse Consolidation Plan §E

**Problem:** Stale and archival leftovers where not referenced.

**Action:**
1. Delete `products/software-org/client/web/src/pages/org/RestructurePage.old.tsx`
2. Delete `products/yappc/docs/archive/index-old.ts`
3. Search for other `*.old.*` files and delete if unreferenced
4. Add lint rule preventing `*.old.*` commits

**Files:**
- Delete: Identified stale files
- Add: Lint rule

**Verification:**
- No broken imports
- CI passes

---

### Task 5.4: Implement Real PHR Plugin Lifecycle
**Priority:** P4 | **Domain:** Java Product | **Effort:** 2 days  
**Audit Source:** Platform Reality Audit §7.9

**Problem:** `PhrKernelPlugin.startPatientRecordService()` etc. use `System.out.println` — not real lifecycle.

**Action:**
1. Implement real lifecycle management in `PhrKernelPlugin`
2. Replace `System.out.println` with proper logging
3. Add lifecycle tests

**Files:**
- Update: `products/phr/src/main/java/com/ghatana/phr/plugin/PhrKernelPlugin.java`

**Verification:**
- PHR plugin lifecycle test passes
- Proper logging in place

---

### Task 5.5: Implement Virtual-Org Launcher HTTP/gRPC Servers
**Priority:** P4 | **Domain:** Java Product | **Effort:** 3 days  
**Audit Source:** Platform Reality Audit §7.11

**Problem:** `VirtualOrgLauncher.main()` logs "HTTP/gRPC server started" but starts nothing.

**Action:**
1. Implement HTTP server in launcher
2. Implement gRPC server in launcher
3. Remove misleading log messages if servers not implemented
4. Add integration tests
5. Resolve known compilation issues in `COMPILATION_ISSUES.md`

**Files:**
- Update: `products/virtual-org/launcher/src/main/java/com/ghatana/virtualorg/launcher/VirtualOrgLauncher.java`
- Resolve: Compilation issues

**Verification:**
- Virtual-Org launcher integration test passes
- HTTP/gRPC servers actually start

---

### Task 5.6: Add Cross-Product Distributed Trace Correlation
**Priority:** P4 | **Domain:** Observability | **Effort:** 3 days  
**Audit Source:** Platform Reality Audit §8

**Problem:** No cross-product `traceparent` propagation confirmed.

**Action:**
1. Add `traceparent` header propagation in all HTTP clients
2. Add `traceparent` propagation in gRPC clients
3. Verify cross-product trace correlation in monitoring
4. Add integration test

**Files:**
- Update: All HTTP and gRPC client implementations
- Update: Monitoring dashboards

**Verification:**
- Cross-product trace correlation visible in Grafana
- Integration test passes

---

### Task 5.7: Implement Flashit Stripe Checkout
**Priority:** P4 | **Domain:** TypeScript Product | **Effort:** 3 days  
**Audit Source:** Platform Reality Audit §7.5

**Problem:** Billing upgrade returns placeholder URL; Stripe checkout not creating real sessions.

**Action:**
1. Implement real Stripe Checkout session creation
2. Remove placeholder comment
3. Add integration test
4. Add webhook handling for successful checkout

**Files:**
- Update: `products/flashit/backend/gateway/src/routes/billing.ts`

**Verification:**
- Stripe checkout integration test passes
- Webhook handling works

---

### Task 5.8: Add PII Detection/Redaction in AEP
**Priority:** P4 | **Domain:** Security | **Effort:** 1 sprint  
**Audit Source:** Platform Reality Audit §13

**Problem:** No PII detection/redaction before event payload storage in AEP.

**Action:**
1. Add PII detection service
2. Add redaction logic before event storage
3. Add audit logging for PII events
4. Add integration test
5. Add documentation

**Files:**
- New: `products/aep/aep-engine/src/main/java/com/ghatana/aep/privacy/PiiDetectionService.java`
- Update: `products/aep/aep-engine/src/main/java/com/ghatana/aep/AepEngine.java`

**Verification:**
- PII detection integration test passes
- Redaction works correctly

---

### Task 5.9: Implement YAPPC → AEP Event Emission
**Priority:** P4 | **Domain:** Integration | **Effort:** 2 days  
**Audit Source:** Platform Reality Audit §8

**Problem:** YAPPC lifecycle events not emitted to AEP.

**Action:**
1. Emit YAPPC phase transitions as AEP events
2. Add event schema for YAPPC lifecycle
3. Add integration test
4. Add documentation

**Files:**
- Update: YAPPC lifecycle service to emit AEP events
- New: AEP event schema for YAPPC

**Verification:**
- YAPPC → AEP integration test passes
- Events visible in AEP

---

### Task 5.10: Implement Audio-Video → Data-Cloud Persistence
**Priority:** P4 | **Domain:** Integration | **Effort:** 2 days  
**Audit Source:** Platform Reality Audit §8

**Problem:** Audio results appear ephemeral; not persisted to Data-Cloud.

**Action:**
1. Persist audio transcription results to Data-Cloud
2. Add tenant isolation
3. Add retention policy
4. Add integration test

**Files:**
- Update: Audio-Video STT service to persist results
- New: Data-Cloud schema for audio results

**Verification:**
- Audio results persisted
- Integration test passes

---

## Phase 6: P5 Long-Term Strategic Work (Beyond 10 weeks)

**Goal:** Address long-term strategic improvements and new product development.

### Task 6.1: Wire Incident Service to Products
**Priority:** P5 | **Domain:** Shared Services | **Effort:** 1 sprint  
**Audit Source:** Platform Reality Audit §6

**Action:**
1. Identify which products should emit incidents
2. Wire incident service to those products
3. Add incident UI in relevant products
4. Add integration tests

---

### Task 6.2: Wire User Profile Service to Products
**Priority:** P5 | **Domain:** Shared Services | **Effort:** 1 sprint  
**Audit Source:** Platform Reality Audit §6

**Action:**
1. Identify which products need user profile data
2. Wire user profile service to those products
3. Add profile management UI
4. Add integration tests

---

### Task 6.3: Implement YAPPC → Data-Cloud Run Persistence
**Priority:** P5 | **Domain:** Integration | **Effort:** 2 days  
**Audit Source:** Platform Reality Audit §8

**Action:**
1. Persist YAPPC run results to Data-Cloud
2. Add tenant isolation
3. Add retention policy
4. Add integration test

---

### Task 6.4: Implement Real Data-Cloud Query Engine
**Priority:** P5 | **Domain:** Java Product | **Effort:** 2 sprints  
**Audit Source:** Platform Reality Audit §7.3

**Action:**
1. Implement SQL support in `EventQueryGrpcService`
2. Implement filtering and aggregation
3. Implement real query planning
4. Add integration tests
5. Update documentation

---

### Task 6.5: Implement PHR Web/Mobile Shared Contracts
**Priority:** P5 | **Domain:** TypeScript Product | **Effort:** 1 sprint  
**Audit Source:** TypeScript/JS Reuse Consolidation Plan §C

**Action:**
1. Create `products/phr/libs/ts/app-core` package
2. Extract shared web/mobile contracts
3. Update both clients to use shared contracts
4. Add integration tests

---

---

## Execution Summary

### Timeline Overview

| Phase | Duration | Focus | Tasks |
|-------|----------|-------|-------|
| Phase 1 | Weeks 1-2 | P0 Production Blockers | 5 critical unblockers |
| Phase 2 | Weeks 2-4 | P1 Exact Duplicates | 10 exact duplicate consolidations |
| Phase 3 | Weeks 4-6 | P2 Platform Health | 10 medium-risk consolidations |
| Phase 4 | Weeks 6-8 | P3 Structural Improvements | 10 higher-risk consolidations |
| Phase 5 | Weeks 8-10 | P4 Cleanup & Enforcement | 10 cleanup tasks |
| Phase 6 | Beyond 10 weeks | P5 Strategic Work | 5 long-term initiatives |

### Risk Profile

| Phase | Risk Level | Mitigation |
|-------|-----------|------------|
| Phase 1 | Low | Fixes broken execution; high confidence in solutions |
| Phase 2 | Low | Exact duplicates; zero semantic risk |
| Phase 3 | Medium | Some conceptual duplicates; requires careful API design |
| Phase 4 | Medium-High | Higher-risk consolidations; requires extensive testing |
| Phase 5 | Low | Cleanup and enforcement; well-understood scope |
| Phase 6 | Variable | Strategic work; requires dedicated product teams |

### Success Metrics

- **Production Readiness:** All products score ≥ 6/10 after Phase 3
- **Code Duplication:** 50% reduction in duplicate files after Phase 4
- **Test Coverage:** All consolidations have regression tests
- **CI Enforcement:** Duplicate patterns blocked by lint rules after Phase 5
- **Cross-Product Integration:** 3 new integration paths after Phase 5

### Dependencies

- **Phase 2 depends on Phase 1:** Some exact duplicate removals touch files affected by P0 fixes
- **Phase 3 depends on Phase 2:** Platform health improvements build on clean duplicate-free code
- **Phase 4 depends on Phase 3:** Higher-risk consolidations require stable platform abstractions
- **Phase 5 depends on Phase 4:** Enforcement rules need consolidated codebase to be effective

### Resource Requirements

- **Java Engineers:** 2-3 for Phases 1-4
- **TypeScript/JavaScript Engineers:** 2-3 for Phases 1-4
- **DevOps Engineers:** 1 for CI enforcement and monitoring
- **Product Engineers:** 1 per product for P0 fixes
- **QA Engineers:** 2 for regression testing across phases

### Rollback Strategy

Each task includes:
- Atomic commit with clear revert point
- Pre-change regression tests
- Post-change validation tests
- Feature flags for high-risk changes

---

*Plan generated: 2026-04-18*  
*Based on: Platform Reality Audit + Java Reuse Consolidation Audit + TypeScript/JS Reuse Consolidation Plan*  
*Total tasks: 50 across 6 phases*  
*Estimated effort: 10 weeks for Phases 1-5, ongoing for Phase 6*
