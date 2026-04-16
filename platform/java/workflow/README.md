# platform:java:workflow

Canonical package: `com.ghatana.platform.workflow.*`

## Purpose

`platform:java:workflow` provides the shared workflow orchestration layer for Java services, including workflow definitions, context/state handling, saga support, orchestration services, and persistence-backed workflow runtime integrations.

## Dependencies

- `platform:java:core`, `platform:java:domain`, `platform:java:observability`, and `platform:java:agent-core`
- ActiveJ promise support for async workflow execution
- Micrometer for workflow metrics
- HikariCP, PostgreSQL, and Flyway for JDBC-backed workflow persistence

## Usage

Add the module as a dependency:

```kotlin
dependencies {
    implementation(project(":platform:java:workflow"))
}
```

Use the shared workflow contracts and orchestration services from this module when products need durable or in-memory workflow execution without redefining workflow primitives locally.

## Public API Surface

- Core workflow abstractions under `com.ghatana.platform.workflow.*`
- Engine, JDBC, operator, pipeline, planning, and runtime support packages
- Shared workflow listeners, stores, and orchestration services