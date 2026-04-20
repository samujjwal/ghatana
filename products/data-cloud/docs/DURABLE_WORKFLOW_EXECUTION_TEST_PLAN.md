# Durable Workflow Execution Test Plan

**Task:** DC-P1-4: Prove durable workflow execution, recovery, and tenant isolation against real providers
**Priority:** High
**Status:** Implementation Plan

## Current State

- Workflow execution snapshots and logs persist through Data Cloud storage when selected storage is durable
- Single-process workflow plugin execution (not distributed scheduling)
- Integration tests exist but may not fully validate durability with real providers
- Tenant isolation exists at the provider level but needs verification against real providers

## Target State

- Integration tests that prove durable workflow execution with real Data Cloud providers
- Tests that verify workflow recovery after failure
- Tests that prove tenant isolation with real providers
- Tests that verify cross-tenant data leakage prevention
- Evidence documented in CI pipeline

## Test Scenarios

### Scenario 1: Durable Workflow Execution with Real Provider

**Objective:** Verify workflow execution snapshots persist through process restarts when using real Data Cloud storage.

**Setup:**
- Use real Data Cloud provider (e.g., PostgreSQL, MongoDB, or cloud storage)
- Configure launcher with durable storage profile
- Create a test workflow with multiple stages

**Steps:**
1. Start launcher with real Data Cloud provider
2. Execute a workflow via `POST /api/v1/pipelines/:id/execute`
3. Verify execution snapshot is persisted in Data Cloud
4. Stop and restart the launcher
5. Query execution history via `GET /api/v1/pipelines/:id/executions`
6. Verify execution history is preserved across restart

**Expected Results:**
- Execution snapshots persisted in Data Cloud
- Execution history available after restart
- No data loss during restart

### Scenario 2: Workflow Recovery After Failure

**Objective:** Verify workflow can recover from mid-execution failures.

**Setup:**
- Configure launcher with real Data Cloud provider
- Create a workflow with failure injection point

**Steps:**
1. Start workflow execution
2. Simulate failure (kill process, network partition, etc.)
3. Restart launcher
4. Query workflow state
5. Resume or re-execute workflow
6. Verify completion and data integrity

**Expected Results:**
- Workflow state preserved at point of failure
- Recovery mechanism works correctly
- No duplicate execution or data corruption

### Scenario 3: Tenant Isolation with Real Provider

**Objective:** Verify tenant isolation with real Data Cloud provider.

**Setup:**
- Configure launcher with real Data Cloud provider
- Create multiple tenants (tenant-a, tenant-b, tenant-c)

**Steps:**
1. Create entities for tenant-a
2. Create entities for tenant-b
3. Query entities for tenant-a (with X-Tenant-ID: tenant-a)
4. Verify only tenant-a entities returned
5. Attempt to query tenant-b entities with tenant-a credentials
6. Verify access denied
7. Repeat for all tenant combinations

**Expected Results:**
- Complete tenant isolation
- No cross-tenant data access
- Proper access control enforcement

### Scenario 4: Cross-Tenant Data Leakage Prevention

**Objective:** Verify no cross-tenant data leakage in queries, exports, or operations.

**Setup:**
- Multiple tenants with overlapping data patterns
- Real Data Cloud provider

**Steps:**
1. Create similar entities in multiple tenants
2. Perform similarity search across tenants
3. Verify results are tenant-scoped
4. Perform analytics queries across tenants
5. Verify results are tenant-scoped
6. Export data from one tenant
7. Verify no data from other tenants included

**Expected Results:**
- All operations properly scoped to tenant
- No cross-tenant data leakage in any operation
- Proper tenant ID validation in all paths

## Implementation

### Test File Structure

Create: `products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/DurableWorkflowExecutionTest.java`

```java
/**
 * Integration tests for durable workflow execution with real Data Cloud providers.
 *
 * @doc.type class
 * @doc.purpose Verify durable workflow execution, recovery, and tenant isolation
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@DisplayName("Durable Workflow Execution")
class DurableWorkflowExecutionTest extends EventloopTestBase {
    
    @Test
    @DisplayName("workflow execution persists through restart with real provider")
    void workflowExecutionPersistsThroughRestart() {
        // Test implementation
    }
    
    @Test
    @DisplayName("workflow recovers from failure with real provider")
    void workflowRecoversFromFailure() {
        // Test implementation
    }
    
    @Test
    @DisplayName("tenant isolation enforced with real provider")
    void tenantIsolationEnforced() {
        // Test implementation
    }
    
    @Test
    @DisplayName("no cross-tenant data leakage in operations")
    void noCrossTenantDataLeakage() {
        // Test implementation
    }
}
```

### Provider Configuration

Support testing with multiple real providers:
- PostgreSQL (via Docker)
- MongoDB (via Docker)
- Cloud storage (if available)
- Sovereign H2 (file-backed)

### CI Integration

Add to CI pipeline:
- Run durable workflow tests in staging environment
- Use real Data Cloud provider in staging
- Fail build if durability tests fail
- Publish test results as evidence

## Success Criteria

- [ ] Integration test for durable workflow execution with real provider
- [ ] Integration test for workflow recovery after failure
- [ ] Integration test for tenant isolation with real provider
- [ ] Integration test for cross-tenant data leakage prevention
- [ ] Tests added to CI pipeline
- [ ] Test evidence documented
- [ ] Tests pass consistently

## Dependencies

- Real Data Cloud provider configuration in test environment
- Docker compose for provider setup (PostgreSQL, MongoDB)
- Test data cleanup between test runs
- Tenant isolation infrastructure

## Risks and Mitigations

| Risk | Mitigation |
|------|-----------|
| Test environment not representative of production | Use same provider types as production |
| Test data pollution | Proper cleanup between tests |
| Flaky tests due to external dependencies | Retry logic, proper test isolation |
| Long test execution time | Optimize test data, parallel execution where possible |

## Timeline

- Test infrastructure setup: 2-3 days
- Test implementation: 3-4 days
- CI integration: 1 day
- Validation and documentation: 1 day

**Total:** 1-2 weeks

## Evidence Documentation

Document evidence in:
- Test execution reports
- CI pipeline artifacts
- Provider configuration documentation
- README updates with durability evidence
