# platform:java:cache

Canonical package: `com.ghatana.platform.cache.*`

## Purpose

`platform:java:cache` provides distributed caching infrastructure for Java services, including Redis-backed cache integration, serialization support, and shared runtime patterns for cache-enabled platform and product modules.

## Dependencies

- `platform:java:core` for shared runtime types
- `platform:java:observability` for cache metrics and diagnostics
- Lettuce for async Redis access
- Jackson for cache payload serialization

## Usage

Add the module as a dependency:

```kotlin
dependencies {
    implementation(project(":platform:java:cache"))
}
```

Wire cache services at the product or platform boundary with module-specific configuration and observability.

## Public API Surface

- Cache-related types under `com.ghatana.platform.cache.*`
- Shared Redis-backed infrastructure patterns for higher-level modules
- Serialization and observability support for cache-enabled services