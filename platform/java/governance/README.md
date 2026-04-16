# platform:java:governance

Canonical packages: `com.ghatana.platform.governance.*`, `com.ghatana.governance.*`

## Purpose

`platform:java:governance` provides shared governance contracts and enforcement primitives such as tenant-aware policy checks, governance-boundary abstractions, and cross-cutting controls that products can apply without duplicating infrastructure.

## Dependencies

- `platform:java:core` for shared runtime and model types
- `platform:contracts` for common cross-module contracts
- ActiveJ promise APIs for async governance flows
- Jackson for configuration and policy materialization

## Usage

Add the module as a dependency:

```kotlin
dependencies {
    implementation(project(":platform:java:governance"))
}
```

Use the governance contracts from product-owned services, repositories, and filters, while keeping product-specific rules and adapters in the owning product area.

## Public API Surface

- Governance abstractions under `com.ghatana.platform.governance.*`
- Shared governance model and policy packages under `com.ghatana.governance.*`
- Public async contracts intended for product and platform integrations