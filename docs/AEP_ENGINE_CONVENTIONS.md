# AEP Engine Conventions

This document captures the consistency rules applied during the 2026-03 AEP audit remediation.

## Naming

- Use `Aep` as the product prefix in Java types to match the established module namespace.
- Prefer domain-specific names such as `AepTraceContext`, `AepConsentCache`, and `AepRateLimiter` over generic names like `Manager` or `Helper`.
- Reserve all-caps `AEP` for prose, headings, and metric namespaces, not Java type names.

## Package Structure

- Keep engine runtime behavior in `com.ghatana.aep` and focused subpackages such as:
  - `config`
  - `cache`
  - `consent`
  - `delivery`
  - `error`
  - `health`
  - `lifecycle`
  - `metrics`
  - `ratelimit`
  - `tracing`
  - `version`
- Keep product-specific adapter code in product modules such as `aep-event-cloud` and `aep-connectors` instead of pushing transport logic into `aep-engine`.

## Builders

- Builder methods should use direct, fluent names that match the runtime concern they configure.
- Duration-based settings should be accepted as `Duration` in builder APIs and normalized internally to primitive config values.
- Optional features should expose both an enable/disable switch and explicit tuning knobs where relevant.
- New AEP builders should validate their required state at `build()` time and reject invalid numeric values early.

## Logging

- Use `debug` for per-event flow details such as pattern matching, idempotency suppression, and sequence-order diagnostics.
- Use `info` for lifecycle transitions such as startup, shutdown, pipeline submission, and successful hot reload.
- Use `warn` for handled operational issues such as subscriber failures, missing optional configuration, and partial downstream delivery failures.
- Use `error` when an operation fails and caller-visible behavior is affected.

## Implemented Runtime Config Options

The AEP engine now supports the following runtime configuration keys via `Aep.AepConfig.customConfig`:

- `idempotencyTtlSeconds`
- `idempotencyMaxKeysPerTenant`
- `consentProvider`
- `asyncTimeoutMs`
- `rateLimitEnabled`
- `rateLimitMaxRequestsPerMinute`
- `rateLimitBurstSize`
- `rateLimitWindowSeconds`
- `consentCacheTtlSeconds`
- `consentCacheMaxEntries`
- `patternCacheTtlSeconds`
- `shutdownDrainTimeoutMs`
- `hotReloadConfigPath`
- `hotReloadCheckIntervalMs`
- `currentEventVersion`
- `minSupportedEventVersion`

## Hot Reload Scope

Only safe operational settings are hot-reloaded:

- anomaly threshold
- tracing enablement
- async timeout
- consent-cache TTL
- rate-limit enablement and capacity

Structural settings such as worker thread count, event-cloud transport selection, and pipeline capacity remain startup-only.
