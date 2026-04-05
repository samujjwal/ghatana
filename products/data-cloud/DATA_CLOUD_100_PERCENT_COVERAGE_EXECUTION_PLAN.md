# Data Cloud 100% Coverage - Detailed Execution Plan

**Document ID:** DC-COVERAGE-EXEC-002  
**Version:** 1.3  
**Date:** 2026-04-05  
**Progress Status:** Phase 1-4 Complete (12 weeks), Phase 5-6 Ready  
**Based on:** [Implementation Plan](DATA_CLOUD_100_PERCENT_COVERAGE_IMPLEMENTATION_PLAN.md)

---

## Progress Summary

| Phase | Status | Completion |
|-------|--------|------------|
| Phase 1 Week 1: Test Infrastructure | **COMPLETE** | 8/8 tasks |
| Phase 1 Week 2: P1 Module Testability | **COMPLETE** | 10/10 tasks |
| Phase 2 Week 3: Reports & Analytics | **COMPLETE** | 9/9 tasks |
| Phase 2 Week 4: Event Durability & CDC | **COMPLETE** | 8/8 tasks |
| Phase 2 Week 5: Memory & Brain | **COMPLETE** | 8/8 tasks |
| Phase 2 Week 6: Learning & Models | **COMPLETE** | 9/9 tasks |
| Phase 3 Week 7: Features, Voice, WebSocket | **COMPLETE** | 9/9 tasks |
| Phase 3 Week 8: Governance & Security | **COMPLETE** | 13/13 tasks |
| Phase 4 Week 9: Plugins & Data Fabric | **COMPLETE** | 9/9 tasks |
| Phase 4 Week 10: AI Assistance | **COMPLETE** | 10/10 tasks |
| Phase 4 Week 11: P3 Structural Closure | **COMPLETE** | 3/3 tasks |
| Phase 4 Week 12: Infrastructure Edge Cases | **COMPLETE** | 5/5 tasks |
| Phase 5-6 (Remaining) | **READY** | 23 tasks pending |

**Total Progress: 101/104 tasks complete (97%)**

---

## Overview

This plan separates **code enhancement** (production code changes) from **testing** (test implementation) to enable parallel workstreams, clear accountability, and incremental delivery. Code enhancements include: production code changes for testability, missing production features, refactoring for coverage, and infrastructure setup. Testing includes: writing test cases, test infrastructure, fixtures/mocks, and coverage validation.

---

## Phase 1: Foundation & Infrastructure (Weeks 1-2) ✅ COMPLETE

### Week 1: Test Infrastructure Setup ✅

**Code Enhancements:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| CI-001 | `products/data-cloud` | Add JaCoCo coverage gates to enforce 100% line/branch/method | ✅ COMPLETE |
| CI-002 | `products/data-cloud` | Add testcontainers dependency | ✅ COMPLETE |
| CI-003 | `products/data-cloud` | `TestConstants.java` with canonical tenant IDs, HTTP status constants | ✅ EXISTS |
| CI-004 | `platform:java:testing` | `EventloopTestBase` with Data Cloud-specific helpers | ✅ EXISTS |

**Testing:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| TEST-INF-001 | `products/data-cloud` | `DataCloudHttpServerTestBase.java` | ✅ EXISTS |
| TEST-INF-002 | `products/data-cloud` | Deterministic test data builders (EntityBuilder, EventBuilder, PipelineBuilder) | ✅ CREATED |
| TEST-INF-003 | `products/data-cloud` | `TestContainersConfig.java` for PostgreSQL, Kafka, Redis, S3 | ✅ CREATED |
| TEST-INF-004 | `products/data-cloud` | `DataCloudClientFixtures.java` with lenient stubs | ✅ CREATED |

**Deliverables:**
- ✅ JaCoCo gates updated to 100% coverage targets
- ✅ Test base classes ready for use
- ✅ Testcontainers infrastructure functional
- ✅ Mock fixtures with lenient stubbing pattern

---

### Week 2: P1 Module - Core Testability Improvements ✅ COMPLETE

**Code Enhancements:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| CE-001 | `spi` | Test-visible factory methods for `QuerySpec`, `BatchResult` | ✅ EXISTS (builders present) |
| CE-002 | `platform-config` | Extract `ConfigValidatorInterface` | ✅ CREATED |
| CE-003 | `platform-entity` | Add `@Builder` to `EntityQuery`, equals/hashCode | ✅ EXISTS |
| CE-004 | `platform-event` | Add `EventOrderingFactory` for deterministic events | ✅ EXISTS (EventBuilder) |
| CE-005 | `platform-analytics` | Add `@VisibleForTesting` constructor | ✅ N/A - using builders |

**Testing:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| TEST-001 | `spi` | `CapabilityContractTest.java` | ✅ EXISTS |
| TEST-002 | `platform-config` | `ConfigValidationTest.java` | ✅ CREATED |
| TEST-003 | `platform-entity` | `EntityQueryBoundaryTest.java` - schema, versioning, CDC | ✅ CREATED |
| TEST-004 | `platform-event` | `EventOrderingInvariantTest.java` - append, query, dedup, replay | ✅ CREATED |
| TEST-005 | `platform-analytics` | `QueryCorrectnessFixtureTest.java` - SUM, AVG, COUNT, MIN, MAX | ✅ CREATED |

**Deliverables:**
- ✅ P1 modules have testable entry points
- ✅ Foundation tests establish coverage baseline
- ✅ All Phase 1 tests created and passing

---

## Phase 2: P1 Critical Features (Weeks 3-6) ✅ COMPLETE

### Week 3: Reports & Analytics (Requirements D001-D005) ✅ COMPLETE

**Code Enhancements:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| CE-006 | `platform-api` | Add `ReportsController` with POST/GET endpoints | ✅ CREATED |
| CE-007 | `platform-api` | Add `ReportCacheService` interface | ✅ CREATED |
| CE-008 | `platform-api` | Wire `ReportsController` into routing | ✅ IN CONTROLLER |
| CE-009 | `platform-api` | Add `GenerateReportRequest`, `ReportResponse` DTOs with `@Builder` | ✅ CREATED |

**Testing:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| TEST-006 | `platform-api` | `DataCloudHttpServerReportsTest.java` (D001, D004) | ✅ CREATED |
| TEST-007 | `platform-analytics` | `ReportServiceTest.java` with fixtures (D002) | ✅ CREATED |
| TEST-008 | `platform-api` | `CacheConsistencyIntegrationTest.java` (D003) | ✅ CREATED |
| TEST-009 | `platform-api` | `CostReportingEndpointTest.java` (D005) | ✅ CREATED |

**Deliverables:**
- ✅ Reports endpoints fully functional with tests
- ✅ Cache consistency validated
- ✅ Cost reporting tested

---

### Week 4: Event Durability & CDC (Requirements D006-D007) ✅ COMPLETE

**Code Enhancements:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| CE-010 | `platform-event` | Add `EventDurabilityService` interface | ✅ CREATED |
| CE-011 | `platform-event` | Add `EventCheckpointRepository` for CDC tracking | ✅ CREATED |
| CE-012 | `platform-event` | Add `EventReplayService` for replay operations | ✅ CREATED |
| CE-013 | `platform-event` | Add `EventDurabilityConfig` for durability settings | ✅ CREATED |

**Testing:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| TEST-010 | `platform-event` | `EventDurabilityIntegrationTest.java` (D006) - CDC capture | ✅ CREATED |
| TEST-011 | `platform-event` | `EventDurabilityContractTest.java` (D006) - durability contract | ✅ CREATED |
| TEST-012 | `platform-event` | `EventReplayTest.java` (D007) - replay from offset | ✅ CREATED |
| TEST-013 | `platform-event` | `EventIdempotencyTest.java` (D007) - idempotent apply | ✅ CREATED |

**Deliverables:**
- ✅ Event durability interfaces and tests complete
- ✅ CDC and replay fully tested

---

### Week 5: Memory & Brain (Requirements D008-D010) ✅ COMPLETE

**Code Enhancements:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| CE-014 | `platform-api` | Add `MemoryService` interface for tiered memory | ✅ CREATED |
| CE-015 | `platform-api` | Add `BrainStateManager` for state transitions | ✅ CREATED |
| CE-016 | `platform-api` | Add `EpisodicMemoryRepository` for short-term storage | ✅ CREATED |
| CE-017 | `platform-api` | Add `SemanticMemoryRepository` for long-term storage | ✅ CREATED |

**Testing:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| TEST-014 | `platform-api` | `MemoryTierTest.java` (D008) - episodic, semantic, procedural | ✅ CREATED |
| TEST-015 | `platform-api` | `BrainStateTransitionTest.java` (D009) - state machine | ✅ CREATED |
| TEST-016 | `platform-api` | `MemoryRecallAccuracyTest.java` (D010) - deterministic fixtures | ✅ CREATED |
| TEST-017 | `platform-api` | `MemoryEvictionTest.java` (D010) - LRU eviction logic | ✅ CREATED |

**Deliverables:**
- ✅ Memory and Brain interfaces complete
- ✅ Tier management and eviction tested

---

### Week 6: Learning & Models (Requirements D011-D012) ✅ COMPLETE

**Code Enhancements:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| CE-018 | `platform-api` | Add `ModelTrainingService` interface | ✅ CREATED |
| CE-019 | `platform-api` | Add `LearningProgressTracker` for metrics | ✅ CREATED |
| CE-020 | `platform-api` | Add `ModelVersionRepository` for versioning | ✅ CREATED |
| CE-021 | `platform-api` | Add `ModelEvaluationService` for accuracy | ✅ CREATED |

**Testing:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| TEST-018 | `platform-api` | `LearningPipelineIntegrationTest.java` (D011) - end-to-end | ✅ CREATED |
| TEST-019 | `platform-api` | `ModelVersionTest.java` (D012) - version rollback | ✅ CREATED |
| TEST-020 | `platform-api` | `ModelAccuracyTest.java` (D012) - deterministic fixtures | ✅ CREATED |
| TEST-021 | `platform-api` | `ModelSchemaValidationTest.java` (D012) - input/output schema | ✅ CREATED |
| TEST-022 | `platform-api` | `LearningProgressMetricsTest.java` (D011) - progress tracking | ✅ CREATED |

**Deliverables:**
- ✅ Learning and Model interfaces complete
- ✅ Training, versioning, and evaluation tested

---

## Phase 3: P2 Features (Weeks 7-8) ✅ COMPLETE

### Week 7: Features, Voice, WebSocket (Requirements F001-F005) ✅ COMPLETE

**Code Enhancements:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| CE-022 | `platform-api` | Add `FeatureFlagService` interface | ✅ CREATED |
| CE-023 | `platform-api` | Add `VoiceCommandHandler` for voice endpoints | ✅ CREATED |
| CE-024 | `platform-api` | Add `WebSocketConnectionManager` | ✅ CREATED |
| CE-025 | `platform-api` | Add `FeatureToggleController` | ✅ CREATED |

**Testing:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| TEST-023 | `platform-api` | `FeatureFlagServiceTest.java` (F001) - toggle logic | ✅ CREATED |
| TEST-024 | `launcher` | `VoiceCommandEndpointTest.java` (F002) - voice API | ✅ CREATED |
| TEST-025 | `platform-api` | `WebSocketConnectionTest.java` (F003) - connection lifecycle | ✅ CREATED |
| TEST-026 | `platform-api` | `WebSocketMessageTest.java` (F003) - message routing | ✅ CREATED |
| TEST-027 | `launcher` | `FeatureToggleIntegrationTest.java` (F001) - end-to-end | ✅ CREATED |

**Deliverables:**
- ✅ Feature flag service with toggle logic tested
- ✅ Voice command endpoints tested
- ✅ WebSocket connection and message routing tested
- ✅ End-to-end integration tests complete

---

### Week 8: Governance & Security (Requirements S001-S008) ✅ COMPLETE

**Code Enhancements:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| CE-026 | `platform-api` | Add `PolicyService` interface for governance | ✅ CREATED |
| CE-027 | `platform-api` | Add `AuditLogService` for compliance | ✅ CREATED |
| CE-028 | `platform-api` | Add `RBACService` for role management | ✅ CREATED |
| CE-029 | `platform-api` | Add `SecurityController` for security endpoints | ✅ CREATED |
| CE-030 | `platform-api` | Add `DataRetentionManager` | ✅ CREATED |

**Testing:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| TEST-028 | `platform-api` | `PolicyEnforcementTest.java` (S001) - policy validation | ✅ CREATED |
| TEST-029 | `platform-api` | `AuditLogRetentionTest.java` (S002) - retention policies | ✅ CREATED |
| TEST-030 | `platform-api` | `RBACPermissionTest.java` (S003) - role permissions | ✅ CREATED |
| TEST-031 | `launcher` | `SecurityEndpointTest.java` (S004) - security API | ✅ CREATED |
| TEST-032 | `platform-api` | `DataPrivacyTest.java` (S005) - privacy compliance | ✅ CREATED |
| TEST-033 | `launcher` | `TenantIsolationSecurityTest.java` (S006) - tenant boundaries | ✅ CREATED |
| TEST-034 | `platform-api` | `EncryptionAtRestTest.java` (S007) - encryption | ✅ CREATED |
| TEST-035 | `platform-api` | `SecurityAuditTrailTest.java` (S008) - audit completeness | ✅ CREATED |

**Deliverables:**
- ✅ Policy enforcement and governance tested
- ✅ Audit logging and retention tested
- ✅ RBAC permissions and role management tested
- ✅ Security endpoints and tenant isolation tested
- ✅ Data privacy and encryption tested
- ✅ Security audit trail completeness tested

---

## Phase 4: P2 Real Interactions (Weeks 9-12) ✅ COMPLETE

### Week 9: Plugins & Data Fabric (Requirements PF001-PF003) ✅ COMPLETE

**Code Enhancements:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| CE-031 | `platform-api` | Add `PluginRegistry` interface | ✅ CREATED |
| CE-032 | `platform-api` | Add `DataFabricConnector` for external sources | ✅ CREATED |
| CE-033 | `platform-api` | Add `DataTransformationPipeline` | ✅ CREATED |
| CE-034 | `platform-api` | Add `PluginController` for plugin management | ✅ CREATED |

**Testing:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| TEST-036 | `platform-api` | `PluginRegistryTest.java` (PF001) - plugin lifecycle | ✅ CREATED |
| TEST-037 | `platform-api` | `DataFabricConnectionTest.java` (PF002) - connector tests | ✅ CREATED |
| TEST-038 | `platform-api` | `DataTransformationTest.java` (PF003) - transformation pipeline | ✅ CREATED |
| TEST-039 | `launcher` | `PluginIntegrationTest.java` (PF001) - end-to-end | ✅ CREATED |
| TEST-040 | `platform-api` | `DataFabricSyncTest.java` (PF002) - sync operations | ✅ CREATED |

**Deliverables:**
- ✅ Plugin registry and lifecycle tested
- ✅ Data fabric connector and sync tested
- ✅ Data transformation pipeline tested
- ✅ Plugin integration end-to-end tested

---

### Week 10: AI Assistance (Requirements AI001-AI005) ✅ COMPLETE

**Code Enhancements:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| CE-035 | `platform-api` | Add `AIAssistService` interface | ✅ CREATED |
| CE-036 | `platform-api` | Add `LLMProvider` interface | ✅ CREATED |
| CE-037 | `platform-api` | Add `PromptTemplateManager` | ✅ CREATED |
| CE-038 | `platform-api` | Add `AIAssistController` | ✅ CREATED |
| CE-039 | `platform-api` | Add `ContextWindowManager` | ✅ CREATED |

**Testing:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| TEST-041 | `platform-api` | `AIAssistServiceTest.java` (AI001) - AI query processing | ✅ CREATED |
| TEST-042 | `platform-api` | `LLMProviderTest.java` (AI002) - provider abstraction | ✅ CREATED |
| TEST-043 | `platform-api` | `PromptTemplateTest.java` (AI003) - template rendering | ✅ CREATED |
| TEST-044 | `launcher` | `AIAssistEndpointTest.java` (AI004) - HTTP endpoints | ✅ CREATED |
| TEST-045 | `platform-api` | `ContextWindowTest.java` (AI005) - context management | ✅ CREATED |

**Deliverables:**
- ✅ AI assist service and query processing tested
- ✅ LLM provider abstraction tested
- ✅ Prompt template management tested
- ✅ AI assist endpoints tested
- ✅ Context window management tested

---

### Week 11: P3 Structural Closure (Requirements SC001-SC003) ✅ COMPLETE

**Code Enhancements:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| N/A | Structural | No new interfaces - verification only | ✅ COMPLETE |

**Testing:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| TEST-046 | `platform-api` | `ModuleBoundaryTest.java` (SC001) - boundary verification | ✅ CREATED |
| TEST-047 | `platform-api` | `ArchitecturalConstraintTest.java` (SC002) - constraint compliance | ✅ CREATED |
| TEST-048 | `platform-api` | `ContractCompletenessTest.java` (SC003) - contract verification | ✅ CREATED |

**Deliverables:**
- ✅ Module boundaries verified
- ✅ Architectural constraints validated
- ✅ Contract completeness verified

---

### Week 12: Infrastructure Edge Cases (Requirements IE001-IE005) ✅ COMPLETE

**Code Enhancements:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| N/A | Infrastructure | No new interfaces - edge case tests only | ✅ COMPLETE |

**Testing:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| TEST-049 | `platform-api` | `ConnectionTimeoutTest.java` (IE001) - timeout handling | ✅ CREATED |
| TEST-050 | `platform-api` | `CircuitBreakerTest.java` (IE002) - circuit breaker logic | ✅ CREATED |
| TEST-051 | `platform-api` | `ResourceExhaustionTest.java` (IE003) - resource limits | ✅ CREATED |
| TEST-052 | `platform-api` | `RetryExhaustionTest.java` (IE004) - retry behavior | ✅ CREATED |
| TEST-053 | `platform-api` | `MemoryPressureTest.java` (IE005) - memory handling | ✅ CREATED |

**Deliverables:**
- ✅ Connection timeout handling tested
- ✅ Circuit breaker logic tested
- ✅ Resource exhaustion scenarios tested
- ✅ Retry exhaustion behavior tested
- ✅ Memory pressure handling tested

---

## Phase 5: Frontend Coverage (Weeks 13-16)

### Week 13: UI Foundation & Dashboard (Requirements M001-M002)

**Code Enhancements:**
| Task | Module | Description | Estimated Hours |
|------|--------|-------------|-----------------|
| CE-051 | `ui/src` | Add `testid` attributes to Shell components for E2E | 3 |
| CE-052 | `ui/src` | Add Zod schemas for all API request/response types | 6 |
| CE-053 | `ui/src` | Add MSW (Mock Service Worker) handlers for all routes | 6 |
| CE-054 | `ui/src` | Add accessibility labels to Shell navigation | 2 |

**Testing:**
| Task | Module | Description | Estimated Hours |
|------|--------|-------------|-----------------|
| TEST-061 | `ui/src` | Create `__tests__/ShellRouting.test.tsx` (M001) - route resolution, auth | 6 |
| TEST-062 | `ui/src` | Create `__tests__/DashboardPage.test.tsx` (M002) - widgets, quick actions | 6 |
| TEST-063 | `ui/e2e` | Create Playwright `dashboard.spec.ts` | 6 |
| TEST-064 | `ui/src` | Add Vitest coverage configuration - 100% target | 3 |

**Deliverables:**
- UI test infrastructure ready
- Dashboard page fully tested
- PR: `feature/ui-foundation-coverage`

---

### Week 14: Collections & Workflows (Requirements M003-M004) ✅ COMPLETE

**Code Enhancements:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| CE-055 | `ui/src/services` | Add `CollectionService` interface | ✅ CREATED |
| CE-056 | `ui/src/services` | Add `WorkflowService` interface | ✅ CREATED |
| CE-057 | `ui/src/types` | Add Zod schemas for collections and workflows | ✅ CREATED |

**Testing:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| TEST-065 | `ui/src` | `CollectionsUI.test.tsx` (M003) - create, edit, validation | ✅ CREATED |
| TEST-066 | `ui/src` | `WorkflowDesigner.test.tsx` (M004) - canvas, state, save | ✅ CREATED |

**Deliverables:**
- ✅ Collection and Workflow services created
- ✅ Collections UI and Workflow Designer tests created
- ✅ Zod schemas for type validation

---

### Week 15: Data Exploration (Requirements M005-M006) ✅ COMPLETE

**Code Enhancements:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| CE-058 | `ui/src/services` | Add `DatasetExplorerService` interface | ✅ CREATED |
| CE-059 | `ui/src/services` | Add `SQLWorkspaceService` interface | ✅ CREATED |
| CE-060 | `ui/src/services` | Query validation types | ✅ CREATED |

**Testing:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| TEST-070 | `ui/src` | `DatasetExplorer.test.tsx` (M005) - search, filter, pagination | ✅ CREATED |
| TEST-071 | `ui/src` | `SqlWorkspace.test.tsx` (M006) - editor, results, history | ✅ CREATED |

**Deliverables:**
- ✅ Dataset Explorer and SQL Workspace services created
- ✅ Dataset Explorer and SQL Workspace tests created
- ✅ Data exploration type definitions complete

---

### Week 16: Settings & Trust (Requirements M007-M008) ✅ COMPLETE

**Code Enhancements:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| CE-061 | `ui/src/services` | Add `SettingsService` interface | ✅ CREATED |
| CE-062 | `ui/src/services` | Add `TrustCenterService` interface | ✅ CREATED |
| CE-063 | `ui/src/types` | Settings and Trust Center type definitions | ✅ CREATED |

**Testing:**
| Task | Module | Description | Status |
|------|--------|-------------|--------|
| TEST-075 | `ui/src` | `TrustCenter.test.tsx` (M007) - a11y, permissions | ✅ CREATED |
| TEST-077 | `ui/src` | `SettingsPage.test.tsx` (M008) - changes, validation | ✅ CREATED |

**Deliverables:**
- ✅ Settings and Trust Center services created
- ✅ Settings Page and Trust Center tests created
- ✅ Privacy, compliance, and security test coverage
- ✅ Accessibility test coverage for Trust Center

---

## Phase 6: CI Gates & Final Verification (Weeks 17-20)

### Week 17: CI Gate Implementation

**Code Enhancements:**
| Task | Module | Description | Estimated Hours |
|------|--------|-------------|-----------------|
| CE-064 | `.github/workflows` | Add `verify-requirement-coverage.sh` to CI | 4 |
| CE-065 | `.github/workflows` | Add `verify-route-coverage.sh` to CI | 4 |
| CE-066 | `.github/workflows` | Add `verify-ui-contracts.sh` to CI | 4 |
| CE-067 | `.github/workflows` | Add `verify-streaming-coverage.sh` to CI | 3 |
| CE-068 | `.github/workflows` | Add `verify-surface-completeness.sh` to CI | 3 |

**Testing:**
| Task | Module | Description | Estimated Hours |
|------|--------|-------------|-----------------|
| TEST-081 | `scripts` | Create requirement coverage verification script | 4 |
| TEST-082 | `scripts` | Create route coverage verification script | 4 |
| TEST-083 | `scripts` | Create UI contracts verification script | 4 |
| TEST-084 | `scripts` | Create streaming coverage verification script | 3 |
| TEST-085 | `scripts` | Create surface completeness verification script | 3 |

**Deliverables:**
- All coverage gates running in CI
- PR: `feature/ci-coverage-gates`

---

### Week 18: Performance & Edge Cases

**Code Enhancements:**
| Task | Module | Description | Estimated Hours |
|------|--------|-------------|-----------------|
| CE-069 | `benchmarks` | Add JMH benchmarks for critical paths (entity create, event append) | 6 |

**Testing:**
| Task | Module | Description | Estimated Hours |
|------|--------|-------------|-----------------|
| TEST-086 | `benchmarks` | Create `EntityCreateBenchmark.java` - < 100ms p99 | 4 |
| TEST-087 | `benchmarks` | Create `EventAppendBenchmark.java` - < 50ms p99 | 4 |
| TEST-088 | `benchmarks` | Create `EntityQueryBenchmark.java` - < 200ms p99 | 4 |
| TEST-089 | `benchmarks` | Create `ReportGenerationBenchmark.java` - < 10s for 1M rows | 4 |
| TEST-090 | `benchmarks` | Create `FeatureIngestBenchmark.java` - < 1s for 1000 features | 4 |
| TEST-091 | `products/data-cloud` | Complete any remaining edge case tests | 8 |

**Deliverables:**
- Performance benchmarks established
- All edge cases covered
- PR: `feature/performance-benchmarks`

---

### Weeks 19-20: Final Verification

**Testing:**
| Task | Module | Description | Estimated Hours |
|------|--------|-------------|-----------------|
| TEST-092 | `products/data-cloud` | Full test suite run - target < 10 minutes | 8 |
| TEST-093 | `products/data-cloud` | Validate JaCoCo 100% line/branch/method | 4 |
| TEST-094 | `ui` | Validate Vitest 100% line/branch/function | 4 |
| TEST-095 | `products/data-cloud` | Requirement matrix validation (69/69) | 4 |
| TEST-096 | `products/data-cloud` | OpenAPI route coverage validation | 4 |
| TEST-097 | `products/data-cloud` | UI contract drift validation | 4 |
| TEST-098 | `products/data-cloud` | Streaming route coverage validation | 2 |
| TEST-099 | `products/data-cloud` | Flakiness check - 10 runs, 0 failures | 8 |

**Deliverables:**
- 100% coverage achieved and verified
- All acceptance checklist items complete
- Final PR: `feature/100-percent-coverage-complete`

---

## Workstream Summary

### Enhancement (Code) Workstream Summary

| Phase | Tasks | Hours | PRs |
|-------|-------|-------|-----|
| Phase 1 (Weeks 1-2) | 5 code tasks | 23h | test-infrastructure, p1-foundation |
| Phase 2 (Weeks 3-6) | 16 code tasks | 82h | reports, events, memory, learning |
| Phase 3 (Weeks 7-8) | 9 code tasks | 50h | features, governance |
| Phase 4 (Weeks 9-12) | 20 code tasks | 91h | plugins, ai, p3, infrastructure |
| Phase 5 (Weeks 13-16) | 13 code tasks | 35h | ui foundation, workflows, data, settings |
| Phase 6 (Weeks 17-20) | 6 code tasks | 16h | ci gates, benchmarks |
| **Total** | **69 tasks** | **~297h** | **18 PRs** |

### Testing Workstream Summary

| Phase | Tasks | Hours | Focus |
|-------|-------|-------|-------|
| Phase 1 (Weeks 1-2) | 9 test tasks | 34h | infrastructure, foundation |
| Phase 2 (Weeks 3-6) | 23 test tasks | 90h | reports, events, memory, learning |
| Phase 3 (Weeks 7-8) | 18 test tasks | 82h | features, governance, edge cases |
| Phase 4 (Weeks 9-12) | 25 test tasks | 95h | plugins, ai, p3 closure, infrastructure |
| Phase 5 (Weeks 13-16) | 20 test tasks | 86h | ui unit, integration, e2e, a11y |
| Phase 6 (Weeks 17-20) | 18 test tasks | 66h | ci gates, benchmarks, verification |
| **Total** | **113 tasks** | **~453h** | **113 test suites** |

---

## Test Naming Convention (Required)

```java
@Test
@DisplayName("[Feature]: [action]_[scenario]_[assertion]")
void entityCreate_validInput_returns201() { }

@Test
@DisplayName("[Boundary]: missing_required_field_returns400")
void entityCreate_missingName_returns400() { }

@Test
@DisplayName("[Tenant Isolation]: different_tenant_sees_no_data")
void entityQuery_tenantAlpha_cantSeeTenantBeta() { }
```

---

## Definition of Done per Task

**Code Enhancement Task:**
- [ ] Follows Ghatana repo conventions and existing patterns
- [ ] Fully typed (TypeScript) / JavaDoc with `@doc.*` tags (Java)
- [ ] Builds without warnings
- [ ] No hardcoded secrets
- [ ] Errors are surfaced, not swallowed
- [ ] Test evidence included (tests pass with the change)

**Testing Task:**
- [ ] Test follows naming convention
- [ ] Asserts outputs (response body, values, errors)
- [ ] Asserts state changes (DB, cache, memory)
- [ ] Asserts side effects (events, metrics, logs)
- [ ] Does NOT mirror implementation
- [ ] Does NOT use shallow assertions only
- [ ] Coverage meets 100% line/branch target

---

## Risk Mitigation

| Risk | Mitigation | Owner |
|------|------------|-------|
| Test flakiness | Use deterministic fixtures, testcontainers, no sleep loops | Testing team |
| Slow test suite | Parallelize, cache testcontainers, target < 10 min | CI/CD team |
| Mock overuse | Prefer real integration tests for critical paths | Tech lead review |
| Coverage gaps | Automated gates, requirement matrix validation | CI pipeline |
| UI contract drift | Automated contract validation, canonical routes | Frontend lead |
| Parallel work conflict | Clear module ownership, daily sync, small PRs | Project manager |

---

## Immediate Next Actions

1. **Create branch:** `feature/test-infrastructure`
2. **Assign:** CI-001 through CI-004 (enhancement) and TEST-INF-001 through TEST-INF-004 (testing)
3. **Deliver:** Test base classes and JaCoCo gates
4. **Review:** PR with tech lead for pattern alignment
5. **Proceed:** Merge and begin Phase 1 Week 2 foundation tests

---

**Reference:**
- Implementation Plan: `DATA_CLOUD_100_PERCENT_COVERAGE_IMPLEMENTATION_PLAN.md`
- Repo Guidelines: `.github/copilot-instructions.md`
