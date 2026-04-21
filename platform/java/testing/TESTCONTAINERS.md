# Testcontainers Utilities

## Overview

The platform provides reusable Testcontainers utilities for integration testing. These utilities are located in `platform/java/testing/src/main/java/com/ghatana/platform/testing/internal/containers/` as internal implementation details.

## Available Utilities

### PostgreSQL

The platform provides an internal PostgreSQL container utility with singleton lifecycle management:

```java
import com.ghatana.platform.testing.internal.containers.PostgresTestContainer;

class MyIntegrationTest {
    @BeforeAll
    static void setupContainer() {
        PostgresTestContainer.start();
    }

    @AfterAll
    static void teardownContainer() {
        PostgresTestContainer.stop();
    }

    @Test
    void testWithDatabase() {
        String jdbcUrl = PostgresTestContainer.getJdbcUrl();
        String username = PostgresTestContainer.getUsername();
        String password = PostgresTestContainer.getPassword();
        // Use jdbcUrl to configure your data source
    }
}
```

## Adding New Container Utilities

When adding a new container utility:

1. Create a new class in `com.ghatana.platform.testing.internal.containers`
2. Follow the pattern of `PostgresTestContainer` with singleton lifecycle management
3. Keep third-party dependencies (org.testcontainers.*) internal to the implementation
4. Add the Testcontainers dependency to `platform/java/testing/build.gradle.kts` if needed
5. Document the usage here

## Dependencies

The testing module includes the following Testcontainers dependencies:
- `org.testcontainers:testcontainers`
- `org.testcontainers:junit-jupiter`
- `org.testcontainers:postgresql`
- `org.testcontainers:kafka`
- `org.testcontainers:mongodb`

These are marked as `implementation` (internal) to avoid polluting consumer dependency trees. Products that need specific Testcontainers modules should declare their own dependencies.

## Usage Pattern

1. Add the testing module as a test dependency:
```kotlin
testImplementation(project(":platform:java:testing"))
```

2. Use the internal utility classes in your integration tests

3. Manage container lifecycle with @BeforeAll/@AfterAll or test setup/teardown

## Current Status

As of the platform coverage audit (P3-27), Testcontainers utilities are available via the testing module as internal implementation details. The PostgreSQL utility is provided as a reference implementation with singleton lifecycle management. Additional container utilities (Kafka, MongoDB, etc.) can be added following the same pattern when needed.
