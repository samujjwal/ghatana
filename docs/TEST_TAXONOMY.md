# Ghatana Test Taxonomy

## Overview

This document defines the test taxonomy for the Ghatana codebase. It specifies the standard tags, folder structure, and categorization for all tests across the platform.

## Test Categories (by Type)

### Unit Tests (`unit`)

**Purpose**: Test individual functions, classes, or components in isolation with mocks/stubs.

**Characteristics**:
- Fast execution (milliseconds)
- No external dependencies (databases, APIs, file system)
- Test single behavior or logic path
- Use mocks/stubs for external collaborators

**Tags**: `@Tag("unit")`

**Folder Structure**:
```
src/test/java/com/ghatana/{module}/{component}/unit/
src/test/java/com/ghatana/{module}/{component}/
```

**Examples**:
- `platform/java/core/src/test/java/com/ghatana/core/validation/ValidatorTest.java`
- `platform/typescript/ui-builder/src/core/__tests__/document.test.ts`

### Integration Tests (`integration`)

**Purpose**: Test interactions between multiple components or with external services (real or testcontainers).

**Characteristics**:
- Medium execution time (seconds)
- Use real database (via Testcontainers), message broker, or HTTP client
- Test component boundaries and integration points
- Validate data flow across components

**Tags**: `@Tag("integration")`

**Folder Structure**:
```
src/test/java/com/ghatana/{module}/{component}/integration/
src/test/java/com/ghatana/{module}/integration/
```

**Examples**:
- `shared-services/auth-gateway/src/test/java/com/ghatana/auth/gateway/integration/AuthGatewayIntegrationTest.java`
- `platform/java/messaging/src/test/java/com/ghatana/platform/messaging/integration/KafkaConnectorIntegrationTest.java`

### Contract Tests (`contract`)

**Purpose**: Validate that APIs conform to their OpenAPI/GraphQL specifications.

**Characteristics**:
- Test request/response schema compliance
- Validate error responses match spec
- Test authentication requirements
- Use MockMvc or HTTP client against API

**Tags**: `@Tag("contract")`

**Folder Structure**:
```
src/test/java/com/ghatana/{service}/contract/
src/test/java/com/ghatana/{module}/contract/
```

**Examples**:
- `shared-services/auth-gateway/src/test/java/com/ghatana/auth/gateway/contract/AuthGatewayContractTest.java`
- `products/aep/aep-api/src/test/java/com/ghatana/aep/contract/AepApiContractTest.java`

### E2E Tests (`e2e`)

**Purpose**: Test complete user workflows across multiple services or full application lifecycle.

**Characteristics**:
- Longer execution time (minutes)
- Test complete business workflows
- Use real service instances or comprehensive mocks
- Test cross-service interactions

**Tags**: `@Tag("e2e")`

**Folder Structure**:
```
e2e-tests/src/test/java/com/ghatana/{product}/e2e/
src/test/java/com/ghatana/{service}/e2e/
```

**Examples**:
- `products/yappc/e2e-tests/src/test/java/com/ghatana/yappc/e2e/FullWorkflowE2ETest.java`
- `shared-services/auth-gateway/src/test/java/com/ghatana/auth/gateway/e2e/AuthGatewayE2ETest.java`

## Test Categories (by Layer)

### Platform Tests (`platform`)

Tests for shared platform modules (contracts, core libraries, utilities).

**Tags**: `@Tag("platform")`

**Folder**: `platform/{java|typescript}/{module}/src/test/`

### Product Tests (`product`)

Tests for product-specific services and applications.

**Tags**: `@Tag("product")`

**Folder**: `products/{product}/*/src/test/`

### Shared Services Tests (`shared-services`)

Tests for cross-cutting shared services (auth-gateway, ai-inference-service, etc.).

**Tags**: `@Tag("shared-services")`

**Folder**: `shared-services/{service}/src/test/`

## Test Categories (by Feature/Area)

### Authentication Tests (`authentication`)

Tests related to authentication flows, token management, and authorization.

**Tags**: `@Tag("authentication")`

### Database Tests (`database`)

Tests related to database operations, persistence, and data integrity.

**Tags**: `@Tag("database")`

### AI/ML Tests (`ai`)

Tests related to AI inference, model registry, and agent workflows.

**Tags**: `@Tag("ai")`

### Workflow Tests (`workflow`)

Tests related to workflow orchestration, phase execution, and state management.

**Tags**: `@Tag("workflow")`

### API Tests (`api`)

Tests related to HTTP endpoints, REST/GraphQL APIs, and API contracts.

**Tags**: `@Tag("api")`

### UI Tests (`ui`)

Tests related to UI components, rendering, and user interactions.

**Tags**: `@Tag("ui")`

## Standard Test Tag Combinations

### Java (JUnit 5)

```java
@Tag("unit")
@Tag("platform")
@DisplayName("ComponentName unit tests")
class ComponentNameTest {
    // tests
}

@Tag("integration")
@Tag("database")
@Tag("shared-services")
@DisplayName("ServiceName integration tests")
class ServiceNameIntegrationTest {
    // tests
}

@Tag("contract")
@Tag("api")
@Tag("product")
@DisplayName("API contract tests")
class ApiContractTest {
    // tests
}

@Tag("e2e")
@Tag("workflow")
@Tag("product")
@DisplayName("Full workflow E2E tests")
class FullWorkflowE2ETest {
    // tests
}
```

### TypeScript (Vitest)

```typescript
describe('ComponentName unit tests', () => {
  // tests
});

describe('ServiceName integration tests', () => {
  // tests
});
```

## Folder Structure Standard

### Java Projects

```
src/test/java/com/ghatana/{module}/
├── unit/                    # Unit tests
│   ├── ComponentTest.java
│   └── ServiceTest.java
├── integration/             # Integration tests
│   ├── DatabaseIntegrationTest.java
│   └── ServiceIntegrationTest.java
├── contract/                # Contract tests
│   └── ApiContractTest.java
└── e2e/                     # E2E tests
    └── WorkflowE2ETest.java
```

### TypeScript Projects

```
src/
├── __tests__/               # Unit tests
│   ├── component.test.ts
│   └── service.test.ts
├── integration/             # Integration tests
│   └── database.test.ts
└── e2e/                     # E2E tests
    └── workflow.test.ts
```

## Test Naming Conventions

### Java

- Unit tests: `{ComponentName}Test.java`
- Integration tests: `{ComponentName}IntegrationTest.java`
- Contract tests: `{ServiceName}ContractTest.java`
- E2E tests: `{WorkflowName}E2ETest.java`

### TypeScript

- Unit tests: `{component}.test.ts`
- Integration tests: `{feature}.integration.test.ts`
- E2E tests: `{workflow}.e2e.test.ts`

## Test Configuration

### JUnit 5 (Java)

Add to `build.gradle.kts`:
```kotlin
tasks.test {
    useJUnitPlatform {
        includeTags("unit")
    }
}

// Create separate tasks for each test type
tasks.register<Test>("unitTests") {
    useJUnitPlatform {
        includeTags("unit")
    }
}

tasks.register<Test>("integrationTests") {
    useJUnitPlatform {
        includeTags("integration")
    }
}

tasks.register<Test>("contractTests") {
    useJUnitPlatform {
        includeTags("contract")
    }
}

tasks.register<Test>("e2eTests") {
    useJUnitPlatform {
        includeTags("e2e")
    }
}
```

### Vitest (TypeScript)

Add to `vitest.config.ts`:
```typescript
export default defineConfig({
  test: {
    include: ['**/*.{test,spec}.{js,mjs,cjs,ts,mts,cts,jsx,tsx}'],
    exclude: ['node_modules', 'dist', 'e2e'],
    testTimeout: 10000,
    // Create separate test suites
    suite: {
      unit: {
        include: ['src/**/__tests__/**/*.test.ts'],
      },
      integration: {
        include: ['src/**/integration/**/*.test.ts'],
      },
      e2e: {
        include: ['e2e/**/*.test.ts'],
      },
    },
  },
})
```

## Running Tests by Category

### Java

```bash
# Run all unit tests
./gradlew unitTests

# Run all integration tests
./gradlew integrationTests

# Run all contract tests
./gradlew contractTests

# Run all e2e tests
./gradlew e2eTests

# Run tests by tag
./gradlew test --tests "*ContractTest"
```

### TypeScript

```bash
# Run unit tests
pnpm test

# Run integration tests
pnpm test:unit

# Run e2e tests
pnpm test:e2e

# Run tests by pattern
pnpm test -- pattern.test.ts
```

## CI/CD Integration

### Test Matrix

Use test taxonomy to create efficient CI matrices:

```yaml
test-matrix:
  unit-tests:
    tags: [unit]
    timeout: 5m
  integration-tests:
    tags: [integration, database]
    timeout: 15m
    services: [postgres, redis]
  contract-tests:
    tags: [contract, api]
    timeout: 10m
  e2e-tests:
    tags: [e2e]
    timeout: 30m
    services: [postgres, redis, kafka]
```

## Migration Guide

To migrate existing tests to the new taxonomy:

1. **Identify test type**: Determine if the test is unit, integration, contract, or e2e
2. **Add appropriate tags**: Add `@Tag("type")` annotation
3. **Move to correct folder**: Move test file to appropriate folder
4. **Rename if needed**: Rename to follow naming convention
5. **Update build config**: Add test task configuration if needed

## Compliance

All new tests must:
- Use appropriate test type tag
- Follow folder structure
- Follow naming convention
- Include `@DisplayName` annotation (Java)
- Include descriptive test names

## References

- [JUnit 5 Tagging](https://junit.org/junit5/docs/current/user-guide/#running-tests-tag-expressions)
- [Vitest Configuration](https://vitest.dev/config/)
- [Testing Best Practices](/docs/TESTING.md)
