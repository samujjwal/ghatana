# platform:java:testing

Canonical package: `com.ghatana.platform.testing.*`

## Purpose

`platform:java:testing` provides the shared testing infrastructure used by product and platform Java modules, including base test classes, local test servers, fixtures, ActiveJ helpers, contract-test support, and architecture-testing utilities.

## Dependencies

- `platform:java:core`, `platform:java:runtime`, and `platform:java:database`
- JUnit 5, AssertJ, Mockito, Awaitility, and shared testing bundles exposed for consumers
- Internal support from Testcontainers, gRPC, JSONPath, and data-generation utilities

## Usage

Add the module as a test dependency:

```kotlin
dependencies {
    testImplementation(project(":platform:java:testing"))
}
```

Use the shared base classes, fixtures, and helper extensions from this module instead of recreating local testing infrastructure in each product.

## Public API Surface

- Base test classes and extensions under `com.ghatana.platform.testing.*`
- ActiveJ, contract, event-loop, fixture, repository, service, and utility testing packages
- Shared support for boundary and integration testing across Java modules