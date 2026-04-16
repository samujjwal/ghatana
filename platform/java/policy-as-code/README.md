# platform:java:policy-as-code

Canonical package: `com.ghatana.platform.pac.*`

## Purpose

`platform:java:policy-as-code` provides the shared policy evaluation foundation for rule-based governance, including pluggable policy execution, risk scoring support, and persistence-backed policy workflows.

## Dependencies

- `platform:java:core`, `platform:java:domain`, and `platform:java:governance` for shared platform and governance contracts
- `platform:java:database` plus PostgreSQL and HikariCP for persistence-backed policy integrations
- ActiveJ promise support for async evaluation flows
- Jackson for policy input and result materialization

## Usage

Add the module as a dependency:

```kotlin
dependencies {
    implementation(project(":platform:java:policy-as-code"))
}
```

Use the platform policy abstractions from product-owned enforcement and evaluation services instead of embedding ad hoc rule engines in each product module.

## Public API Surface

- Policy-as-code abstractions under `com.ghatana.platform.pac.*`
- Shared async policy-evaluation support
- Persistence-aware policy integration building blocks for higher-level services