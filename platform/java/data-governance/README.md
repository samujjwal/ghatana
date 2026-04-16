# platform:java:data-governance

Canonical package: `com.ghatana.data.governance.*`

## Purpose

`platform:java:data-governance` provides the shared building blocks for consent enforcement, PII classification, purpose limitation, and data minimization so products can apply consistent governance rules at their persistence and service boundaries.

## Dependencies

- `platform:java:core` for common platform types
- `platform:java:domain` for domain model alignment
- `platform:java:governance` for governance contracts and policy integration
- `platform:java:database` plus PostgreSQL/HikariCP for persistence-backed implementations

## Usage

Add the module as a dependency:

```kotlin
dependencies {
    implementation(project(":platform:java:data-governance"))
}
```

Use the module from product-owned repositories and services that need shared consent, classification, or policy enforcement behavior.

## Public API Surface

- Governance domain types and services under `com.ghatana.data.governance.*`
- Async contracts built on ActiveJ `Promise`
- Shared persistence-aware building blocks for product data governance adapters