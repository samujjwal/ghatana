# Test Reclassification Guide

## Overview

This guide helps identify and fix mislabeled integration tests in the Ghatana codebase. Tests should be classified according to the test taxonomy defined in `TEST_TAXONOMY.md`.

## Classification Criteria

### Unit Tests (`unit`)
- Test individual functions, classes, or components in isolation
- Use mocks/stubs for external dependencies
- No external services (databases, APIs, file system)
- Fast execution (milliseconds)
- Test name pattern: `{Component}Test.java` or `component.test.ts`

### Integration Tests (`integration`)
- Test interactions between multiple components
- Use real database (via Testcontainers), message broker, or HTTP client
- Test component boundaries and integration points
- Medium execution time (seconds)
- Test name pattern: `{Component}IntegrationTest.java` or `component.integration.test.ts`

### Contract Tests (`contract`)
- Validate API compliance with OpenAPI/GraphQL specifications
- Test request/response schema compliance
- Use MockMvc or HTTP client against API
- Test name pattern: `{Service}ContractTest.java`

### E2E Tests (`e2e`)
- Test complete user workflows across multiple services
- Test full application lifecycle
- Longer execution time (minutes)
- Test name pattern: `{Workflow}E2ETest.java` or `workflow.e2e.test.ts`

## Common Misclassifications

### 1. Unit Tests Labeled as Integration Tests

**Symptoms**:
- Test uses mocks for all dependencies
- No external service connections
- Fast execution
- Located in `integration/` folder but doesn't integrate with external systems

**Example**:
```java
// MISLABELED as integration test
@Tag("integration")
class UserServiceIntegrationTest {
    @Mock UserRepository repository;
    @Mock EmailService emailService;
    
    @Test
    void shouldCreateUser() {
        // All dependencies are mocked - this is a unit test
    }
}
```

**Fix**: Remove `@Tag("integration")`, add `@Tag("unit")`, move to appropriate folder.

### 2. Integration Tests Labeled as Unit Tests

**Symptoms**:
- Test uses real database (Testcontainers)
- Test uses real HTTP client to call external services
- Test uses real message broker
- Located in standard test folder but uses external resources

**Example**:
```java
// MISLABELED as unit test
class UserServiceTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>();
    
    @Test
    void shouldPersistUser() {
        // Uses real database - this is an integration test
    }
}
```

**Fix**: Add `@Tag("integration")`, move to `integration/` folder.

### 3. E2E Tests Labeled as Integration Tests

**Symptoms**:
- Test spans multiple services
- Test completes full business workflow
- Test takes minutes to execute
- Tests cross-service interactions

**Example**:
```java
// MISLABELED as integration test
@Tag("integration")
class AuthWorkflowIntegrationTest {
    @Test
    void shouldCompleteFullAuthFlow() {
        // Tests login → token validation → resource access → logout
        // This is an E2E test
    }
}
```

**Fix**: Add `@Tag("e2e")`, rename to `{Workflow}E2ETest.java`, move to `e2e/` folder.

## Reclassification Process

### Step 1: Identify Potential Misclassifications

Use the provided script to scan for potential misclassifications:

```bash
./scripts/scan-test-classifications.sh
```

This script will:
- Find all test files with `@Tag("integration")`
- Check if they use Testcontainers (PostgreSQL, Redis, Kafka)
- Check if they use real HTTP clients
- Flag potential misclassifications

### Step 2: Manual Review

Review flagged tests and determine correct classification based on criteria above.

### Step 3: Apply Fixes

For each misclassified test:

1. **Update tags**:
   ```java
   // Change from
   @Tag("integration")
   // To
   @Tag("unit")
   ```

2. **Move file to correct folder**:
   ```bash
   # Move from integration/ to unit/
   mv src/test/java/com/ghatana/service/integration/ServiceTest.java \
      src/test/java/com/ghatana/service/unit/ServiceTest.java
   ```

3. **Rename file if needed**:
   ```bash
   # Rename to follow naming convention
   mv ServiceTest.java ServiceIntegrationTest.java
   ```

### Step 4: Verify

Run tests to ensure they still pass after reclassification:

```bash
./scripts/test-tiered.sh unit
./scripts/test-tiered.sh integration
./scripts/test-tiered.sh e2e
```

## Quick Reference

| Current State | Should Be | Action |
|--------------|-----------|--------|
| `integration/` folder, all mocks, no external deps | `unit/` folder | Move to unit/, add `@Tag("unit")` |
| Standard folder, uses Testcontainers | `integration/` folder | Move to integration/, add `@Tag("integration")` |
| `integration/` folder, spans multiple services | `e2e/` folder | Move to e2e/, add `@Tag("e2e")` |
| Missing tags entirely | Add appropriate tags | Add `@Tag("unit/integration/contract/e2e")` |

## Verification Checklist

After reclassification, verify:

- [ ] Test file is in correct folder
- [ ] Test has correct `@Tag` annotation
- [ ] Test name follows naming convention
- [ ] Test still passes
- [ ] Test runs in correct tier (unit/integration/e2e)

## Automation Script

The `scripts/scan-test-classifications.sh` script automates the detection of potential misclassifications. Run it regularly to maintain test classification hygiene.

## References

- [Test Taxonomy](/docs/TEST_TAXONOMY.md)
- [Testing Best Practices](/docs/TESTING.md)
- [JUnit 5 Tagging](https://junit.org/junit5/docs/current/user-guide/#running-tests-tag-expressions)
