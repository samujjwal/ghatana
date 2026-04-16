# platform:java:domain

Canonical packages: `com.ghatana.platform.domain.*`, `com.ghatana.platform.schema.*`

## Purpose

`platform:java:domain` provides shared domain models and schema-oriented abstractions that multiple platform modules depend on when exchanging structured data, events, and registry-style metadata.

## Dependencies

- `platform:java:core` for foundational platform types
- `platform:contracts` for shared cross-module contracts
- Jackson bundles for JSON and YAML serialization
- ActiveJ promise support for async schema-related flows

## Usage

Add the module as a dependency:

```kotlin
dependencies {
    implementation(project(":platform:java:domain"))
}
```

Consume the shared domain and schema models from product or platform modules instead of redefining duplicate record and schema types locally.

## Public API Surface

- Shared domain types under `com.ghatana.platform.domain.*`
- Schema-oriented abstractions under `com.ghatana.platform.schema.*`
- Serialization-friendly shared models used by higher-level platform modules