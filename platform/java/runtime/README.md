# platform:java:runtime

Canonical package: `com.ghatana.core.*`

## Purpose

`platform:java:runtime` provides the shared ActiveJ-based runtime infrastructure for Java services, including event-loop integration, launcher support, dependency injection, and async execution foundations used across platform and product modules.

## Dependencies

- `platform:java:core` for foundational platform types
- ActiveJ core, promise, inject, launcher, HTTP, and service graph modules
- Shared testing bundles exposed for runtime-oriented support code that lives in main sources

## Usage

Add the module as a dependency:

```kotlin
dependencies {
    implementation(project(":platform:java:runtime"))
}
```

Use the runtime module when building ActiveJ-based services or shared runtime utilities that need event-loop and launcher integration.

## Public API Surface

- Core runtime support under `com.ghatana.core.*`
- Shared ActiveJ-aligned runtime abstractions for platform and product services
- Async and launcher infrastructure used by higher-level HTTP and service modules