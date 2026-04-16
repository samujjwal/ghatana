# platform:java:audit

Canonical package: `com.ghatana.platform.audit.*`

## Purpose

`platform:java:audit` provides platform-neutral audit abstractions for recording, querying, and tracking security- and governance-relevant events without coupling products directly to a specific storage or transport implementation.

## Dependencies

- `platform:java:core` for shared platform types
- `platform:java:domain` for domain model alignment
- `platform:java:observability` for audit instrumentation hooks
- ActiveJ promise APIs for async public contracts

## Usage

Add the module as a dependency:

```kotlin
dependencies {
    implementation(project(":platform:java:audit"))
}
```

Use the platform audit abstractions from product code and supply the concrete persistence integration at the product boundary.

## Public API Surface

- Platform audit contracts under `com.ghatana.platform.audit.*`
- Async interfaces that return ActiveJ `Promise` values
- Product-neutral event tracking abstractions intended to be extended by product-owned adapters