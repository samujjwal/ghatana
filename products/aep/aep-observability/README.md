# @ghatana/aep-observability

## Purpose

`products/aep/aep-observability` contains AEP-specific tracing and orchestration instrumentation that does not belong in the generic platform observability module.

This module owns:

- AEP tracing provider bootstrap for pipeline, agent, and event spans
- orchestration instrumentation wrappers that apply AEP-specific MDC keys and span attributes
- regression tests for the above instrumentation surfaces

## Boundaries

- Reuses `platform:java:observability` for the shared observability foundation
- Stays product-specific because the span names, MDC keys, and orchestration semantics are AEP-owned
- Does not own HTTP endpoints, runtime orchestration, or analytics persistence

## Verification

```bash
./gradlew :products:aep:aep-observability:test
```