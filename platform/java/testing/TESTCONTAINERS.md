# Testcontainers Utilities

## Overview

The platform provides reusable Testcontainers utilities for integration testing. These utilities are located in `platform/java/testing/src/main/java/com/ghatana/platform/testing/testcontainers/`.

## Available Utilities

### PostgreSQL

```java
import com.ghatana.platform.testing.testcontainers.PostgresTestContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MyIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = PostgresTestContainer.create();

    @Test
    void testWithDatabase() {
        String jdbcUrl = PostgresTestContainer.getJdbcUrl(postgres);
        // Use jdbcUrl to configure your data source
    }
}
```

### Custom Database Name

```java
@Container
static PostgreSQLContainer<?> postgres = PostgresTestContainer.create("customdb");
```

## Adding New Container Utilities

When adding a new container utility:

1. Create a new class in `com.ghatana.platform.testing.testcontainers`
2. Follow the pattern of `PostgresTestContainer`
3. Use static factory methods for configuration
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

2. Use the utility classes in your integration tests

3. Annotate your test class with `@Testcontainers` to manage container lifecycle

## Current Status

As of the platform coverage audit (P3-27), Testcontainers utilities are available via the testing module. The PostgreSQL utility is provided as a reference implementation. Additional container utilities (Kafka, MongoDB, etc.) can be added following the same pattern when needed.
