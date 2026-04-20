# Test Coverage Improvement Plan

**Task:** COP-TEST-1: Improve test coverage for Data Cloud
**Priority:** Medium
**Status:** Implementation Plan

## Current State

- Coverage thresholds at 0.20 instruction / 0.10 branch (very low)
- platform-launcher already has raised thresholds (0.50 instruction / 0.30 branch)
- Other Data Cloud modules may have low coverage
- Need to identify gaps and write additional tests

## Target State

- Coverage thresholds raised to 0.50 instruction / 0.30 branch across all Data Cloud modules
- Additional unit tests for uncovered code paths
- Additional integration tests for critical flows
- Coverage enforcement in CI
- Coverage trends monitored

## Coverage Audit

### Phase 1: Measure Current Coverage

**Modules to Audit:**
1. platform-launcher - Already verified at 0.50/0.30 ✓
2. platform-entity - Need to measure
3. platform-event - Need to measure
4. platform-analytics - Need to measure
5. platform-plugins - Need to measure
6. platform-governance - Need to measure
7. spi - Need to measure
8. launcher - Need to measure
9. api - Need to measure
10. feature-store-ingest - Need to measure

**Measurement Method:**
```bash
./gradlew :products:data-cloud:platform-entity:test jacocoTestReport
./gradlew :products:data-cloud:platform-event:test jacocoTestReport
# ... repeat for all modules
```

### Phase 2: Identify Coverage Gaps

For each module:
1. Generate JaCoCo coverage report
2. Identify classes with low coverage (< 50%)
3. Identify uncovered branches
4. Prioritize critical paths (auth, data access, API handlers)

### Phase 3: Write Additional Tests

**Unit Test Focus:**
- Error handling paths
- Edge cases and boundary conditions
- Null/empty input handling
- Exception scenarios
- Configuration validation

**Integration Test Focus:**
- API endpoint contracts
- Data persistence and retrieval
- Multi-tenant isolation
- Cross-module integration
- Error recovery

## Implementation Plan

### Step 1: Baseline Measurement

Run coverage for all modules and document baseline:
```bash
./gradlew :products:data-cloud:test jacocoTestReport
```

Document results in coverage baseline report.

### Step 2: Set New Thresholds

Update build.gradle.kts for each module to set new thresholds:
```kotlin
jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = 0.50 // instruction coverage
            }
            limit {
                minimum = 0.30 // branch coverage
            }
        }
    }
}
```

### Step 3: Write Tests for High-Priority Gaps

Prioritization criteria:
1. Security-related code (auth, validation)
2. Data access and persistence
3. API handlers and contracts
4. Error handling and recovery

### Step 4: Add Coverage Enforcement to CI

Update CI workflow to:
- Run tests with JaCoCo
- Fail build if coverage below threshold
- Upload coverage reports
- Track coverage trends over time

### Step 5: Monitor Coverage Trends

Set up coverage monitoring:
- Track coverage over time
- Alert on coverage regressions
- Set coverage improvement targets
- Report coverage metrics in dashboards

## Test Writing Guidelines

### Unit Tests

**When to Write:**
- New business logic
- Complex algorithms
- Data transformations
- Validation logic
- Error handling

**Structure:**
```java
@DisplayName("ClassName")
class ClassNameTest {
    
    @Test
    @DisplayName("should do X when Y")
    void shouldDoXWhenY() {
        // Arrange
        // Act
        // Assert
    }
}
```

### Integration Tests

**When to Write:**
- API contracts
- Database operations
- Cross-module flows
- External service integration

**Structure:**
```java
@DisplayName("Integration: Feature Name")
class FeatureIntegrationTest extends EventloopTestBase {
    
    @Test
    @DisplayName("end-to-end flow should complete successfully")
    void endToEndFlowShouldComplete() {
        // Setup real components
        // Execute flow
        // Verify results
    }
}
```

## Success Criteria

- [ ] Baseline coverage measured for all modules
- [ ] Coverage thresholds raised to 0.50/0.30 for all modules
- [ ] Additional unit tests written for high-priority gaps
- [ ] Additional integration tests written for critical flows
- [ ] Coverage enforcement added to CI pipeline
- [ ] Coverage trends monitored
- [ ] No regressions in coverage

## Timeline

- Phase 1 (Baseline measurement): 1 day
- Phase 2 (Gap identification): 2 days
- Phase 3 (Test writing): 5-10 days (depends on gaps found)
- Phase 4 (CI integration): 1 day
- Phase 5 (Monitoring setup): 1 day

**Total:** 10-15 days (varies based on coverage gaps)

## Dependencies

- Test infrastructure must be in place
- Mocking frameworks for unit tests
- Test data fixtures for integration tests
- CI/CD pipeline access

## Risks and Mitigations

| Risk | Mitigation |
|------|-----------|
| Large coverage gaps require extensive testing | Prioritize critical paths, phase in improvements |
| Tests flaky or unreliable | Invest in test stability, proper isolation |
| Coverage targets unrealistic | Adjust targets based on module complexity |
| Test execution time increases | Optimize test suite, parallel execution |

## Output Artifacts

- Coverage baseline report
- Updated build.gradle.kts files with new thresholds
- New test files
- Updated CI workflow
- Coverage monitoring dashboard
