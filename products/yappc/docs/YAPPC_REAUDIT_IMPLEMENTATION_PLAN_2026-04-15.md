# YAPPC Re-Audit Implementation Plan
**Date:** 2026-04-15  
**Based on:** YAPPC_REAUDIT_REPORT_2026-04-15.md  
**Estimated Total Duration:** 14-20 weeks  
**Status:** Ready to Start

---

## Overview

This implementation plan addresses all critical findings from the re-audit report. Tasks are organized into three phases with clear dependencies, acceptance criteria, and time estimates.

**Phase 0:** Unresolved Blockers + Fake Closures (1-2 weeks)  
**Phase 1:** Correctness + Hardening (3-4 weeks)  
**Phase 2:** Completeness + UX + Proof (4-6 weeks)  
**Phase 3:** Efficiency + Maintainability + Competitive Differentiation (6-8 weeks)

---

## Phase 0: Unresolved Blockers + Fake Closures (1-2 weeks)

### Task 0.1: Remove default-tenant Fallbacks
**Priority:** P0 - Critical  
**Estimated Time:** 3 days  
**Files to Modify:**
- `core/services-lifecycle/src/main/java/com/ghatana/yappc/services/security/YappcApiSecurity.java`
- `core/services-lifecycle/src/main/java/com/ghatana/yappc/services/security/JwtAuthController.java`
- `core/yappc-services/src/main/java/com/ghatana/yappc/api/YappcApiAuthFilter.java`
- `core/yappc-services/src/main/java/com/ghatana/yappc/storage/DataCloudArtifactStore.java`
- `infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/repository/AgentStateRepository.java`
- `core/ai/src/main/java/com/ghatana/yappc/ai/history/ConversationRepository.java`
- `core/ai/src/main/java/com/ghatana/yappc/ai/prompts/PromptVersioningService.java`

**Implementation Steps:**
1. Search for all occurrences of `"default-tenant"` string literal
2. Replace `getOrDefault(apiKey, "default-tenant")` with `get(apiKey)` followed by `orElseThrow(() -> new IllegalStateException("No tenant mapping for API key"))`
3. Replace `orElse("default-tenant")` with `orElseThrow(() -> new IllegalStateException("Tenant ID required in JWT claims"))`
4. Replace `if (tenantId == null || tenantId.isBlank() || "default-tenant".equals(tenantId))` with `if (tenantId == null || tenantId.isBlank())`
5. Add unit tests to verify IllegalStateException is thrown when tenant context is missing
6. Run full test suite to ensure no regressions

**Acceptance Criteria:**
- Zero occurrences of `"default-tenant"` string literal in production code
- All tenant resolution throws IllegalStateException when tenant is missing
- All tests pass
- No security vulnerabilities related to tenant leakage

**Dependencies:** None

**Verification:**
```bash
grep -r "default-tenant" products/yappc/core --include="*.java"
# Expected: No results in production code (test files OK)
```

---

### Task 0.2: Fix Data Cloud Query Operator Usage
**Priority:** P0 - Critical  
**Estimated Time:** 2 days  
**Files to Modify:**
- `infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/repository/ProjectRepository.java`
- `infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/repository/TaskRepository.java`
- `infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/repository/PhaseStateRepository.java`

**Implementation Steps:**
1. Remove `findRecentlyActive()` method from ProjectRepository (line 142-147)
2. Remove query methods that use `$lte` from TaskRepository (line 188)
3. Remove query methods that use `$gte` and `$lte` from PhaseStateRepository (line 164-165)
4. Add deprecation notice to removed methods with migration path
5. Update callers to use alternative query patterns (equality filters only)
6. Add unit tests to verify alternative queries work correctly

**Alternative Approach (if operators are required):**
1. Implement operator support in YappcDataCloudRepository
2. Add support for `$gte`, `$gt`, `$lte`, `$lt` in filter parsing
3. Update Data Cloud client to support these operators
4. Add integration tests with real Data Cloud to verify

**Acceptance Criteria:**
- No usage of `$gte`, `$gt`, `$lte`, `$lt` operators in repository code
- All query methods use only equality filters
- All tests pass
- Documentation updated to reflect query limitations

**Dependencies:** Task 0.1 (tenant isolation must be verified first)

**Verification:**
```bash
grep -r '\$gte\|\$gt\|\$lte\|\$lt' products/yappc/infrastructure/datacloud --include="*.java"
# Expected: No results in repository code
```

---

### Task 0.3: Remove Non-Functional Auth Endpoints
**Priority:** P0 - Critical  
**Estimated Time:** 1 day  
**Files to Modify:**
- `frontend/apps/api/src/routes/auth.ts`
- `frontend/apps/api/src/services/auth/proxy-auth.service.ts`

**Implementation Steps:**
1. Remove POST `/auth/register` endpoint from auth.ts (lines 40-83)
2. Remove POST `/auth/forgot-password` endpoint from auth.ts (lines 191-218)
3. Remove POST `/auth/reset-password` endpoint from auth.ts (lines 221-249)
4. Remove POST `/auth/verify-email` endpoint from auth.ts (lines 252-279)
5. Remove POST `/auth/change-password` endpoint from auth.ts (lines 304-334)
6. Remove corresponding stub methods from proxy-auth.service.ts (lines 120-168)
7. Update OpenAPI schema to reflect removed endpoints
8. Add 404 responses for removed endpoints with clear message
9. Update documentation to reflect auth limitations

**Alternative Approach (if features are needed):**
1. Implement register in Java lifecycle service
2. Implement password reset in Java lifecycle service
3. Implement email verification in Java lifecycle service
4. Implement change password in Java lifecycle service
5. Remove stub methods and wire to real implementations

**Acceptance Criteria:**
- Non-functional auth endpoints removed from Node.js API
- OpenAPI schema updated
- Documentation updated
- All tests pass

**Dependencies:** None

**Verification:**
```bash
# Test that removed endpoints return 404
curl -X POST http://localhost:8082/auth/register -H "Content-Type: application/json" -d '{"email":"test@example.com","password":"password","name":"Test"}'
# Expected: 404 Not Found
```

---

### Task 0.4: Update Documentation for Port 8082
**Priority:** P0 - Critical  
**Estimated Time:** 1 day  
**Files to Modify:**
- `docs/development/RUN_DEV_GUIDE.md`
- `docs/guides/getting-started.md`
- `docs/deployment/README_DOCKER.md`
- `README.md`
- Any other documentation files referencing port 8080

**Implementation Steps:**
1. Search for all occurrences of `8080` in documentation
2. Replace with `8082` where referencing YAPPC API
3. Verify context - some 8080 references may be for other services
4. Update curl commands in getting-started guide
5. Update deployment documentation
6. Update README.md
7. Add note about port change in migration guide

**Acceptance Criteria:**
- All YAPPC API references use port 8082
- No confusion about which port to use
- Documentation is consistent with deployment configs
- Developer guides updated

**Dependencies:** None

**Verification:**
```bash
grep -r "8080" products/yappc/docs --include="*.md"
# Expected: No YAPPC API references to 8080 (other services OK)
```

---

## Phase 1: Correctness + Hardening (3-4 weeks)

### Task 1.1: Implement Redis-Backed Collaboration Storage
**Priority:** P0 - Critical  
**Estimated Time:** 5 days  
**Files to Create:**
- `frontend/apps/api/src/services/RealTimeServiceRedis.ts`
- `frontend/apps/api/src/services/redis/RedisRoomManager.ts`
- `frontend/apps/api/src/services/redis/RedisPresenceManager.ts`

**Files to Modify:**
- `frontend/apps/api/src/services/RealTimeService.ts`
- `frontend/apps/api/package.json` (add redis dependency)

**Implementation Steps:**
1. Add Redis client dependency (ioredis or redis)
2. Create RedisRoomManager to store room state in Redis
3. Create RedisPresenceManager to store user presence in Redis
4. Implement pub/sub for real-time updates across instances
5. Add TTL for inactive rooms (1 hour)
6. Add connection pooling for Redis
7. Migrate RealTimeService to use Redis managers
8. Add fallback to in-memory if Redis unavailable
9. Add health check for Redis connectivity
10. Add integration tests with Testcontainers (Redis)

**Acceptance Criteria:**
- Collaboration state persisted in Redis
- State survives service restart
- Multiple service instances can collaborate
- Pub/sub updates broadcast across instances
- Health check includes Redis status
- Integration tests pass with Testcontainers

**Dependencies:** None

**Verification:**
```bash
# Start multiple instances and verify collaboration works
# Kill one instance and verify state preserved
# Verify pub/sub updates broadcast across instances
```

---

### Task 1.2: Migrate DataCloudClient Usage to Adapter
**Priority:** P0 - Critical  
**Estimated Time:** 3 days  
**Files to Modify:**
- `core/ai/src/main/java/com/ghatana/yappc/ai/history/ConversationRepository.java`
- `core/yappc-services/src/main/java/com/ghatana/yappc/storage/DataCloudArtifactStore.java`
- `infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/adapter/YappcDataCloudRepository.java` (may need enhancements)

**Implementation Steps:**
1. Audit all direct DataCloudClient usage
2. Identify required methods missing from YappcDataCloudRepository
3. Add missing methods to YappcDataCloudRepository:
   - Query by prefix (for artifact store)
   - Stream/batch operations
4. Migrate ConversationRepository to use YappcDataCloudRepository
5. Migrate DataCloudArtifactStore to use YappcDataCloudRepository
6. Ensure tenant isolation is enforced by adapter
7. Add unit tests for new adapter methods
8. Run integration tests to verify migration

**Acceptance Criteria:**
- All DataCloudClient usage goes through YappcDataCloudRepository
- Tenant isolation enforced uniformly
- No direct DataCloudClient access in production code
- All tests pass

**Dependencies:** Task 0.1 (tenant isolation), Task 0.2 (query operators)

**Verification:**
```bash
grep -r "DataCloudClient" products/yappc/core --include="*.java" | grep -v "YappcDataCloudRepository"
# Expected: No results (only in adapter/infrastructure layer)
```

---

### Task 1.3: Add Retry with Exponential Backoff
**Priority:** P1 - High  
**Estimated Time:** 2 days  
**Files to Create:**
- `infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/client/RetryableDataCloudClient.java`

**Files to Modify:**
- `infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/adapter/YappcDataCloudRepository.java`

**Implementation Steps:**
1. Create RetryableDataCloudClient wrapper around DataCloudClient
2. Implement exponential backoff: 100ms, 200ms, 400ms, 800ms, 1600ms
3. Add max retry limit (5 attempts)
4. Retry on transient errors (network timeouts, 5xx errors)
5. Do not retry on client errors (4xx, validation errors)
6. Add metrics for retry attempts and failures
7. Wire RetryableDataCloudClient into YappcDataCloudRepository
8. Add unit tests for retry logic
9. Add integration tests with simulated failures

**Acceptance Criteria:**
- Transient errors retried with exponential backoff
- Client errors not retried
- Max 5 retry attempts
- Metrics emitted for retry attempts
- All tests pass

**Dependencies:** Task 1.2 (adapter migration)

**Verification:**
```bash
# Test with simulated network failures
# Verify retry behavior in logs
# Check metrics for retry counts
```

---

### Task 1.4: Add Circuit Breaker Pattern
**Priority:** P1 - High  
**Estimated Time:** 3 days  
**Files to Create:**
- `infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/client/CircuitBreakerDataCloudClient.java`

**Files to Modify:**
- `infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/adapter/YappcDataCloudRepository.java`

**Implementation Steps:**
1. Create CircuitBreakerDataCloudClient wrapper
2. Implement circuit breaker states: CLOSED, OPEN, HALF_OPEN
3. Threshold: 5 failures in 1 minute opens circuit
4. Timeout: Circuit stays open for 30 seconds
5. Half-open: Allow 1 test request, reset on success
6. Add metrics for circuit state transitions
7. Wire CircuitBreakerDataCloudClient into YappcDataCloudRepository
8. Add unit tests for circuit breaker logic
9. Add integration tests with simulated failures

**Acceptance Criteria:**
- Circuit opens after 5 failures in 1 minute
- Circuit stays open for 30 seconds
- Half-open state allows test request
- Metrics emitted for state transitions
- All tests pass

**Dependencies:** Task 1.3 (retry implementation)

**Verification:**
```bash
# Test with 5 consecutive failures
# Verify circuit opens
# Verify requests fail fast when circuit open
# Check metrics for circuit state
```

---

### Task 1.5: Add Data Cloud Health Check
**Priority:** P1 - High  
**Estimated Time:** 1 day  
**Files to Create:**
- `infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/health/DataCloudHealthCheck.java`

**Files to Modify:**
- `core/services-lifecycle/src/main/java/com/ghatana/yappc/services/lifecycle/LifecycleServiceModule.java`

**Implementation Steps:**
1. Create DataCloudHealthCheck that queries Data Cloud
2. Add simple query to verify connectivity (e.g., findById for known entity)
3. Add timeout (5 seconds)
4. Return health status: UP, DOWN, DEGRADED
5. Wire into lifecycle service health endpoint
6. Add metrics for health check latency
7. Add unit tests for health check
8. Add integration test with Testcontainers

**Acceptance Criteria:**
- Health check queries Data Cloud
- Returns correct status based on connectivity
- Timeout after 5 seconds
- Metrics emitted for latency
- All tests pass

**Dependencies:** None

**Verification:**
```bash
curl http://localhost:8082/health
# Expected: Includes Data Cloud status
```

---

### Task 1.6: Add Integration Tests with Testcontainers
**Priority:** P1 - High  
**Estimated Time:** 5 days  
**Files to Create:**
- `infrastructure/datacloud/src/integrationTest/java/com/ghatana/yappc/infrastructure/datacloud/DataCloudIntegrationTest.java`
- `core/services-lifecycle/src/integrationTest/java/com/ghatana/yappc/services/lifecycle/AuthIntegrationTest.java`
- `core/yappc-services/src/integrationTest/java/com/ghatana/yappc/storage/ArtifactStoreIntegrationTest.java`

**Implementation Steps:**
1. Create base integration test class with Testcontainers setup
2. Add PostgreSQL Testcontainer for Data Cloud simulation
3. Add Redis Testcontainer for collaboration tests
4. Create DataCloudIntegrationTest:
   - Test CRUD operations with real Data Cloud
   - Test tenant isolation with multiple tenants
   - Test query operators (verify they fail or work)
5. Create AuthIntegrationTest:
   - Test login flow end-to-end
   - Test token refresh flow
   - Test token validation
6. Create ArtifactStoreIntegrationTest:
   - Test artifact storage and retrieval
   - Test tenant-scoped queries
7. Add cleanup logic for each test
8. Add assertions for data integrity

**Acceptance Criteria:**
- Integration tests use Testcontainers
- Tests verify end-to-end behavior
- Tenant isolation tested
- Data integrity tested
- All tests pass

**Dependencies:** Task 0.1 (tenant isolation), Task 1.2 (adapter migration)

**Verification:**
```bash
./gradlew :products:yappc:infrastructure:datacloud:integrationTest
./gradlew :products:yappc:core:services-lifecycle:integrationTest
# Expected: All tests pass
```

---

## Phase 2: Completeness + UX + Proof (4-6 weeks)

### Task 2.1: Implement or Remove Placeholder Handlers
**Priority:** P1 - High  
**Estimated Time:** 3 days  
**Files to Modify:**
- `frontend/web/src/canvas/phase-actions.ts`

**Implementation Steps:**
1. Audit all 20 placeholder handlers in phase-actions.ts
2. Determine which handlers are critical for product:
   - Critical: Implement real logic
   - Non-critical: Remove handlers
3. For critical handlers:
   - Implement backend API endpoints
   - Wire handlers to real API calls
   - Add loading/error states
   - Add success feedback
4. For non-critical handlers:
   - Remove from phase-actions.ts
   - Remove from UI
   - Update documentation
5. Add unit tests for implemented handlers
6. Add integration tests for API endpoints

**Acceptance Criteria:**
- No placeholder handlers remain
- Critical phase transitions work end-to-end
- Non-critical features removed cleanly
- All tests pass

**Dependencies:** None

**Verification:**
```bash
grep -r "placeholder" frontend/web/src/canvas/phase-actions.ts
# Expected: No results
```

---

### Task 2.2: Remove or Complete TODO/FIXME Comments
**Priority:** P1 - High  
**Estimated Time:** 4 days  
**Files to Modify:**
- `core/ai/src/main/java/com/ghatana/yappc/ai/canvas/CanvasGenerationService.java`
- `core/agents/workflow/src/main/java/com/ghatana/yappc/agent/ops/CanaryStep.java`
- `core/ai/src/main/java/com/ghatana/yappc/ai/requirements/ai/feedback/FeedbackLearningService.java`
- `core/scaffold/engine/src/main/java/com/ghatana/yappc/core/security/SecurityReviewFramework.java`
- `core/refactorer/api/src/main/java/com/ghatana/refactorer/orchestrator/PolyfixOrchestrator.java`

**Implementation Steps:**
1. Search for all TODO/FIXME comments in production code
2. Categorize each:
   - Critical for functionality: Implement
   - Nice-to-have: Schedule for later
   - Outdated: Remove
3. For critical TODOs:
   - Implement the missing logic
   - Add unit tests
   - Remove TODO comment
4. For nice-to-have TODOs:
   - Create GitHub issues
   - Add issue reference in comment
   - Format: `TODO: [description] (Issue #123)`
5. For outdated TODOs:
   - Remove comment
   - Verify code is still correct
6. Run full test suite

**Acceptance Criteria:**
- No untracked TODO/FIXME in production code
- All tracked TODOs have GitHub issue references
- All tests pass

**Dependencies:** None

**Verification:**
```bash
grep -r "TODO\|FIXME" products/yappc/core --include="*.java" | grep -v "Issue #"
# Expected: No results (all TODOs have issue references)
```

---

### Task 2.3: Remove or Complete Placeholder Implementations
**Priority:** P1 - High  
**Estimated Time:** 3 days  
**Files to Modify:**
- `knowledge/retrieval/src/main/java/com/ghatana/yappc/knowledge/retrieval/YappcDenseVectorRetriever.java`
- `core/refactorer/api/src/main/java/com/ghatana/refactorer/orchestrator/report/ReportGenerator.java`
- `core/refactorer/api/src/main/java/com/ghatana/refactorer/orchestrator/DebugPlanner.java`
- `core/yappc-agents/src/main/java/com/ghatana/yappc/agent/eval/AgentEvalCli.java`
- `core/yappc-services/src/main/java/com/ghatana/yappc/services/shape/ShapeServiceImpl.java`

**Implementation Steps:**
1. Audit all placeholder implementations identified in re-audit
2. Determine which are critical:
   - Critical: Implement real logic
   - Non-critical: Remove
3. For critical placeholders:
   - Implement real logic
   - Add unit tests
   - Remove placeholder comment
4. For non-critical placeholders:
   - Remove code
   - Update callers
   - Update documentation
5. Run full test suite

**Acceptance Criteria:**
- No placeholder implementations in production code
- Critical features implemented
- All tests pass

**Dependencies:** Task 2.2 (TODO cleanup)

**Verification:**
```bash
grep -r "placeholder\|Placeholder" products/yappc --include="*.java" --include="*.ts"
# Expected: No results in production code (UI placeholders OK)
```

---

### Task 2.4: Replace Frontend Mocks with Real Implementations
**Priority:** P2 - Medium  
**Estimated Time:** 5 days  
**Files to Modify:**
- `frontend/web/src/__mocks__/@yappc/state.ts`
- `frontend/web/src/__mocks__/@ghatana/canvas.ts`
- `frontend/web/src/__mocks__/@ghatana/theme.ts`
- `frontend/web/vitest.config.ts`

**Implementation Steps:**
1. Audit all mock files in __mocks__
2. Determine which mocks can be replaced with real implementations:
   - Can replace: Use real package
   - Cannot replace: Keep mock but document why
3. For replaceable mocks:
   - Remove from __mocks__
   - Update vitest.config.ts to use real package
   - Update tests to work with real implementation
4. For irreplaceable mocks:
   - Add documentation explaining why mock is needed
   - Add comment with issue tracking
5. Add integration tests to verify real behavior

**Acceptance Criteria:**
- Mocks replaced where possible
- Remaining mocks documented
- All tests pass
- Integration tests added for real behavior

**Dependencies:** None

**Verification:**
```bash
# Count mock files
ls -la frontend/web/src/__mocks__/
# Expected: Minimal, well-documented mocks
```

---

### Task 2.5: Add E2E Tests for Critical Workflows
**Priority:** P1 - High  
**Estimated Time:** 5 days  
**Files to Create:**
- `frontend/web/e2e/auth.spec.ts`
- `frontend/web/e2e/project-creation.spec.ts`
- `frontend/web/e2e/canvas-operations.spec.ts`
- `frontend/web/e2e/phase-transitions.spec.ts`

**Implementation Steps:**
1. Create auth.spec.ts:
   - Test login flow
   - Test token refresh
   - Test logout
2. Create project-creation.spec.ts:
   - Test project creation
   - Test project listing
   - Test project deletion
3. Create canvas-operations.spec.ts:
   - Test node creation
   - Test node editing
   - Test connection creation
   - Test canvas save/load
4. Create phase-transitions.spec.ts:
   - Test phase advancement
   - Test phase rollback
   - Test phase requirements
5. Add page objects for reusable locators
6. Add test data setup/teardown
7. Configure Playwright for CI

**Acceptance Criteria:**
- E2E tests cover critical workflows
- Tests run in CI
- Tests are reliable (not flaky)
- All tests pass

**Dependencies:** Task 2.1 (placeholder handlers)

**Verification:**
```bash
npx playwright test
# Expected: All E2E tests pass
```

---

### Task 2.6: Consolidate Duplicate Agent Trees
**Priority:** P2 - Medium  
**Estimated Time:** 3 days  
**Directories to Audit:**
- `core/agents/`
- `core/yappc-agents/`

**Implementation Steps:**
1. Audit both agent directories
2. Identify unique code in each:
   - core/agents: Generic platform agents
   - core/yappc-agents: YAPPC-specific agents
3. Determine consolidation strategy:
   - Option A: Merge into core/agents with subdirectories
   - Option B: Keep separate but clarify ownership
4. For Option A:
   - Move yappc-agents to core/agents/yappc/
   - Update build.gradle.kts
   - Update imports
   - Update documentation
5. For Option B:
   - Add README to each directory explaining ownership
   - Update documentation
6. Run full test suite

**Acceptance Criteria:**
- Clear ownership of each agent module
- No duplicate code
- All tests pass
- Documentation updated

**Dependencies:** None

**Verification:**
```bash
# Verify no duplicate classes
diff -r core/agents core/yappc-agents
# Expected: No duplicate files
```

---

### Task 2.7: Delete Duplicate Web App
**Priority:** P2 - Medium  
**Estimated Time:** 1 day  
**Directory to Delete:**
- `frontend/apps/web/`

**Implementation Steps:**
1. Verify frontend/web/ is the canonical web app
2. Check for any unique code in frontend/apps/web/
3. If unique code exists, migrate to frontend/web/
4. Update CI configurations to remove frontend/apps/web/
5. Update workspace configuration
6. Delete frontend/apps/web/ directory
7. Update documentation
8. Run full test suite

**Acceptance Criteria:**
- frontend/apps/web/ deleted
- No unique code lost
- CI updated
- All tests pass

**Dependencies:** None

**Verification:**
```bash
ls frontend/apps/web/
# Expected: No such directory
```

---

### Task 2.8: Decompose Canvas Route Further
**Priority:** P2 - Medium  
**Estimated Time:** 3 days  
**Files to Modify:**
- `frontend/web/src/routes/app/project/canvas.tsx`

**Implementation Steps:**
1. Audit current canvas.tsx (828 lines)
2. Identify logical sections:
   - Canvas orchestration
   - Event handlers
   - Panel management
   - Toolbar management
3. Extract sections into separate modules:
   - `canvas/CanvasOrchestrator.tsx` - Main orchestration
   - `canvas/CanvasEventHandler.ts` - Event handling logic
   - `canvas/CanvasPanelManager.tsx` - Panel management
   - `canvas/CanvasToolbarManager.tsx` - Toolbar management
4. Target: <200 lines per module
5. Update imports
6. Run tests
7. Verify no functionality lost

**Acceptance Criteria:**
- canvas.tsx <200 lines
- Each extracted module <200 lines
- All tests pass
- No functionality lost

**Dependencies:** None

**Verification:**
```bash
wc -l frontend/web/src/routes/app/project/canvas.tsx
# Expected: <200 lines
```

---

## Phase 3: Efficiency + Maintainability + Competitive Differentiation (6-8 weeks)

### Task 3.1: Add Caching for Frequently Accessed Data
**Priority:** P2 - Medium  
**Estimated Time:** 4 days  
**Files to Create:**
- `infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/cache/EntityCacheManager.java`

**Files to Modify:**
- `infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/adapter/YappcDataCloudRepository.java`

**Implementation Steps:**
1. Create EntityCacheManager with Redis backing
2. Add cache for:
   - Projects (by ID)
   - Users (by ID)
   - Tenants (by ID)
3. Add TTL (5 minutes for most, 1 hour for static data)
4. Add cache invalidation on write operations
5. Add metrics for cache hit/miss rates
6. Wire into YappcDataCloudRepository
7. Add unit tests for cache logic
8. Add integration tests with Redis

**Acceptance Criteria:**
- Frequently accessed entities cached
- Cache invalidated on writes
- Metrics emitted for hit/miss rates
- All tests pass

**Dependencies:** Task 1.1 (Redis collaboration)

**Verification:**
```bash
# Check metrics for cache hit rate
# Expected: >80% hit rate for frequent queries
```

---

### Task 3.2: Add Performance Benchmarks in Production Context
**Priority:** P2 - Medium  
**Estimated Time:** 3 days  
**Files to Create:**
- `infrastructure/datacloud/src/jmh/java/.../benchmarks/ProductionDataCloudBenchmark.java`
- `core/services-lifecycle/src/jmh/java/.../benchmarks/ProductionAuthBenchmark.java`

**Implementation Steps:**
1. Create production-like benchmark for Data Cloud queries:
   - Use realistic data volumes
   - Test with multiple tenants
   - Test with concurrent users
2. Create production-like benchmark for auth operations:
   - Test login latency
   - Test token validation latency
   - Test token refresh latency
3. Configure JMH for production context:
   - Warmup iterations
   - Measurement iterations
   - Forks
   - Heap size
4. Add benchmark targets in documentation
5. Run benchmarks and establish baselines
6. Add CI job to run benchmarks periodically

**Acceptance Criteria:**
- Production-like benchmarks created
- Baseline performance metrics established
- CI job configured
- Documentation updated

**Dependencies:** None

**Verification:**
```bash
./gradlew :products:yappc:jmh
# Expected: Benchmarks run successfully
```

---

### Task 3.3: Implement IDE Extensions
**Priority:** P2 - Medium  
**Estimated Time:** 10 days  
**Files to Create:**
- `ide/vscode-yappc/` - VSCode extension
- `ide/jetbrains-yappc/` - JetBrains plugin

**Implementation Steps:**
1. Create VSCode extension:
   - Project creation command
   - Canvas view integration
   - AI suggestions inline
   - Status bar integration
2. Create JetBrains plugin:
   - Project creation action
   - Canvas tool window
   - AI suggestions inline
   - Status bar widget
3. Add authentication to both extensions
4. Add configuration for extension settings
5. Add error handling
6. Add documentation for extension usage
7. Publish to marketplaces (optional)

**Acceptance Criteria:**
- VSCode extension functional
- JetBrains plugin functional
- Both extensions authenticate
- Documentation complete
- Extensions tested

**Dependencies:** None

**Verification:**
```bash
# Test VSCode extension in VSCode
# Test JetBrains plugin in IntelliJ
# Expected: Both work correctly
```

---

### Task 3.4: Simplify Deployment
**Priority:** P2 - Medium  
**Estimated Time:** 5 days  
**Files to Create:**
- `deployment/docker-compose-simple.yml`
- `deployment/kind-simple/` - Kind deployment
- `deployment/README_SIMPLE.md`

**Files to Modify:**
- `deployment/docker-compose.yml`
- `deployment/helm/values.yaml`

**Implementation Steps:**
1. Create simplified docker-compose:
   - Single command to start all services
   - Environment variables in .env file
   - No complex networking
2. Create Kind deployment:
   - Simple manifests
   - Single command to deploy
   - No Helm required
3. Update deployment documentation:
   - Quick start guide
   - Simple deployment options
   - Advanced deployment (existing Helm)
4. Add validation scripts
5. Test simple deployment end-to-end

**Acceptance Criteria:**
- Single-command deployment works
- Documentation clear for beginners
- Advanced deployment still available
- All deployment options tested

**Dependencies:** None

**Verification:**
```bash
# Test simple deployment
docker-compose -f deployment/docker-compose-simple.yml up
# Expected: All services start successfully
```

---

### Task 3.5: Add Connection Pooling for Data Cloud
**Priority:** P2 - Medium  
**Estimated Time:** 2 days  
**Files to Modify:**
- `infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/client/PooledDataCloudClient.java`

**Implementation Steps:**
1. Create PooledDataCloudClient wrapper
2. Configure connection pool:
   - Max connections: 100
   - Min idle: 10
   - Timeout: 30 seconds
3. Add metrics for pool utilization
4. Wire into YappcDataCloudRepository
5. Add unit tests for pool behavior
6. Add integration tests with concurrent load

**Acceptance Criteria:**
- Connection pool configured
- Metrics emitted for pool utilization
- All tests pass
- No connection exhaustion under load

**Dependencies:** Task 1.2 (adapter migration)

**Verification:**
```bash
# Test with concurrent load
# Verify pool metrics
# Expected: No connection exhaustion
```

---

### Task 3.6: Add Rate Limiting to Auth Endpoints
**Priority:** P2 - Medium  
**Estimated Time:** 2 days  
**Files to Create:**
- `frontend/apps/api/src/middleware/RateLimitMiddleware.ts` (enhance existing)

**Files to Modify:**
- `frontend/apps/api/src/routes/auth.ts`

**Implementation Steps:**
1. Enhance RateLimitMiddleware for auth endpoints:
   - Stricter limits for login (5 per minute per IP)
   - Stricter limits for register (3 per hour per IP)
   - Use Redis for distributed rate limiting
2. Apply to auth endpoints
3. Add metrics for rate limit violations
4. Add unit tests for rate limiting
5. Add integration tests

**Acceptance Criteria:**
- Auth endpoints rate limited
- Distributed rate limiting with Redis
- Metrics emitted for violations
- All tests pass

**Dependencies:** Task 1.1 (Redis collaboration)

**Verification:**
```bash
# Test rate limiting
curl -X POST http://localhost:8082/auth/login [repeat 6 times]
# Expected: 5th request rate limited
```

---

### Task 3.7: Optimize Canvas Rendering
**Priority:** P2 - Medium  
**Estimated Time:** 4 days  
**Files to Modify:**
- `frontend/web/src/routes/app/project/canvas.tsx`
- `frontend/web/src/routes/app/project/canvas/_canvas/` (various modules)

**Implementation Steps:**
1. Add React.memo to canvas components
2. Add useCallback for event handlers
3. Add useMemo for computed values
4. Add debouncing for real-time updates (100ms)
5. Add virtualization for large node lists
6. Profile rendering performance
7. Optimize hot paths
8. Add performance metrics

**Acceptance Criteria:**
- Canvas renders <16ms (60fps)
- No unnecessary re-renders
- Real-time updates debounced
- Large canvases virtualized
- Performance metrics added

**Dependencies:** Task 2.8 (canvas decomposition)

**Verification:**
```bash
# Profile canvas with React DevTools
# Expected: <16ms render time
```

---

## Task Dependencies Graph

```
Phase 0:
├── Task 0.1 (default-tenant)
├── Task 0.2 (query operators) → depends on 0.1
├── Task 0.3 (auth endpoints)
└── Task 0.4 (documentation)

Phase 1:
├── Task 1.1 (Redis collaboration)
├── Task 1.2 (adapter migration) → depends on 0.1, 0.2
├── Task 1.3 (retry) → depends on 1.2
├── Task 1.4 (circuit breaker) → depends on 1.3
├── Task 1.5 (health check)
└── Task 1.6 (integration tests) → depends on 0.1, 1.2

Phase 2:
├── Task 2.1 (placeholder handlers)
├── Task 2.2 (TODO cleanup)
├── Task 2.3 (placeholder implementations) → depends on 2.2
├── Task 2.4 (replace mocks)
├── Task 2.5 (E2E tests) → depends on 2.1
├── Task 2.6 (agent consolidation)
├── Task 2.7 (delete duplicate web app)
└── Task 2.8 (canvas decomposition)

Phase 3:
├── Task 3.1 (caching) → depends on 1.1
├── Task 3.2 (performance benchmarks)
├── Task 3.3 (IDE extensions)
├── Task 3.4 (simplify deployment)
├── Task 3.5 (connection pooling) → depends on 1.2
├── Task 3.6 (rate limiting) → depends on 1.1
└── Task 3.7 (canvas optimization) → depends on 2.8
```

---

## Milestone Checkpoints

### Milestone 1: Phase 0 Complete (Week 2)
**Definition:** All critical blockers resolved
**Verification:**
- No default-tenant fallbacks
- No unsupported query operators
- No non-functional auth endpoints
- Documentation aligned with port 8082

**Go/No-Go Criteria:**
- All Phase 0 tasks complete
- All tests pass
- No new critical bugs introduced

---

### Milestone 2: Phase 1 Complete (Week 6)
**Definition:** System is production-hardened
**Verification:**
- Redis collaboration implemented
- All DataCloudClient usage migrated to adapter
- Retry and circuit breaker implemented
- Health checks added
- Integration tests passing

**Go/No-Go Criteria:**
- All Phase 1 tasks complete
- Integration tests pass
- System can handle production load

---

### Milestone 3: Phase 2 Complete (Week 12)
**Definition:** Features are complete and tested
**Verification:**
- No placeholder implementations
- No TODO/FIXME without issue references
- E2E tests passing
- Duplicate code consolidated
- Canvas decomposed

**Go/No-Go Criteria:**
- All Phase 2 tasks complete
- E2E tests passing
- Feature completeness verified

---

### Milestone 4: Phase 3 Complete (Week 20)
**Definition:** System is competitive and efficient
**Verification:**
- Caching implemented
- Performance benchmarks established
- IDE extensions functional
- Deployment simplified
- Canvas optimized

**Go/No-Go Criteria:**
- All Phase 3 tasks complete
- Performance targets met
- Competitive features implemented

---

## Risk Mitigation

### Risk 1: Data Cloud Operator Support Not Available
**Impact:** High - query methods cannot be implemented  
**Mitigation:** Remove query methods that use operators; document limitation; provide alternative query patterns

### Risk 2: Redis Collaboration Complexity
**Impact:** Medium - may take longer than estimated  
**Mitigation:** Start with simple implementation; add pub/sub later; fallback to in-memory if Redis unavailable

### Risk 3: Adapter Migration Breaks Existing Code
**Impact:** High - regressions possible  
**Mitigation:** Comprehensive integration tests; feature flags for gradual rollout; rollback plan

### Risk 4: E2E Test Flakiness
**Impact:** Medium - unreliable tests  
**Mitigation:** Use stable test data; add retries for transient failures; run tests in isolation

### Risk 5: IDE Extension Complexity
**Impact:** Low - nice-to-have feature  
**Mitigation:** Start with basic functionality; iterate based on user feedback; can defer if needed

---

## Success Metrics

### Phase 0 Success Metrics
- Zero critical security vulnerabilities
- Zero critical bugs
- Documentation 100% aligned with implementation
- All tests passing

### Phase 1 Success Metrics
- 99.9% uptime for Data Cloud operations (retry + circuit breaker)
- <100ms p95 latency for Data Cloud queries
- Health check latency <50ms
- Integration test coverage >80% for critical paths

### Phase 2 Success Metrics
- Zero placeholder implementations in production code
- E2E test coverage >70% for critical workflows
- Code duplication <5%
- Average module size <300 lines

### Phase 3 Success Metrics
- Cache hit rate >80% for frequent queries
- Canvas render time <16ms (60fps)
- IDE extension adoption >10% of users
- Deployment time <5 minutes

---

## Resources Required

### Development Resources
- 2 senior backend engineers (Java)
- 1 senior frontend engineer (TypeScript/React)
- 1 DevOps engineer (deployment, infrastructure)

### Infrastructure Resources
- Test environment with PostgreSQL and Redis
- CI/CD pipeline capacity for integration tests
- Performance testing environment

### Tools
- Testcontainers for integration tests
- Playwright for E2E tests
- JMH for performance benchmarks
- Redis for caching and collaboration

---

## Communication Plan

### Weekly Status Updates
- Progress report on current phase
- Blockers and risks
- Upcoming tasks

### Milestone Reviews
- Go/No-Go decision at each milestone
- Demo of completed features
- Retrospective on completed phase

### Stakeholder Updates
- Bi-weekly summary for stakeholders
- Major milestone announcements
- Risk escalation when needed

---

## Appendix: File Inventory

### Phase 0 Files
- `core/services-lifecycle/src/main/java/com/ghatana/yappc/services/security/YappcApiSecurity.java`
- `core/services-lifecycle/src/main/java/com/ghatana/yappc/services/security/JwtAuthController.java`
- `core/yappc-services/src/main/java/com/ghatana/yappc/api/YappcApiAuthFilter.java`
- `core/yappc-services/src/main/java/com/ghatana/yappc/storage/DataCloudArtifactStore.java`
- `infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/repository/AgentStateRepository.java`
- `core/ai/src/main/java/com/ghatana/yappc/ai/history/ConversationRepository.java`
- `core/ai/src/main/java/com/ghatana/yappc/ai/prompts/PromptVersioningService.java`
- `infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/repository/ProjectRepository.java`
- `infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/repository/TaskRepository.java`
- `infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/repository/PhaseStateRepository.java`
- `frontend/apps/api/src/routes/auth.ts`
- `frontend/apps/api/src/services/auth/proxy-auth.service.ts`
- `docs/development/RUN_DEV_GUIDE.md`
- `docs/guides/getting-started.md`
- `docs/deployment/README_DOCKER.md`
- `README.md`

### Phase 1 Files
- `frontend/apps/api/src/services/RealTimeServiceRedis.ts` (new)
- `frontend/apps/api/src/services/redis/RedisRoomManager.ts` (new)
- `frontend/apps/api/src/services/redis/RedisPresenceManager.ts` (new)
- `infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/client/RetryableDataCloudClient.java` (new)
- `infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/client/CircuitBreakerDataCloudClient.java` (new)
- `infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/health/DataCloudHealthCheck.java` (new)
- `infrastructure/datacloud/src/integrationTest/java/com/ghatana/yappc/infrastructure/datacloud/DataCloudIntegrationTest.java` (new)
- `core/services-lifecycle/src/integrationTest/java/com/ghatana/yappc/services/lifecycle/AuthIntegrationTest.java` (new)
- `core/yappc-services/src/integrationTest/java/com/ghatana/yappc/storage/ArtifactStoreIntegrationTest.java` (new)

### Phase 2 Files
- `frontend/web/src/canvas/phase-actions.ts`
- `core/ai/src/main/java/com/ghatana/yappc/ai/canvas/CanvasGenerationService.java`
- `core/agents/workflow/src/main/java/com/ghatana/yappc/agent/ops/CanaryStep.java`
- `core/ai/src/main/java/com/ghatana/yappc/ai/requirements/ai/feedback/FeedbackLearningService.java`
- `core/scaffold/engine/src/main/java/com/ghatana/yappc/core/security/SecurityReviewFramework.java`
- `core/refactorer/api/src/main/java/com/ghatana/refactorer/orchestrator/PolyfixOrchestrator.java`
- `knowledge/retrieval/src/main/java/com/ghatana/yappc/knowledge/retrieval/YappcDenseVectorRetriever.java`
- `core/refactorer/api/src/main/java/com/ghatana/refactorer/orchestrator/report/ReportGenerator.java`
- `core/refactorer/api/src/main/java/com/ghatana/refactorer/orchestrator/DebugPlanner.java`
- `core/yappc-agents/src/main/java/com/ghatana/yappc/agent/eval/AgentEvalCli.java`
- `core/yappc-services/src/main/java/com/ghatana/yappc/services/shape/ShapeServiceImpl.java`
- `frontend/web/src/__mocks__/` (multiple files)
- `frontend/web/e2e/auth.spec.ts` (new)
- `frontend/web/e2e/project-creation.spec.ts` (new)
- `frontend/web/e2e/canvas-operations.spec.ts` (new)
- `frontend/web/e2e/phase-transitions.spec.ts` (new)
- `frontend/web/src/routes/app/project/canvas.tsx`
- `frontend/apps/web/` (directory to delete)

### Phase 3 Files
- `infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/cache/EntityCacheManager.java` (new)
- `infrastructure/datacloud/src/jmh/java/.../benchmarks/ProductionDataCloudBenchmark.java` (new)
- `core/services-lifecycle/src/jmh/java/.../benchmarks/ProductionAuthBenchmark.java` (new)
- `ide/vscode-yappc/` (new directory)
- `ide/jetbrains-yappc/` (new directory)
- `deployment/docker-compose-simple.yml` (new)
- `deployment/kind-simple/` (new directory)
- `deployment/README_SIMPLE.md` (new)
- `infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/client/PooledDataCloudClient.java` (new)
- `frontend/apps/api/src/middleware/RateLimitMiddleware.ts`

---

**Document Status:** Ready for Implementation  
**Next Steps:** Begin Phase 0, Task 0.1 (Remove default-tenant Fallbacks)  
**Owner:** Engineering Team  
**Review Date:** 2026-04-22 (after Phase 0 completion)
